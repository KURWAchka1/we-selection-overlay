package org.marshal.wesotest;

import com.mojang.blaze3d.platform.InputConstants;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.neoforge.NeoForgeAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewController;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewSnapshot;

@Mod("weso_command_test")
public final class NeoCommandTest {
    private static final BlockVector3 MIN = BlockVector3.at(0, 66, 0), MAX = BlockVector3.at(5, 71, 5);
    private int ticks, stage, wait;
    private CompletableFuture<?> setup;
    private CompletableFuture<?> selectionChecks;
    private Object overlay, clipboardSource, copied, random;
    private int latencyIndex;
    private long started;
    private final long[] latency = new long[8];
    private long expectedLargeChanges;
    private int replacementIndex;
    private int regionCommandIndex;
    private static final List<String> REGION_COMMANDS = List.of("//center gold_block", "//naturalize", "//hollow 1", "//line gold_block", "//line -h gold_block 1");
    private static final long[] REGION_COUNTS = {8, 144, 216, 6, 36};
    private Object floraSample;
    private final java.util.concurrent.atomic.AtomicBoolean changingGrass = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicInteger grassMutations = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicReference<Throwable> grassFailure = new java.util.concurrent.atomic.AtomicReference<>();
    private volatile Object grassSource;
    private long grassEpoch;
    private List<?> streamingChunks;
    private int streamingIndex;
    private boolean streamingDisplayed;
    private static final List<String> REPLACEMENTS = List.of("stone_button[face=floor]", "oak_slab[type=bottom]", "oak_stairs[facing=east]", "oak_fence", "glass_pane", "oak_trapdoor[open=true]", "77", "35:14");
    private long previousFrame;
    private final java.util.ArrayList<Long> idleFrames = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> calculationFrames = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> largeRenderFrames = new java.util.ArrayList<>();

    public NeoCommandTest() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener(this::serverTick);
    }
    private void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!changingGrass.get() || grassSource == null) return;
        try {
            Object job = field(grassSource, "job");
            if (job == null) return;
            var memory = (org.marshal.weaddon_lightposition.runtime.mc26_2.preview.PreviewExtent) field(job, "memory");
            var original = memory.original().get(MIN);
            if (original == null) return;
            var actor = NeoForgeAdapter.get().fromNativePlayer(event.getServer().getPlayerList().getPlayers().getFirst());
            var type = original.getBlockType() == BlockTypes.GRASS_BLOCK ? BlockTypes.DIRT : BlockTypes.GRASS_BLOCK;
            actor.getWorld().setBlock(MIN, type.getDefaultState());
            grassMutations.incrementAndGet();
        } catch (Throwable ex) { grassFailure.compareAndSet(null, ex); changingGrass.set(false); }
    }
    private void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        Minecraft client = Minecraft.getInstance(); client.options.pauseOnLostFocus = false;
        long now = System.nanoTime(), elapsed = now - previousFrame;
        if (previousFrame != 0 && client.level != null) {
            var target = stage == 7 ? idleFrames : stage == 16 ? calculationFrames : stage == 18 ? largeRenderFrames : null;
            if (target != null && target.size() < 512) target.add(elapsed);
        }
        previousFrame = now;
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen) screen.onClose();
    }
    private void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        // Includes the bounded palette/selection input suites before command assertions.
        check(++ticks < 4800, "Timeout, stage " + stage);
        if (client.level == null || client.player == null) return;
        if (wait > 0) { wait--; return; }
        var controller = CommandPreviewController.instance();
        if (stage == 0) {
            client.options.renderDistance().set(4); client.options.guiScale().set(2); client.options.framerateLimit().set(100); client.resizeGui();
            setup = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer(); server.getWorldData().setAllowCommands(true);
                for (String command : List.of("fill -8 64 -8 24 64 24 stone", "fill 0 66 0 5 71 5 stone", "gamemode spectator @p", "tp @p 12 77 16 145 30", "time set day"))
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                var actor = NeoForgeAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                var session = WorldEdit.getInstance().getSessionManager().get(actor);
                session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), MIN, MAX));
                var clipboard = new BlockArrayClipboard(new CuboidRegion(MIN, MIN.add(1, 1, 1)));
                clipboard.setOrigin(MIN);
                try { for (var point : clipboard.getRegion()) clipboard.setBlock(point, BlockTypes.GOLD_BLOCK.getDefaultState()); }
                catch (Exception ex) { throw new RuntimeException(ex); }
                session.setClipboard(new ClipboardHolder(clipboard));
            });
            stage = 1;
        } else if (stage == 1) {
            if (!setup.isDone()) return; setup.join();
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(79)); stage = 2;
        } else if (stage == 2) {
            if (client.gui.screen() == null || !client.gui.screen().getClass().getSimpleName().equals("OverlaySettingsScreen")) return;
            overlay = field(client.gui.screen(), "overlayClient");
            check(!(boolean) call(overlay, "clipboardPreviewEnabled"), "Clipboard must default off");
            call(overlay, "setClipboardPreviewEnabled", true);
            clipboardSource = field(field(overlay, "clipboardPreviewController"), "source");
            client.gui.setScreen(null); stage = 3; wait = 40;
        } else if (stage == 3) {
            if (selectionChecks == null) {
                selectionChecks = CompletableFuture.runAsync(() -> SelectionEditingChecks.run(client, overlay, clipboardSource,
                        name -> client.submit(() -> screenshot(client, name + ".png")).join()));
                return;
            }
            if (!selectionChecks.isDone()) return;
            selectionChecks.join();
            if (setup != null) {
                setup = client.getSingleplayerServer().submit(() -> {
                    var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                    var session = WorldEdit.getInstance().getSessionManager().get(actor);
                    session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), MIN, MAX));
                    session.dispatchCUISelection(actor);
                    var server = client.getSingleplayerServer();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @p 12 77 16 145 30");
                });
                wait = 10; stage = 39; return;
            }
        } else if (stage == 39) {
            if (!setup.isDone()) return; setup.join();
            copied = call(clipboardSource, "snapshot");
            check(!((List<?>) call(copied, "blocks")).isEmpty(), "Clipboard failed on NeoForge with WorldEdit");
            screenshot(client, "clipboard-before-command.png");
            controller.setEnabled(true); client.gui.setScreen(new ChatScreen("//set diamond_block", false)); stage = 4;
        } else if (stage == 4) {
            if (!ready(controller)) return;
            check(controller.snapshot().statistics().affected() == 216, "Set statistics");
            check(controller.ownsPreviewChannel(), "Command must suppress clipboard");
            check((boolean) call(overlay, "clipboardPreviewEnabled"), "Command changed clipboard preference");
            stage = 5; wait = 20;
        } else if (stage == 5) {
            check(copied == call(clipboardSource, "snapshot"), "Suppression discarded clipboard cache");
            check(!((java.util.Map<?, ?>) field(field(controller, "renderer"), "sections")).isEmpty(), "Command model buffer is empty");
            screenshot(client, "command-set-ghost.png");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                check(actor.getWorld().getBlock(MIN).getBlockType() == BlockTypes.STONE, "Preview wrote the world");
            });
            client.gui.setScreen(new ChatScreen("//replace 1 " + REPLACEMENTS.getFirst(), false)); stage = 20;
        } else if (stage == 20) {
            if (!ready(controller) || !built(controller)) return;
            check(controller.snapshot().statistics().replaced() == 216, "Partial model or numeric ID replacement failed");
            stage = 21; wait = 12;
        } else if (stage == 21) {
            var inside = controller.snapshot().cells().getFirst().position();
            check(org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewTerrainState.hides(inside), "Replacement source remains visible");
            check(!org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewTerrainState.hides(inside.offset(-20, 0, 0)), "Unrelated source terrain hidden");
            check(client.level.getBlockState(inside).getBlock() == net.minecraft.world.level.block.Blocks.STONE, "Client block data mutated");
            if (replacementIndex == 0) screenshot(client, "command-button-replacement.png");
            if (++replacementIndex < REPLACEMENTS.size()) {
                client.gui.setScreen(new ChatScreen("//replace 1 " + REPLACEMENTS.get(replacementIndex), false)); stage = 20;
            } else {
                client.gui.screen().keyPressed(new KeyEvent(256, 0, 0));
                check(!org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewTerrainState.hides(inside), "Escape retained hidden terrain");
                setup = client.getSingleplayerServer().submit(() -> {
                    var server = client.getSingleplayerServer();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fill 0 66 0 5 72 5 air");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fill 0 66 0 5 66 5 grass_block");
                    var actor = NeoForgeAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                    WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), MIN, MIN.add(5, 0, 5)));
                });
                stage = 30;
            }
        } else if (stage == 30) {
            if (!setup.isDone()) return; setup.join();
            client.gui.setScreen(new ChatScreen("//flora 100", false)); stage = 31;
        } else if (stage == 31) {
            if (!ready(controller) || !built(controller)) return;
            check(controller.snapshot().random() && controller.snapshot().statistics().added() == 36, "Flora surface/density mismatch");
            floraSample = controller.snapshot();
            for (int i = 0; i < 30; i++) controller.selectionChanged();
            stage = 32; wait = 20;
        } else if (stage == 32) {
            check(floraSample == controller.snapshot(), "Flora sample re-rolled");
            screenshot(client, "command-flora.png");
            var plants = controller.snapshot().cells();
            setup = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer();
                var actor = NeoForgeAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                for (var cell : plants) check(actor.getWorld().getBlock(BlockVector3.at(cell.position().getX(), 67, cell.position().getZ())).getBlockType().getMaterial().isAir(), "Flora wrote server world");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "fill 0 66 0 5 71 5 stone");
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), MIN, MAX));
            });
            stage = 33;
        } else if (stage == 33) {
            if (!setup.isDone()) return; setup.join();
            client.gui.setScreen(new ChatScreen(REGION_COMMANDS.getFirst(), false)); stage = 40;
        } else if (stage == 40) {
            if (!ready(controller) || !built(controller)) return;
            check(controller.snapshot().statistics().affected() == REGION_COUNTS[regionCommandIndex], "Region command mismatch: " + REGION_COMMANDS.get(regionCommandIndex)
                    + " got " + controller.snapshot().statistics().affected());
            stage = 41; wait = 10;
        } else if (stage == 41) {
            if (++regionCommandIndex < REGION_COMMANDS.size()) {
                client.gui.setScreen(new ChatScreen(REGION_COMMANDS.get(regionCommandIndex), false)); stage = 40;
            } else {
                screenshot(client, "command-line.png");
                client.gui.setScreen(new ChatScreen("//replace stone 70%dirt,30%grass_block", false)); stage = 6;
            }
        } else if (stage == 6) {
            if (!ready(controller)) return; setup.join();
            check(controller.snapshot().random(), "Percentage mixture not recognized"); random = controller.snapshot();
            for (int i = 0; i < 30; i++) controller.selectionChanged();
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                WorldEdit.getInstance().getSessionManager().get(actor).dispatchCUISelection(actor);
            });
            stage = 7; wait = 25;
        } else if (stage == 7) {
            check(random == controller.snapshot(), "Random sample re-rolled"); screenshot(client, "command-random-stable.png");
            client.gui.screen().keyPressed(new KeyEvent(256, 0, 0));
            check(!controller.ownsPreviewChannel(), "Escape did not restore clipboard");
            check((boolean) call(overlay, "clipboardPreviewEnabled"), "Escape changed clipboard preference"); stage = 8; wait = 20;
        } else if (stage == 8) {
            screenshot(client, "clipboard-after-command.png");
            call(overlay, "setClipboardPreviewEnabled", false);
            started = System.nanoTime();
            client.gui.setScreen(new ChatScreen("//set gold_block", false)); stage = 12;
        } else if (stage == 9) {
            if (!ready(controller)) return;
            client.gui.screen().keyPressed(new KeyEvent(257, 0, 0)); stage = 10; wait = 25;
        } else if (stage == 10) {
            check(client.gui.screen() == null, "Enter did not close chat");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                check(actor.getWorld().getBlock(MIN).getBlockType() == BlockTypes.DIAMOND_BLOCK, "Normal command submission changed");
            }); stage = 11;
        } else if (stage == 11) {
            if (!setup.isDone()) return; setup.join();
            System.out.println("WESO NEO COMMAND PASS: local WorldEdit, ghost rendering, zero preview writes, stable mixtures, clipboard priority/cache/restore, normal Enter");
            client.stop(); stage = 12;
        } else if (stage == 12) {
            if (!ready(controller) || !built(controller)) return;
            latency[latencyIndex++] = (System.nanoTime() - started) / 1_000_000;
            if (latencyIndex < latency.length) {
                started = System.nanoTime();
                chatInput((ChatScreen) client.gui.screen()).setValue(latencyIndex % 2 == 0 ? "//set gold_block" : "//set diamond_block");
                return;
            }
            System.out.println("WESO NEO COMMAND LATENCY visible-ms=" + java.util.Arrays.toString(latency));
            java.util.Arrays.sort(latency);
            check(latency[latency.length / 2] < 250, "Warm preview latency regressed above 250 ms");
            check(((java.util.Map<?, ?>) field(field(controller, "renderer"), "models")).size() >= 2, "Model cache was cleared by editing");
            setup = client.reloadResourcePacks(); stage = 13;
        } else if (stage == 13) {
            if (!setup.isDone()) return; setup.join(); stage = 14; wait = 20;
        } else if (stage == 14) {
            if (!ready(controller) || !built(controller)) return;
            check(!((java.util.Map<?, ?>) field(field(controller, "renderer"), "sections")).isEmpty(), "Reload lost model geometry");
            check(((java.util.Map<?, ?>) field(field(controller, "renderer"), "models")).size() <= 256, "Unbounded model cache");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                expectedLargeChanges = 0;
                for (var point : new CuboidRegion(MIN, MIN.add(47, 47, 47))) {
                    if (actor.getWorld().getBlock(point).getBlockType() != BlockTypes.DIAMOND_BLOCK) expectedLargeChanges++;
                }
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(),
                        new CuboidRegionSelector(actor.getWorld(), MIN, MIN.add(47, 47, 47)));
            }); stage = 15;
        } else if (stage == 15) {
            if (!setup.isDone()) return; setup.join(); started = System.nanoTime();
            var input = chatInput((ChatScreen) client.gui.screen());
            for (int i = 0; i < 100; i++) input.setValue(i % 2 == 0 ? "//set dirt" : "//set diamond_block");
            check(controller.snapshot().cells().isEmpty(), "Burst retained obsolete geometry"); stage = 16;
        } else if (stage == 16) {
            if (!ready(controller)) return;
            check(controller.snapshot().total() == 110_592 && controller.snapshot().statistics().affected() == expectedLargeChanges, "Large selection count: "
                    + controller.snapshot().statistics().affected() + " total=" + controller.snapshot().total()
                    + " draft=" + field(controller, "draft") + " input=" + chatInput((ChatScreen) client.gui.screen()).getValue());
            check(!controller.snapshot().limited(), "Large visual output was truncated");
            check(controller.snapshot().cells().size() >= 13_255, "Large surface is incomplete");
            check(controller.snapshot().cells().stream().allMatch(c -> c.after() == net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState()), "Stale burst result published");
            System.out.println("WESO NEO COMMAND LARGE 110592 cells ready-ms=" + (System.nanoTime() - started) / 1_000_000);
            stage = 18; wait = 30;
        } else if (stage == 18) {
            check(built(controller), "Large geometry never completed");
            System.out.println("WESO NEO COMMAND FRAME median-ms idle=" + median(idleFrames)
                    + " calculating=" + median(calculationFrames) + " large-render=" + median(largeRenderFrames));
            check(median(calculationFrames) < Math.max(50, median(idleFrames) * 3), "Calculation caused a sustained frame stall");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(),
                        new CuboidRegionSelector(actor.getWorld(), MIN, MIN.add(1, 1, 1)));
            }); stage = 17;
        } else if (stage == 17) {
            if (!setup.isDone()) return; setup.join(); controller.selectionChanged();
            if (!ready(controller) || controller.snapshot().statistics().affected() != 8) return;
            client.gui.setScreen(null);
            setup = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule randomTickSpeed 0");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule minecraft:random_tick_speed 0");
                var actor = NeoForgeAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).maxBlocks(-1).build()) {
                    edit.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none());
                    edit.setBlocks(new CuboidRegion(MIN, MIN.add(39, 39, 39)), BlockTypes.GRASS_BLOCK.getDefaultState());
                } catch (Exception ex) { throw new RuntimeException(ex); }
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(),
                        new CuboidRegionSelector(actor.getWorld(), MIN, MIN.add(39, 39, 39)));
            });
            stage = 80;
        } else if (stage == 80) {
            if (!setup.isDone()) return; setup.join();
            grassSource = field(controller, "source"); changingGrass.set(true);
            client.gui.setScreen(new ChatScreen("//replace grass_block dirt", false)); stage = 81;
        } else if (stage == 81) {
            if (controller.snapshot().state() != CommandPreviewSnapshot.State.COMPUTING) return;
            grassEpoch = ((java.util.concurrent.atomic.AtomicLong) field(grassSource, "epoch")).get(); stage = 82;
        } else if (stage == 82) {
            if (grassFailure.get() != null) throw new AssertionError("Changing-grass fixture failed", grassFailure.get());
            check(((java.util.concurrent.atomic.AtomicLong) field(grassSource, "epoch")).get() == grassEpoch,
                    "One grass block restarted the complete large scan");
            if (!ready(controller)) return;
            changingGrass.set(false);
            check(grassMutations.get() > 5, "Changing-source test did not run multiple ticks");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                try { actor.getWorld().setBlock(MIN, BlockTypes.GRASS_BLOCK.getDefaultState()); }
                catch (Exception ex) { throw new RuntimeException(ex); }
            }); stage = 83;
        } else if (stage == 83) {
            if (!setup.isDone()) return; setup.join();
            if (!ready(controller) || controller.snapshot().statistics().replaced() != 64_000) return;
            check(controller.snapshot().cells().stream().allMatch(c -> c.after() == net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
                    "Grass repair kept an obsolete model");
            System.out.println("WESO NEO CHANGING GRASS PASS: 64000 cells, mutations=" + grassMutations.get() + ", preserved source epoch");
            client.gui.setScreen(null);
            streamingChunks = ((List<?>) field(grassSource, "loaded")).stream().filter(c -> (int) field(c, "x") >= 0 && (int) field(c, "x") <= 2
                    && (int) field(c, "z") >= 0 && (int) field(c, "z") <= 2).toList();
            check(streamingChunks.size() == 9, "Native streaming fixture chunks are not loaded");
            for (int i = 1; i < streamingChunks.size(); i++) chunkEvent(grassSource, streamingChunks.get(i), false);
            client.gui.setScreen(new ChatScreen("//replace grass_block dirt", false)); stage = 84;
        } else if (stage == 84) {
            if (controller.snapshot().state() != CommandPreviewSnapshot.State.COMPUTING) return;
            grassEpoch = ((java.util.concurrent.atomic.AtomicLong) field(grassSource, "epoch")).get();
            streamingIndex = 1; stage = 85;
        } else if (stage == 85) {
            check(((java.util.concurrent.atomic.AtomicLong) field(grassSource, "epoch")).get() == grassEpoch,
                    "Arriving chunk discarded the in-flight capture");
            if (streamingIndex < streamingChunks.size()) chunkEvent(grassSource, streamingChunks.get(streamingIndex++), true);
            else stage = 86;
        } else if (stage == 86) {
            if (streamingDisplayed) check(controller.snapshot().state() == CommandPreviewSnapshot.State.READY, "Background chunks cleared visible geometry");
            streamingDisplayed |= ready(controller);
            if (!streamingDisplayed || controller.snapshot().total() != 64_000 || controller.snapshot().statistics().replaced() != 64_000) return;
            System.out.println("WESO NEO STREAMING PASS: 9 chunks, preserved epoch, complete background expansion");
            client.gui.setScreen(null);
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).maxBlocks(-1).build()) {
                    edit.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none());
                    edit.setBlocks(new CuboidRegion(MIN, MIN.add(39, 39, 39)), BlockTypes.AIR.getDefaultState());
                    edit.setBlocks(new CuboidRegion(MIN, MAX), BlockTypes.STONE.getDefaultState());
                } catch (Exception ex) { throw new RuntimeException(ex); }
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), MIN, MIN.add(1, 1, 1)));
            }); stage = 87;
        } else if (stage == 87) {
            if (!setup.isDone()) return; setup.join();
            client.gui.setScreen(new ChatScreen("//set diamond_block", false)); stage = 9;
        }
    }
    private static void chunkEvent(Object source, Object chunk, boolean present) {
        try {
            var method = source.getClass().getDeclaredMethod("chunkChanged", Object.class, int.class, int.class, boolean.class);
            method.setAccessible(true);
            method.invoke(source, field(source, "trackedLevel"), field(chunk, "x"), field(chunk, "z"), present);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static double median(java.util.ArrayList<Long> values) {
        check(!values.isEmpty(), "No frame samples");
        var sorted = new java.util.ArrayList<>(values); java.util.Collections.sort(sorted);
        return sorted.get(sorted.size() / 2) / 1_000_000.0;
    }
    private static boolean built(CommandPreviewController controller) {
        Object renderer = field(controller, "renderer");
        return field(renderer, "prepared") == controller.snapshot()
                && (int) field(renderer, "buildIndex") == controller.snapshot().cells().size();
    }
    private static net.minecraft.client.gui.components.EditBox chatInput(ChatScreen screen) {
        try {
            for (var f : ChatScreen.class.getDeclaredFields()) if (net.minecraft.client.gui.components.EditBox.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true); return (net.minecraft.client.gui.components.EditBox) f.get(screen);
            }
            throw new AssertionError("Missing native chat input");
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static boolean ready(CommandPreviewController controller) {
        var state = controller.snapshot().state();
        check(state != CommandPreviewSnapshot.State.ERROR && state != CommandPreviewSnapshot.State.UNAVAILABLE, "Preview failed: " + controller.snapshot());
        return state == CommandPreviewSnapshot.State.READY;
    }
    private static void screenshot(Minecraft client, String name) { Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1, ignored -> {}); }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object call(Object object, String name, Object... args) {
        try {
            for (var method : object.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(object, args);
            throw new AssertionError("Missing method " + name);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

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
    private Object overlay, clipboardSource, copied, random;
    private int latencyIndex;
    private long started;
    private final long[] latency = new long[8];
    private long expectedLargeChanges;
    private long previousFrame;
    private final java.util.ArrayList<Long> idleFrames = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> calculationFrames = new java.util.ArrayList<>();
    private final java.util.ArrayList<Long> largeRenderFrames = new java.util.ArrayList<>();

    public NeoCommandTest() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
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
        check(++ticks < 2400, "Timeout, stage " + stage);
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
            client.gui.setScreen(new ChatScreen("//replace stone 70%dirt,30%grass_block", false)); stage = 6;
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
            stage = 9;
        }
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

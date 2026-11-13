package org.marshal.wesotest.legacy;

import java.nio.file.Files;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.WorldEditPointOverlayClient;
import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewController;
import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewSource;
import org.marshal.weaddon_lightposition.client.clipboard.ClipboardPreviewBounds;
import org.marshal.weaddon_lightposition.client.overlay.SelectionBlockAnalyzer;
import org.marshal.weaddon_lightposition.client.overlay.SelectionManager;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewController;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewSnapshot;
import org.marshal.weaddon_lightposition.client.screen.OverlayColorSettingsScreen;
import org.marshal.weaddon_lightposition.client.screen.OverlaySettingsScreen;
import org.marshal.weaddon_lightposition.client.screen.SelectionBlocksScreen;
import org.marshal.weaddon_lightposition.client.selection.WorldEditSelectionEditSource;
import org.marshal.weaddon_lightposition.selection.CuboidDraft;

public final class LegacySmokeClient implements ClientModInitializer {
    private static final BlockPos SOURCE = new BlockPos(2, -59, 5);
    private static final BlockPos NEIGHBOR = SOURCE.east();
    private static final String WORLD = "weso-1204-fixture";
    private static final long DEADLINE = System.nanoTime() + 180_000_000_000L;
    private boolean withWorldEdit, opening;
    private int phase, delay;
    private volatile boolean sessionCui, editAccepted;
    private ClipboardPreviewBounds originalClipboardBounds;
    private WorldEditPointOverlayClient overlay;
    private SelectionManager selection;
    private ClipboardPreviewController clipboard;

    @Override
    public void onInitializeClient() {
        require("active".equals(System.getProperty("weso.test.cursorIsolation")) || System.getenv("WESO_TEST_DESKTOP") != null,
                "unguarded process");
        withWorldEdit = "withWorldEdit".equals(System.getProperty("weso1204.profile"));
        require(Runtime.version().feature() == 17, "wrong JVM");
        require(FabricLoader.getInstance().isModLoaded("worldedit") == withWorldEdit, "wrong fixture dependencies");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        try {
            if (phase < 0) return;
            require(System.nanoTime() < DEADLINE, "timeout at phase " + phase + ": " + CommandPreviewController.instance().snapshot());
            if (delay > 0) { delay--; return; }
            if (!opening && client.currentScreen instanceof AccessibilityOnboardingScreen screen && client.getOverlay() == null) {
                screen.close();
                return;
            }
            if (!opening && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                require("active".equals(System.getProperty("weso.test.cursorIsolation")), "GLFW guard did not attach");
                opening = true;
                client.options.pauseOnLostFocus = false;
                client.options.getGuiScale().setValue(2);
                client.options.getViewDistance().setValue(5);
                var config = DataConfiguration.SAFE_MODE;
                var info = new LevelInfo("WESO 1.20.4 smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), config);
                client.createIntegratedServerLoader().createAndStart(WORLD, info, new GeneratorOptions(6L, false, false),
                        registries -> registries.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createDimensionsRegistryHolder(),
                        client.currentScreen);
                return;
            }
            if (client.player == null || client.world == null || client.getServer() == null) return;
            switch (phase) {
                case 0 -> {
                    if (client.currentScreen != null) return;
                    KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_O));
                    next(4);
                }
                case 1 -> {
                    require(client.currentScreen instanceof OverlaySettingsScreen, "settings key did not open settings");
                    overlay = (WorldEditPointOverlayClient) field(client.currentScreen, "overlayClient");
                    selection = (SelectionManager) field(overlay, "selectionManager");
                    clipboard = (ClipboardPreviewController) field(overlay, "clipboardPreviewController");
                    require(!overlay.clipboardPreviewEnabled(), "clipboard preview must default off");
                    capture(client, "settings");
                    client.setScreen(new OverlayColorSettingsScreen(overlay, client.currentScreen));
                    next(12);
                }
                case 2 -> {
                    require(client.currentScreen instanceof OverlayColorSettingsScreen, "palette did not open");
                    capture(client, "palette");
                    client.setScreen(null);
                    if (!withWorldEdit) {
                        require(!clipboard.available(), "WorldEdit provider present without WorldEdit");
                        finish(client);
                        return;
                    }
                    command(client, "tp @s 2.5 -60 0.5 0 0");
                    command(client, "setblock 2 -59 5 minecraft:stone");
                    command(client, "setblock 3 -59 5 minecraft:dirt");
                    next(14);
                }
                case 3 -> {
                    if (!client.world.getBlockState(SOURCE).isOf(Blocks.STONE) || !client.world.getBlockState(NEIGHBOR).isOf(Blocks.DIRT)) return;
                    command(client, "/pos1 2,-59,5");
                    command(client, "/pos2 3,-59,5");
                    client.getServer().execute(() -> {
                        sessionCui = WorldEditSmoke.hasCui(client);
                    });
                    next(5);
                }
                case 4 -> {
                    if (selection.primaryRegionForAnalysis() == null
                            || selection.primaryRegionForAnalysis().boundsForAnalysis() == null) return;
                    require(sessionCui && (int) field(overlay, "receivedSelectionPackets") > 0, "selection did not arrive via CUI");
                    require(selection.primaryRegionForAnalysis().boundsForAnalysis().volume() == 2, "wrong selection volume");
                    overlay.setRulerEnabled(true);
                    next(10);
                }
                case 5 -> {
                    capture(client, "selection");
                    KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_B));
                    next(8);
                }
                case 6 -> {
                    require(client.currentScreen instanceof SelectionBlocksScreen, "block browser did not open");
                    var analyzer = (SelectionBlockAnalyzer) field(overlay, "selectionBlockAnalyzer");
                    if (analyzer.snapshot().selectedCells() != 2) return;
                    require(analyzer.snapshot().entries().size() == 2, "wrong block counts");
                    capture(client, "inspector");
                    client.setScreen(null);
                    var isolate = overlay.getClass().getDeclaredMethod("setIsolatedBlockIds", Set.class);
                    isolate.setAccessible(true);
                    isolate.invoke(overlay, Set.of("minecraft:stone"));
                    require(BlockIsolationController.shouldHideBlock(Blocks.DIRT.getDefaultState(), NEIGHBOR), "isolation mask missing");
                    require(!BlockIsolationController.shouldHideBlock(Blocks.DIRT.getDefaultState(), NEIGHBOR.east()), "isolation escaped region");
                    next(20);
                }
                case 7 -> {
                    require(client.world.getBlockState(NEIGHBOR).isOf(Blocks.DIRT), "isolation altered world");
                    capture(client, "isolation");
                    next(10);
                }
                case 8 -> {
                    var isolate = overlay.getClass().getDeclaredMethod("setIsolatedBlockIds", Set.class);
                    isolate.setAccessible(true);
                    isolate.invoke(overlay, Set.of());
                    CommandPreviewController.instance().setEnabled(true);
                    client.setScreen(new ChatScreen("//replace 1 stone_button"));
                    next(8);
                }
                case 9 -> {
                    var preview = CommandPreviewController.instance().snapshot();
                    if (preview.state() == CommandPreviewSnapshot.State.ERROR) throw new AssertionError(preview.toString());
                    if (preview.state() != CommandPreviewSnapshot.State.READY) return;
                    require(preview.cells().size() == 1 && preview.cells().get(0).after().isOf(Blocks.STONE_BUTTON),
                            "numeric ID replacement preview mismatch: " + preview);
                    require(client.world.getBlockState(SOURCE).isOf(Blocks.STONE), "draft edited world");
                    capture(client, "command-preview");
                    client.setScreen(null);
                    command(client, "/copy");
                    command(client, "tp @s 1.5 -60 3.5 0 0");
                    overlay.setClipboardPreviewEnabled(true);
                    next(15);
                }
                case 10 -> {
                    if (!clipboard.active()) return;
                    originalClipboardBounds = ((ClipboardPreviewSource) field(clipboard, "source")).snapshot().bounds();
                    capture(client, "clipboard");
                    next(10);
                }
                case 11 -> {
                    client.setScreen(new ChatScreen("//rotate 90"));
                    next(8);
                }
                case 12 -> {
                    if (!"clipboard_ready".equals(clipboard.transformStatus().reason())) return;
                    var rotated = ((ClipboardPreviewSource) field(clipboard, "source")).snapshot().bounds();
                    require(rotated.maxZ() - rotated.minZ() == originalClipboardBounds.maxX() - originalClipboardBounds.minX()
                            && rotated.maxX() - rotated.minX() == originalClipboardBounds.maxZ() - originalClipboardBounds.minZ(),
                            "rotation did not update rendered bounds");
                    capture(client, "clipboard-rotate");
                    client.setScreen(new ChatScreen("//flip east"));
                    next(8);
                }
                case 13 -> {
                    if (!"clipboard_ready".equals(clipboard.transformStatus().reason())) return;
                    client.setScreen(null);
                    overlay.setClipboardPreviewEnabled(false);
                    var edit = new WorldEditSelectionEditSource();
                    edit.read(client, original -> {
                        require(original.ready(), "selection editing source unavailable: " + original.reason());
                        CuboidDraft moved = new CuboidDraft(4, -59, 5, 5, -59, 5);
                        edit.commit(client, original, moved, null, null,
                                result -> editAccepted = result.ready() && moved.equals(result.bounds()));
                    });
                    next(12);
                }
                case 14 -> {
                    if (!editAccepted || selection.primaryRegionForAnalysis() == null
                            || selection.primaryRegionForAnalysis().boundsForAnalysis().minX() != 4) return;
                    require(client.world.getBlockState(SOURCE).isOf(Blocks.STONE)
                            && client.world.getBlockState(NEIGHBOR).isOf(Blocks.DIRT), "editing selection moved blocks");
                    command(client, "/deselect");
                    next(8);
                }
                case 15 -> {
                    var region = selection.primaryRegionForAnalysis();
                    if (region != null && region.boundsForAnalysis() != null) return;
                    finish(client);
                }
                default -> throw new AssertionError("unexpected phase " + phase);
            }
        } catch (Exception | AssertionError failure) {
            throw new IllegalStateException("WESO 1.20.4 smoke failed at phase " + phase, failure);
        }
    }

    private void next(int wait) {
        System.out.println("WESO 1.20.4 fixture completed phase " + phase);
        phase++;
        delay = wait;
    }
    private static void command(MinecraftClient client, String command) { client.getNetworkHandler().sendChatCommand(command); }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void require(boolean value, String failure) { if (!value) throw new AssertionError(failure); }
    private static void capture(MinecraftClient client, String name) {
        var path = client.runDirectory.toPath().resolve("proof").resolve(name + ".png");
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            Files.createDirectories(path.getParent());
            image.writeTo(path);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Could not capture " + name, failure); }
    }
    private void finish(MinecraftClient client) {
        phase = -1;
        System.out.println("WESO FABRIC 1.20.4 PASS: " + System.getProperty("weso1204.profile"));
        client.scheduleStop();
    }
}

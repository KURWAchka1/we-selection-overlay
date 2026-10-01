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

    public NeoCommandTest() {
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }
    private void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        Minecraft client = Minecraft.getInstance(); client.options.pauseOnLostFocus = false;
        if (client.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen screen) screen.onClose();
    }
    private void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        check(++ticks < 2400, "Timeout, stage " + stage);
        if (client.level == null || client.player == null) return;
        if (wait > 0) { wait--; return; }
        var controller = CommandPreviewController.instance();
        if (stage == 0) {
            client.options.renderDistance().set(4); client.options.guiScale().set(2); client.resizeGui();
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
            check(!((List<?>) field(field(controller, "renderer"), "mesh")).isEmpty(), "Command model buffer is empty");
            screenshot(client, "command-set-ghost.png");
            setup = client.getSingleplayerServer().submit(() -> {
                var actor = NeoForgeAdapter.get().fromNativePlayer(client.getSingleplayerServer().getPlayerList().getPlayers().getFirst());
                check(actor.getWorld().getBlock(MIN).getBlockType() == BlockTypes.STONE, "Preview wrote the world");
            });
            client.gui.setScreen(new ChatScreen("//replace stone 70%dirt,30%grass_block", false)); stage = 6;
        } else if (stage == 6) {
            if (!ready(controller)) return; setup.join();
            check(controller.snapshot().random(), "Percentage mixture not recognized"); random = controller.snapshot(); stage = 7; wait = 25;
        } else if (stage == 7) {
            check(random == controller.snapshot(), "Random sample re-rolled"); screenshot(client, "command-random-stable.png");
            client.gui.screen().keyPressed(new KeyEvent(256, 0, 0));
            check(!controller.ownsPreviewChannel(), "Escape did not restore clipboard");
            check((boolean) call(overlay, "clipboardPreviewEnabled"), "Escape changed clipboard preference"); stage = 8; wait = 20;
        } else if (stage == 8) {
            screenshot(client, "clipboard-after-command.png");
            call(overlay, "setClipboardPreviewEnabled", false);
            client.gui.setScreen(new ChatScreen("//set diamond_block", false)); stage = 9;
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
        }
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

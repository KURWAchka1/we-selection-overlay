package org.marshal.wesotest;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.*;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.server.MinecraftServer;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewController;
import org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewSnapshot;
import org.marshal.weaddon_lightposition.runtime.mc26_2.preview.*;

public final class CommandGameTest implements FabricClientGameTest {
    private static final BlockVector3 MIN = BlockVector3.at(0, 66, 0), MAX = BlockVector3.at(5, 71, 5);

    @Override public void runTest(ClientGameTestContext context) {
        check(PreviewDraft.recognizes("  //replace stone dirt"), "Routing with arguments");
        check(PreviewDraft.recognizes("//set\tstone"), "Routing with tab");
        check(!PreviewDraft.recognizes("//setbiome stone"), "Unknown command reserved channel");
        Properties values = new Properties(); values.setProperty("opacity", "NaN");
        check(Float.isFinite(PreviewOptions.load(values).opacity()), "Nonfinite config accepted");
        context.runOnClient(client -> {
            client.options.renderDistance().set(4); client.options.guiScale().set(2);
            client.options.framerateLimit().set(100); client.resizeGui();
        });
        try (var world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("fill -8 64 -8 24 64 24 stone");
            world.getServer().runCommand("tp @p 12 77 16 145 30");
            world.getServer().runCommand("time set day");
            world.getConnection().waitForChunksRender();
            MinecraftServer server = context.computeOnClient(client -> client.getSingleplayerServer());
            onServer(world.getServer(), () -> { comparePrograms(server); select(server, MIN, MAX); return null; });
            List<?> history = onServer(world.getServer(), () -> history(server));
            context.runOnClient(client -> net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(79)));
            context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals("OverlaySettingsScreen"));
            Object overlay = context.computeOnClient(client -> field(client.gui.screen(), "overlayClient"));
            context.runOnClient(client -> {
                check(!(boolean) call(overlay, "clipboardPreviewEnabled"), "Clipboard must default off");
                call(overlay, "setClipboardPreviewEnabled", true);
                client.gui.setScreen(null);
            });
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                var clipboard = new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(new CuboidRegion(MIN, MIN.add(1, 1, 1)));
                clipboard.setOrigin(MIN);
                for (var p : clipboard.getRegion()) clipboard.setBlock(p, com.sk89q.worldedit.world.block.BlockTypes.GOLD_BLOCK.getDefaultState());
                WorldEdit.getInstance().getSessionManager().get(actor).setClipboard(new com.sk89q.worldedit.session.ClipboardHolder(clipboard));
                return null;
            });
            Object copiedSource = context.computeOnClient(client -> field(field(overlay, "clipboardPreviewController"), "source"));
            context.waitFor(client -> !((List<?>) call(call(copiedSource, "snapshot"), "blocks")).isEmpty());
            Object copied = context.computeOnClient(client -> call(copiedSource, "snapshot"));
            context.runOnClient(client -> { CommandPreviewController.instance().setEnabled(true); client.gui.setScreen(new ChatScreen("//set diamond_block", false)); });
            ready(context);
            context.runOnClient(client -> {
                var controller = CommandPreviewController.instance();
                check(controller.ownsPreviewChannel(), "Command did not own preview channel");
                check(controller.snapshot().statistics().affected() == 216, "Set counts mismatch");
            });
            onServer(world.getServer(), () -> {
                var player = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                check(player.getWorld().getBlock(MIN).getBlockType().id().equals("minecraft:stone"), "Preview wrote the world");
                return null;
            });
            context.waitTicks(12);
            context.runOnClient(client -> {
                check(copied == call(copiedSource, "snapshot"), "Command discarded clipboard cache");
                check((boolean) call(overlay, "clipboardPreviewEnabled"), "Command changed clipboard preference");
            });
            check(history.equals(onServer(world.getServer(), () -> history(server))), "Preview modified WorldEdit history");
            context.takeScreenshot("command-set-ghost");
            context.runOnClient(client -> {
                var input = chatInput((ChatScreen) client.gui.screen());
                for (String value : List.of("//set g", "//set gold", "//set gold_block", "//set diamond_block")) input.setValue(value);
                check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Typing did not discard obsolete geometry immediately");
            });
            ready(context);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().cells().stream().allMatch(c -> c.kind() == PreviewChangeKind.REPLACED), "Rapid typing published an obsolete result"));
            draft(context, "//replace stone 70%dirt,30%grass_block");
            ready(context);
            Object sample = context.computeOnClient(client -> CommandPreviewController.instance().snapshot());
            context.waitTicks(30);
            check(sample == context.computeOnClient(client -> CommandPreviewController.instance().snapshot()), "Random sample re-rolled while idle");
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().random(), "Random warning missing"));
            context.takeScreenshot("command-random-stable");
            draft(context, "//set");
            context.waitTicks(12);
            context.runOnClient(client -> {
                var controller = CommandPreviewController.instance();
                check(controller.ownsPreviewChannel(), "Incomplete draft released channel");
                check(controller.snapshot().cells().isEmpty(), "Incomplete draft left old geometry");
                client.gui.screen().keyPressed(new KeyEvent(256, 0, 0));
                check(!controller.ownsPreviewChannel(), "Closing chat did not restore clipboard channel");
                check(controller.snapshot().cells().isEmpty(), "Closing chat left geometry");
                check((boolean) call(overlay, "clipboardPreviewEnabled"), "Escape changed clipboard preference");
            });
            context.runOnClient(client -> call(overlay, "setClipboardPreviewEnabled", false));
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                WorldEdit.getInstance().getSessionManager().get(actor).setBlockChangeLimit(1);
                return null;
            });
            draft(context, "//set gold_block");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.LIMIT);
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                WorldEdit.getInstance().getSessionManager().get(actor).setBlockChangeLimit(-1);
                select(server, MIN, MIN.add(64, 64, 64)); return null;
            });
            draft(context, "//set gold_block");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().reason().equals("too_large"));
            onServer(world.getServer(), () -> { select(server, MIN, MAX); return null; });
            draft(context, "//set gold_block");
            ready(context);
            onServer(world.getServer(), () -> { select(server, MIN, MIN.add(1, 1, 1)); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                    && CommandPreviewController.instance().snapshot().statistics().affected() == 8);
            onServer(world.getServer(), () -> { mask(server, "dirt"); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                    && CommandPreviewController.instance().snapshot().statistics().affected() == 0);
            onServer(world.getServer(), () -> { mask(server, "stone"); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                    && CommandPreviewController.instance().snapshot().statistics().affected() == 8);
            onServer(world.getServer(), () -> { mask(server, null); sourceBlock(server, "air"); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                    && CommandPreviewController.instance().snapshot().statistics().added() == 1
                    && CommandPreviewController.instance().snapshot().statistics().replaced() == 7);
            onServer(world.getServer(), () -> { sourceBlock(server, "stone"); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                    && CommandPreviewController.instance().snapshot().statistics().added() == 0
                    && CommandPreviewController.instance().snapshot().statistics().replaced() == 8);
            context.runOnClient(client -> {
                client.gui.screen().keyPressed(new KeyEvent(297, 0, 0));
                check(!CommandPreviewController.instance().options().enabled(), "F8 did not disable preview in chat");
                check(!CommandPreviewController.instance().ownsPreviewChannel(), "Disabled preview retained channel");
                check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "F8 retained obsolete geometry");
                client.gui.screen().keyPressed(new KeyEvent(297, 0, 0));
                check(CommandPreviewController.instance().options().enabled(), "F8 did not re-enable preview in chat");
            });
            ready(context);
            onServer(world.getServer(), () -> { select(server, BlockVector3.at(1_000_000, 66, 1_000_000), BlockVector3.at(1_000_001, 67, 1_000_001)); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().reason().equals("unloaded"));
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Unloaded selection retained geometry"));
            onServer(world.getServer(), () -> { select(server, MIN, MIN.add(1, 1, 1)); return null; });
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY);
            draft(context, "//set -h stone");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.ERROR);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Unsupported flags retained geometry"));
            draft(context, "//set stone");
            ready(context);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().affected() == 0, "No-op reported edits"));
            draft(context, "//set air");
            ready(context);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().removed() == 8, "Removal counts"));
            context.takeScreenshot("command-remove-markers");
            draft(context, "//set invalid_block_name");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.ERROR);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Invalid draft retained ghost"));
            draft(context, "//set diamond_block");
            ready(context);
            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(257, 0, 0)));
            context.waitFor(client -> client.gui.screen() == null);
            context.waitTicks(20);
            onServer(world.getServer(), () -> {
                var player = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                check(player.getWorld().getBlock(MIN).getBlockType().id().equals("minecraft:diamond_block"), "Enter no longer executes normal WorldEdit command");
                return null;
            });
            System.out.println("WESO COMMAND PASS: 40 deterministic WorldEdit comparisons, zero preview writes/history, stable mixtures, clipboard coexistence, F8/cancellation, selection/source/mask invalidation, unloaded/limit/invalid drafts, unchanged Enter submission");
        }
    }

    private static void comparePrograms(MinecraftServer server) throws Exception {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        var world = actor.getWorld();
        var convex = new ConvexPolyhedralRegion(world);
        for (BlockVector3 p : List.of(MIN, BlockVector3.at(5, 66, 0), BlockVector3.at(0, 66, 5), BlockVector3.at(1, 71, 1))) convex.addVertex(p);
        List<Region> regions = List.of(new CuboidRegion(world, MIN, MAX),
                new EllipsoidRegion(world, BlockVector3.at(3, 69, 3), Vector3.at(2, 2, 2)),
                new CylinderRegion(world, BlockVector3.at(3, 66, 3), Vector2.at(2, 2), 66, 71),
                new Polygonal2DRegion(world, List.of(BlockVector2.at(0, 0), BlockVector2.at(5, 0), BlockVector2.at(3, 5)), 66, 71), convex);
        for (Region region : regions) for (String draft : List.of("//set gold_block", "//replace stone diamond_block", "//walls glass", "//faces bricks", "//overlay dirt", "//outline bricks", "//replace gold_block", "//set oak_log[axis=x]")) {
            try (var reset = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
                reset.setBlocks(new CuboidRegion(MIN.subtract(1, 1, 1), MAX.add(1, 2, 1)), com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                reset.setBlocks(new CuboidRegion(MIN, MAX), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
            }
            BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint().add(0, 1, 0);
            PreviewExtent memory = new PreviewExtent(min, max);
            for (BlockVector3 p : new CuboidRegion(min, max)) memory.capture(p, world.getFullBlock(p));
            PreviewCommand command = PreviewCommand.parse(draft);
            ParserContext parser = new ParserContext(); parser.setActor(actor); parser.setWorld(world); parser.setRestricted(true);
            WorldEditPreviewProgram program = new WorldEditPreviewProgram(command, region, memory, parser, null);
            int passes = 0;
            while (!program.complete()) { program.advance(7, System.nanoTime() + 5_000_000); check(++passes < 1000, "Provider never completed"); }
            for (var p : memory.original().entrySet()) check(world.getFullBlock(p.getKey()).equals(p.getValue()), "Domain simulation wrote world");
            try (var actual = WorldEdit.getInstance().newEditSessionBuilder().world(world).actor(actor).maxBlocks(-1).build()) {
                ParserContext official = new ParserContext(); official.setActor(actor); official.setWorld(world); official.setExtent(actual); official.setRestricted(true);
                var pattern = WorldEdit.getInstance().getPatternFactory().parseFromInput(command.pattern(), official);
                switch (command.kind()) {
                    case SET -> actual.setBlocks(region, pattern);
                    case REPLACE -> actual.replaceBlocks(region, WorldEdit.getInstance().getMaskFactory().parseFromInput(command.mask(), official), pattern);
                    case WALLS -> actual.makeWalls(region, pattern);
                    case FACES -> actual.makeFaces(region, pattern);
                    case OVERLAY -> actual.overlayCuboidBlocks(region, pattern);
                }
            }
            for (BlockVector3 p : new CuboidRegion(min, max)) check(world.getFullBlock(p).equals(memory.getFullBlock(p)), "Result differs from WorldEdit: " + region.getClass().getSimpleName() + " " + draft + " at " + p);
        }
        try (var reset = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
            reset.setBlocks(new CuboidRegion(MIN, MAX), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
        }
        var quoted = PreviewCommand.parse("//set \"stone\""); check(quoted.pattern().equals("stone"), "Quoted parser mismatch");
    }

    private static void select(MinecraftServer server, BlockVector3 min, BlockVector3 max) {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        var session = WorldEdit.getInstance().getSessionManager().get(actor);
        session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), min, max));
    }
    private static void mask(MinecraftServer server, String input) throws Exception {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        ParserContext parser = new ParserContext(); parser.setActor(actor); parser.setWorld(actor.getWorld()); parser.setExtent(actor.getWorld());
        WorldEdit.getInstance().getSessionManager().get(actor).setMask(input == null ? null
                : WorldEdit.getInstance().getMaskFactory().parseFromInput(input, parser));
    }
    private static void sourceBlock(MinecraftServer server, String id) throws Exception {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).maxBlocks(-1).build()) {
            edit.setBlock(MIN, com.sk89q.worldedit.world.block.BlockTypes.get("minecraft:" + id).getDefaultState());
        }
    }
    private static void draft(ClientGameTestContext context, String command) {
        context.runOnClient(client -> client.gui.setScreen(new ChatScreen(command, false)));
    }
    private static void ready(ClientGameTestContext context) {
        context.waitFor(client -> {
            var s = CommandPreviewController.instance().snapshot();
            if (s.state() == CommandPreviewSnapshot.State.ERROR || s.state() == CommandPreviewSnapshot.State.UNAVAILABLE)
                throw new AssertionError("Preview failed: " + s.reason() + " " + s.detail());
            return s.state() == CommandPreviewSnapshot.State.READY;
        });
    }
    private static <T> T onServer(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server, Callable<T> task) {
        try { return server.computeOnServer(ignored -> task.call()); }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static List<?> history(MinecraftServer server) {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        return List.copyOf((java.util.Collection<?>) field(WorldEdit.getInstance().getSessionManager().get(actor), "history"));
    }
    private static Object field(Object object, String name) {
        try { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static net.minecraft.client.gui.components.EditBox chatInput(ChatScreen screen) {
        try {
            for (var f : ChatScreen.class.getDeclaredFields()) if (net.minecraft.client.gui.components.EditBox.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true); return (net.minecraft.client.gui.components.EditBox) f.get(screen);
            }
            throw new AssertionError("Missing native chat input");
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object call(Object object, String name, Object... args) {
        try {
            for (var method : object.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(object, args);
            throw new AssertionError("Missing method " + name);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}

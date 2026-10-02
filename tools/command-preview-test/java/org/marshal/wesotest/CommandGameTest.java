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
        checkPaletteIndices();
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
            onServer(world.getServer(), () -> { comparePrograms(server); checkPointRefreshPrograms(server); select(server, MIN, MAX); return null; });
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
            Object section = context.computeOnClient(client -> ((java.util.Map<?, ?>) field(field(CommandPreviewController.instance(), "renderer"), "sections")).values().iterator().next());
            Object vertices = context.computeOnClient(client -> field(section, "vertices"));
            context.waitTicks(12);
            context.runOnClient(client -> check(vertices == field(section, "vertices"), "Stable preview rewrites vertex buffers"));
            measureLatency(context);
            context.runOnClient(client -> check(field(section, "vertices") == null, "Replacing a draft leaked its vertex buffer"));
            context.runOnClient(client -> {
                var input = chatInput((ChatScreen) client.gui.screen());
                for (String value : List.of("//set g", "//set gold", "//set gold_block", "//set diamond_block")) input.setValue(value);
                check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Typing did not discard obsolete geometry immediately");
            });
            ready(context);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().cells().stream().allMatch(c -> c.kind() == PreviewChangeKind.REPLACED), "Rapid typing published an obsolete result"));
            checkModelCache(context);
            for (String pattern : List.of("stone_button[face=floor]", "oak_slab[type=bottom]", "oak_stairs[facing=east]", "oak_fence", "glass_pane", "oak_trapdoor[open=true]", "77", "35:14")) {
                draft(context, "//replace 1 " + pattern);
                ready(context);
                context.waitTicks(10);
                context.runOnClient(client -> {
                    var snapshot = CommandPreviewController.instance().snapshot();
                    var inside = cellPos(snapshot.cells().getFirst());
                    check(snapshot.statistics().replaced() == 216, "Incomplete partial-model or legacy replacement");
                    check(hiddenSource(inside), "Old block remains under ghost model");
                    check(!hiddenSource(inside.offset(-20, 0, 0)), "Preview hides unrelated terrain");
                    check(client.level.getBlockState(inside).getBlock() == net.minecraft.world.level.block.Blocks.STONE, "Preview mutated client block data");
                    check((boolean) callStatic(org.marshal.weaddon_lightposition.runtime.mc26_2.client.BlockIsolationController.class, "shouldDrawFaceAgainstHiddenNeighbor",
                            client.level, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), net.minecraft.core.Direction.EAST,
                            inside), "Unchanged neighbor face remains cut off");
                });
                if (pattern.equals("stone_button[face=floor]")) context.takeScreenshot("command-button-replacement");
            }
            draft(context, "//replace 1 70%35:14,30%35:0");
            ready(context);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().random()
                    && CommandPreviewController.instance().snapshot().statistics().replaced() == 216, "Legacy weighted pattern failed"));
            checkFlora(context, world.getServer(), server);
            List<String> regionCommands = List.of("//center gold_block", "//naturalize", "//hollow 1", "//line gold_block", "//line -h gold_block 1");
            long[] counts = {8, 144, 216, 6, 36};
            for (int i = 0; i < regionCommands.size(); i++) {
                draft(context, regionCommands.get(i)); ready(context);
                final int index = i;
                context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().affected() == counts[index],
                        "Region command count mismatch: " + regionCommands.get(index) + " got " + CommandPreviewController.instance().snapshot().statistics().affected()));
                if (i == 4) { context.waitTicks(10); context.takeScreenshot("command-line"); }
            }
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                for (var p : new CuboidRegion(MIN, MAX)) check(actor.getWorld().getBlock(p).getBlockType() == com.sk89q.worldedit.world.block.BlockTypes.STONE, "New region preview wrote world");
                var selector = new com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector(actor.getWorld());
                var convex = (ConvexPolyhedralRegion) selector.getIncompleteRegion();
                for (var p : List.of(MIN, MIN.add(5, 0, 0), MIN.add(0, 0, 5), MIN.add(1, 5, 1))) convex.addVertex(p);
                selector.learnChanges();
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(), selector);
                return null;
            });
            draft(context, "//curve -h gold_block 1"); ready(context); context.waitTicks(12);
            context.runOnClient(client -> {
                var snapshot = CommandPreviewController.instance().snapshot();
                check(snapshot.statistics().affected() > 0 && !snapshot.random(), "Convex curve did not render");
                check(snapshot.cells().stream().allMatch(c -> call(c, "after") == net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState()), "Curve model state incorrect");
            });
            context.takeScreenshot("command-curve");
            Object curve = context.computeOnClient(client -> CommandPreviewController.instance().snapshot());
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                var selector = new com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector(actor.getWorld());
                var convex = (ConvexPolyhedralRegion) selector.getIncompleteRegion();
                for (var p : List.of(MIN.add(1, 5, 1), MIN.add(0, 0, 5), MIN.add(5, 0, 0), MIN)) convex.addVertex(p);
                selector.learnChanges();
                WorldEdit.getInstance().getSessionManager().get(actor).setRegionSelector(actor.getWorld(), selector);
                return null;
            });
            context.waitFor(client -> CommandPreviewController.instance().snapshot() != curve
                    && CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY);
            onServer(world.getServer(), () -> { select(server, MIN, MAX); return null; });
            draft(context, "//set water");
            ready(context);
            context.waitFor(client -> {
                var controller = CommandPreviewController.instance();
                Object renderer = field(controller, "renderer");
                return field(renderer, "prepared") == controller.snapshot() && (int) field(renderer, "buildIndex") == controller.snapshot().cells().size();
            });
            context.runOnClient(client -> check(((List<?>) field(field(CommandPreviewController.instance(), "renderer"), "markers")).size() == 1,
                    "Solid fluid volume was not compacted into one marker"));
            context.takeScreenshot("command-fluid-marker");
            draft(context, "//replace stone 70%dirt,30%grass_block");
            ready(context);
            Object sample = context.computeOnClient(client -> CommandPreviewController.instance().snapshot());
            onServer(world.getServer(), () -> {
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                WorldEdit.getInstance().getSessionManager().get(actor).dispatchCUISelection(actor);
                return null;
            });
            context.runOnClient(client -> {
                for (int i = 0; i < 30; i++) CommandPreviewController.instance().selectionChanged();
            });
            context.waitTicks(30);
            check(sample == context.computeOnClient(client -> CommandPreviewController.instance().snapshot()), "Repeated CUI updates re-rolled the random sample");
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
            ready(context);
            context.runOnClient(client -> {
                var snapshot = CommandPreviewController.instance().snapshot();
                check(snapshot.statistics().affected() == 274_625, "Former input cap still limits the result");
                check(snapshot.cells().size() == 24_578, "Former visual cap or internal-face culling is incorrect");
                Object memory = field(field(field(CommandPreviewController.instance(), "source"), "job"), "memory");
                long bytes = 0;
                for (Object stored : ((java.util.Map<?, ?>) field(memory, "sections")).values()) {
                    for (String state : List.of("original", "after")) {
                        Object indices = field(stored, state);
                        if (indices == null) continue;
                        for (String kind : List.of("bytes", "shorts", "ints")) {
                            Object data = field(indices, kind);
                            if (data != null) bytes += (long) java.lang.reflect.Array.getLength(data) * (kind.equals("bytes") ? 1 : kind.equals("shorts") ? 2 : 4);
                        }
                    }
                }
                System.out.println("WESO COMMAND STORAGE 274625 cells index-array-bytes=" + bytes);
                check(bytes < 274_625L * 2, "Uniform palette storage regressed to dense per-cell arrays");
            });
            onServer(world.getServer(), () -> {
                select(server, BlockVector3.at(-1_000_000, 66, -1_000_000), BlockVector3.at(1_000_000, 67, 1_000_000));
                return null;
            });
            draft(context, "//set diamond_block");
            ready(context);
            context.runOnClient(client -> {
                Object source = field(CommandPreviewController.instance(), "source");
                int loaded = ((List<?>) field(source, "loaded")).size();
                var snapshot = CommandPreviewController.instance().snapshot();
                check(snapshot.total() == (long) loaded * 512, "Huge unloaded bounds were scanned or loaded chunks omitted");
                check(snapshot.statistics().affected() == snapshot.total(), "Loaded intersection was truncated");
                System.out.println("WESO COMMAND LOADED INTERSECTION chunks=" + loaded + " cells=" + snapshot.total());
            });
            onServer(world.getServer(), () -> { select(server, MIN, MAX); return null; });
            draft(context, "//set diamond_block");
            ready(context);
            world.getServer().runCommand("tp @p 512 77 512");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.ERROR
                    && CommandPreviewController.instance().snapshot().reason().equals("unloaded")
                    && ((java.util.Map<?, ?>) field(field(CommandPreviewController.instance(), "renderer"), "sections")).isEmpty());
            world.getServer().runCommand("tp @p 12 77 16 145 30");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY);
            context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().affected() == 216,
                    "Reloading selection chunks did not restore the complete preview"));
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
            stressLargeSelection(context, world.getServer(), server);
            stressChangingGrass(context, world.getServer(), server);
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
            System.out.println("WESO COMMAND PASS: expanded full/clipped WorldEdit comparisons, center/naturalize/hollow/line/curve, partial-model source hiding/restoration, legacy IDs/data/mixtures, flora, complete loaded results beyond former caps, zero preview writes/history, clipboard coexistence, F8/cancellation, unchanged Enter submission");
        }
    }

    private static void checkPaletteIndices() {
        try {
            Class<?> type = java.util.Arrays.stream(PreviewExtent.class.getDeclaredClasses()).filter(c -> c.getSimpleName().equals("Indices")).findFirst().orElseThrow();
            var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
            Object indices = constructor.newInstance();
            var set = type.getDeclaredMethod("set", int.class, int.class); set.setAccessible(true);
            var get = type.getDeclaredMethod("get", int.class); get.setAccessible(true);
            var uniformConstructor = type.getDeclaredConstructor(int.class); uniformConstructor.setAccessible(true);
            Object uniform = uniformConstructor.newInstance(65536);
            set.invoke(uniform, 7, 65536);
            for (String name : List.of("bytes", "shorts", "ints")) check(field(uniform, name) == null, "Uniform section allocated a per-cell array");
            set.invoke(uniform, 7, 3);
            for (int i = 0; i < 4096; i++) check((int) get.invoke(uniform, i) == (i == 7 ? 3 : 65536), "Uniform expansion corrupted cell data");
            for (int i = 0; i < 4096; i++) set.invoke(indices, i, i % 253);
            for (int id : new int[] {255, 256, 65535, 65536}) {
                set.invoke(indices, 0, id);
                check((int) get.invoke(indices, 0) == id, "Palette index promotion truncated an id");
                for (int i = 1; i < 4096; i++) check((int) get.invoke(indices, i) == i % 253, "Palette promotion corrupted another cell");
            }
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }

    private static void checkFlora(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext testServer, MinecraftServer server) {
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(256, 0, 0)));
        context.runOnClient(client -> check(!hiddenSource(
                new net.minecraft.core.BlockPos(0, 66, 0)), "Escape did not restore source terrain"));
        testServer.runCommand("fill 0 66 0 5 72 5 air");
        testServer.runCommand("fill 0 66 0 5 66 5 grass_block");
        onServer(testServer, () -> { select(server, MIN, MIN.add(5, 0, 5)); return null; });
        draft(context, "//flora 100"); ready(context); context.waitTicks(12);
        var sample = context.computeOnClient(client -> CommandPreviewController.instance().snapshot());
        check(sample.statistics().added() == 36 && sample.random(), "Flora surface/density semantics mismatch");
        context.runOnClient(client -> {
            check(!(boolean) call(sample.terrain(), "contains", new net.minecraft.core.BlockPos(0, 66, 0)), "Flora hides its supporting ground");
            for (var cell : sample.cells()) check(cellPos(cell).getY() == 67, "Flora wrote the wrong layer");
            for (int i = 0; i < 30; i++) CommandPreviewController.instance().selectionChanged();
        });
        context.waitTicks(20);
        check(sample == context.computeOnClient(client -> CommandPreviewController.instance().snapshot()), "Flora re-rolled on repeated CUI");
        context.takeScreenshot("command-flora");
        onServer(testServer, () -> {
            var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
            for (var cell : sample.cells()) check(actor.getWorld().getBlock(BlockVector3.at(cellPos(cell).getX(), 67, cellPos(cell).getZ())).getBlockType().getMaterial().isAir(), "Flora preview wrote server world");
            try (var actual = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).actor(actor).maxBlocks(-1).build()) {
                new com.sk89q.worldedit.command.RegionCommands().flora(actor, actual, new CuboidRegion(MIN, MIN.add(5, 0, 5)), 100);
            }
            for (var cell : sample.cells()) check(!actor.getWorld().getBlock(BlockVector3.at(cellPos(cell).getX(), 67, cellPos(cell).getZ())).getBlockType().getMaterial().isAir(), "Flora result differs from official generator coverage");
            return null;
        });
        testServer.runCommand("fill 0 67 0 5 72 5 air");
        draft(context, "//flora 0"); ready(context);
        context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().affected() == 0, "Zero-density flora adds plants"));
        onServer(testServer, () -> { mask(server, "stone"); return null; });
        draft(context, "//flora 100"); ready(context);
        context.runOnClient(client -> check(CommandPreviewController.instance().snapshot().statistics().affected() == 0, "Flora ignored global mask"));
        onServer(testServer, () -> { mask(server, null); return null; });
        draft(context, "//flora 101");
        context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.ERROR);
        testServer.runCommand("fill 0 66 0 5 71 5 stone");
        onServer(testServer, () -> { select(server, MIN, MAX); return null; });
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
        int comparisons = 0;
        for (Region region : regions) {
            var drafts = new java.util.ArrayList<>(List.of("//set gold_block", "//replace stone diamond_block", "//walls glass", "//faces bricks", "//overlay dirt", "//outline bricks", "//replace gold_block", "//set oak_log[axis=x]",
                "//replace stone stone_button[face=floor]", "//replace stone oak_slab[type=bottom]", "//replace stone oak_stairs[facing=east]", "//replace stone oak_fence",
                "//replace stone glass_pane", "//replace stone oak_trapdoor[open=true]", "//replace 1 77", "//set 35:14",
                "//center gold_block", "//middle diamond_block", "//naturalize", "//hollow", "//hollow 1", "//hollow 2 glass"));
            if (region instanceof CuboidRegion || region instanceof ConvexPolyhedralRegion)
                drafts.addAll(List.of("//line gold_block", "//line gold_block 1", "//line -h gold_block 2", "//line gold_block 0.5"));
            if (region instanceof ConvexPolyhedralRegion)
                drafts.addAll(List.of("//curve gold_block", "//curve gold_block 1", "//curve -h gold_block 2"));
            for (String draft : drafts) {
            try (var reset = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
                reset.setBlocks(new CuboidRegion(MIN.subtract(8, 8, 8), MAX.add(8, 8, 8)), com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                reset.setBlocks(new CuboidRegion(MIN, MAX), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
            }
            BlockVector3 min = region.getMinimumPoint().subtract(4, 4, 4), max = region.getMaximumPoint().add(4, 4, 4);
            PreviewExtent memory = new PreviewExtent(min, max);
            for (BlockVector3 p : new CuboidRegion(min, max)) memory.capture(p, world.getFullBlock(p));
            PreviewCommand command = PreviewCommand.parse(draft);
            ParserContext parser = new ParserContext(); parser.setActor(actor); parser.setWorld(world); parser.setRestricted(true);
            parser.setTryLegacy(true);
            WorldEditPreviewProgram program = new WorldEditPreviewProgram(command, region, memory, parser, null);
            int passes = 0;
            while (!program.complete()) { program.advance(7, System.nanoTime() + 5_000_000); check(++passes < 30000, "Provider never completed"); }
            var clip = new CuboidRegion(min, BlockVector3.at(Math.min(region.getMinimumPoint().x() + 1, max.x()), max.y(), max.z()));
            PreviewExtent partial = new PreviewExtent(min, max);
            for (var entry : memory.original().entrySet()) if (clip.contains(entry.getKey())) partial.capture(entry.getKey(), entry.getValue());
            WorldEditPreviewProgram clipped = new WorldEditPreviewProgram(command, region, partial, parser, null,
                    clip::iterator, () -> clip.asFlatRegion().iterator());
            if (command.kind() == PreviewCommand.Kind.HOLLOW) {
                try { clipped.advance(7, System.nanoTime() + 5_000_000); throw new AssertionError("Incomplete exterior flood accepted"); }
                catch (IllegalArgumentException ex) { check(ex.getMessage().equals("unloaded"), "Wrong partial hollow failure"); }
            } else while (!clipped.complete()) clipped.advance(7, System.nanoTime() + 5_000_000);
            for (var p : memory.original().entrySet()) check(world.getFullBlock(p.getKey()).equals(p.getValue()), "Domain simulation wrote world");
            try (var actual = WorldEdit.getInstance().newEditSessionBuilder().world(world).actor(actor).maxBlocks(-1).build()) {
                // Unsupported buttons can drop during native updates. Compare direct
                // writes for partial models; server physics is not the preview contract.
                if (draft.contains("button") || draft.contains("slab") || draft.contains("stairs") || draft.contains("fence")
                        || draft.contains("pane") || draft.contains("trapdoor") || draft.equals("//replace 1 77"))
                    actual.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none());
                ParserContext official = new ParserContext(); official.setActor(actor); official.setWorld(world); official.setExtent(actual); official.setRestricted(true);
                official.setTryLegacy(true);
                var pattern = command.pattern().isEmpty() ? null : WorldEdit.getInstance().getPatternFactory().parseFromInput(command.pattern(), official);
                switch (command.kind()) {
                    case SET -> actual.setBlocks(region, pattern);
                    case REPLACE -> actual.replaceBlocks(region, WorldEdit.getInstance().getMaskFactory().parseFromInput(command.mask(), official), pattern);
                    case WALLS -> actual.makeWalls(region, pattern);
                    case FACES -> actual.makeFaces(region, pattern);
                    case OVERLAY -> actual.overlayCuboidBlocks(region, pattern);
                    case CENTER -> actual.center(region, pattern);
                    case NATURALIZE -> actual.naturalizeCuboidBlocks(region);
                    case HOLLOW -> actual.hollowOutRegion(region, (int) command.thickness(), pattern);
                    case LINE -> actual.drawLine(pattern, region instanceof CuboidRegion cube ? List.of(cube.getPos1(), cube.getPos2())
                            : List.copyOf(((ConvexPolyhedralRegion) region).getVertices()), command.thickness(), !command.shell());
                    case CURVE -> actual.drawSpline(pattern, List.copyOf(((ConvexPolyhedralRegion) region).getVertices()), 0, 0, 0, 10, command.thickness(), !command.shell());
                }
            }
            for (BlockVector3 p : new CuboidRegion(min, max)) check(world.getFullBlock(p).equals(memory.getFullBlock(p)), "Result differs from WorldEdit: " + region.getClass().getSimpleName() + " " + draft + " at " + p);
            comparisons++;
            if (command.kind() != PreviewCommand.Kind.HOLLOW) {
                for (BlockVector3 p : clip) check(world.getFullBlock(p).equals(partial.getFullBlock(p)), "Loaded intersection differs from WorldEdit: " + region.getClass().getSimpleName() + " " + draft + " at " + p);
                comparisons++;
            }
            }
        }
        for (int thickness = 0; thickness < 4; thickness++) {
            var region = new CuboidRegion(MIN, MAX);
            var bounds = new CuboidRegion(MIN.subtract(1, 1, 1), MAX.add(1, 1, 1));
            try (var reset = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
                reset.setBlocks(bounds, com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                reset.setBlocks(new CuboidRegion(MIN.add(1, 1, 1), MAX.subtract(1, 1, 1)), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
            }
            PreviewExtent memory = new PreviewExtent(bounds.getMinimumPoint(), bounds.getMaximumPoint());
            for (var p : bounds) memory.capture(p, world.getFullBlock(p));
            ParserContext parser = new ParserContext(); parser.setActor(actor); parser.setWorld(world); parser.setRestricted(true);
            var program = new WorldEditPreviewProgram(PreviewCommand.parse("//hollow " + thickness), region, memory, parser, null);
            while (!program.complete()) program.advance(7, System.nanoTime() + 5_000_000);
            try (var actual = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
                actual.hollowOutRegion(region, thickness, com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
            }
            for (var p : bounds) check(world.getFullBlock(p).equals(memory.getFullBlock(p)), "Exterior-connected hollow mismatch: " + thickness + " at " + p);
            comparisons++;
        }
        System.out.println("WESO COMMAND COMPARISONS exact-full-and-clipped=" + comparisons);
        try (var reset = WorldEdit.getInstance().newEditSessionBuilder().world(world).maxBlocks(-1).build()) {
            reset.setBlocks(new CuboidRegion(MIN.subtract(8, 8, 8), MAX.add(8, 8, 8)), com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
            reset.setBlocks(new CuboidRegion(MIN, MAX), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
        }
        var quoted = PreviewCommand.parse("//set \"stone\""); check(quoted.pattern().equals("stone"), "Quoted parser mismatch");
    }

    private static void select(MinecraftServer server, BlockVector3 min, BlockVector3 max) {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        var session = WorldEdit.getInstance().getSessionManager().get(actor);
        session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(), min, max));
    }
    private static void checkPointRefreshPrograms(MinecraftServer server) throws Exception {
        var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
        var region = new CuboidRegion(MIN, MIN.add(1, 1, 1));
        for (String draft : List.of("//set gold_block", "//replace stone diamond_block", "//replace #existing air",
                "//replace 1 77", "//set 70%gold_block,30%diamond_block")) {
            var memory = new PreviewExtent(region.getMinimumPoint(), region.getMaximumPoint());
            for (var p : region) memory.capture(p, com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState().toBaseBlock());
            var parser = new ParserContext(); parser.setActor(actor); parser.setWorld(actor.getWorld()); parser.setTryLegacy(true);
            var program = new WorldEditPreviewProgram(PreviewCommand.parse(draft), region, memory, parser, null);
            check(program.supportsPointRefresh(), "Independent operation cannot refresh: " + draft);
            try { program.refreshPoint(MIN, com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState().toBaseBlock());
                throw new AssertionError("Point refresh before domain completion accepted"); }
            catch (IllegalStateException expected) { }
            while (!program.complete()) program.advance(3, System.nanoTime() + 5_000_000);
            var unchanged = new java.util.HashMap<>(memory.changes()); unchanged.remove(MIN);
            for (var type : List.of(com.sk89q.worldedit.world.block.BlockTypes.AIR,
                    com.sk89q.worldedit.world.block.BlockTypes.DIRT, com.sk89q.worldedit.world.block.BlockTypes.STONE)) {
                program.refreshPoint(MIN, type.getDefaultState().toBaseBlock());
                check(memory.original().size() == 8, "Recapture duplicated source cells");
                check(memory.original().get(MIN).getBlockType() == type, "Recapture kept the old source");
                var result = new java.util.HashMap<>(memory.changes()); result.remove(MIN);
                check(result.equals(unchanged), "Point refresh re-rolled or modified another cell");
                if (!program.random()) {
                    var expected = new PreviewExtent(region.getMinimumPoint(), region.getMaximumPoint());
                    for (var p : region) expected.capture(p, memory.original().get(p));
                    var reference = new WorldEditPreviewProgram(PreviewCommand.parse(draft), region, expected, parser, null);
                    while (!reference.complete()) reference.advance(3, System.nanoTime() + 5_000_000);
                    check(memory.changes().equals(expected.changes()), "Point refresh differs from a complete fresh result: " + draft);
                }
            }
        }
        System.out.println("WESO COMMAND POINT REFRESH PASS: masks, removals, legacy IDs, unchanged random cells");
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
    private static void measureLatency(ClientGameTestContext context) {
        long[] readyMillis = new long[8], visibleMillis = new long[8];
        for (int i = 0; i < readyMillis.length; i++) {
            String value = i % 2 == 0 ? "//set gold_block" : "//set diamond_block";
            long[] start = new long[1];
            context.runOnClient(client -> {
                start[0] = System.nanoTime();
                chatInput((ChatScreen) client.gui.screen()).setValue(value);
            });
            ready(context);
            readyMillis[i] = (System.nanoTime() - start[0]) / 1_000_000;
            context.waitFor(client -> {
                var controller = CommandPreviewController.instance();
                Object renderer = field(controller, "renderer");
                return field(renderer, "prepared") == controller.snapshot()
                        && (int) field(renderer, "buildIndex") == controller.snapshot().cells().size();
            });
            visibleMillis[i] = (System.nanoTime() - start[0]) / 1_000_000;
        }
        System.out.println("WESO COMMAND LATENCY ready-ms=" + java.util.Arrays.toString(readyMillis)
                + " visible-ms=" + java.util.Arrays.toString(visibleMillis));
        java.util.Arrays.sort(visibleMillis);
        check(visibleMillis[visibleMillis.length / 2] < 250, "Warm preview latency regressed above 250 ms");
    }
    private static void checkModelCache(ClientGameTestContext context) {
        context.waitFor(client -> field(field(CommandPreviewController.instance(), "renderer"), "prepared")
                == CommandPreviewController.instance().snapshot());
        Object renderer = context.computeOnClient(client -> field(CommandPreviewController.instance(), "renderer"));
        Object probe = context.computeOnClient(client -> field(renderer, "probeModel"));
        check(probe != null, "No baked-model cache probe");
        context.runOnClient(client -> check(((java.util.Map<?, ?>) field(renderer, "models")).size() >= 2,
                "Editing discarded cached model templates"));
        Object invalidProbe = new Object();
        context.runOnClient(client -> {
            try { var f = renderer.getClass().getDeclaredField("probeModel"); f.setAccessible(true); f.set(renderer, invalidProbe); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        });
        context.waitFor(client -> field(renderer, "probeModel") != null && field(renderer, "probeModel") != invalidProbe
                && !((java.util.Map<?, ?>) field(renderer, "sections")).isEmpty());
        context.runOnClient(client -> {
            var models = (java.util.Map<?, ?>) field(renderer, "models");
            check(models.size() <= 256, "Baked-model cache is unbounded");
            check(!((java.util.Map<?, ?>) field(renderer, "sections")).isEmpty(), "Model identity change discarded the preview permanently");
        });
    }
    private static void stressLargeSelection(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext testServer, MinecraftServer server) {
        onServer(testServer, () -> { select(server, MIN, MIN.add(39, 39, 39)); return null; });
        draft(context, "//set gold_block");
        context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.COMPUTING);
        long start = System.nanoTime();
        context.runOnClient(client -> {
            var input = chatInput((ChatScreen) client.gui.screen());
            for (int i = 0; i < 100; i++) input.setValue(i % 2 == 0 ? "//set dirt" : "//set diamond_block");
            check(CommandPreviewController.instance().snapshot().cells().isEmpty(), "Obsolete large result survived editing");
        });
        ready(context);
        context.runOnClient(client -> {
            var snapshot = CommandPreviewController.instance().snapshot();
            check(snapshot.statistics().affected() == 64_000, "Large selection count is incomplete");
            check(!snapshot.limited(), "Large preview was truncated");
            check(snapshot.cells().size() == 9_128, "Complete 40-cube surface missing");
            check(snapshot.cells().stream().allMatch(c -> call(c, "after") == net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState()), "Stale large result published");
        });
        System.out.println("WESO COMMAND LARGE 64000 cells ready-ms=" + (System.nanoTime() - start) / 1_000_000);
        onServer(testServer, () -> { select(server, MIN, MIN.add(1, 1, 1)); return null; });
        context.runOnClient(client -> CommandPreviewController.instance().selectionChanged());
        context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                && CommandPreviewController.instance().snapshot().statistics().affected() == 8);
    }
    private static void ready(ClientGameTestContext context) {
        context.waitFor(client -> {
            var s = CommandPreviewController.instance().snapshot();
            if (s.state() == CommandPreviewSnapshot.State.ERROR || s.state() == CommandPreviewSnapshot.State.UNAVAILABLE)
                throw new AssertionError("Preview failed: " + s.reason() + " " + s.detail());
            return s.state() == CommandPreviewSnapshot.State.READY;
        });
    }
    private static void stressChangingGrass(ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext testServer, MinecraftServer server) {
        context.runOnClient(client -> client.gui.setScreen(null));
        // Game-rule names changed between the supported native runtimes.
        testServer.runCommand("gamerule randomTickSpeed 0");
        testServer.runCommand("gamerule minecraft:random_tick_speed 0");
        var max = MIN.add(39, 39, 39);
        onServer(testServer, () -> {
            var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
            select(server, MIN, max);
            try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).maxBlocks(-1).build()) {
                edit.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none());
                edit.setBlocks(new CuboidRegion(MIN, max), com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK.getDefaultState());
            }
            return null;
        });
        Object source = context.computeOnClient(client -> field(CommandPreviewController.instance(), "source"));
        var changing = new java.util.concurrent.atomic.AtomicBoolean(true);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var mutations = new java.util.concurrent.atomic.AtomicInteger();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ticked -> {
            if (ticked != server || !changing.get()) return;
            try {
                Object job = field(source, "job");
                if (job == null) return;
                var memory = (PreviewExtent) field(job, "memory");
                var captured = memory.original().get(MIN);
                if (captured == null) return;
                var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
                var type = captured.getBlockType() == com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK
                        ? com.sk89q.worldedit.world.block.BlockTypes.DIRT : com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK;
                actor.getWorld().setBlock(MIN, type.getDefaultState());
                mutations.incrementAndGet();
            } catch (Throwable ex) { failure.compareAndSet(null, ex); changing.set(false); }
        });
        try {
            draft(context, "//replace grass_block dirt");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.COMPUTING);
            long epoch = ((java.util.concurrent.atomic.AtomicLong) field(source, "epoch")).get();
            context.waitFor(client -> {
                if (failure.get() != null) throw new AssertionError("Changing-grass fixture failed", failure.get());
                check(((java.util.concurrent.atomic.AtomicLong) field(source, "epoch")).get() == epoch,
                        "One changing grass block restarted the entire large preview");
                return CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY;
            });
        } finally { changing.set(false); }
        check(mutations.get() > 5, "Large-source mutation test did not exercise multiple ticks");
        onServer(testServer, () -> {
            var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
            actor.getWorld().setBlock(MIN, com.sk89q.worldedit.world.block.BlockTypes.GRASS_BLOCK.getDefaultState());
            return null;
        });
        context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.READY
                && CommandPreviewController.instance().snapshot().statistics().replaced() == 64_000);
        context.waitTicks(15);
        context.runOnClient(client -> {
            var snapshot = CommandPreviewController.instance().snapshot();
            check(snapshot.statistics().affected() == 64_000, "Grass refresh lost result cells");
            check(snapshot.cells().stream().allMatch(c -> call(c, "after") == net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
                    "Grass refresh kept an obsolete model");
        });
        stressChunkStreaming(context, source);
        context.runOnClient(client -> client.gui.setScreen(null));
        onServer(testServer, () -> {
            var actor = FabricAdapter.get().fromNativePlayer(server.getPlayerList().getPlayers().getFirst());
            try (var edit = WorldEdit.getInstance().newEditSessionBuilder().world(actor.getWorld()).maxBlocks(-1).build()) {
                edit.setSideEffectApplier(com.sk89q.worldedit.util.SideEffectSet.none());
                edit.setBlocks(new CuboidRegion(MIN, max), com.sk89q.worldedit.world.block.BlockTypes.AIR.getDefaultState());
                edit.setBlocks(new CuboidRegion(MIN, MAX), com.sk89q.worldedit.world.block.BlockTypes.STONE.getDefaultState());
            }
            select(server, MIN, MIN.add(1, 1, 1));
            return null;
        });
        System.out.println("WESO COMMAND CHANGING GRASS PASS: 64000 cells, mutations=" + mutations.get() + ", no full capture restart");
    }
    private static void stressChunkStreaming(ClientGameTestContext context, Object source) {
        context.runOnClient(client -> client.gui.setScreen(null));
        var chunks = context.computeOnClient(client -> ((List<?>) field(source, "loaded")).stream()
                .filter(c -> (int) field(c, "x") >= 0 && (int) field(c, "x") <= 2
                        && (int) field(c, "z") >= 0 && (int) field(c, "z") <= 2).toList());
        check(chunks.size() == 9, "Streaming fixture is missing its actual native chunks");
        try {
            context.runOnClient(client -> {
                for (int i = 1; i < chunks.size(); i++) chunkEvent(source, chunks.get(i), false);
            });
            draft(context, "//replace grass_block dirt");
            context.waitFor(client -> CommandPreviewController.instance().snapshot().state() == CommandPreviewSnapshot.State.COMPUTING);
            long epoch = ((java.util.concurrent.atomic.AtomicLong) field(source, "epoch")).get();
            for (int i = 1; i < chunks.size(); i++) {
                final Object chunk = chunks.get(i);
                context.runOnClient(client -> chunkEvent(source, chunk, true));
                context.waitTicks(1);
                check(((java.util.concurrent.atomic.AtomicLong) field(source, "epoch")).get() == epoch,
                        "Chunk arrival discarded the in-flight source scan");
            }
            boolean[] displayed = {false};
            context.waitFor(client -> {
                var snapshot = CommandPreviewController.instance().snapshot();
                if (displayed[0]) check(snapshot.state() == CommandPreviewSnapshot.State.READY,
                        "Background chunk refresh removed the displayed result");
                displayed[0] |= snapshot.state() == CommandPreviewSnapshot.State.READY;
                return displayed[0] && snapshot.total() == 64_000 && snapshot.statistics().replaced() == 64_000;
            });
            System.out.println("WESO COMMAND STREAMING PASS: 9 loaded chunks, preserved epoch, complete background expansion");
        } finally {
            context.runOnClient(client -> { for (var c : chunks) chunkEvent(source, c, true); });
        }
    }
    private static void chunkEvent(Object source, Object chunk, boolean present) {
        try {
            var method = source.getClass().getDeclaredMethod("chunkChanged", Object.class, int.class, int.class, boolean.class);
            method.setAccessible(true);
            method.invoke(source, field(source, "trackedLevel"), field(chunk, "x"), field(chunk, "z"), present);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
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
    // The 1.21.11 production JAR uses intermediary native signatures. Reflection
    // keeps the fixture's compile-time named Minecraft types out of those calls.
    private static net.minecraft.core.BlockPos cellPos(Object cell) { return (net.minecraft.core.BlockPos) call(cell, "position"); }
    private static boolean hiddenSource(Object pos) {
        return (boolean) callStatic(org.marshal.weaddon_lightposition.runtime.mc26_2.client.preview.CommandPreviewTerrainState.class, "hides", pos);
    }
    private static Object callStatic(Class<?> type, String name, Object... args) {
        try {
            for (var method : type.getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) return method.invoke(null, args);
            throw new AssertionError("Missing static method " + name);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}

package org.marshal.wesovideo;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.*;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Test-only native footage. No desktop input or production render modifications. */
public final class VideoShowcase implements FabricClientGameTest {
    private ClientGameTestContext context;
    private Object overlay;
    private Path output;
    private final List<String> shotIds = new ArrayList<>();
    private final int frames = Integer.getInteger("weso.video.frames", 90);
    private final boolean beauty = Boolean.getBoolean("weso.video.beauty");
    private final boolean pickups = Boolean.getBoolean("weso.video.pickups");
    private final boolean closing = Boolean.getBoolean("weso.video.closing");
    private final boolean editingPickups = Boolean.getBoolean("weso.video.editingPickups");
    private final String locationMode = System.getProperty("weso.video.location", "none");
    private final boolean location = !locationMode.equals("none");

    @Override public void runTest(ClientGameTestContext context) {
        this.context = context;
        output = Path.of(System.getProperty("weso.video.output"));
        try { Files.createDirectories(output); }
        catch (Exception ex) { throw new AssertionError(ex); }
        context.runOnClient(client -> {
            client.options.renderDistance().set(location ? (locationMode.equals("full") ? 12 : 8) : 6);
            client.options.guiScale().set(2);
            client.options.framerateLimit().set(30);
            client.options.enableVsync().set(false);
            client.options.bobView().set(false);
            client.options.fov().set(70);
            client.options.pauseOnLostFocus = false;
            client.options.getSoundSourceOptionInstance(net.minecraft.sounds.SoundSource.MASTER).set(0.0);
            client.resizeGui();
        });
        try (var world = openFilmingWorld()) {
            var server = world.getServer();
            if (location) {
                if (locationMode.equals("full")) captureCelestia(world);
                else if (locationMode.equals("scout")) captureLocation(world);
                else throw new AssertionError("Unknown filming location mode");
                finish(); return;
            }
            server.runOnServer(nativeServer -> {
                nativeServer.getPlayerList().setViewDistance(8);
                var source = nativeServer.createCommandSourceStack().withSuppressedOutput();
                for (String command : buildCommands())
                    nativeServer.getCommands().performPrefixedCommand(source, command);
            });
            context.waitFor(client -> {
                for (int x = -3; x <= 1; x++) for (int z = -2; z <= 3; z++)
                    if (client.level.getChunkSource().getChunk(x, z,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) return false;
                return true;
            });
            context.takeScreenshot("atelier-readiness");
            // Check rendered meshes after the actual filming area loads, not every far view-distance corner.
            world.getConnection().waitForChunksRender(false);
            configureOverlay();
            server.runOnServer(nativeServer -> {
                var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
                var we = WorldEdit.getInstance();
                var session = we.getSessionManager().get(actor);
                session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                        BlockVector3.at(ShowcaseWorld.MIN_X, ShowcaseWorld.MIN_Y, ShowcaseWorld.MIN_Z),
                        BlockVector3.at(ShowcaseWorld.MAX_X, ShowcaseWorld.MAX_Y, ShowcaseWorld.MAX_Z)));
                session.dispatchCUISelection(actor);
            });
            context.waitTicks(20);
            if (beauty) {
                context.waitTicks(80);
                orbit("beauty-01-courtyard", -40, 81, 54, -36, 81, 55, 6, 80, 8);
                draft("//replace stone_bricks quartz_block"); ready();
                shot("beauty-02-quartz-draft"); escape();
                orbit("beauty-03-restored", -36, 81, 55, -40, 81, 54, 6, 80, 8);
                finish(); return;
            }
            shot("01-gate-original");
            draft("//replace stone_bricks quartz_block"); ready();
            context.waitTicks(20); shot("02-replace-preview");
            escape(); context.waitTicks(20); shot("03-replace-cancelled");
            context.runOnClient(client -> { call(overlay, "setRulerEnabled", true); call(overlay, "setSelectionGridEnabled", true); });
            context.waitTicks(10); shot("20-grid-and-ruler");
            context.runOnClient(client -> call(overlay, "setSelectionGridEnabled", false));
            context.waitTicks(10); shot("04-boundary-ruler-no-grid");
            context.runOnClient(client -> call(overlay, "setRulerEnabled", false));
            open(66, "SelectionBlocksScreen"); context.waitTicks(30); shot("05-inspector");
            context.runOnClient(client -> ((EditBox) field(client.gui.screen(), "searchField")).setValue("#solid"));
            context.waitTicks(8); shot("21-inspector-search");
            context.runOnClient(client -> ((EditBox) field(client.gui.screen(), "searchField")).setValue(""));
            context.runOnClient(client -> call(overlay, "setIsolatedBlockIds", Set.of("minecraft:gold_block", "minecraft:cut_copper")));
            context.waitTicks(12); shot("06-inspector-selected");
            context.runOnClient(client -> client.gui.setScreen(null));
            context.waitTicks(30); shot("07-isolated-materials");
            context.runOnClient(client -> call(overlay, "setOverlayEnabled", false));
            context.waitTicks(10); shot("22-isolation-overlay-off");
            context.runOnClient(client -> call(overlay, "setOverlayEnabled", true));
            context.runOnClient(client -> call(overlay, "setIsolatedBlockIds", Set.of()));
            context.waitTicks(30); shot("08-isolation-reset");
            draft("//replace stone_bricks oak_button"); ready();
            context.waitTicks(10); shot("23-partial-model-replacement"); escape();
            draft("//replace 98 1"); ready(); context.waitTicks(10); shot("24-legacy-ids"); escape();
            draft("//replace stone_bricks 70%stone_bricks,30%quartz_block"); ready();
            context.waitTicks(12); shot("09-percentage-mixture");
            escape(); draft("//set air"); ready(); context.waitTicks(12); shot("10-removal-preview");
            escape(); draft("//walls glass"); ready(); context.waitTicks(12); shot("11-walls-preview");
            escape();

            captureEditing(server);
            captureClipboard(server);

            for (String shape : List.of("ellipsoid", "cylinder", "polygon", "convex")) {
                server.runOnServer(nativeServer -> {
                    var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
                    RegionSelector selector;
                    if (shape.equals("ellipsoid")) selector = new EllipsoidRegionSelector(actor.getWorld(),
                            BlockVector3.at(6, 80, 8), Vector3.at(18, 16, 13));
                    else if (shape.equals("cylinder")) selector = new CylinderRegionSelector(actor.getWorld(),
                            BlockVector2.at(6, 8), Vector2.at(18, 13), 65, 94);
                    else if (shape.equals("polygon")) selector = new Polygonal2DRegionSelector(actor.getWorld(),
                            List.of(BlockVector2.at(-9, -3), BlockVector2.at(20, -1), BlockVector2.at(19, 19), BlockVector2.at(-4, 22)), 65, 94);
                    else {
                        var convex = new ConvexPolyhedralRegionSelector(actor.getWorld());
                        var region = (com.sk89q.worldedit.regions.ConvexPolyhedralRegion) convex.getIncompleteRegion();
                        for (var point : List.of(BlockVector3.at(-9, 65, -3), BlockVector3.at(20, 65, -1),
                                BlockVector3.at(14, 65, 22), BlockVector3.at(6, 95, 8))) region.addVertex(point);
                        convex.learnChanges(); selector = convex;
                    }
                    var session = WorldEdit.getInstance().getSessionManager().get(actor);
                    session.setRegionSelector(actor.getWorld(), selector); session.dispatchCUISelection(actor);
                });
                context.waitTicks(20); shot("12-shape-" + shape);
            }
            server.runOnServer(nativeServer -> {
                var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
                var session = WorldEdit.getInstance().getSessionManager().get(actor);
                session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                        BlockVector3.at(ShowcaseWorld.MIN_X, ShowcaseWorld.MIN_Y, ShowcaseWorld.MIN_Z),
                        BlockVector3.at(ShowcaseWorld.MAX_X, ShowcaseWorld.MAX_Y, ShowcaseWorld.MAX_Z)));
                session.dispatchCUISelection(actor);
            });
            open(79, "OverlaySettingsScreen"); context.waitTicks(10); shot("13-settings");
            clickField("colorEditorButton"); context.waitTicks(15); shot("14-palette-editor");
            context.runOnClient(client -> {
                Object screen = client.gui.screen();
                double x = ((Number) call(screen, "spectrumX")).doubleValue() + 150;
                double y = ((Number) call(screen, "spectrumY")).doubleValue() + 30;
                call(screen, "beginPaletteEdit"); call(screen, "updateSpectrum", x, y, false); call(screen, "finishPaletteEdit");
            });
            context.waitTicks(8); shot("25-palette-color-change");
            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(90, 0, 2)));
            context.waitTicks(8); shot("26-palette-undo");
            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(89, 0, 2)));
            context.waitTicks(8); shot("27-palette-redo");
            context.clickScreenButton("Presets..."); context.waitTicks(6); shot("15-palette-presets");
            context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(256, 0, 0)));
            context.runOnClient(client -> client.gui.screen().onClose()); context.waitTicks(12);
            context.clickScreenButton("Appearance"); context.waitTicks(6); shot("16-appearance-options");
            context.clickScreenButton("Studio"); context.waitTicks(10); shot("17-settings-studio");
            context.clickScreenButton("Appearance"); context.clickScreenButton("Classic");
            context.waitTicks(10); shot("18-settings-classic");
            context.clickScreenButton("Appearance"); context.clickScreenButton("Glass");
            context.runOnClient(client -> client.gui.setScreen(null)); context.waitTicks(10);
            shot("19-final-gate");
            // Source-world verification: every command in this fixture was a visual draft.
            server.runOnServer(nativeServer -> {
                var state = nativeServer.getPlayerList().getPlayers().getFirst().level().getBlockState(new net.minecraft.core.BlockPos(0, 69, 3));
                if (!state.is(net.minecraft.world.level.block.Blocks.STONE_BRICKS))
                    throw new AssertionError("A filmed draft changed the real build");
            });
            finish();
        }
    }

    private TestSingleplayerContext openFilmingWorld() {
        var created = context.worldBuilder().adjustSettings(s -> s.setAllowCommands(true)).create();
        if (!location) return created;
        var save = created.getWorldSave();
        created.close();
        Path directory = save.getSaveDirectory().toAbsolutePath().normalize();
        try {
            Path archive = Path.of(System.getProperty("weso.video.map"));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(archive)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = stream.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(
                    "1f6c55d0a3691fe4b8aae5d0551766cbf33298bcf64eb3a763d411ea52ee45cb"))
                throw new AssertionError("Celestia source archive changed; review rights and source before import");
            // Import overworld regions only; retain the current test level, not players or packs.
            Path regions = net.minecraft.world.level.dimension.DimensionType.getStorageFolder(
                    net.minecraft.world.level.Level.OVERWORLD, directory).resolve("region").toAbsolutePath().normalize();
            if (!regions.startsWith(directory)) throw new AssertionError("Dimension path escaped disposable save");
            Files.createDirectories(regions);
            int count = 0;
            long total = 0;
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    String prefix = "Celestia Town by leolerenard/region/";
                    if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                    String name = entry.getName().substring(prefix.length());
                    if (!name.matches("r\\.-?\\d+\\.-?\\d+\\.mca")) throw new AssertionError("Unexpected region path");
                    total += entry.getSize();
                    if (entry.getSize() < 0 || total > 512L * 1024 * 1024) throw new AssertionError("Unbounded map import");
                    Path target = regions.resolve(name).normalize();
                    if (!target.getParent().equals(regions)) throw new AssertionError("Map path escaped test save");
                    try (var stream = zip.getInputStream(entry)) {
                        Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    count++;
                }
            }
            if (count != 23) throw new AssertionError("Incomplete Celestia region import: " + count);
        } catch (Exception ex) { throw new AssertionError("Disposable filming-world import failed", ex); }
        return save.open();
    }

    private void captureLocation(TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("gamemode spectator @p");
        server.runCommand("time set 2000");
        server.runCommand("weather clear");
        server.runCommand("gamerule minecraft:advance_time false");
        server.runCommand("gamerule minecraft:spawn_mobs false");
        int n = 0;
        for (double[] view : new double[][]{{365,99,-130,423,75,-175},
                {444,84,-113,420,75,-139}, {409,87,-184,438,78,-207},
                {470,94,-214,513,77,-255}}) {
            double x=view[0], y=view[1], z=view[2], tx=view[3], ty=view[4], tz=view[5];
            int yaw = (int) Math.round(Math.toDegrees(Math.atan2(x - tx, tz - z)));
            int pitch = (int) Math.round(-Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx-x,tz-z))));
            server.runCommand("tp @p " + x + " " + (y - 1.62) + " " + z + " " + yaw + " " + pitch);
            context.waitFor(client -> {
                int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
                for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++)
                    if (client.level.getChunkSource().getChunk(cx+dx, cz+dz,
                            net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) return false;
                return true;
            });
            world.getConnection().waitForChunksRender(false);
            context.waitTicks(40);
            context.runOnClient(client -> {
                if (client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        (int) tx, (int) tz) < 60) throw new AssertionError("Imported filming terrain is absent");
                client.gui.hud.getChat().clearMessages(false);
            });
            orbit("location-0" + (++n), x,y,z,x+2,y,z,tx,ty,tz);
        }
        configureOverlay();
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                    BlockVector3.at(490, 68, -281), BlockVector3.at(537, 88, -234)));
            session.dispatchCUISelection(actor);
        });
        context.waitTicks(20);
        shot("location-05-real-selection");
        draft("//replace smooth_sandstone cyan_terracotta"); ready();
        context.waitTicks(12); shot("location-06-native-command-draft"); escape();
        context.waitTicks(12); shot("location-07-restored");
        open(66, "SelectionBlocksScreen"); context.waitTicks(30); shot("location-08-native-inspector");
        context.runOnClient(client -> client.gui.setScreen(null));
    }

    private void configureOverlay() {
        open(79, "OverlaySettingsScreen");
        overlay = context.computeOnClient(client -> field(client.gui.screen(), "overlayClient"));
        context.runOnClient(client -> {
            call(overlay, "setUiBlur", false);
            call(overlay, "setReducedMotion", false);
            call(overlay, "setIsolatedBlockIds", Set.of());
            call(overlay, "setClipboardPreviewEnabled", false);
            call(overlay, "setOverlayEnabled", true);
            call(overlay, "setRulerEnabled", false);
            call(overlay, "setSelectionGridEnabled", false);
            call(overlay, "resetAllOverlayColors");
            call(overlay, "setAllOverlayLineWidthScale", 1.0f);
            client.gui.setScreen(null);
            client.gui.hud.getChat().clearMessages(false);
        });
    }

    private void houseSelection(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            session.setRegionSelector(actor.getWorld(), new CuboidRegionSelector(actor.getWorld(),
                    BlockVector3.at(431, 70, -214), BlockVector3.at(445, 87, -200)));
            session.dispatchCUISelection(actor);
        });
        context.waitTicks(16);
    }

    private void houseCamera(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runCommand("tp @p 416 83.38 -188 -131 14");
        context.waitTicks(16);
    }

    private void captureCelestia(TestSingleplayerContext world) {
        var server = world.getServer();
        server.runCommand("gamemode spectator @p");
        server.runCommand("time set 2000"); server.runCommand("weather clear");
        server.runCommand("gamerule minecraft:advance_time false");
        server.runCommand("gamerule minecraft:spawn_mobs false");
        server.runOnServer(nativeServer -> nativeServer.getPlayerList().setViewDistance(12));
        houseCamera(server);
        context.waitFor(client -> {
            int cx = client.player.chunkPosition().x(), cz = client.player.chunkPosition().z();
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++)
                if (client.level.getChunkSource().getChunk(cx+dx, cz+dz,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) return false;
            return true;
        });
        // Fabric's download helper checks a square; vanilla tracks a rounded area instead.
        var visibleChunks = server.computeOnServer(nativeServer -> {
            var positions = new ArrayList<net.minecraft.world.level.ChunkPos>();
            var player = nativeServer.getPlayerList().getPlayers().getFirst();
            net.minecraft.server.level.ChunkTrackingView.of(player.chunkPosition(),12)
                    .forEach(positions::add);
            return List.copyOf(positions);
        });
        context.waitFor(client -> visibleChunks.stream().allMatch(pos -> client.level.getChunkSource().getChunk(
                pos.x(),pos.z(),net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false) != null),2400);
        world.getConnection().waitForChunksRender(false,2400); context.waitTicks(40);
        System.out.println("VIDEO TERRAIN READY: native tracking area downloaded and rendered; chunks="+
                visibleChunks.size()+", distance=12");
        configureOverlay(); houseSelection(server);
        String before = houseFingerprint(world);
        if (editingPickups) {
            if (beauty || pickups || closing) throw new AssertionError("Editing evidence requires its own vanilla pass");
            captureEditingPickups(server);
            if (!before.equals(houseFingerprint(world))) throw new AssertionError("Editing changed world blocks");
            System.out.println("VIDEO WORLD VERIFY PASS: editing pickups changed only WorldEdit bounds");
            return;
        }
        if (pickups) {
            if (beauty) throw new AssertionError("UI evidence must be recorded without shaders");
            capturePalettePickups();
            if (!before.equals(houseFingerprint(world))) throw new AssertionError("Palette changed world blocks");
            System.out.println("VIDEO WORLD VERIFY PASS: palette pickups left every source block unchanged");
            return;
        }
        if (closing) {
            if (!beauty) throw new AssertionError("Closing requires the separate beauty profile");
            orbit("53-closing-orbit",416,85,-188,421,86,-184,438,78,-207);
            if (!before.equals(houseFingerprint(world))) throw new AssertionError("Closing changed world blocks");
            System.out.println("VIDEO WORLD VERIFY PASS: continuous closing left every source block unchanged");
            return;
        }
        shot("01-house-original");
        draft("//replace smooth_sandstone cyan_terracotta"); ready();
        context.waitTicks(12); shot("02-replace-preview"); escape();
        context.waitTicks(12); shot("03-replace-cancelled");
        if (beauty) {
            orbit("04-hero-orbit",416,85,-188,419,85,-185,438,78,-207);
            if (!before.equals(houseFingerprint(world))) throw new AssertionError("Beauty draft changed the build");
            return;
        }
        context.runOnClient(client -> { call(overlay, "setRulerEnabled", true); call(overlay, "setSelectionGridEnabled", true); });
        context.waitTicks(12); shot("05-grid-ruler");
        context.runOnClient(client -> call(overlay, "setSelectionGridEnabled", false));
        context.waitTicks(12); shot("06-boundary-ruler");
        orbit("07-ruler-camera",409,87,-184,414,87,-181,438,78,-207);
        houseCamera(server);
        context.runOnClient(client -> call(overlay, "setRulerEnabled", false));
        for (String shape : List.of("ellipsoid", "cylinder", "polygon", "convex")) {
            server.runOnServer(nativeServer -> {
                var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
                RegionSelector selector;
                if (shape.equals("ellipsoid")) selector = new EllipsoidRegionSelector(actor.getWorld(),
                        BlockVector3.at(438,79,-207),Vector3.at(9,11,9));
                else if (shape.equals("cylinder")) selector = new CylinderRegionSelector(actor.getWorld(),
                        BlockVector2.at(438,-207),Vector2.at(9,9),70,90);
                else if (shape.equals("polygon")) selector = new Polygonal2DRegionSelector(actor.getWorld(),
                        List.of(BlockVector2.at(429,-214),BlockVector2.at(445,-217),BlockVector2.at(450,-200),BlockVector2.at(433,-195)),70,89);
                else {
                    var convex = new ConvexPolyhedralRegionSelector(actor.getWorld());
                    var region = (com.sk89q.worldedit.regions.ConvexPolyhedralRegion) convex.getIncompleteRegion();
                    for (var point : List.of(BlockVector3.at(429,70,-214),BlockVector3.at(447,70,-216),
                            BlockVector3.at(440,70,-195),BlockVector3.at(438,91,-207))) region.addVertex(point);
                    convex.learnChanges(); selector = convex;
                }
                var session = WorldEdit.getInstance().getSessionManager().get(actor);
                session.setRegionSelector(actor.getWorld(),selector); session.dispatchCUISelection(actor);
            });
            context.waitTicks(16); shot("08-shape-" + shape);
        }
        houseSelection(server);
        open(79,"OverlaySettingsScreen"); context.waitTicks(10); shot("09-render-settings");
        context.runOnClient(client -> client.gui.setScreen(null));
        key(78,1); key(78,0); context.waitTicks(12); shot("10-deselected"); houseSelection(server);
        open(66,"SelectionBlocksScreen"); context.waitTicks(30); shot("11-inspector");
        context.runOnClient(client -> ((EditBox) field(client.gui.screen(),"searchField")).setValue("#solid"));
        context.waitTicks(8); shot("12-inspector-filter");
        context.runOnClient(client -> ((EditBox) field(client.gui.screen(),"searchField")).setValue(""));
        context.runOnClient(client -> call(overlay,"setIsolatedBlockIds",Set.of("minecraft:smooth_sandstone","minecraft:bricks")));
        context.waitTicks(16); shot("13-inspector-selected");
        context.runOnClient(client -> client.gui.setScreen(null)); context.waitTicks(30); shot("14-isolated-materials");
        context.runOnClient(client -> call(overlay,"setOverlayEnabled",false));
        context.waitTicks(12); shot("15-isolation-overlay-off");
        context.runOnClient(client -> { call(overlay,"setOverlayEnabled",true); call(overlay,"setIsolatedBlockIds",Set.of()); });
        context.waitTicks(30); shot("16-isolation-reset");
        // The fountain is a separate, real source-map selection for the fluid inspection shot.
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            session.setRegionSelector(actor.getWorld(),new CuboidRegionSelector(actor.getWorld(),
                    BlockVector3.at(437,65,-199),BlockVector3.at(461,80,-177)));
            session.dispatchCUISelection(actor);
        }); context.waitTicks(16);
        open(66,"SelectionBlocksScreen"); context.waitTicks(30);
        context.runOnClient(client -> ((EditBox) field(client.gui.screen(),"searchField")).setValue("#fluid"));
        context.waitTicks(8); shot("17-fluid-inspector");
        context.runOnClient(client -> client.gui.setScreen(null)); houseSelection(server);
        draft("//replace smooth_sandstone oak_button"); ready(); context.waitTicks(12); shot("18-partial-models"); escape();
        draft("//set 1"); ready(); context.waitTicks(12); shot("19-numeric-id"); escape();
        draft("//replace smooth_sandstone 70%smooth_sandstone,30%cyan_terracotta"); ready();
        context.waitTicks(12); shot("20-random-mixture"); escape();
        draft("//set air"); ready(); context.waitTicks(12); shot("21-removal"); escape();
        draft("//walls glass"); ready(); context.waitTicks(12); shot("22-walls"); escape();
        draft("//outline glass"); ready(); context.waitTicks(12); shot("23-outline"); escape();
        captureHouseEditing(server); houseSelection(server); houseCamera(server);
        captureHouseClipboard(server); houseSelection(server); houseCamera(server);
        capturePalette();
        orbit("45-final-house",409,87,-184,413,87,-181,438,78,-207);
        if (!before.equals(houseFingerprint(world))) throw new AssertionError("A visual draft changed source-world blocks");
        System.out.println("VIDEO WORLD VERIFY PASS: every state in the selected house unchanged after all drafts");
    }

    private String houseFingerprint(TestSingleplayerContext world) {
        return world.getServer().computeOnServer(server -> {
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                var level = server.getPlayerList().getPlayers().getFirst().level();
                int sandstone = 0;
                for (int y=70; y<=87; y++) for (int z=-214; z<=-200; z++) for (int x=431; x<=445; x++) {
                    var state = level.getBlockState(new net.minecraft.core.BlockPos(x,y,z));
                    digest.update(state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (state.is(net.minecraft.world.level.block.Blocks.SMOOTH_SANDSTONE)) sandstone++;
                }
                if (sandstone == 0) throw new AssertionError("Chosen house lacks the demonstrated source material");
                return HexFormat.of().formatHex(digest.digest());
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
    }

    private void chooseEditingMode(Object editing, boolean move, String take) {
        key(71,1); context.waitFor(client -> (boolean) call(editing,"radialOpen"));
        context.runOnClient(client -> {
            double target = ((Number) call(editing,"menuRadius",client)).doubleValue() * (move ? .65 : -.65);
            double x = ((Number) field(editing,"radialMouseX")).doubleValue() +
                    (target - ((Number) field(editing,"radialX")).doubleValue()) *
                    client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
            nativeCall(client.mouseHandler,"onMove",client.getWindow().handle(),x,
                    ((Number) field(editing,"radialMouseY")).doubleValue());
        });
        shot(take); key(71,0); context.waitTicks(8);
    }

    private void captureHouseEditing(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        open(79,"OverlaySettingsScreen"); context.waitTicks(8); shot("24-edit-settings");
        context.runOnClient(client -> client.gui.setScreen(null));
        Object editing = field(overlay,"selectionEditController");
        context.runOnClient(client -> {
            setField(client.getWindow(),"focused",true);
            setField(client.mouseHandler,"mouseGrabbed",true);
            call(editing,"configure",true,1,8);
        });
        context.waitFor(client -> field(editing,"shown") != null);
        server.runCommand("tp @p 438.5 77.38 -182 180 0"); context.waitTicks(12);
        chooseEditingMode(editing,false,"25-radial-resize");
        tapG(); context.waitFor(client -> (boolean) call(editing,"active"));
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onMove",client.getWindow().handle(),960.0,540.0));
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onButton",client.getWindow().handle(),new MouseButtonInfo(0,0),1));
        shot("26-resize-face",frame -> {
            if (frame >= frames/4 && frame <= frames*3/4) context.runOnClient(client ->
                    nativeCall(client.mouseHandler,"onMove",client.getWindow().handle(),960.0,540.0-96.0*(frame-frames/4)/(frames/2.0)));
        });
        context.runOnClient(client -> {
            nativeCall(client.mouseHandler,"onButton",client.getWindow().handle(),new MouseButtonInfo(0,0),0);
            if (!(boolean) call(editing,"active")) throw new AssertionError("Resize applied before G");
        }); shot("27-resize-held"); tapG(); context.waitFor(client -> !(boolean) call(editing,"pending"));
        context.waitTicks(12); shot("28-resize-applied"); houseSelection(server);
        context.waitFor(client -> field(editing,"shown") != null);
        chooseEditingMode(editing,true,"29-radial-move"); tapG();
        context.waitFor(client -> (boolean) call(editing,"active"));
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onButton",client.getWindow().handle(),new MouseButtonInfo(0,0),1));
        context.waitFor(client -> (boolean) call(editing,"dragging")); key(89,1); key(89,0);
        shot("30-move-y-wheel",frame -> {
            if (frame == frames/3 || frame == frames*2/3) context.runOnClient(client ->
                    nativeCall(client.mouseHandler,"onScroll",client.getWindow().handle(),0.0,2.0));
        });
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onButton",client.getWindow().handle(),new MouseButtonInfo(0,0),0));
        shot("31-move-held"); key(256,1); key(256,0); context.waitTicks(12); shot("32-edit-cancelled");
        context.runOnClient(client -> call(editing,"configure",false,1,8));
    }

    private void captureEditingPickups(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        Object editing = field(overlay,"selectionEditController");
        context.runOnClient(client -> {
            setField(client.getWindow(),"focused",true);
            setField(client.mouseHandler,"mouseGrabbed",true);
            call(editing,"configure",true,1,8);
        });
        context.waitFor(client -> field(editing,"shown") != null);
        server.runCommand("tp @p 438.5 80.38 -178 180 5"); context.waitTicks(16);
        // Aim at a real visible corner, then use the shipped direct-corner input path.
        context.runOnClient(client -> {
            var eye=client.gameRenderer.mainCamera().position();
            double dx=431-eye.x, dy=88-eye.y, dz=-199-eye.z;
            client.player.setYRot((float) Math.toDegrees(Math.atan2(-dx,dz)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        });
        context.waitTicks(8);
        context.waitFor(client -> {
            Object renderer=field(editing,"renderer");
            return call(renderer,"cornerHit",call(field(editing,"shown"),"bounds"),call(renderer,"view",client))!=null;
        });
        shot("54-quick-corner",frame -> {
            if (frame == frames/5) {
                mouseButton(1);
                context.runOnClient(client -> {
                    if (!(boolean) call(editing,"dragging")) throw new AssertionError("Quick-corner take did not grip a corner");
                });
            }
            if (frame > frames/5 && frame < frames*3/5) context.runOnClient(client ->
                    client.player.setXRot(client.player.getXRot()-0.10f));
            if (frame == frames*3/5) mouseButton(0);
        });
        context.waitFor(client -> !(boolean) call(editing,"pending"));
        houseSelection(server);
        server.runCommand("tp @p 438.5 80.38 -178 180 5"); context.waitTicks(12);
        chooseEditingMode(editing,true,"59-move-menu");
        shot("60-move-grab",frame -> {
            if (frame == frames/6) tapG();
            if (frame == frames/3) mouseButton(1);
        });
        context.waitFor(client -> (boolean) call(editing,"dragging"));
        shot("61-move-compass");
        Object draftBeforeTurn = context.computeOnClient(client -> call(editing,"draft"));
        shot("62-move-camera",frame -> {
            double t = Math.clamp((frame-frames/6.0)/(frames*2.0/3),0,1);
            t = t*t*(3-2*t);
            float yaw = (float) (180-10*t);
            context.runOnClient(client -> client.player.setYRot(yaw));
        });
        context.runOnClient(client -> {
            if (!draftBeforeTurn.equals(call(editing,"draft"))) throw new AssertionError("Camera turn moved the selection");
        });
        shot("63-move-x",frame -> {
            if (frame == frames/6) { key(88,1); key(88,0); }
            if (frame == frames/3 || frame == frames/2 || frame == frames*2/3) scroll(1);
        });
        context.runOnClient(client -> {
            Object draft = call(editing,"draft");
            if (((Number) call(draft,"minX")).intValue()!=434 || ((Number) call(draft,"minY")).intValue()!=70)
                throw new AssertionError("Filmed X scroll did not change only X");
        });
        shot("64-move-y",frame -> {
            if (frame == frames/6) { key(89,1); key(89,0); }
            if (frame == frames/3 || frame == frames/2 || frame == frames*2/3) scroll(1);
        });
        context.runOnClient(client -> {
            Object draft = call(editing,"draft");
            if (((Number) call(draft,"minX")).intValue()!=434 || ((Number) call(draft,"minY")).intValue()!=73)
                throw new AssertionError("Filmed Y scroll did not retain X and lift Y");
        });
        shot("65-move-release",frame -> { if (frame == frames/6) mouseButton(0); });
        context.runOnClient(client -> {
            if (!(boolean) call(editing,"active")) throw new AssertionError("Move applied on mouse release");
        });
        shot("66-move-apply",frame -> { if (frame == frames/3) tapG(); });
        context.waitFor(client -> !(boolean) call(editing,"pending"));
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            try {
                var bounds = WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld());
                if (bounds.getMinimumPoint().x()!=434 || bounds.getMinimumPoint().y()!=73)
                    throw new AssertionError("G did not apply the demonstrated WorldEdit bounds");
            } catch (com.sk89q.worldedit.IncompleteRegionException ex) { throw new AssertionError(ex); }
        });
        context.waitFor(client -> field(editing,"shown") != null);
        tapG(); mouseButton(1); key(89,1); key(89,0); scroll(-2); mouseButton(0);
        shot("67-move-cancel",frame -> { if (frame == frames/3) { key(256,1); key(256,0); } });
        context.runOnClient(client -> {
            if ((boolean) call(editing,"active")) throw new AssertionError("Escape did not cancel the draft");
        });
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            try {
                if (WorldEdit.getInstance().getSessionManager().get(actor).getSelection(actor.getWorld()).getMinimumPoint().y()!=73)
                    throw new AssertionError("Cancellation changed the applied WorldEdit bounds");
            } catch (com.sk89q.worldedit.IncompleteRegionException ex) { throw new AssertionError(ex); }
        });
        System.out.println("VIDEO EDITING VERIFY PASS: camera stable, X/Y scroll, mouse release, G apply, Escape cancel");
        context.runOnClient(client -> call(editing,"configure",false,1,8));
        houseSelection(server);
    }

    private void mouseButton(int action) {
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onButton",client.getWindow().handle(),new MouseButtonInfo(0,0),action));
    }

    private void scroll(double amount) {
        context.runOnClient(client -> nativeCall(client.mouseHandler,"onScroll",client.getWindow().handle(),0.0,amount));
    }

    private void captureHouseClipboard(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runCommand("tp @p 438 71 -207 0 0"); context.waitTicks(12);
        draft("//copy"); context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(257,0,0)));
        context.waitTicks(20);
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            try { session.getClipboard(); } catch (Exception ex) { throw new AssertionError("//copy failed",ex); }
            session.setPlacement(new com.sk89q.worldedit.session.Placement(
                    com.sk89q.worldedit.session.PlacementType.WORLD,BlockVector3.at(468,75,-164)));
        });
        server.runCommand("tp @p 438 92.38 -130 -139 16"); context.waitTicks(20);
        context.runOnClient(client -> { call(overlay,"setClipboardPreviewEnabled",true); client.gui.hud.getChat().clearMessages(false); });
        Object source = field(field(overlay,"clipboardPreviewController"),"source");
        context.waitFor(client -> !(boolean) call(call(source,"snapshot"),"scanning") &&
                !((List<?>) call(call(source,"snapshot"),"blocks")).isEmpty());
        shot("33-clipboard-original"); draft("//rotate 90");
        context.waitFor(client -> call(call(source,"transformStatus"),"reason").equals("clipboard_ready"));
        context.waitTicks(12); shot("34-clipboard-rotate"); escape();
        draft("//flip east"); context.waitFor(client -> call(call(source,"transformStatus"),"reason").equals("clipboard_ready"));
        context.waitTicks(12); shot("35-clipboard-flip"); escape(); shot("36-clipboard-restored");
        draft("//replace smooth_sandstone cyan_terracotta"); ready(); context.waitTicks(12); shot("37-preview-priority");
        escape(); context.waitTicks(12); shot("38-clipboard-returned");
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            try {
                if (!WorldEdit.getInstance().getSessionManager().get(actor).getClipboard().getTransform().isIdentity())
                    throw new AssertionError("Clipboard draft changed the true transform");
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
        context.runOnClient(client -> call(overlay,"setClipboardPreviewEnabled",false));
        System.out.println("VIDEO CLIPBOARD VERIFY PASS: rotation and reflection never changed the true buffer");
    }

    private void capturePalette() {
        open(79,"OverlaySettingsScreen"); context.waitTicks(10); shot("39-settings");
        clickField("colorEditorButton"); context.waitTicks(15); shot("40-palette-editor");
        context.runOnClient(client -> {
            Object screen = client.gui.screen();
            double x=((Number) call(screen,"spectrumX")).doubleValue()+150;
            double y=((Number) call(screen,"spectrumY")).doubleValue()+30;
            call(screen,"beginPaletteEdit"); call(screen,"updateSpectrum",x,y,false); call(screen,"finishPaletteEdit");
        }); context.waitTicks(8); shot("41-palette-color");
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(90,0,2)));
        context.waitTicks(8); shot("42-palette-undo");
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(89,0,2)));
        context.waitTicks(8); shot("43-palette-redo");
        context.clickScreenButton("Presets..."); context.waitTicks(8); shot("44-presets");
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(256,0,0)));
        context.runOnClient(client -> client.gui.screen().onClose()); context.waitTicks(12);
        context.clickScreenButton("Appearance"); context.waitTicks(8); shot("44a-appearance");
        context.clickScreenButton("Studio"); context.waitTicks(10); shot("44b-studio");
        context.clickScreenButton("Appearance"); context.clickScreenButton("Classic");
        context.waitTicks(10); shot("44c-classic");
        context.clickScreenButton("Appearance"); context.clickScreenButton("Glass");
        context.runOnClient(client -> client.gui.setScreen(null)); context.waitTicks(12);
    }

    private void capturePalettePickups() {
        open(79,"OverlaySettingsScreen"); clickField("colorEditorButton"); context.waitTicks(15);
        context.clickScreenButton("Ruler"); context.waitTicks(8); shot("46-ruler-palette");
        context.clickScreenButton("Editing"); context.waitTicks(8); shot("47-editing-palette");
        context.clickScreenButton("Cuboid / Extend"); context.waitTicks(8);
        shot("48-element-thickness", frame -> {
            if (frame >= frames/4 && frame <= frames*3/4) context.runOnClient(client -> {
                Object screen = client.gui.screen();
                double x = ((Number) call(screen,"redSliderX")).doubleValue()+18;
                double width = ((Number) call(screen,"controlsWidth")).doubleValue()-18;
                call(screen,"updateLineWidthScale",x+width*(1.0/3+0.55*(frame-frames/4)/(frames/2.0)),false);
            });
        });
        context.runOnClient(client -> {
            call(client.gui.screen(),"finishPaletteEdit");
            if ((boolean) call(client.gui.screen(),"isGlobalLineWidthEnabled"))
                throw new AssertionError("Mixed widths did not disable global slider");
        });
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(90,0,2)));
        context.waitTicks(8);
        context.runOnClient(client -> {
            if (!(boolean) call(client.gui.screen(),"isGlobalLineWidthEnabled"))
                throw new AssertionError("Undo failed to restore matching widths");
        }); shot("49-global-thickness");
        context.clickScreenButton("Presets..."); context.waitTicks(8);
        context.runOnClient(client -> {
            Object screen = client.gui.screen();
            double x = ((Number) call(screen,"templateMenuX")).doubleValue()+20;
            double y = ((Number) call(screen,"templateMenuY")).doubleValue()+
                    ((Number) call(screen,"templateMenuItemHeight")).doubleValue()+12;
            if (!(boolean) call(screen,"tryClickTemplateMenu",x,y) || field(screen,"pendingPreset") == null)
                throw new AssertionError("Preset confirmation did not open");
        }); context.waitTicks(8); shot("50-preset-confirm");
        context.runOnClient(client -> {
            Object screen = client.gui.screen();
            double x = ((Number) call(screen,"confirmApplyX")).doubleValue()+20;
            double y = ((Number) call(screen,"confirmButtonsY")).doubleValue()+8;
            call(screen,"clickPresetConfirmation",x,y);
            if (field(screen,"pendingPreset") != null) throw new AssertionError("Preset did not apply");
        }); context.waitTicks(10); shot("51-preset-applied");
        clickField("resetMenuButton"); context.waitTicks(8); shot("52-reset-menu");
        escape();
        context.runOnClient(client -> client.gui.screen().onClose());
        context.runOnClient(client -> { call(overlay,"resetAllOverlayColors"); call(overlay,"setAllOverlayLineWidthScale",1.0f); });
        context.runOnClient(client -> client.gui.setScreen(null)); context.waitTicks(8);
    }

    private void shot(String name) {
        shot(name, frame -> {});
    }
    private void shot(String name, IntConsumer action) {
        context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
        context.takeScreenshot("video-" + name);
        Path encoder = Path.of(System.getProperty("weso.video.ffmpeg"));
        if (!Files.isRegularFile(encoder)) throw new AssertionError("Missing verified FFmpeg path");
        if (frames < 1 || frames > 1800) throw new AssertionError("Unbounded recording request");
        Process process = null;
        try {
            process = new ProcessBuilder(encoder.toString(), "-hide_banner", "-nostdin", "-y",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", "1920x1080", "-framerate", "30", "-i", "pipe:0",
                    "-an", "-c:v", "libx264", "-threads", "2", "-preset", "veryfast", "-crf", "17",
                    "-pix_fmt", "yuv420p", "-colorspace", "bt709", "-color_primaries", "bt709",
                    "-color_trc", "bt709", "-movflags", "+faststart", output.resolve(name + ".mp4").toString())
                    .redirectError(output.resolve(name + ".encoder.log").toFile()).start();
            byte[] bytes = new byte[1920 * 1080 * 4];
            try (OutputStream stream = process.getOutputStream()) {
                for (int frame = 0; frame < frames; frame++) {
                    action.accept(frame);
                    CompletableFuture<NativeImage> image = new CompletableFuture<>();
                    context.runOnClient(client -> Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image::complete));
                    context.waitFor(client -> image.isDone());
                    try (NativeImage pixels = image.get(20, TimeUnit.SECONDS)) {
                        if (pixels.getWidth() != 1920 || pixels.getHeight() != 1080)
                            throw new AssertionError("Recording viewport changed");
                        pixels.getPixelBytes().get(bytes);
                        stream.write(bytes);
                    }
                }
            }
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0)
                throw new AssertionError("Encoder failed: " + name);
            shotIds.add(name);
            System.out.println("VIDEO TAKE: " + name + ", " + frames + " native frames");
        } catch (Exception ex) { throw new AssertionError("Capture failed: " + name, ex); }
        finally { if (process != null && process.isAlive()) process.destroyForcibly(); }
    }

    private void finish() {
        try { Files.write(output.resolve("shots.txt"), shotIds); }
        catch (Exception ex) { throw new AssertionError(ex); }
        System.out.println("WESO VIDEO CAPTURE PASS: native UI and geometry, " + shotIds.size() + " source takes, location=" + locationMode + ", shaders=" + beauty);
    }

    private void orbit(String name, double ax, double ay, double az, double bx, double by, double bz,
                       double tx, double ty, double tz) {
        shot(name, frame -> context.runOnClient(client -> {
            double t = frame / (double) Math.max(1, frames - 1);
            t = t * t * (3 - 2 * t);
            double x = ax + (bx - ax) * t, y = ay + (by - ay) * t, z = az + (bz - az) * t;
            client.player.setPos(x, y - 1.62, z);
            client.player.setYRot((float) Math.toDegrees(Math.atan2(x - tx, tz - z)));
            client.player.setXRot((float) -Math.toDegrees(Math.atan2(ty - y, Math.hypot(tx - x, tz - z))));
        }));
    }

    private void captureEditing(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        Object editing = field(overlay, "selectionEditController");
        context.runOnClient(client -> call(editing, "configure", true, 1, 8));
        context.waitFor(client -> field(editing, "shown") != null);
        server.runCommand("tp @p 6 78.88 54 180 0"); context.waitTicks(12);
        context.runOnClient(client -> { setField(client.getWindow(), "focused", true); nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 1, new KeyEvent(71, 0, 0)); });
        context.waitFor(client -> (boolean) call(editing, "radialOpen"));
        context.runOnClient(client -> {
            double radius = ((Number) call(editing, "menuRadius", client)).doubleValue();
            double dx = radius * 0.65;
            double x = ((Number) field(editing, "radialMouseX")).doubleValue() + dx * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth();
            nativeCall(client.mouseHandler, "onMove", client.getWindow().handle(), x, ((Number) field(editing, "radialMouseY")).doubleValue());
        });
        shot("28-edit-radial");
        context.runOnClient(client -> nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), 0, new KeyEvent(71, 0, 0)));
        context.waitTicks(8);
        tapG(); context.waitFor(client -> (boolean) call(editing, "active"));
        shot("29-edit-move-ready");
        context.runOnClient(client -> nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 1));
        context.waitFor(client -> (boolean) call(editing, "dragging"));
        key(89, 1); key(89, 0);
        shot("30-edit-y-wheel", frame -> {
            if (frame == frames / 3 || frame == frames * 2 / 3)
                context.runOnClient(client -> nativeCall(client.mouseHandler, "onScroll", client.getWindow().handle(), 0.0, 2.0));
        });
        context.runOnClient(client -> nativeCall(client.mouseHandler, "onButton", client.getWindow().handle(), new MouseButtonInfo(0, 0), 0));
        shot("31-edit-draft-held");
        key(256, 1); key(256, 0); context.waitTicks(12); shot("32-edit-cancelled");
        context.runOnClient(client -> call(editing, "configure", false, 1, 8));
        server.runCommand("tp @p -40 79.38 54 -135 1"); context.waitTicks(12);
    }

    private void captureClipboard(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runCommand("tp @p 8 65 5 0 0"); context.waitTicks(12);
        draft("//copy");
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(257, 0, 0)));
        context.waitTicks(20);
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            var session = WorldEdit.getInstance().getSessionManager().get(actor);
            try { session.getClipboard(); } catch (Exception ex) { throw new AssertionError("Native //copy failed", ex); }
            session.setPlacement(new com.sk89q.worldedit.session.Placement(com.sk89q.worldedit.session.PlacementType.WORLD, BlockVector3.at(64, 65, 10)));
        });
        server.runCommand("tp @p 94 84 46 140 5"); context.waitTicks(20);
        context.runOnClient(client -> { call(overlay, "setClipboardPreviewEnabled", true); client.gui.hud.getChat().clearMessages(false); });
        Object source = field(field(overlay, "clipboardPreviewController"), "source");
        context.waitFor(client -> !(boolean) call(call(source, "snapshot"), "scanning") && !((List<?>) call(call(source, "snapshot"), "blocks")).isEmpty());
        shot("33-clipboard-original");
        draft("//rotate 90"); context.waitFor(client -> call(call(source, "transformStatus"), "reason").equals("clipboard_ready"));
        context.waitTicks(15); shot("34-clipboard-rotate"); escape(); context.waitTicks(15);
        draft("//flip east"); context.waitFor(client -> call(call(source, "transformStatus"), "reason").equals("clipboard_ready"));
        context.waitTicks(15); shot("35-clipboard-flip"); escape(); context.waitTicks(15); shot("36-clipboard-restored");
        server.runOnServer(nativeServer -> {
            var actor = FabricAdapter.get().fromNativePlayer(nativeServer.getPlayerList().getPlayers().getFirst());
            try {
                if (!WorldEdit.getInstance().getSessionManager().get(actor).getClipboard().getTransform().isIdentity())
                    throw new AssertionError("A clipboard draft changed the real transform");
            } catch (Exception ex) { throw new AssertionError(ex); }
        });
        context.runOnClient(client -> call(overlay, "setClipboardPreviewEnabled", false));
        server.runCommand("tp @p -40 79.38 54 -135 1"); context.waitTicks(15);
    }

    private void key(int key, int action) {
        context.runOnClient(client -> nativeCall(client.keyboardHandler, "keyPress", client.getWindow().handle(), action, new KeyEvent(key, 0, 0)));
    }
    private void tapG() { key(71, 1); key(71, 0); context.waitTicks(4); }
    private static void setField(Object target, String name, Object value) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static void nativeCall(Object target, String name, Object... args) { call(target, name, args); }

    private void draft(String text) {
        context.runOnClient(client -> client.gui.setScreen(new ChatScreen(text, false)));
    }
    private void ready() {
        context.waitFor(client -> {
            Object controller = field(overlay, "commandPreviewController");
            return call(call(controller, "snapshot"), "state").toString().equals("READY");
        });
    }
    private void escape() {
        context.runOnClient(client -> client.gui.screen().keyPressed(new KeyEvent(256, 0, 0)));
        context.waitTicks(10);
    }
    private void open(int key, String name) {
        context.runOnClient(client -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(key)));
        context.waitFor(client -> client.gui.screen() != null && client.gui.screen().getClass().getSimpleName().equals(name));
    }
    private void clickField(String name) {
        String text = context.computeOnClient(client -> ((AbstractWidget) field(client.gui.screen(), name)).getMessage().getString());
        context.clickScreenButton(text);
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
    private static Object call(Object target, String name, Object... args) {
        for (var method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            try { method.setAccessible(true); return method.invoke(target, args); }
            catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
        }
        throw new AssertionError("Missing method " + name);
    }
    private static List<String> buildCommands() {
        return ShowcaseWorld.commands();
    }
}

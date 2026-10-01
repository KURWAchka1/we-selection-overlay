package org.marshal.wesotest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

@Mod("weso_isolation_test")
public final class NeoIsolationTest {
    private static final BlockPos DIAMOND = new BlockPos(15, 66, 15);
    private static final BlockPos GOLD = new BlockPos(17, 67, 17);
    private static final BlockPos OUTSIDE = new BlockPos(-4, 64, 0);
    private static final List<Set<String>> STEPS = List.of(Set.of("minecraft:diamond_block"),
            Set.of("minecraft:diamond_block", "minecraft:gold_block"), Set.of("minecraft:gold_block"), Set.of());
    private int ticks;
    private int stage;
    private int step;
    private int wait;
    private int baselineWait;
    private boolean uiCaptured;
    private Object overlay;
    private Object area;
    private Object oldMesh;
    private Object outsideMesh;

    public NeoIsolationTest() {
        System.out.println("WESO NEO TEST: initialized");
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
    }

    private void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
        }
    }

    private void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        check(++ticks < 2400, "Test timed out at stage " + stage + " step " + step);
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            onboarding.onClose();
        }
        if (ticks % 100 == 0) {
            System.out.println("WESO NEO TEST: stage=" + stage + " step=" + step + " screen=" + client.screen);
        }
        if (client.level == null || client.player == null) return;
        if (wait > 0) { wait--; return; }
        if (stage == 0) {
            new ModelBlockRenderer(client.getBlockColors());
            client.options.renderDistance().set(5);
            client.getSingleplayerServer().execute(() -> {
                var server = client.getSingleplayerServer();
                for (String command : List.of("fill -8 64 -8 40 64 32 stone", "fill 12 65 12 20 76 20 air",
                        "fill 14 65 14 18 69 18 stone", "setblock 15 66 15 diamond_block", "setblock 17 67 17 gold_block")) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode spectator @p");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @p 27 76 34 145 25");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight");
            });
            stage = 1;
            wait = 100;
        } else if (stage == 1) {
            KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(79));
            stage = 2;
        } else if (stage == 2) {
            if (client.screen == null || !client.screen.getClass().getSimpleName().equals("OverlaySettingsScreen")) return;
            if (!uiCaptured) { uiCaptured = true; wait = 12; return; }
            Screenshot.grab(client.gameDirectory, "ui-settings.png", client.getMainRenderTarget(), 1, message -> {});
            overlay = field(client.screen, "overlayClient");
            Object manager = field(overlay, "selectionManager");
            call(manager, "setFallbackCuboidPoint", 0, new BlockPos(14, 65, 14));
            call(manager, "setFallbackCuboidPoint", 1, new BlockPos(18, 69, 18));
            call(overlay, "setIsolatedBlockIds", Set.of());
            client.setScreen(null);
            client.player.setYRot(145);
            client.player.setXRot(25);
            client.player.yRotO = 145;
            client.player.xRotO = 25;
            stage = 3;
            wait = 40;
        } else if (stage == 3) {
            if (step == 8) {
                System.out.println("WESO NEO ISOLATION PASS: 8 toggles, surrounding meshes preserved, selected faces visible, all vertices fullbright with AO on/off");
                stage = 5;
                wait = 20;
                return;
            }
            area = field(client.levelRenderer, "viewArea");
            if (step == 0 && baselineWait++ == 0) {
                System.out.println("WESO NEO BASELINE: outside=" + client.level.getBlockState(OUTSIDE)
                        + " yaw=" + client.player.getYRot() + " pitch=" + client.player.getXRot()
                        + " mesh=" + section(client, OUTSIDE).getSectionMesh());
                Screenshot.grab(client.gameDirectory, "baseline.png", client.getMainRenderTarget(), 1, message -> {});
            }
            if (step == 0 && !section(client, OUTSIDE).getSectionMesh().hasRenderableLayers()) {
                check(baselineWait < 200, "Fixture section never became visible");
                return;
            }
            outsideMesh = section(client, OUTSIDE).getSectionMesh();
            oldMesh = section(client, DIAMOND).getSectionMesh();
            check(section(client, OUTSIDE).getSectionMesh().hasRenderableLayers(), "Outside section empty before toggle");
            call(overlay, "setIsolatedBlockIds", STEPS.get(step % 4));
            assertSurroundings(client);
            stage = 4;
            wait = 20;
        } else if (stage == 4) {
            if (section(client, DIAMOND).getSectionMesh() == oldMesh) return;
            assertSurroundings(client);
            var selected = STEPS.get(step % 4);
            if (selected.contains("minecraft:diamond_block")) assertFullbright(client, DIAMOND, Blocks.DIAMOND_BLOCK.defaultBlockState());
            if (selected.contains("minecraft:gold_block")) assertFullbright(client, GOLD, Blocks.GOLD_BLOCK.defaultBlockState());
            assertSelectedFace(client, DIAMOND, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                    selected.contains("minecraft:diamond_block"));
            assertSelectedFace(client, GOLD, Blocks.GOLD_BLOCK.defaultBlockState(),
                    selected.contains("minecraft:gold_block"));
            Screenshot.grab(client.gameDirectory, "isolation-" + (++step) + ".png", client.getMainRenderTarget(), 1, message -> {});
            stage = 3;
            wait = 10;
        } else if (stage == 5) {
            client.stop();
        }
    }

    private void assertSurroundings(Minecraft client) {
        check(field(client.levelRenderer, "viewArea") == area, "Toggle replaced the view area");
        check(section(client, OUTSIDE).getSectionMesh() == outsideMesh,
                "Toggle discarded an unrelated section mesh");
    }

    private void assertSelectedFace(Minecraft client, BlockPos pos, BlockState state, boolean expected) {
        try {
            Class<?> controller = Class.forName(overlay.getClass().getPackageName() + ".BlockIsolationController");
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getName().equals("shouldDrawFaceAgainstHiddenNeighbor") && method.getParameterCount() == 4) {
                    method.setAccessible(true);
                    boolean actual = (boolean) method.invoke(null, client.level, state, Direction.NORTH, pos.relative(Direction.NORTH));
                    check(actual == expected, "Selected face visibility mismatch at " + pos);
                    return;
                }
            }
            throw new AssertionError("Missing selected-face visibility method");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void assertFullbright(Minecraft client, BlockPos pos, BlockState state) {
        var dispatcher = client.getBlockRenderer();
        var parts = dispatcher.getBlockModel(state).collectParts(RandomSource.create(0));
        for (boolean ao : new boolean[]{false, true}) {
            client.options.ambientOcclusion().set(ao);
            var output = new LightConsumer();
            dispatcher.getModelRenderer().tesselateBlock(client.level, parts, state, pos, new PoseStack(),
                    layer -> output, false, OverlayTexture.NO_OVERLAY);
            check(output.vertices >= 24, "Renderer did not emit all faces");
        }
    }

    private static final class LightConsumer implements VertexConsumer {
        int vertices;
        public VertexConsumer addVertex(float x, float y, float z) { vertices++; return this; }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            check(r == 255 && g == 255 && b == 255, "NeoForge quad still shaded");
            return this;
        }
        public VertexConsumer setColor(int color) { return setColor((color >>> 16) & 255, (color >>> 8) & 255, color & 255, color >>> 24); }
        public VertexConsumer setUv(float u, float v) { return this; }
        public VertexConsumer setUv1(int u, int v) { return this; }
        public VertexConsumer setUv2(int u, int v) {
            check((u | (v << 16)) == LightTexture.FULL_BRIGHT, "NeoForge vertex not fullbright");
            return this;
        }
        public VertexConsumer setNormal(float x, float y, float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }

    private static net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section(Minecraft client, BlockPos pos) {
        return (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection)
                call(field(client.levelRenderer, "viewArea"), "getRenderSectionAt", pos);
    }

    private static Object field(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return invoke(method, target, args);
            }
        }
        throw new AssertionError("Missing method " + name);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try { return method.invoke(target, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}

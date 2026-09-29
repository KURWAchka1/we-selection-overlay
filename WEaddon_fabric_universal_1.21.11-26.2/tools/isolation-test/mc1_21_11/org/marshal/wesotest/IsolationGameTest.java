package org.marshal.wesotest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public final class IsolationGameTest implements FabricClientGameTest {
    private static final BlockPos DIAMOND = new BlockPos(14, 66, 18);
    private static final BlockPos GOLD = new BlockPos(18, 67, 18);
    private static final BlockPos OUTSIDE = new BlockPos(-4, 64, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.getViewDistance().setValue(5));
        try (var world = context.worldBuilder().create()) {
            for (String command : List.of("gamemode spectator @p", "fill -8 64 -8 40 64 32 stone",
                    "fill 12 65 12 20 76 20 air", "fill 14 65 14 18 69 18 stone",
                    "setblock 14 66 18 diamond_block", "setblock 18 67 18 gold_block",
                    "time set midnight", "tp @p 27 76 34 145 25")) world.getServer().runCommand(command);
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(79)));
            context.waitFor(client -> client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("OverlaySettingsScreen"));
            Object overlay = context.computeOnClient(client -> field(client.currentScreen, "overlayClient"));
            context.runOnClient(client -> {
                Object manager = field(overlay, "selectionManager");
                call(manager, "setFallbackCuboidPoint", 0, new BlockPos(14, 65, 14));
                call(manager, "setFallbackCuboidPoint", 1, new BlockPos(18, 69, 18));
                call(overlay, "setIsolatedBlockIds", Set.of());
                client.setScreen(null);
            });
            context.waitTicks(10);
            world.getClientWorld().waitForChunksRender();
            var steps = List.of(Set.of("minecraft:diamond_block"), Set.of("minecraft:diamond_block", "minecraft:gold_block"),
                    Set.of("minecraft:gold_block"), Set.<String>of());
            for (int i = 0; i < 8; i++) {
                Set<String> selected = steps.get(i % 4);
                Object builder = context.computeOnClient(client -> client.worldRenderer.getChunkBuilder());
                Object outside = context.computeOnClient(client -> section(client, OUTSIDE).getCurrentRenderData());
                Object previous = context.computeOnClient(client -> section(client, DIAMOND).getCurrentRenderData());
                context.runOnClient(client -> {
                    check(section(client, OUTSIDE).getCurrentRenderData().hasData(), "Outside chunk is empty");
                    call(overlay, "setIsolatedBlockIds", selected);
                    check(client.worldRenderer.getChunkBuilder() == builder, "Toggle discarded the chunk builder");
                    check(section(client, OUTSIDE).getCurrentRenderData() == outside, "Toggle discarded unrelated geometry");
                });
                context.waitFor(client -> section(client, DIAMOND).getCurrentRenderData() != previous);
                world.getClientWorld().waitForChunksRender();
                context.runOnClient(client -> {
                    check(section(client, OUTSIDE).getCurrentRenderData().hasData(), "Surrounding world disappeared");
                    if (selected.contains("minecraft:diamond_block")) fullbright(client, DIAMOND, Blocks.DIAMOND_BLOCK.getDefaultState());
                    if (selected.contains("minecraft:gold_block")) fullbright(client, GOLD, Blocks.GOLD_BLOCK.getDefaultState());
                    selectedFace(overlay, client, DIAMOND, Blocks.DIAMOND_BLOCK.getDefaultState(),
                            selected.contains("minecraft:diamond_block"));
                    selectedFace(overlay, client, GOLD, Blocks.GOLD_BLOCK.getDefaultState(),
                            selected.contains("minecraft:gold_block"));
                });
                context.takeScreenshot("isolation-" + i + "-" + selected.size() + "-selected");
            }
            System.out.println("WESO 1.21.11 ISOLATION PASS: 8 toggles, surrounding chunks preserved, selected faces visible, fullbright vanilla smooth/flat and Indigo");
        }
    }

    private static ChunkBuilder.BuiltChunk section(MinecraftClient client, BlockPos pos) {
        return client.worldRenderer.getBuiltChunks().stream()
                .filter(chunk -> chunk.getSectionPos() == ChunkSectionPos.from(pos).asLong()).findFirst()
                .orElseThrow(() -> new AssertionError("Section not visible: " + pos));
    }

    private static void selectedFace(Object overlay, MinecraftClient client, BlockPos pos, BlockState state, boolean expected) {
        try {
            Class<?> controller = Class.forName(overlay.getClass().getPackageName() + ".BlockIsolationController");
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getName().equals("shouldDrawFaceAgainstHiddenNeighbor") && method.getParameterCount() == 4) {
                    method.setAccessible(true);
                    boolean actual = (boolean) method.invoke(null, client.world, state, Direction.NORTH, pos.offset(Direction.NORTH));
                    check(actual == expected, "Selected face visibility mismatch at " + pos);
                    return;
                }
            }
            throw new AssertionError("Missing selected-face visibility method");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void fullbright(MinecraftClient client, BlockPos pos, BlockState state) {
        var manager = client.getBlockRenderManager();
        var model = manager.getModel(state);
        var renderer = manager.getModelRenderer();
        var parts = model.getParts(Random.create(0));
        var output = new LightConsumer();
        renderer.renderSmooth(client.world, parts, state, pos, new MatrixStack(), output, false, OverlayTexture.DEFAULT_UV);
        check(output.vertices >= 24, "Smooth renderer did not emit all faces");
        output.vertices = 0;
        renderer.renderFlat(client.world, parts, state, pos, new MatrixStack(), output, false, OverlayTexture.DEFAULT_UV);
        check(output.vertices >= 24, "Flat renderer did not emit all faces");
        output.vertices = 0;
        Renderer.get().render(renderer, client.world, model, state, pos, new MatrixStack(), layer -> output,
                false, state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV);
        check(output.vertices >= 24, "Indigo did not emit all faces");
    }

    private static final class LightConsumer implements VertexConsumer {
        int vertices;
        public VertexConsumer vertex(float x, float y, float z) { vertices++; return this; }
        public VertexConsumer color(int r, int g, int b, int a) {
            check(r == 255 && g == 255 && b == 255, "Quad still has AO or directional shading");
            return this;
        }
        public VertexConsumer color(int color) { return color((color >>> 16) & 255, (color >>> 8) & 255, color & 255, color >>> 24); }
        public VertexConsumer texture(float u, float v) { return this; }
        public VertexConsumer overlay(int u, int v) { return this; }
        public VertexConsumer light(int u, int v) {
            check((u | (v << 16)) == LightmapTextureManager.MAX_LIGHT_COORDINATE, "Vertex not fullbright");
            return this;
        }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public VertexConsumer lineWidth(float width) { return this; }
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
                try { method.setAccessible(true); return method.invoke(target, args); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }
        }
        throw new AssertionError("Missing method: " + name);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

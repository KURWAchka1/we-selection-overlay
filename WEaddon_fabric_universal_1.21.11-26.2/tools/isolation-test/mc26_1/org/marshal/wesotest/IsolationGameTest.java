package org.marshal.wesotest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public final class IsolationGameTest implements FabricClientGameTest {
    private static final BlockPos DIAMOND = new BlockPos(15, 66, 15);
    private static final BlockPos GOLD = new BlockPos(17, 67, 17);
    private static final BlockPos OUTSIDE = new BlockPos(-4, 64, 0);

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.renderDistance().set(5));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("fill -8 64 -8 40 64 32 stone");
            world.getServer().runCommand("fill 12 65 12 20 76 20 air");
            world.getServer().runCommand("fill 14 65 14 18 69 18 stone");
            world.getServer().runCommand("setblock 15 66 15 diamond_block");
            world.getServer().runCommand("setblock 17 67 17 gold_block");
            world.getServer().runCommand("time set midnight");
            world.getServer().runCommand("tp @p 27 76 34 145 25");
            world.getClientLevel().waitForChunksRender();

            context.runOnClient(client -> KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(79)));
            context.waitFor(client -> client.screen != null
                    && client.screen.getClass().getSimpleName().equals("OverlaySettingsScreen"));
            Object overlay = context.computeOnClient(client -> field(client.screen, "overlayClient"));
            context.waitTicks(12);
            context.takeScreenshot("ui-settings");
            context.runOnClient(client -> {
                Object manager = field(overlay, "selectionManager");
                call(manager, "setFallbackCuboidPoint", 0, new BlockPos(14, 65, 14));
                call(manager, "setFallbackCuboidPoint", 1, new BlockPos(18, 69, 18));
                call(overlay, "setIsolatedBlockIds", Set.of());
                client.setScreen(null);
            });
            context.waitTicks(10);
            world.getClientLevel().waitForChunksRender();
            context.takeScreenshot("isolation-00-baseline");

            var steps = List.of(Set.of("minecraft:diamond_block"),
                    Set.of("minecraft:diamond_block", "minecraft:gold_block"),
                    Set.of("minecraft:gold_block"), Set.<String>of());
            int stepIndex = 0;
            for (int cycle = 0; cycle < 2; cycle++) {
                for (Set<String> selected : steps) {
                    Object area = context.computeOnClient(client -> field(client.levelRenderer, "viewArea"));
                    Object outsideMesh = context.computeOnClient(client -> section(client, OUTSIDE).getSectionMesh());
                    Object oldMesh = context.computeOnClient(client -> section(client, DIAMOND).getSectionMesh());
                    context.runOnClient(client -> {
                        check(outsideMesh instanceof CompiledSectionMesh mesh && mesh.hasRenderableLayers(), "Outside section is empty before toggle");
                        call(overlay, "setIsolatedBlockIds", selected);
                        check(field(client.levelRenderer, "viewArea") == area, "Toggle discarded the world's view area");
                        check(section(client, OUTSIDE).getSectionMesh() == outsideMesh,
                                "Toggle discarded an unrelated chunk mesh");
                    });
                    context.waitFor(client -> section(client, DIAMOND).getSectionMesh() != oldMesh);
                    world.getClientLevel().waitForChunksRender();
                    context.runOnClient(client -> {
                        check(field(client.levelRenderer, "viewArea") == area, "View area changed after rebuild");
                        check(section(client, OUTSIDE).getSectionMesh() instanceof CompiledSectionMesh mesh
                                && mesh.hasRenderableLayers(), "Outside world disappeared after rebuild");
                        if (selected.contains("minecraft:diamond_block")) {
                            assertFullbright(client, DIAMOND, Blocks.DIAMOND_BLOCK.defaultBlockState());
                        }
                        assertSelectedFace(overlay, client, DIAMOND, Blocks.DIAMOND_BLOCK.defaultBlockState(),
                                selected.contains("minecraft:diamond_block"));
                        if (selected.contains("minecraft:gold_block")) {
                            assertFullbright(client, GOLD, Blocks.GOLD_BLOCK.defaultBlockState());
                        }
                        assertSelectedFace(overlay, client, GOLD, Blocks.GOLD_BLOCK.defaultBlockState(),
                                selected.contains("minecraft:gold_block"));
                    });
                    context.takeScreenshot("isolation-" + (++stepIndex) + "-" + selected.size() + "-selected");
                }
            }
            System.out.println("WESO ISOLATION PASS: 8 toggles, local rebuilds, selected faces visible, all quad vertices fullbright (AO on/off, vanilla/Indigo)");
        }
    }

    private static void assertSelectedFace(Object overlay, Minecraft client, BlockPos pos, BlockState state, boolean expected) {
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
        var level = new RenderRegionCache().createRegion(client.level, SectionPos.of(pos).asLong());
        Object models = client.getModelManager().getBlockStateModelSet();
        BlockStateModel model = null;
        for (Method method : models.getClass().getMethods()) {
            if (method.getReturnType() == BlockStateModel.class && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == BlockState.class) {
                model = (BlockStateModel) invoke(method, models, state);
                break;
            }
        }
        check(model != null, "Block model is missing");
        for (boolean ao : new boolean[]{false, true}) {
            int[] vertices = {0};
            new ModelBlockRenderer(ao, false, client.getBlockColors()).tesselateBlock((x, y, z, quad, instance) -> {
                for (int i = 0; i < 4; i++) {
                    check(instance.getLightCoords(i) == LightCoordsUtil.FULL_BRIGHT, "Vanilla lightmap not fullbright");
                    check(instance.getColor(i) == -1, "Vanilla quad still shaded");
                    vertices[0]++;
                }
            }, 0, 0, 0, level, pos, state, model, state.getSeed(pos));
            check(vertices[0] >= 24, "Vanilla did not emit six block faces");
            vertices[0] = 0;
            var renderer = Renderer.get();
            var output = renderer.quadEmitter(quad -> {
                for (int i = 0; i < 4; i++) {
                    check(quad.lightmap(i) == LightCoordsUtil.FULL_BRIGHT, "Indigo lightmap not fullbright");
                    check(quad.color(i) == -1, "Indigo quad still shaded");
                    vertices[0]++;
                }
            });
            renderer.altModelBlockRenderer(ao, false, client.getBlockColors())
                    .tesselateBlock(output, 0, 0, 0, level, pos, state, model, state.getSeed(pos));
            check(vertices[0] >= 24, "Indigo did not emit six block faces");
        }
    }

    private static net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection section(Minecraft client, BlockPos pos) {
        return (net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection)
                call(field(client.levelRenderer, "viewArea"), "getRenderSectionAt", pos);
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return invoke(method, target, args);
            }
        }
        throw new AssertionError("Missing method: " + name);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}

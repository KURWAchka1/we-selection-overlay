package org.marshal.weaddon_lightposition.client.clipboard;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.IdentityHashMap;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

final class ClipboardPreviewRenderer {
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final int OUTLINE_COLOR = 0x00000000;
    private static final int BOUNDS_STROKE = 0xD56DF7FF;
    private static final float BOUNDS_STROKE_WIDTH = 1.6f;
    private static final double MAX_RENDER_DISTANCE_SQR = 192.0 * 192.0;

    private final IdentityHashMap<BlockState, BlockModelRenderState> modelCache = new IdentityHashMap<>();
    private final BlockDisplayContext displayContext = BlockDisplayContext.create();

    private ModelManager cachedModelManager;
    private BlockModelResolver resolver;

    void render(LevelRenderContext context, ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gameRenderer == null) {
            return;
        }

        ensureResolver(client);

        Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
        PoseStack poseStack = context.poseStack();
        List<ClipboardPreviewBlock> blocks = snapshot.blocks();
        int baseX = snapshot.placement().getX();
        int baseY = snapshot.placement().getY();
        int baseZ = snapshot.placement().getZ();

        for (ClipboardPreviewBlock block : blocks) {
            int worldX = baseX + block.x();
            int worldY = baseY + block.y();
            int worldZ = baseZ + block.z();
            double dx = worldX - cameraPos.x();
            double dy = worldY - cameraPos.y();
            double dz = worldZ - cameraPos.z();
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            BlockModelRenderState model = modelFor(block.state());
            if (model.isEmpty()) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(dx, dy, dz);
            model.submit(poseStack, context.submitNodeCollector(), FULL_BRIGHT_LIGHT, OverlayTexture.NO_OVERLAY, OUTLINE_COLOR);
            poseStack.popPose();
        }
    }

    void renderBounds(ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        Gizmos.cuboid(
                        snapshot.bounds().toWorldAabb(snapshot.placement()),
                        GizmoStyle.stroke(BOUNDS_STROKE, BOUNDS_STROKE_WIDTH),
                        true
                )
                .setAlwaysOnTop()
                .persistForMillis(75);
    }

    void clearCache() {
        modelCache.clear();
        cachedModelManager = null;
        resolver = null;
    }

    private void ensureResolver(Minecraft client) {
        ModelManager modelManager = client.getModelManager();
        if (resolver != null && cachedModelManager == modelManager) {
            return;
        }

        cachedModelManager = modelManager;
        resolver = new BlockModelResolver(modelManager);
        modelCache.clear();
    }

    private BlockModelRenderState modelFor(BlockState state) {
        BlockModelRenderState cached = modelCache.get(state);
        if (cached != null) {
            return cached;
        }

        BlockModelRenderState model = new BlockModelRenderState();
        resolver.update(model, state, displayContext);
        modelCache.put(state, model);
        return model;
    }
}

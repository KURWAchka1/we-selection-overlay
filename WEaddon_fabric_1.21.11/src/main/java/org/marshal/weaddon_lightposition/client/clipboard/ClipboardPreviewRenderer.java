package org.marshal.weaddon_lightposition.client.clipboard;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

final class ClipboardPreviewRenderer {
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final int BOUNDS_STROKE = 0xD56DF7FF;
    private static final float BOUNDS_STROKE_WIDTH = 1.6f;
    private static final double MAX_RENDER_DISTANCE_SQR = 192.0 * 192.0;

    void render(WorldRenderContext context, ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.gameRenderer == null) {
            return;
        }

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) {
            return;
        }

        Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
        List<ClipboardPreviewBlock> blocks = snapshot.blocks();
        int baseX = snapshot.placement().getX();
        int baseY = snapshot.placement().getY();
        int baseZ = snapshot.placement().getZ();

        for (ClipboardPreviewBlock block : blocks) {
            int worldX = baseX + block.x();
            int worldY = baseY + block.y();
            int worldZ = baseZ + block.z();
            double dx = worldX - cameraPos.x;
            double dy = worldY - cameraPos.y;
            double dz = worldZ - cameraPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_RENDER_DISTANCE_SQR) {
                continue;
            }

            matrices.push();
            matrices.translate(dx, dy, dz);
            client.getBlockRenderManager().renderBlockAsEntity(
                    block.state(),
                    matrices,
                    consumers,
                    FULL_BRIGHT_LIGHT,
                    OverlayTexture.DEFAULT_UV
            );
            matrices.pop();
        }
    }

    void renderBounds(ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        GizmoDrawing.box(
                        snapshot.bounds().toWorldBox(snapshot.placement()),
                        DrawStyle.stroked(BOUNDS_STROKE, BOUNDS_STROKE_WIDTH)
                )
                .ignoreOcclusion()
                .withLifespan(75);
    }

    void clearCache() {
    }
}

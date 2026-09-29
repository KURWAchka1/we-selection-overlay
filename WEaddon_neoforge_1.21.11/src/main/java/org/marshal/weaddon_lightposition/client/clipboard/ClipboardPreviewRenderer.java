package org.marshal.weaddon_lightposition.client.clipboard;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

final class ClipboardPreviewRenderer {
    private static final int FULL_BRIGHT_LIGHT = 0x00F000F0;
    private static final int BOUNDS_STROKE = 0xD56DF7FF;
    private static final float BOUNDS_STROKE_WIDTH = 1.6f;
    private static final double MAX_RENDER_DISTANCE_SQR = 192.0 * 192.0;

    void render(PoseStack matrices, ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.gameRenderer == null) {
            return;
        }

        if (matrices == null) {
            return;
        }
        MultiBufferSource.BufferSource consumers = client.renderBuffers().bufferSource();

        Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
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

            matrices.pushPose();
            matrices.translate(dx, dy, dz);
            client.getBlockRenderer().renderSingleBlock(
                    block.state(),
                    matrices,
                    consumers,
                    FULL_BRIGHT_LIGHT,
                    OverlayTexture.NO_OVERLAY
            );
            matrices.popPose();
        }
        consumers.endBatch();
    }

    void renderBounds(ClipboardPreviewSnapshot snapshot) {
        if (snapshot == null || !snapshot.active()) {
            return;
        }

        Gizmos.cuboid(
                        snapshot.bounds().toWorldBox(snapshot.placement()),
                        GizmoStyle.stroke(BOUNDS_STROKE, BOUNDS_STROKE_WIDTH)
                )
                .setAlwaysOnTop()
                .persistForMillis(75);
    }

    void clearCache() {
    }
}

package org.marshal.weaddon_lightposition.mixin;

import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.marshal.weaddon_lightposition.client.VertexConsumerRenderGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;

@Mixin(VertexConsumer.class)
public interface VertexConsumerFullbrightMixin {
    @Inject(method = "addVertex(FFFIFFIIFFF)V", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fadePackedVertexColor(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int overlay,
            int light,
            float normalX,
            float normalY,
            float normalZ,
            CallbackInfo ci
    ) {
        if (VertexConsumerRenderGuard.packedVertexBypassActive()) {
            return;
        }

        int fadedColor = BlockIsolationRenderState.applyAlpha(color);
        if (fadedColor == color) {
            return;
        }

        VertexConsumerRenderGuard.enterPackedVertexBypass();
        try {
            ((VertexConsumer) (Object) this).addVertex(
                    x,
                    y,
                    z,
                    fadedColor,
                    u,
                    v,
                    overlay,
                    light,
                    normalX,
                    normalY,
                    normalZ
            );
        } finally {
            VertexConsumerRenderGuard.exitPackedVertexBypass();
        }
        ci.cancel();
    }

    @Inject(method = "setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fadePackedColor(
            int color,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        int fadedColor = BlockIsolationRenderState.applyAlpha(color);
        if (fadedColor == color) {
            return;
        }

        cir.setReturnValue(((VertexConsumer) (Object) this).setColor(
                fadedColor >> 16 & 255,
                fadedColor >> 8 & 255,
                fadedColor & 255,
                fadedColor >>> 24
        ));
    }

    @Inject(
            method = "putBulkData(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/block/model/BakedQuad;[FFFFF[II)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$fullbrightQuad(
            PoseStack.Pose matrixEntry,
            BakedQuad quad,
            float[] brightnesses,
            float red,
            float green,
            float blue,
            float alpha,
            int[] lights,
            int overlay,
            CallbackInfo ci
    ) {
        if (!BlockIsolationRenderState.fullbrightActive()) {
            return;
        }

        if (brightnesses != null) {
            Arrays.fill(brightnesses, 1.0F);
        }
        if (lights != null) {
            Arrays.fill(lights, LightTexture.FULL_BRIGHT);
        }
    }

    @Inject(method = "setLight(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullbrightVertex(
            int light,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            int fullLight = LightTexture.FULL_BRIGHT;
            cir.setReturnValue(((VertexConsumer) (Object) this).setUv2(
                    fullLight & 0xFFFF,
                    fullLight >> 16 & 0xFFFF
            ));
        }
    }
}

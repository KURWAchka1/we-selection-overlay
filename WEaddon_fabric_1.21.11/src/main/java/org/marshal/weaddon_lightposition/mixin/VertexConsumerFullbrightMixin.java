package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.marshal.weaddon_lightposition.client.VertexConsumerRenderGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

@Mixin(VertexConsumer.class)
public interface VertexConsumerFullbrightMixin {
    @Inject(method = "vertex(FFFIFFIIFFF)V", at = @At("HEAD"), cancellable = true)
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
            ((VertexConsumer) (Object) this).vertex(
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

    @Inject(method = "color(I)Lnet/minecraft/client/render/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fadePackedColor(
            int color,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        int fadedColor = BlockIsolationRenderState.applyAlpha(color);
        if (fadedColor == color) {
            return;
        }

        cir.setReturnValue(((VertexConsumer) (Object) this).color(
                fadedColor >> 16 & 255,
                fadedColor >> 8 & 255,
                fadedColor & 255,
                fadedColor >>> 24
        ));
    }

    @Inject(
            method = "quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFFF[II)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$fullbrightQuad(
            MatrixStack.Entry matrixEntry,
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
            Arrays.fill(lights, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    @Inject(method = "light(I)Lnet/minecraft/client/render/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullbrightVertex(
            int light,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            int fullLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            cir.setReturnValue(((VertexConsumer) (Object) this).light(
                    fullLight & 0xFFFF,
                    fullLight >> 16 & 0xFFFF
            ));
        }
    }
}

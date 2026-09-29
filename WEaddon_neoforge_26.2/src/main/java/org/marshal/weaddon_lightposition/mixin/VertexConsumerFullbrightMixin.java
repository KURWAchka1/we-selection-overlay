package org.marshal.weaddon_lightposition.mixin;

import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.vertex.VertexConsumer;

@Mixin(VertexConsumer.class)
public interface VertexConsumerFullbrightMixin {
    @Inject(method = "setLight(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullbrightVertex(
            int light,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(((VertexConsumer) (Object) this).setUv2(
                    BlockIsolationRenderState.FULL_BRIGHT_LIGHT & 0xFFFF,
                    BlockIsolationRenderState.FULL_BRIGHT_LIGHT >> 16 & 0xFFFF
            ));
        }
    }
}

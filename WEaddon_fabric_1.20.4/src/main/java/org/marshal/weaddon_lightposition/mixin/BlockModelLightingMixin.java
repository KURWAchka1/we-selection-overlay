package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.render.block.BlockModelRenderer;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BlockModelRenderer.class)
abstract class BlockModelLightingMixin {
    @ModifyArgs(method = "renderQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFF[IIZ)V"))
    private void weso$fullbright(Args args) {
        if (!BlockIsolationRenderState.fullbrightActive()) return;
        float[] brightness = args.get(2);
        int[] light = args.get(6);
        java.util.Arrays.fill(brightness, 1.0f);
        java.util.Arrays.fill(light, BlockIsolationRenderState.FULL_BRIGHT_LIGHT);
    }
}

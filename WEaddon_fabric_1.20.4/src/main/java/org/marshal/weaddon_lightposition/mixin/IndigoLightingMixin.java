package org.marshal.weaddon_lightposition.mixin;

import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractBlockRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
abstract class IndigoLightingMixin {
    @Shadow @Final protected BlockRenderInfo blockInfo;

    @Inject(method = "shadeQuad", at = @At("HEAD"), cancellable = true)
    private void weso$fullbright(MutableQuadViewImpl quad, boolean vanilla, boolean ao, boolean emissive, CallbackInfo ci) {
        if (!BlockIsolationController.active() || !BlockIsolationController.shouldForceFullbright(blockInfo.blockState, blockInfo.blockPos)) return;
        for (int vertex = 0; vertex < 4; vertex++) quad.lightmap(vertex, BlockIsolationRenderState.FULL_BRIGHT_LIGHT);
        ci.cancel();
    }
}

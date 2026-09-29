package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$Cache")
public abstract class BlockModelRendererBrightnessCacheMixin {
    @Inject(method = "getLightColor", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullLightmap(
            BlockState state,
            BlockAndTintGetter world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(LightTexture.FULL_BRIGHT);
        }
    }

    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullAmbientLight(
            BlockState state,
            BlockAndTintGetter world,
            BlockPos pos,
            CallbackInfoReturnable<Float> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(1.0F);
        }
    }
}

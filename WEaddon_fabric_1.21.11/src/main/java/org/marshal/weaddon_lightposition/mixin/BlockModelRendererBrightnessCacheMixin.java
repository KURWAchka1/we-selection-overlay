package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.render.block.BlockModelRenderer$BrightnessCache")
public abstract class BlockModelRendererBrightnessCacheMixin {
    @Inject(method = "getInt", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullLightmap(
            BlockState state,
            BlockRenderView world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    @Inject(method = "getFloat", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullAmbientLight(
            BlockState state,
            BlockRenderView world,
            BlockPos pos,
            CallbackInfoReturnable<Float> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(1.0F);
        }
    }
}

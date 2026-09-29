package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererRenderStateMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockAndTintGetter world,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockAndTintGetter world,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popFullbright();
    }
}

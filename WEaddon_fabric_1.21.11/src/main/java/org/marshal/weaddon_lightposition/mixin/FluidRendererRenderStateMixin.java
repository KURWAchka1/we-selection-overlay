package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererRenderStateMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockRenderView world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.fluidRenderAlpha(blockState, fluidState, pos));
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockRenderView world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }
}

package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedBlock(
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(method = "renderBlock", at = @At("RETURN"))
    private void worldeditSelectionOverlay$clearIsolatedBlockState(
            BlockState state,
            BlockPos pos,
            BlockRenderView world,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedFluid(
            BlockPos pos,
            BlockRenderView world,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.fluidRenderAlpha(blockState, fluidState, pos));
    }

    @Inject(method = "renderFluid", at = @At("RETURN"))
    private void worldeditSelectionOverlay$clearIsolatedFluidState(
            BlockPos pos,
            BlockRenderView world,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }
}

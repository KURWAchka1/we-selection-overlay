package org.marshal.weaddon_lightposition.mixin;

import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderManagerMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedBlock(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter world,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(method = "renderBatched", at = @At("RETURN"))
    private void worldeditSelectionOverlay$clearIsolatedBlockState(
            BlockState state,
            BlockPos pos,
            BlockAndTintGetter world,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            List<BlockModelPart> parts,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(method = "renderLiquid", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedFluid(
            BlockPos pos,
            BlockAndTintGetter world,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.fluidRenderAlpha(blockState, fluidState, pos));
    }

    @Inject(method = "renderLiquid", at = @At("RETURN"))
    private void worldeditSelectionOverlay$clearIsolatedFluidState(
            BlockPos pos,
            BlockAndTintGetter world,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }
}

package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRendererRegion.class)
public abstract class ChunkRendererRegionMixin {
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullDirectionalBrightness(
            Direction direction,
            boolean shaded,
            CallbackInfoReturnable<Float> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedChunkBlock(
            BlockPos pos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        BlockState state = cir.getReturnValue();
        if (BlockIsolationController.shouldHideBlock(state, pos)) {
            cir.setReturnValue(Blocks.AIR.getDefaultState());
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedChunkFluid(
            BlockPos pos,
            CallbackInfoReturnable<FluidState> cir
    ) {
        FluidState fluidState = cir.getReturnValue();
        if (BlockIsolationController.shouldHideFluid(fluidState, pos)) {
            cir.setReturnValue(Fluids.EMPTY.getDefaultState());
        }
    }
}

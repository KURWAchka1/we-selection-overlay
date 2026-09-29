package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public abstract class ChunkRendererRegionMixin {
    @Inject(method = "getBlockState", at = @At("RETURN"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedChunkBlock(
            BlockPos pos,
            CallbackInfoReturnable<BlockState> cir
    ) {
        BlockState state = cir.getReturnValue();
        if (BlockIsolationController.shouldHideBlock(state, pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState", at = @At("RETURN"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedChunkFluid(
            BlockPos pos,
            CallbackInfoReturnable<FluidState> cir
    ) {
        FluidState fluidState = cir.getReturnValue();
        if (BlockIsolationController.shouldHideFluid(fluidState, pos)) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }
}

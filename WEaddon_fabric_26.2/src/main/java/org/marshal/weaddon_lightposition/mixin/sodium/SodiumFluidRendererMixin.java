package org.marshal.weaddon_lightposition.mixin.sodium;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.SodiumIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.fabric.render.FluidRendererImpl", remap = false)
public abstract class SodiumFluidRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void worldeditSelectionOverlay$hideIsolatedFluid(
            @Coerce Object levelSlice,
            BlockState blockState,
            FluidState fluidState,
            BlockPos pos,
            BlockPos origin,
            @Coerce Object collector,
            @Coerce Object buffers,
            CallbackInfo ci
    ) {
        SodiumIsolationRenderState.clear();
        if (BlockIsolationController.shouldHideFluid(blockState, fluidState, pos)) {
            ci.cancel();
            return;
        }
        SodiumIsolationRenderState.setFullbrightActive(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false, require = 0)
    private void worldeditSelectionOverlay$clearRenderState(
            @Coerce Object levelSlice,
            BlockState blockState,
            FluidState fluidState,
            BlockPos pos,
            BlockPos origin,
            @Coerce Object collector,
            @Coerce Object buffers,
            CallbackInfo ci
    ) {
        SodiumIsolationRenderState.clear();
    }
}

package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void worldeditSelectionOverlay$fullbrightIsolatedBlock(
            BlockAndLightGetter world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(BlockIsolationRenderState.FULL_BRIGHT_LIGHT);
            return;
        }
        if (world == null || pos == null) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(BlockIsolationRenderState.FULL_BRIGHT_LIGHT);
        }
    }

    @Inject(method = "getLightCoords(Lnet/minecraft/client/renderer/LevelRenderer$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void worldeditSelectionOverlay$fullbrightIsolatedBlockWithState(
            LevelRenderer.BrightnessGetter brightnessGetter,
            BlockAndLightGetter world,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(BlockIsolationRenderState.FULL_BRIGHT_LIGHT);
        }
    }
}

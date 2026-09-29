package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void worldeditSelectionOverlay$fullbrightIsolatedBlock(
            BlockRenderView world,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
            return;
        }
        if (world == null || pos == null) {
            return;
        }

        BlockState state = world.getBlockState(pos);
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    @Inject(method = "getLightmapCoordinates(Lnet/minecraft/client/render/WorldRenderer$BrightnessGetter;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I", at = @At("HEAD"), cancellable = true)
    private static void worldeditSelectionOverlay$fullbrightIsolatedBlockWithState(
            WorldRenderer.BrightnessGetter brightnessGetter,
            BlockRenderView world,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive() || BlockIsolationController.shouldForceFullbright(state, pos)) {
            cir.setReturnValue(LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }
}

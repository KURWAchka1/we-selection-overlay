package org.marshal.weaddon_lightposition.mixin;

import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererRenderStateMixin {
    @Inject(
            method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter world,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popFullbright();
    }
}

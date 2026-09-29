package org.marshal.weaddon_lightposition.mixin.sodium;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.SodiumIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void worldeditSelectionOverlay$hideIsolatedBlock(
            BlockStateModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            CallbackInfo ci
    ) {
        SodiumIsolationRenderState.clear();
        if (BlockIsolationController.shouldHideBlock(state, pos)) {
            ci.cancel();
            return;
        }
        SodiumIsolationRenderState.setFullbrightActive(BlockIsolationController.shouldForceFullbright(state, pos));
    }

    @Inject(method = "renderModel", at = @At("RETURN"), remap = false, require = 0)
    private void worldeditSelectionOverlay$clearRenderState(
            BlockStateModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            CallbackInfo ci
    ) {
        SodiumIsolationRenderState.clear();
    }
}

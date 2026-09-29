package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererMixin {
    @Inject(method = "shouldDrawFace", at = @At("HEAD"), cancellable = true)
    private static void worldeditSelectionOverlay$drawFaceAgainstHiddenNeighbor(
            BlockRenderView world,
            BlockState state,
            boolean cull,
            Direction direction,
            BlockPos neighborPos,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BlockIsolationController.shouldDrawFaceAgainstHiddenNeighbor(world, state, direction, neighborPos)) {
            cir.setReturnValue(true);
        }
    }
}

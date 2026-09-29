package org.marshal.weaddon_lightposition.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LiquidBlockRenderer.class)
public abstract class FluidRendererRenderStateMixin {
    @Inject(method = "tesselate", at = @At("HEAD"))
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockAndTintGetter world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(blockState, fluidState, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.fluidRenderAlpha(blockState, fluidState, pos));
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockAndTintGetter world,
            BlockPos pos,
            VertexConsumer vertexConsumer,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }
}

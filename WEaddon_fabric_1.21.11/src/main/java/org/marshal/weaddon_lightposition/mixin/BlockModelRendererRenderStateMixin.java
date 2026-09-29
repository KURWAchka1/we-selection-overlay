package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BlockModelRenderer.class)
public abstract class BlockModelRendererRenderStateMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "render(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(
            method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushSmoothFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "renderSmooth(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popSmoothFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(
            method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushFlatFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "renderFlat(Lnet/minecraft/world/BlockRenderView;Ljava/util/List;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popFlatFullbrightState(
            BlockRenderView world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            MatrixStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }
}

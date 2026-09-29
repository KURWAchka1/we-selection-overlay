package org.marshal.weaddon_lightposition.mixin;

import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererRenderStateMixin {
    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/util/function/Function;ZI)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void worldeditSelectionOverlay$renderSelectedIsolationWithoutAo(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            Function<ChunkSectionLayer, VertexConsumer> bufferLookup,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        if (parts.isEmpty() || !BlockIsolationController.shouldForceFullbright(state, pos)) {
            return;
        }

        BlockIsolationRenderState.pushFullbright(true);
        BlockIsolationRenderState.pushAlpha(1.0F);
        try {
            matrices.translate(state.getOffset(pos));
            ((ModelBlockRenderer) (Object) this).tesselateWithoutAO(
                    world,
                    parts,
                    state,
                    pos,
                    matrices,
                    bufferLookup,
                    cull,
                    overlay
            );
        } finally {
            BlockIsolationRenderState.popAlpha();
            BlockIsolationRenderState.popFullbright();
        }
        ci.cancel();
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(
            method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushSmoothFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popSmoothFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(
            method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$pushFlatFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.pushFullbright(BlockIsolationController.shouldForceFullbright(state, pos));
        BlockIsolationRenderState.pushAlpha(BlockIsolationController.blockRenderAlpha(state, pos));
    }

    @Inject(
            method = "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Ljava/util/List;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZI)V",
            at = @At("RETURN")
    )
    private void worldeditSelectionOverlay$popFlatFullbrightState(
            BlockAndTintGetter world,
            List<BlockModelPart> parts,
            BlockState state,
            BlockPos pos,
            PoseStack matrices,
            VertexConsumer vertexConsumer,
            boolean cull,
            int overlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.popAlpha();
        BlockIsolationRenderState.popFullbright();
    }

    @Inject(
            method = "putQuadData",
            at = @At("HEAD")
    )
    private void worldeditSelectionOverlay$forceFullbrightQuadData(
            BlockAndTintGetter world,
            BlockState state,
            BlockPos pos,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            BakedQuad quad,
            ModelBlockRenderer.CommonRenderStorage renderStorage,
            int packedOverlay,
            CallbackInfo ci
    ) {
        BlockIsolationRenderState.forceFullbright(renderStorage);
    }
}

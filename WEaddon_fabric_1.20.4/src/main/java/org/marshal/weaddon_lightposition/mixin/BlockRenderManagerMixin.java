package org.marshal.weaddon_lightposition.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockRenderManager.class)
abstract class BlockRenderManagerMixin {
    @WrapMethod(method = "renderBlock")
    private void weso$block(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
                            VertexConsumer vertices, boolean cull, Random random, Operation<Void> original) {
        if (BlockIsolationController.shouldHideBlock(state, pos)) return;
        boolean previous = BlockIsolationRenderState.enter(BlockIsolationController.shouldForceFullbright(state, pos));
        try { original.call(state, pos, world, matrices, vertices, cull, random); }
        finally { BlockIsolationRenderState.restore(previous); }
    }

    @WrapMethod(method = "renderFluid")
    private void weso$fluid(BlockPos pos, BlockRenderView world, VertexConsumer vertices, BlockState state,
                            FluidState fluid, Operation<Void> original) {
        if (BlockIsolationController.shouldHideFluid(state, fluid, pos)) return;
        boolean previous = BlockIsolationRenderState.enter(BlockIsolationController.shouldForceFullbright(state, fluid, pos));
        try { original.call(pos, world, vertices, state, fluid); }
        finally { BlockIsolationRenderState.restore(previous); }
    }
}

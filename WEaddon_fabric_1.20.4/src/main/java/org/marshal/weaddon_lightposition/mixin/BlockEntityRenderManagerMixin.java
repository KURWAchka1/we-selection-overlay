package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderManagerMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$hideIsolatedBlockEntity(
            BlockEntity blockEntity,
            float tickProgress,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            CallbackInfo ci
    ) {
        if (blockEntity != null && BlockIsolationController.shouldHideBlock(blockEntity.getCachedState(), blockEntity.getPos())) {
            ci.cancel();
        }
    }
}

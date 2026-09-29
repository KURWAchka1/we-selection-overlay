package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityRenderManagerMixin {
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void worldeditSelectionOverlay$hideIsolatedBlockEntity(
            E blockEntity,
            float tickProgress,
            ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
            CallbackInfoReturnable<S> cir
    ) {
        if (blockEntity != null && BlockIsolationController.shouldHideBlock(blockEntity.getCachedState(), blockEntity.getPos())) {
            cir.setReturnValue(null);
        }
    }
}

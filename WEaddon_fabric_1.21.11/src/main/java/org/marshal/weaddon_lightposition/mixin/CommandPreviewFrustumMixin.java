package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fabric 1.21.11 does not expose the active frustum in WorldRenderContext. */
@Mixin(WorldRenderer.class)
public abstract class CommandPreviewFrustumMixin {
    @Inject(method = "setupFrustum", at = @At("RETURN"))
    private void weso$capturePreviewFrustum(CallbackInfoReturnable<Frustum> callback) {
        CommandPreviewFrustum.capture(callback.getReturnValue());
    }
}

package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.LevelRenderer;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewFrustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge 1.21.11 does not expose the active frustum in its render-stage event. */
@Mixin(LevelRenderer.class)
public abstract class CommandPreviewFrustumMixin {
    @Inject(method = "prepareCullFrustum", at = @At("RETURN"))
    private void weso$capturePreviewFrustum(CallbackInfoReturnable<Frustum> callback) {
        CommandPreviewFrustum.capture(callback.getReturnValue());
    }
}

package org.marshal.weaddon_lightposition.mixin.sodium;

import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.marshal.weaddon_lightposition.client.SodiumIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl", remap = false)
public abstract class SodiumMutableQuadViewImplMixin {
    @ModifyVariable(method = "setLight", at = @At("HEAD"), argsOnly = true, ordinal = 1, remap = false, require = 0)
    private int worldeditSelectionOverlay$fullbrightIsolatedQuad(int light) {
        return SodiumIsolationRenderState.fullbrightActive()
                ? BlockIsolationRenderState.FULL_BRIGHT_LIGHT
                : light;
    }
}

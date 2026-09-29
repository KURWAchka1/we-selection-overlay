package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientWorldBrightnessMixin {
    @Inject(method = "getShade", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullDirectionalBrightness(
            Direction direction,
            boolean shaded,
            CallbackInfoReturnable<Float> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(1.0F);
        }
    }
}

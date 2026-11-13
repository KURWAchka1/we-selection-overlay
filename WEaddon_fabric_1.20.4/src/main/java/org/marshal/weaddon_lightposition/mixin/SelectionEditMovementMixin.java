package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SelectionEditMovementMixin extends Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void weso$freeze(boolean slowDown, float factor, CallbackInfo ci) {
        if (!SelectionEditController.instance().capturesCamera()) return;
        movementForward = movementSideways = 0;
        pressingForward = pressingBack = pressingLeft = pressingRight = jumping = sneaking = false;
        ci.cancel();
    }
}

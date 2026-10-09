package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SelectionEditMovementMixin extends Input {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void weso$freeze(CallbackInfo ci) {
        if (!SelectionEditController.instance().capturesCamera()) return;
        playerInput = PlayerInput.DEFAULT; movementVector = Vec2f.ZERO; ci.cancel();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void weso$editingModifier(CallbackInfo ci) {
        if (!SelectionEditController.instance().suppressSneak()) return;
        PlayerInput input = playerInput;
        playerInput = new PlayerInput(input.forward(), input.backward(), input.left(), input.right(), input.jump(), false, input.sprint());
    }
}

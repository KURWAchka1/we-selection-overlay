package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class SelectionEditMovementMixin extends ClientInput {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void weso$freeze(CallbackInfo ci) {
        if (!SelectionEditController.instance().dragging()) return;
        keyPresses = Input.EMPTY; moveVector = Vec2.ZERO; ci.cancel();
    }
}

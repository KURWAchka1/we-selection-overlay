package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.MouseInput;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class SelectionEditMouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void weso$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && SelectionEditController.instance().scroll(vertical)) ci.cancel();
    }
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void weso$button(long window, MouseInput event, int action, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle() && SelectionEditController.instance().button(event.button(), action)) ci.cancel();
    }
    @Inject(method = "onCursorPos", at = @At("RETURN"))
    private void weso$move(long window, double x, double y, CallbackInfo ci) {
        if (window == MinecraftClient.getInstance().getWindow().getHandle()) SelectionEditController.instance().move(x, y);
    }
}

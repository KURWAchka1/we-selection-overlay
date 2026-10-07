package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class SelectionEditMouseMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void weso$scroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && SelectionEditController.instance().scroll(vertical)) ci.cancel();
    }
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void weso$button(long window, MouseButtonInfo event, int action, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && SelectionEditController.instance().button(event.button(), action)) ci.cancel();
    }
    @Inject(method = "onMove", at = @At("RETURN"))
    private void weso$move(long window, double x, double y, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle()) SelectionEditController.instance().move(x, y);
    }
}

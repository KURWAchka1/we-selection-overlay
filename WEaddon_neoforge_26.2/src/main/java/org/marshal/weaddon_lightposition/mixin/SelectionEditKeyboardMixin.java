package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class SelectionEditKeyboardMixin {
    @Inject(method = "keyPress", at = @At("RETURN"))
    private void weso$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle()) SelectionEditController.instance().keyEvent();
    }
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void weso$shortcut(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().handle()) return;
        SelectionEditController editing = SelectionEditController.instance();
        if (editing.axisKey(action, event)) { ci.cancel(); return; }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS && editing.inputActive() && !editing.pending()) {
            editing.cancel(); ci.cancel();
        }
    }
}

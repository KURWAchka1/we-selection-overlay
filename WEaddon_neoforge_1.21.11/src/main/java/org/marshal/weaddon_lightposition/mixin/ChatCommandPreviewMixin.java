package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatCommandPreviewMixin {
    @Shadow protected EditBox input;
    @Inject(method = "init", at = @At("TAIL"))
    private void weso$initialDraft(CallbackInfo ci) { CommandPreviewController.instance().edited(input.getValue()); }
    @Inject(method = "onEdited", at = @At("TAIL"))
    private void weso$edited(String value, CallbackInfo ci) { CommandPreviewController.instance().edited(value); }
    @Inject(method = "removed", at = @At("TAIL"))
    private void weso$closed(CallbackInfo ci) { CommandPreviewController.instance().clearDraft(); }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void weso$toggle(KeyEvent key, CallbackInfoReturnable<Boolean> ci) {
        if (CommandPreviewController.instance().handleToggleKey(key)) ci.setReturnValue(true);
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void weso$statistics(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CommandPreviewController.instance().renderHud(graphics);
    }
}

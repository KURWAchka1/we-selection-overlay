package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.input.KeyInput;
import org.marshal.weaddon_lightposition.client.preview.CommandPreviewController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatCommandPreviewMixin {
    @Shadow protected TextFieldWidget chatField;
    @Inject(method = "init", at = @At("TAIL"))
    private void weso$initialDraft(CallbackInfo ci) { CommandPreviewController.instance().edited(chatField.getText()); }
    @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
    private void weso$edited(String value, CallbackInfo ci) { CommandPreviewController.instance().edited(value); }
    @Inject(method = "removed", at = @At("TAIL"))
    private void weso$closed(CallbackInfo ci) { CommandPreviewController.instance().clearDraft(); }
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void weso$toggle(KeyInput key, CallbackInfoReturnable<Boolean> ci) {
        if (CommandPreviewController.instance().handleToggleKey(key)) ci.setReturnValue(true);
    }
    @Inject(method = "render", at = @At("TAIL"))
    private void weso$statistics(DrawContext graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CommandPreviewController.instance().renderHud(graphics);
    }
}

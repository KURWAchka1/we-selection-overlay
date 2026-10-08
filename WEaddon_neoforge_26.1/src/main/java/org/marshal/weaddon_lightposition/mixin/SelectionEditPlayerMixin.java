package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class SelectionEditPlayerMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void weso$holdPosition(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (SelectionEditController.instance().cursorDrag()) ci.cancel();
    }
}

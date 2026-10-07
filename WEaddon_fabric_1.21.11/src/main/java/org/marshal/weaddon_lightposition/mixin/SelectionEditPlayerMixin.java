package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.marshal.weaddon_lightposition.client.selection.SelectionEditController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class SelectionEditPlayerMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void weso$holdPosition(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (SelectionEditController.instance().dragging()) ci.cancel();
    }
}

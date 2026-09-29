package org.marshal.weaddon_lightposition.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.marshal.weaddon_lightposition.client.BlockIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CardinalLighting.class)
public abstract class CardinalLightingMixin {
    @Inject(method = "byFace", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$fullDirectionalBrightness(
            Direction direction,
            CallbackInfoReturnable<Float> cir
    ) {
        if (BlockIsolationRenderState.fullbrightActive()) {
            cir.setReturnValue(1.0F);
        }
    }
}

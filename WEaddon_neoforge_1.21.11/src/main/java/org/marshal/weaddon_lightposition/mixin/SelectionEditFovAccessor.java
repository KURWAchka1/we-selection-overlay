package org.marshal.weaddon_lightposition.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface SelectionEditFovAccessor {
    @Invoker("getFov") float weso$editFov(Camera camera, float partialTick, boolean useSetting);
}

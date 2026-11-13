package org.marshal.weaddon_lightposition.mixin;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(GameRenderer.class)
public interface SelectionEditFovAccessor {
    @Invoker("getFov") double weso$editFov(Camera camera, float partialTick, boolean useSetting);
}

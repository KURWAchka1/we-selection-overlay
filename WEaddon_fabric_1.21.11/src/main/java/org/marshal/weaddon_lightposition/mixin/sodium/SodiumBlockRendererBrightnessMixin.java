package org.marshal.weaddon_lightposition.mixin.sodium;

import org.marshal.weaddon_lightposition.client.SodiumIsolationRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Arrays;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class SodiumBlockRendererBrightnessMixin {
    private static volatile Field worldeditSelectionOverlay$quadLightDataField;
    private static volatile Field worldeditSelectionOverlay$brightnessField;

    @Inject(
            method = "processQuad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
            ),
            remap = false,
            require = 0
    )
    private void worldeditSelectionOverlay$fullAmbientLight(CallbackInfo ci) {
        if (!SodiumIsolationRenderState.fullbrightActive()) {
            return;
        }

        Object quadLightData = worldeditSelectionOverlay$quadLightData(this);
        if (quadLightData == null) {
            return;
        }

        float[] brightness = worldeditSelectionOverlay$brightness(quadLightData);
        if (brightness != null) {
            Arrays.fill(brightness, 1.0F);
        }
    }

    private static Object worldeditSelectionOverlay$quadLightData(Object owner) {
        try {
            Field field = worldeditSelectionOverlay$quadLightDataField;
            if (field == null) {
                field = worldeditSelectionOverlay$findField(owner.getClass(), "quadLightData");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                worldeditSelectionOverlay$quadLightDataField = field;
            }
            return field.get(owner);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static float[] worldeditSelectionOverlay$brightness(Object quadLightData) {
        try {
            Field field = worldeditSelectionOverlay$brightnessField;
            if (field == null) {
                field = worldeditSelectionOverlay$findField(quadLightData.getClass(), "br");
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                worldeditSelectionOverlay$brightnessField = field;
            }
            Object value = field.get(quadLightData);
            return value instanceof float[] brightness ? brightness : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Field worldeditSelectionOverlay$findField(Class<?> startClass, String fieldName) {
        Class<?> current = startClass;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}

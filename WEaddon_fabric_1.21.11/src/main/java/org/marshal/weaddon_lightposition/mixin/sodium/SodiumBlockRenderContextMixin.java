package org.marshal.weaddon_lightposition.mixin.sodium;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext", remap = false)
public abstract class SodiumBlockRenderContextMixin {
    @Unique
    private static volatile Field worldeditSelectionOverlay$levelField;
    @Unique
    private static volatile Field worldeditSelectionOverlay$stateField;
    @Unique
    private static volatile Field worldeditSelectionOverlay$posField;

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void worldeditSelectionOverlay$drawFaceAgainstHiddenNeighbor(
            Direction direction,
            CallbackInfoReturnable<Boolean> cir
    ) {
        BlockRenderView level = worldeditSelectionOverlay$fieldValue("level", worldeditSelectionOverlay$levelField, BlockRenderView.class);
        BlockState state = worldeditSelectionOverlay$fieldValue("state", worldeditSelectionOverlay$stateField, BlockState.class);
        BlockPos pos = worldeditSelectionOverlay$fieldValue("pos", worldeditSelectionOverlay$posField, BlockPos.class);
        if (level == null || state == null || pos == null || direction == null) {
            return;
        }

        BlockPos neighborPos = pos.offset(direction);
        if (BlockIsolationController.shouldDrawFaceAgainstHiddenNeighbor(level, state, direction, neighborPos)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private <T> T worldeditSelectionOverlay$fieldValue(String fieldName, Field cachedField, Class<T> type) {
        Field field = cachedField;
        if (field == null) {
            field = worldeditSelectionOverlay$findField(getClass(), fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            if ("level".equals(fieldName)) {
                worldeditSelectionOverlay$levelField = field;
            } else if ("state".equals(fieldName)) {
                worldeditSelectionOverlay$stateField = field;
            } else if ("pos".equals(fieldName)) {
                worldeditSelectionOverlay$posField = field;
            }
        }

        try {
            Object value = field.get(this);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Unique
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

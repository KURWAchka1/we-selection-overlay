package org.marshal.weaddon_lightposition.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class BlockModelRendererQuadLightMixin {
    @Shadow @Final private QuadInstance quadInstance;

    @Inject(method = "putQuadWithTint", at = @At("HEAD"))
    private void worldeditSelectionOverlay$lightSelectedQuad(
            BlockQuadOutput output, float x, float y, float z,
            BlockAndTintGetter level, BlockState state, BlockPos pos,
            BakedQuad quad, CallbackInfo ci
    ) {
        if (BlockIsolationController.shouldForceFullbright(state, pos)) {
            // Remove baked lighting before the renderer applies the block's actual tint.
            quadInstance.setColor(-1);
            quadInstance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
        }
    }
}

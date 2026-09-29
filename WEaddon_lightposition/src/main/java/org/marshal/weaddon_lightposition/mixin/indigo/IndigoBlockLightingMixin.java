package org.marshal.weaddon_lightposition.mixin.indigo;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.state.BlockState;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public abstract class IndigoBlockLightingMixin {
    @Shadow private BlockPos pos;
    @Shadow private BlockState blockState;

    @Inject(method = "shadeQuad", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$lightSelectedQuad(
            @Coerce MutableQuadView quad, boolean ao, boolean emissive, boolean vanillaShade, CallbackInfo ci
    ) {
        if (BlockIsolationController.shouldForceFullbright(blockState, pos)) {
            // Keep model colors and tint; skip only AO, directional shade and world lighting.
            quad.lightmap(LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT,
                    LightCoordsUtil.FULL_BRIGHT, LightCoordsUtil.FULL_BRIGHT);
            ci.cancel();
        }
    }
}

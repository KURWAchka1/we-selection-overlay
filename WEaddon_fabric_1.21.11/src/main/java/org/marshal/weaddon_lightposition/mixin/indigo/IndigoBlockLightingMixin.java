package org.marshal.weaddon_lightposition.mixin.indigo;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.BlockPos;
import org.marshal.weaddon_lightposition.client.BlockIsolationController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext", remap = false)
public abstract class IndigoBlockLightingMixin {
    @Unique private boolean worldeditSelectionOverlay$fullbright;

    @Inject(method = "prepare", at = @At("HEAD"))
    private void worldeditSelectionOverlay$prepare(BlockPos pos, BlockState state, CallbackInfo ci) {
        worldeditSelectionOverlay$fullbright = BlockIsolationController.shouldForceFullbright(state, pos);
    }

    @Inject(method = "shadeQuad", at = @At("HEAD"), cancellable = true)
    private void worldeditSelectionOverlay$lightSelectedQuad(
            @Coerce MutableQuadView quad, boolean ao, boolean emissive, boolean vanillaShade, CallbackInfo ci
    ) {
        if (worldeditSelectionOverlay$fullbright) {
            // Tint is already applied here. Preserve it while bypassing baked shading.
            quad.lightmap(LightmapTextureManager.MAX_LIGHT_COORDINATE, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            ci.cancel();
        }
    }
}

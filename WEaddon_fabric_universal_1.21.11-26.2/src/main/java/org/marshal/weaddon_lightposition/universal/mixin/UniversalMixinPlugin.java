package org.marshal.weaddon_lightposition.universal.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.marshal.weaddon_lightposition.universal.RuntimeTarget;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class UniversalMixinPlugin implements IMixinConfigPlugin {
    private static final List<String> MC_1_21_11 = List.of(
            "ChatCommandPreviewMixin",
            "CommandPreviewFrustumMixin",
            "BlockEntityRenderManagerMixin",
            "BlockModelRendererBrightnessCacheMixin",
            "BlockModelRendererMixin",
            "BlockModelRendererRenderStateMixin",
            "BlockRenderManagerMixin",
            "ChunkRendererRegionMixin",
            "ClientWorldBrightnessMixin",
            "FluidRendererRenderStateMixin",
            "WorldRendererMixin"
    );
    private static final List<String> MC_26_1 = List.of(
            "ChatCommandPreviewMixin",
            "BlockEntityRenderManagerMixin",
            "BlockModelRendererBrightnessCacheMixin",
            "BlockModelRendererMixin",
            "BlockModelRendererRenderStateMixin",
            "BlockModelRendererQuadLightMixin",
            "CardinalLightingMixin",
            "ChunkRendererRegionMixin",
            "FluidRendererRenderStateMixin",
            "WorldRendererMixin"
    );
    private static final List<String> MC_26_2 = List.of(
            "ChatCommandPreviewMixin",
            "BlockEntityRenderManagerMixin",
            "BlockModelRendererBrightnessCacheMixin",
            "BlockModelRendererMixin",
            "BlockModelRendererRenderStateMixin",
            "BlockModelRendererQuadLightMixin",
            "CardinalLightingMixin",
            "ChunkRendererRegionMixin",
            "FluidRendererRenderStateMixin",
            "VertexConsumerFullbrightMixin",
            "WorldRendererMixin"
    );
    private static final List<String> SODIUM = List.of(
            "sodium.SodiumBlockRendererBrightnessMixin",
            "sodium.SodiumBlockRenderContextMixin",
            "sodium.SodiumBlockRendererMixin",
            "sodium.SodiumFluidRendererMixin",
            "sodium.SodiumMutableQuadViewImplMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        RuntimeTarget target = RuntimeTarget.current();
        List<String> names = switch (target) {
            case MC_1_21_11 -> MC_1_21_11;
            case MC_26_1 -> MC_26_1;
            case MC_26_2 -> MC_26_2;
        };

        List<String> mixins = new ArrayList<>(names.size() + SODIUM.size());
        for (String name : names) {
            mixins.add(target.id() + "." + name);
        }
        if (FabricLoader.getInstance().isModLoaded("fabric-renderer-indigo")) {
            mixins.add(target.id() + ".indigo.IndigoBlockLightingMixin");
        }
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            for (String name : SODIUM) {
                mixins.add(target.id() + "." + name);
            }
        }
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

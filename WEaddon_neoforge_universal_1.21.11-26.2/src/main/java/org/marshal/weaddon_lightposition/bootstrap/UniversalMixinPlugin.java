package org.marshal.weaddon_lightposition.bootstrap;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class UniversalMixinPlugin implements IMixinConfigPlugin {
    private static final List<String> MC_1_21_11_MIXINS = List.of(
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

    private static final List<String> MC_26_MIXINS = List.of(
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

    private static final List<String> MC_26_SODIUM_MIXINS = List.of(
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
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return List.of();
        }

        RuntimeSelector.Target target = RuntimeSelector.current();
        List<String> selected = new ArrayList<>();
        List<String> baseMixins = target == RuntimeSelector.Target.MC_1_21_11
                ? MC_1_21_11_MIXINS
                : MC_26_MIXINS;
        for (String mixin : baseMixins) {
            selected.add(target.mixinClass(mixin));
        }
        if (target != RuntimeSelector.Target.MC_1_21_11
                && FMLLoader.getCurrent().getLoadingModList().getModFileById("sodium") != null) {
            for (String mixin : MC_26_SODIUM_MIXINS) {
                selected.add(target.mixinClass(mixin));
            }
        }
        return selected;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

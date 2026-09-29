package org.marshal.weaddon_lightposition.bootstrap;

import net.neoforged.fml.loading.FMLLoader;

final class RuntimeSelector {
    private RuntimeSelector() {
    }

    static Target current() {
        String minecraftVersion = FMLLoader.getCurrent().getVersionInfo().mcVersion();
        if (minecraftVersion.equals("1.21.11")) {
            return Target.MC_1_21_11;
        }
        if (minecraftVersion.equals("26.1") || minecraftVersion.startsWith("26.1.")) {
            return Target.MC_26_1;
        }
        if (minecraftVersion.equals("26.2") || minecraftVersion.startsWith("26.2.")) {
            return Target.MC_26_2;
        }
        throw new IllegalStateException("Unsupported Minecraft version: " + minecraftVersion);
    }

    enum Target {
        MC_1_21_11("org.marshal.weaddon_lightposition.runtime.mc1_21_11", "mc1_21_11"),
        MC_26_1("org.marshal.weaddon_lightposition.runtime.mc26_1", "mc26_1"),
        MC_26_2("org.marshal.weaddon_lightposition.runtime.mc26_2", "mc26_2");

        private final String runtimePackage;
        private final String mixinPackage;

        Target(String runtimePackage, String mixinPackage) {
            this.runtimePackage = runtimePackage;
            this.mixinPackage = mixinPackage;
        }

        String runtimePackage() {
            return runtimePackage;
        }

        String mixinClass(String simpleName) {
            return mixinPackage + "." + simpleName;
        }
    }
}

package org.marshal.weaddon_lightposition.universal;

import net.fabricmc.loader.api.FabricLoader;

public enum RuntimeTarget {
    MC_1_21_11("mc1_21_11"),
    MC_26_1("mc26_1"),
    MC_26_2("mc26_2");

    private final String id;

    RuntimeTarget(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String applicationPackage() {
        return "org.marshal.weaddon_lightposition.runtime." + id;
    }

    public static RuntimeTarget current() {
        String version = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .orElseThrow(() -> new IllegalStateException("Minecraft mod metadata is unavailable"))
                .getMetadata()
                .getVersion()
                .getFriendlyString();

        return forMinecraft(version);
    }

    public static RuntimeTarget forMinecraft(String version) {
        if (version.equals("1.21.11")) {
            return MC_1_21_11;
        }
        if (version.equals("26.1") || version.startsWith("26.1.")) {
            return MC_26_1;
        }
        if (version.equals("26.2") || version.startsWith("26.2.")) {
            return MC_26_2;
        }
        throw new IllegalStateException("Unsupported Minecraft version: " + version);
    }
}

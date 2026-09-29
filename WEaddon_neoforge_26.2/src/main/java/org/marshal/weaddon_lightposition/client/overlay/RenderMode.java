package org.marshal.weaddon_lightposition.client.overlay;

public enum RenderMode {
    BLOCKY,
    SMOOTH;

    public RenderMode next() {
        return this == BLOCKY ? SMOOTH : BLOCKY;
    }

    public static RenderMode fromConfigValue(String value, RenderMode fallback) {
        if (value == null) {
            return fallback;
        }

        for (RenderMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return fallback;
    }

    public String translationSuffix() {
        return name().toLowerCase();
    }
}

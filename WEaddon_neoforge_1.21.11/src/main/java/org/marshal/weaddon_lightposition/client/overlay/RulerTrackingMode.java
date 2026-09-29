package org.marshal.weaddon_lightposition.client.overlay;

public enum RulerTrackingMode {
    FOLLOW_CAMERA,
    FIXED_SIDE;

    public RulerTrackingMode next() {
        return this == FOLLOW_CAMERA ? FIXED_SIDE : FOLLOW_CAMERA;
    }

    public static RulerTrackingMode fromConfigValue(String value, RulerTrackingMode fallback) {
        if (value == null) {
            return fallback;
        }

        for (RulerTrackingMode mode : values()) {
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

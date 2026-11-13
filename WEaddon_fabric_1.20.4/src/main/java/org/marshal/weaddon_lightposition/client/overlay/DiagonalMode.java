package org.marshal.weaddon_lightposition.client.overlay;

public enum DiagonalMode {
    NONE,
    ONE,
    TWO;

    public DiagonalMode next() {
        int nextOrdinal = ordinal() + 1;
        if (nextOrdinal >= values().length) {
            nextOrdinal = 0;
        }
        return values()[nextOrdinal];
    }

    public static DiagonalMode fromConfigValue(String value, DiagonalMode fallback) {
        if (value == null) {
            return fallback;
        }
        for (DiagonalMode mode : values()) {
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

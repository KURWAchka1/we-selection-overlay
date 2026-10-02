package org.marshal.weaddon_lightposition.preview;

import java.util.Properties;

public record PreviewOptions(boolean enabled, float opacity, boolean statistics) {
    public static PreviewOptions defaults() {
        return new PreviewOptions(true, 0.65f, true);
    }

    public static PreviewOptions load(Properties values) {
        PreviewOptions d = defaults();
        return new PreviewOptions(Boolean.parseBoolean(values.getProperty("enabled", "true")),
                decimal(values, "opacity", d.opacity, 0.15f, 0.9f),
                Boolean.parseBoolean(values.getProperty("statistics", "true")));
    }

    public PreviewOptions withEnabled(boolean value) {
        return new PreviewOptions(value, opacity, statistics);
    }

    public Properties properties() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("opacity", Float.toString(opacity));
        p.setProperty("statistics", Boolean.toString(statistics));
        return p;
    }

    private static float decimal(Properties p, String key, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(p.getProperty(key, ""));
            return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }
}

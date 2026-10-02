package org.marshal.weaddon_lightposition.preview;

import java.util.Properties;

public record PreviewOptions(boolean enabled, float opacity, int maxCells, int maxRenderBlocks,
                             int renderDistance, boolean statistics) {
    public static PreviewOptions defaults() {
        return new PreviewOptions(true, 0.65f, 65_536, 2_048, 96, true);
    }

    public static PreviewOptions load(Properties values) {
        PreviewOptions d = defaults();
        return new PreviewOptions(Boolean.parseBoolean(values.getProperty("enabled", "true")),
                decimal(values, "opacity", d.opacity, 0.15f, 0.9f),
                integer(values, "maxCells", d.maxCells, 256, 262_144),
                integer(values, "maxRenderBlocks", d.maxRenderBlocks, 64, 8_192),
                integer(values, "renderDistance", d.renderDistance, 16, 192),
                Boolean.parseBoolean(values.getProperty("statistics", "true")));
    }

    public PreviewOptions withEnabled(boolean value) {
        return new PreviewOptions(value, opacity, maxCells, maxRenderBlocks, renderDistance, statistics);
    }

    public Properties properties() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("opacity", Float.toString(opacity));
        p.setProperty("maxCells", Integer.toString(maxCells));
        p.setProperty("maxRenderBlocks", Integer.toString(maxRenderBlocks));
        p.setProperty("renderDistance", Integer.toString(renderDistance));
        p.setProperty("statistics", Boolean.toString(statistics));
        return p;
    }

    private static int integer(Properties p, String key, int fallback, int min, int max) {
        try { return Math.clamp(Integer.parseInt(p.getProperty(key, "")), min, max); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float decimal(Properties p, String key, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(p.getProperty(key, ""));
            return Float.isFinite(value) ? Math.clamp(value, min, max) : fallback;
        } catch (NumberFormatException ignored) { return fallback; }
    }
}

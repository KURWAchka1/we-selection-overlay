package org.marshal.weaddon_lightposition.client.overlay;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

public final class OverlayColorProfile {
    private final EnumMap<OverlayColorSlot, Integer> colors = new EnumMap<>(OverlayColorSlot.class);
    private final EnumMap<OverlayColorSlot, Float> lineWidthScales = new EnumMap<>(OverlayColorSlot.class);

    public OverlayColorProfile() {
        resetAll();
    }

    public static OverlayColorProfile defaults() {
        return new OverlayColorProfile();
    }

    public int color(OverlayColorSlot slot) {
        if (slot == null) {
            return 0xFFFFFFFF;
        }
        return colors.getOrDefault(slot, slot.defaultColor());
    }

    public float lineWidthScale(OverlayColorSlot slot) {
        if (slot == null) {
            return 1.0f;
        }
        return lineWidthScales.getOrDefault(slot, 1.0f);
    }

    public boolean setColor(OverlayColorSlot slot, int color) {
        if (slot == null) {
            return false;
        }
        int normalized = color;
        if (color(slot) == normalized) {
            return false;
        }
        colors.put(slot, normalized);
        return true;
    }

    public boolean reset(OverlayColorSlot slot) {
        return setColor(slot, slot.defaultColor());
    }

    public boolean resetColors() {
        boolean changed = false;
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            changed |= reset(slot);
        }
        return changed;
    }

    public boolean setLineWidthScale(OverlayColorSlot slot, float scale) {
        if (slot == null) {
            return false;
        }
        float normalized = clampLineWidthScale(scale);
        if (Math.abs(lineWidthScale(slot) - normalized) < 0.001f) {
            return false;
        }
        lineWidthScales.put(slot, normalized);
        return true;
    }

    public boolean resetLineWidthScale(OverlayColorSlot slot) {
        return setLineWidthScale(slot, 1.0f);
    }

    public boolean resetLineWidthScales() {
        boolean changed = false;
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            changed |= resetLineWidthScale(slot);
        }
        return changed;
    }

    public boolean setLineWidthScales(float scale) {
        boolean changed = false;
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            if (slot.supportsLineWidth()) {
                changed |= setLineWidthScale(slot, scale);
            }
        }
        return changed;
    }

    public boolean allLineWidthScalesDefault() {
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            if (Math.abs(lineWidthScale(slot) - 1.0f) >= 0.001f) {
                return false;
            }
        }
        return true;
    }

    public boolean allLineWidthScalesEqual() {
        Float first = null;
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            if (!slot.supportsLineWidth()) {
                continue;
            }
            float value = lineWidthScale(slot);
            if (first == null) {
                first = value;
            } else if (Math.abs(first - value) >= 0.001f) {
                return false;
            }
        }
        return true;
    }

    public float commonLineWidthScale() {
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            if (slot.supportsLineWidth()) {
                return lineWidthScale(slot);
            }
        }
        return 1.0f;
    }

    public void resetAll() {
        colors.clear();
        lineWidthScales.clear();
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            colors.put(slot, slot.defaultColor());
            lineWidthScales.put(slot, 1.0f);
        }
    }

    public void copyFrom(OverlayColorProfile other) {
        resetAll();
        if (other == null) {
            return;
        }
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            colors.put(slot, other.color(slot));
            lineWidthScales.put(slot, other.lineWidthScale(slot));
        }
    }

    public boolean applyConfigValue(String key, String rawValue) {
        OverlayColorSlot slot = OverlayColorSlot.fromConfigKey(key);
        Integer color = parseColor(rawValue);
        if (slot == null || color == null) {
            return false;
        }
        return setColor(slot, color);
    }

    public boolean applyLineWidthConfigValue(String key, String rawValue) {
        OverlayColorSlot slot = OverlayColorSlot.fromConfigKey(key);
        if (slot == null) {
            return false;
        }
        try {
            return setLineWidthScale(slot, Float.parseFloat(rawValue.trim()));
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<String> toConfigLines(String prefix) {
        ArrayList<String> lines = new ArrayList<>();
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            lines.add(prefix + slot.configKey() + "=" + formatColor(color(slot)));
        }
        return lines;
    }

    public List<String> toLineWidthConfigLines(String prefix) {
        ArrayList<String> lines = new ArrayList<>();
        for (OverlayColorSlot slot : OverlayColorSlot.values()) {
            lines.add(prefix + slot.configKey() + "=" + lineWidthScale(slot));
        }
        return lines;
    }

    public static String formatColor(int argb) {
        return String.format("#%08X", argb);
    }

    public static Integer parseColor(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6 && value.length() != 8) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value, 16);
            if (value.length() == 6) {
                parsed |= 0xFF000000L;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static float clampLineWidthScale(float scale) {
        return Math.max(0.5f, Math.min(2.0f, scale));
    }
}

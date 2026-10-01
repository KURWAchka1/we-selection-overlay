package org.marshal.weaddon_lightposition.client.screen;

import java.util.Locale;

public enum UiStyle {
    GLASS(0xCC202326, 0xB83A3E41, 0x30FFFFFF, 0xFFA1DBBD, 0xFFF3F4F2, 0xFFB9BFBE),
    STUDIO(0xF51D2022, 0xFF34383B, 0x24FFFFFF, 0xFFE6C485, 0xFFF3F4F2, 0xFFB9BFBE),
    LEGACY(0xD0121721, 0xFF283343, 0xFF3A465A, 0xFF8AB4FF, 0xFFEAF0FA, 0xFFB4C0D3);

    public final int surface;
    public final int raised;
    public final int border;
    public final int accent;
    public final int text;
    public final int secondaryText;
    private static UiStyle current = GLASS;
    private static boolean reducedMotion;
    private static boolean blur;
    private static float frameAlpha = 1.0f;

    UiStyle(int surface, int raised, int border, int accent, int text, int secondaryText) {
        this.surface = surface;
        this.raised = raised;
        this.border = border;
        this.accent = accent;
        this.text = text;
        this.secondaryText = secondaryText;
    }

    public static UiStyle current() { return current; }
    public static void setCurrent(UiStyle style) { current = style == null ? GLASS : style; }
    public static boolean reducedMotion() { return reducedMotion; }
    public static void setReducedMotion(boolean value) { reducedMotion = value; }
    public static boolean blur() { return blur && current == GLASS; }
    public static void setBlur(boolean value) { blur = value; }
    public static void setFrameAlpha(float value) { frameAlpha = Math.max(0.0f, Math.min(1.0f, value)); }
    public static int color(int argb) { return (argb & 0x00FFFFFF) | (Math.round((argb >>> 24) * frameAlpha) << 24); }

    public static UiStyle fromConfigValue(String value) {
        if (value != null) {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { }
        }
        return GLASS;
    }

    public UiStyle next() { return values()[(ordinal() + 1) % values().length]; }
}

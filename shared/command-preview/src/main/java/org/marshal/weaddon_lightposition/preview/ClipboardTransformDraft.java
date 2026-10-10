package org.marshal.weaddon_lightposition.preview;

import java.util.Locale;

/** Pure chat routing: loading this class must not require WorldEdit. */
public record ClipboardTransformDraft(Kind kind, double y, double x, double z, String direction, String reason) {
    public enum Kind { NONE, ROTATE, FLIP }
    private static final ClipboardTransformDraft NONE = new ClipboardTransformDraft(Kind.NONE, 0, 0, 0, "", "");

    public static ClipboardTransformDraft none() { return NONE; }
    public boolean recognized() { return kind != Kind.NONE; }
    public boolean valid() { return recognized() && reason.isEmpty(); }
    public boolean approximate() {
        return kind == Kind.ROTATE && (Math.abs(y % 90) > 0.001
                || Math.abs(x % 90) > 0.001 || Math.abs(z % 90) > 0.001);
    }

    public static ClipboardTransformDraft parse(String input) {
        if (input == null || input.isBlank()) return NONE;
        String[] words = input.strip().split("\\s+");
        Kind kind = switch (words[0].toLowerCase(Locale.ROOT)) {
            case "//rotate" -> Kind.ROTATE;
            case "//flip", "//mirror" -> Kind.FLIP;
            default -> Kind.NONE;
        };
        if (kind == Kind.NONE) return NONE;
        if (kind == Kind.FLIP) return words.length <= 2
                ? new ClipboardTransformDraft(kind, 0, 0, 0, words.length == 1 ? "me" : words[1].toLowerCase(Locale.ROOT), "")
                : invalid(kind, "clipboard_syntax");
        if (words.length == 1) return invalid(kind, "clipboard_angle");
        if (words.length > 4) return invalid(kind, "clipboard_syntax");
        try {
            double y = Double.parseDouble(words[1]);
            double x = words.length > 2 ? Double.parseDouble(words[2]) : 0;
            double z = words.length > 3 ? Double.parseDouble(words[3]) : 0;
            if (!Double.isFinite(y) || !Double.isFinite(x) || !Double.isFinite(z)) return invalid(kind, "clipboard_angle");
            return new ClipboardTransformDraft(kind, y == 0 ? 0 : y, x == 0 ? 0 : x, z == 0 ? 0 : z, "", "");
        } catch (NumberFormatException ignored) { return invalid(kind, "clipboard_angle"); }
    }

    private static ClipboardTransformDraft invalid(Kind kind, String reason) {
        return new ClipboardTransformDraft(kind, 0, 0, 0, "", reason);
    }
}

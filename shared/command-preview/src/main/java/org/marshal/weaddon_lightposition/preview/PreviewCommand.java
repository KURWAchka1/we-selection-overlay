package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import java.util.List;
import java.util.Locale;

public record PreviewCommand(Kind kind, String mask, String pattern, double thickness, boolean shell) {
    public enum Kind { SET, REPLACE, WALLS, FACES, OVERLAY, FLORA, CENTER, NATURALIZE, HOLLOW, LINE, CURVE }

    public PreviewCommand(Kind kind, String mask, String pattern) { this(kind, mask, pattern, 0, false); }

    public static boolean recognizes(String draft) {
        return PreviewDraft.recognizes(draft);
    }

    public static PreviewCommand parse(String draft) {
        if (draft.length() > 512 || !balancedQuotes(draft)) throw new IllegalArgumentException("incomplete");
        List<String> args = CommandArgParser.forArgString(draft.strip()).parseArgs()
                .map(Substring::getSubstring).toList();
        if (args.isEmpty()) throw new IllegalArgumentException("incomplete");
        Kind kind = switch (args.getFirst().toLowerCase(Locale.ROOT)) {
            case "//set" -> Kind.SET;
            case "//replace", "//re", "//rep" -> Kind.REPLACE;
            case "//walls" -> Kind.WALLS;
            case "//faces", "//outline" -> Kind.FACES;
            case "//overlay" -> Kind.OVERLAY;
            case "//flora" -> Kind.FLORA;
            case "//center", "//middle" -> Kind.CENTER;
            case "//naturalize" -> Kind.NATURALIZE;
            case "//hollow" -> Kind.HOLLOW;
            case "//line" -> Kind.LINE;
            case "//curve" -> Kind.CURVE;
            default -> throw new IllegalArgumentException("unsupported_command");
        };
        if (kind == Kind.NATURALIZE) {
            if (args.size() != 1) throw new IllegalArgumentException("incomplete");
            return new PreviewCommand(kind, "", "");
        }
        if (kind == Kind.HOLLOW) {
            if (args.size() > 3) throw new IllegalArgumentException("incomplete");
            double thickness = args.size() > 1 ? nonNegative(args.get(1)) : 0;
            if (thickness != Math.rint(thickness) || thickness > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid");
            return new PreviewCommand(kind, "", args.size() > 2 ? args.get(2) : "air", thickness, true);
        }
        if (kind == Kind.LINE || kind == Kind.CURVE) {
            boolean shell = args.contains("-h");
            List<String> positional = args.stream().skip(1).filter(a -> !a.equals("-h")).toList();
            if (positional.size() < 1 || positional.size() > 2) throw new IllegalArgumentException("incomplete");
            return new PreviewCommand(kind, "", positional.getFirst(), positional.size() == 2 ? nonNegative(positional.get(1)) : 0, shell);
        }
        if (kind == Kind.FLORA) {
            if (args.size() > 2) throw new IllegalArgumentException("incomplete");
            String density = args.size() == 1 ? "5" : args.get(1);
            try {
                double value = Double.parseDouble(density);
                if (!Double.isFinite(value) || value < 0 || value > 100) throw new IllegalArgumentException("invalid");
            } catch (NumberFormatException ex) { throw new IllegalArgumentException("incomplete"); }
            return new PreviewCommand(kind, "", density);
        }
        if (args.size() == 2) return new PreviewCommand(kind, kind == Kind.REPLACE ? "#existing" : "", args.get(1));
        if (kind == Kind.REPLACE && args.size() == 3) return new PreviewCommand(kind, args.get(1), args.get(2));
        throw new IllegalArgumentException("incomplete");
    }

    private static double nonNegative(String input) {
        try {
            double value = Double.parseDouble(input);
            if (!Double.isFinite(value) || value < 0 || value > Integer.MAX_VALUE / 4.0) throw new IllegalArgumentException("invalid");
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("incomplete"); }
    }

    private static boolean balancedQuotes(String text) {
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"' || c == '\'') {
                if (quote == c) quote = 0;
                else if (quote == 0) quote = c;
            }
        }
        return quote == 0;
    }
}

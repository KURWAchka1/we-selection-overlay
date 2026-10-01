package org.marshal.weaddon_lightposition.preview;

import com.sk89q.worldedit.internal.command.CommandArgParser;
import com.sk89q.worldedit.internal.util.Substring;
import java.util.List;
import java.util.Locale;

public record PreviewCommand(Kind kind, String mask, String pattern) {
    public enum Kind { SET, REPLACE, WALLS, FACES, OVERLAY }

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
            case "//replace" -> Kind.REPLACE;
            case "//walls" -> Kind.WALLS;
            case "//faces", "//outline" -> Kind.FACES;
            case "//overlay" -> Kind.OVERLAY;
            default -> throw new IllegalArgumentException("unsupported_command");
        };
        if (args.size() == 2) return new PreviewCommand(kind, kind == Kind.REPLACE ? "#existing" : "", args.get(1));
        if (kind == Kind.REPLACE && args.size() == 3) return new PreviewCommand(kind, args.get(1), args.get(2));
        throw new IllegalArgumentException("incomplete");
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

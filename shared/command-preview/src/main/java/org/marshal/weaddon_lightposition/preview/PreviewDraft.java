package org.marshal.weaddon_lightposition.preview;

import java.util.Locale;
import java.util.Set;

/** Chat routing must remain available when WorldEdit is not installed. */
public final class PreviewDraft {
    private static final Set<String> COMMANDS = Set.of("//set", "//replace", "//re", "//rep", "//walls", "//faces", "//outline", "//overlay", "//flora",
            "//center", "//middle", "//naturalize", "//hollow", "//line", "//curve");
    private PreviewDraft() { }
    public static boolean recognizes(String input) {
        String text = input.stripLeading();
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        return COMMANDS.contains(text.substring(0, end).toLowerCase(Locale.ROOT));
    }
}

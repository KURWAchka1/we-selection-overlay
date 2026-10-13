package org.marshal.weaddon_lightposition.bridge.paper;

/** Explicit development tuples; a version range is not compatibility evidence. */
final class PaperHostCompatibility {
    private PaperHostCompatibility() { }

    static boolean supports(String minecraft, String pluginName, String worldedit, boolean enabled) {
        if (!enabled || !"WorldEdit".equals(pluginName) || worldedit == null || minecraft == null) return false;
        String expected = switch (minecraft) {
            case "1.21.11" -> "7.4.2";
            case "26.1.2" -> "7.4.3";
            case "26.2" -> "7.4.5";
            default -> null;
        };
        return expected != null && (worldedit.equals(expected) || worldedit.startsWith(expected + "+"));
    }
}

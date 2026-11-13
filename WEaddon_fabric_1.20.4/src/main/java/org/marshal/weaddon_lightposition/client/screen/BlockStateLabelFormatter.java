package org.marshal.weaddon_lightposition.client.screen;

import net.minecraft.client.resource.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Map.entry;

public final class BlockStateLabelFormatter {
    private static final int MAX_LINE_LEN = 52;
    private static final Map<String, String> PROPERTY_FALLBACKS = Map.ofEntries(
            entry("age", "Age"),
            entry("attached", "Attached"),
            entry("attachment", "Attachment"),
            entry("axis", "Axis"),
            entry("bites", "Bites"),
            entry("bloom", "Bloom"),
            entry("bottom", "Bottom"),
            entry("candles", "Candles"),
            entry("conditional", "Conditional"),
            entry("delay", "Delay"),
            entry("distance", "Distance"),
            entry("disarmed", "Disarmed"),
            entry("down", "Down"),
            entry("east", "East"),
            entry("enabled", "Enabled"),
            entry("extended", "Extended"),
            entry("eye", "Eye"),
            entry("face", "Mount"),
            entry("facing", "Facing"),
            entry("half", "Half"),
            entry("hanging", "Hanging"),
            entry("has_book", "Book"),
            entry("has_record", "Record"),
            entry("has_bottle_0", "Bottle 1"),
            entry("has_bottle_1", "Bottle 2"),
            entry("has_bottle_2", "Bottle 3"),
            entry("hatch", "Hatch"),
            entry("hinge", "Hinge"),
            entry("honey_level", "Honey"),
            entry("in_wall", "In Wall"),
            entry("inverted", "Inverted"),
            entry("layers", "Layers"),
            entry("leaves", "Leaves"),
            entry("level", "Level"),
            entry("lit", "Lit"),
            entry("locked", "Locked"),
            entry("mode", "Mode"),
            entry("moisture", "Moisture"),
            entry("north", "North"),
            entry("occupied", "Occupied"),
            entry("open", "Open"),
            entry("orientation", "Orientation"),
            entry("part", "Part"),
            entry("persistent", "Persistent"),
            entry("pickles", "Pickles"),
            entry("powered", "Powered"),
            entry("rotation", "Rotation"),
            entry("sculk_sensor_phase", "Phase"),
            entry("shape", "Shape"),
            entry("short", "Short"),
            entry("signal_fire", "Signal Fire"),
            entry("snowy", "Snowy"),
            entry("south", "South"),
            entry("stage", "Stage"),
            entry("thickness", "Thickness"),
            entry("tip", "Tip"),
            entry("triggered", "Triggered"),
            entry("type", "Type"),
            entry("unstable", "Unstable"),
            entry("up", "Up"),
            entry("vertical_direction", "Vertical"),
            entry("waterlogged", "Water"),
            entry("west", "West")
    );
    private static final Map<String, String> VALUE_FALLBACKS = Map.ofEntries(
            entry("true", "Yes"),
            entry("false", "No"),
            entry("north", "North"),
            entry("south", "South"),
            entry("east", "East"),
            entry("west", "West"),
            entry("up", "Up"),
            entry("down", "Down"),
            entry("x", "X"),
            entry("y", "Y"),
            entry("z", "Z"),
            entry("top", "Top"),
            entry("bottom", "Bottom"),
            entry("upper", "Upper"),
            entry("lower", "Lower"),
            entry("single", "Single"),
            entry("double", "Double"),
            entry("left", "Left"),
            entry("right", "Right"),
            entry("head", "Head"),
            entry("foot", "Foot"),
            entry("front", "Front"),
            entry("back", "Back"),
            entry("none", "None"),
            entry("floor", "Floor"),
            entry("wall", "Wall"),
            entry("ceiling", "Ceiling"),
            entry("straight", "Straight"),
            entry("inner_left", "Inner Left"),
            entry("inner_right", "Inner Right"),
            entry("outer_left", "Outer Left"),
            entry("outer_right", "Outer Right"),
            entry("north_south", "North-South"),
            entry("east_west", "East-West"),
            entry("ascending_north", "Ascending North"),
            entry("ascending_south", "Ascending South"),
            entry("ascending_east", "Ascending East"),
            entry("ascending_west", "Ascending West"),
            entry("compare", "Compare"),
            entry("subtract", "Subtract"),
            entry("save", "Save"),
            entry("load", "Load"),
            entry("corner", "Corner"),
            entry("data", "Data"),
            entry("inactive", "Inactive"),
            entry("active", "Active"),
            entry("cooldown", "Cooldown"),
            entry("small", "Small"),
            entry("large", "Large"),
            entry("tall", "Tall")
    );

    private BlockStateLabelFormatter() {
    }

    public static StateLabel format(String stateId) {
        List<PropertyEntry> entries = parseProperties(stateId);
        if (entries.isEmpty()) {
            return new StateLabel(
                    tr("screen.worldedit_selection_overlay.blocks.state.default", "Default state"),
                    ""
            );
        }

        ArrayList<String> tokens = new ArrayList<>(entries.size());
        for (PropertyEntry entry : entries) {
            String keyLabel = localizeProperty(entry.key());
            String valueLabel = localizeValue(entry.key(), entry.value());
            tokens.add(keyLabel + ": " + valueLabel);
        }

        int firstEnd = splitLine(tokens, 0, MAX_LINE_LEN, 3);
        String firstLine = join(tokens, 0, firstEnd);
        if (firstEnd >= tokens.size()) {
            return new StateLabel(firstLine, "");
        }

        int secondEnd = splitLine(tokens, firstEnd, MAX_LINE_LEN, 3);
        String secondLine = join(tokens, firstEnd, secondEnd);
        int remaining = tokens.size() - secondEnd;
        if (remaining > 0) {
            String more = tr(
                    "screen.worldedit_selection_overlay.blocks.state.more",
                    "+%s more",
                    Integer.toString(remaining)
            );
            secondLine = secondLine.isEmpty() ? more : secondLine + " | " + more;
        }
        return new StateLabel(firstLine, secondLine);
    }

    private static int splitLine(List<String> tokens, int start, int maxLen, int maxParts) {
        int index = start;
        int length = 0;
        int parts = 0;
        while (index < tokens.size() && parts < maxParts) {
            String token = tokens.get(index);
            int candidateLength = length == 0 ? token.length() : length + 3 + token.length();
            if (candidateLength > maxLen && parts > 0) {
                break;
            }
            length = candidateLength;
            index++;
            parts++;
        }
        return index;
    }

    private static String join(List<String> tokens, int from, int toExclusive) {
        if (toExclusive <= from) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < toExclusive; i++) {
            if (i > from) {
                builder.append(" | ");
            }
            builder.append(tokens.get(i));
        }
        return builder.toString();
    }

    private static List<PropertyEntry> parseProperties(String stateId) {
        int bracketStart = stateId.indexOf('[');
        int bracketEnd = stateId.lastIndexOf(']');
        if (bracketStart < 0 || bracketEnd <= bracketStart + 1) {
            return List.of();
        }

        String inside = stateId.substring(bracketStart + 1, bracketEnd);
        if (inside.isBlank()) {
            return List.of();
        }

        String[] parts = inside.split(",");
        ArrayList<PropertyEntry> entries = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalIndex = trimmed.indexOf('=');
            if (equalIndex <= 0 || equalIndex >= trimmed.length() - 1) {
                continue;
            }
            String key = trimmed.substring(0, equalIndex).trim();
            String value = trimmed.substring(equalIndex + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                entries.add(new PropertyEntry(key, value));
            }
        }
        return entries;
    }

    private static String localizeProperty(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        String translationKey = "state.worldedit_selection_overlay.property." + normalized;
        if (I18n.hasTranslation(translationKey)) {
            return I18n.translate(translationKey);
        }
        String fallback = PROPERTY_FALLBACKS.get(normalized);
        return fallback != null ? fallback : humanize(normalized);
    }

    private static String localizeValue(String property, String value) {
        String normalizedProperty = property.toLowerCase(Locale.ROOT);
        String normalizedValue = value.toLowerCase(Locale.ROOT);

        String specificKey = "state.worldedit_selection_overlay.value." + normalizedProperty + "." + normalizedValue;
        if (I18n.hasTranslation(specificKey)) {
            return I18n.translate(specificKey);
        }

        String genericKey = "state.worldedit_selection_overlay.value." + normalizedValue;
        if (I18n.hasTranslation(genericKey)) {
            return I18n.translate(genericKey);
        }

        String fallback = VALUE_FALLBACKS.get(normalizedValue);
        if (fallback != null) {
            return fallback;
        }
        if (isInteger(normalizedValue)) {
            return normalizedValue;
        }
        return humanize(normalizedValue);
    }

    private static String tr(String key, String fallback, Object... args) {
        if (I18n.hasTranslation(key)) {
            return I18n.translate(key, args);
        }
        return String.format(Locale.ROOT, fallback, args);
    }

    private static boolean isInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 0 && c == '-') {
                if (value.length() == 1) {
                    return false;
                }
                continue;
            }
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String[] words = raw.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            if (word.length() == 1) {
                builder.append(word.toUpperCase(Locale.ROOT));
            } else {
                builder.append(Character.toUpperCase(word.charAt(0)));
                builder.append(word.substring(1));
            }
        }
        return builder.toString();
    }

    private record PropertyEntry(String key, String value) {
    }

    public record StateLabel(String primary, String secondary) {
    }
}

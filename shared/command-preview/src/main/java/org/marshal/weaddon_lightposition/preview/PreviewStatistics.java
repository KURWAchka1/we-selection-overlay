package org.marshal.weaddon_lightposition.preview;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record PreviewStatistics(long added, long replaced, long removed, List<Transition> transitions, long unlisted) {
    public record Transition(String before, String after, long count) { }
    private record Pair(String before, String after) { }
    public long affected() { return added + replaced + removed; }
    public static PreviewStatistics empty() { return new PreviewStatistics(0, 0, 0, List.of(), 0); }

    /** Feed inside the bounded output pass; never rescan the snapshot at publication. */
    public static final class Accumulator {
        private final Map<Pair, Long> counts = new HashMap<>();
        private long added, replaced, removed, unlisted;
        public void accept(PreviewChangeKind kind, String before, String after) {
            switch (kind) { case ADDED -> added++; case REPLACED -> replaced++; case REMOVED -> removed++; }
            Pair key = new Pair(before, after);
            if (counts.containsKey(key) || counts.size() < 512) counts.merge(key, 1L, Long::sum);
            else unlisted++;
        }
        public PreviewStatistics finish() {
            List<Transition> rows = counts.entrySet().stream()
                    .map(e -> new Transition(e.getKey().before(), e.getKey().after(), e.getValue()))
                    .sorted(Comparator.comparingLong(Transition::count).reversed()
                            .thenComparing(Transition::before).thenComparing(Transition::after)).toList();
            return new PreviewStatistics(added, replaced, removed, rows, unlisted);
        }
    }
}

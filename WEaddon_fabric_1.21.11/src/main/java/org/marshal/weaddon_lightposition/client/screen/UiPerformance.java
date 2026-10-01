package org.marshal.weaddon_lightposition.client.screen;

import java.util.Arrays;

/** Opt-in CPU sampling for the production UI fixture, never active in a normal game. */
public final class UiPerformance {
    private static final boolean ENABLED = Boolean.getBoolean("weso.ui.profile");
    private static final long[] SAMPLES = ENABLED ? new long[1024] : null;
    private static int count;

    private UiPerformance() { }
    public static boolean enabled() { return ENABLED; }
    public static void reset() { count = 0; }
    public static void record(long nanos) { if (ENABLED && count < SAMPLES.length) SAMPLES[count++] = nanos; }
    public static double[] result() {
        if (!ENABLED || count == 0) return new double[]{0, 0, 0};
        long[] sorted = Arrays.copyOf(SAMPLES, count);
        Arrays.sort(sorted);
        return new double[]{count, sorted[count / 2] / 1_000_000.0, sorted[Math.min(count - 1, (int) (count * 0.95))] / 1_000_000.0};
    }
}

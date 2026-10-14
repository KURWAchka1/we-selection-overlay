import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Bounded two-pass reader. Inferred Paper intervals are not exact start-event brackets. */
class JfrTickTimeline {
    private record Tick(int sample, int number, Instant start, Instant end, double paperMillis,
                        long bracketCpu, long bracketAllocation) { }
    private static final class Window {
        final String phase;
        final int expected;
        final Instant start, end;
        final List<Tick> ticks = new ArrayList<>();
        final BitSet samples = new BitSet();
        Window(String phase, String expected, String start, String end) {
            if (!phase.matches("[a-z][a-z0-9-]{0,31}")) throw new IllegalArgumentException("Invalid phase");
            this.phase = phase; this.expected = Integer.parseInt(expected);
            this.start = Instant.parse(start); this.end = Instant.parse(end);
            if (this.expected < 1 || this.expected > 2400 || !this.end.isAfter(this.start))
                throw new IllegalArgumentException("Invalid measurement window");
        }
    }
    private static final class Outlier {
        final int window;
        final Tick tick;
        final Instant inferredStart;
        long gcPause, vmOperation, safepointBegin, ownerMonitor, ownerPark, ownerSamples, bridgeSamples;
        long drainCount, drainOverlap, drainMax;
        Outlier(int window, Tick tick) {
            this.window = window; this.tick = tick;
            inferredStart = tick.end.minusNanos(Math.round(tick.paperMillis * 1_000_000.0));
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5 || args.length > 65 || (args.length - 1) % 4 != 0)
            throw new IllegalArgumentException("Expected recording and up to 16 phase/count/start/end groups");
        Path path = Path.of(args[0]);
        Window[] windows = new Window[(args.length - 1) / 4];
        for (int i = 0; i < windows.length; i++)
            windows[i] = new Window(args[i * 4 + 1], args[i * 4 + 2], args[i * 4 + 3], args[i * 4 + 4]);
        for (int i = 0; i < windows.length; i++) for (int j = 0; j < i; j++)
            if (windows[i].phase.equals(windows[j].phase)) throw new IllegalArgumentException("Duplicate phase");
        try (RecordingFile file = new RecordingFile(path)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                if (!event.getEventType().getName().equals("weso.fixture.Tick")) continue;
                for (Window w : windows) {
                    // Instant.now() and JFR epoch conversion differ slightly on the tested JVM.
                    // Join by the fixture's unique phase/sample IDs, not wall-clock clipping.
                    if (!w.phase.equals(event.getString("phase"))) continue;
                    int sample = event.getInt("sample");
                    double paperMillis = event.getDouble("paperMillis");
                    long cpu = event.getLong("bracketCpuNanos"), allocated = event.getLong("bracketAllocatedBytes");
                    if (sample < 0 || sample >= w.expected || w.samples.get(sample)
                            || !Double.isFinite(paperMillis) || paperMillis < 0 || paperMillis > 3_600_000
                            || cpu < 0 || allocated < 0 || event.getEndTime().isBefore(event.getStartTime()))
                        throw new IllegalStateException("Invalid or duplicate tick marker");
                    w.samples.set(sample);
                    w.ticks.add(new Tick(sample, event.getInt("paperTick"), event.getStartTime(), event.getEndTime(),
                            paperMillis, cpu, allocated));
                }
            }
        }
        List<Outlier> outliers = new ArrayList<>();
        for (int i = 0; i < windows.length; i++) {
            Window w = windows[i];
            if (w.ticks.size() != w.expected) throw new IllegalStateException("Missing tick markers: " + w.phase);
            w.ticks.sort(Comparator.comparingDouble(Tick::paperMillis).reversed());
            for (int n = 0; n < Math.min(5, w.ticks.size()); n++) outliers.add(new Outlier(i, w.ticks.get(n)));
        }
        try (RecordingFile file = new RecordingFile(path)) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                if (!List.of("jdk.GCPhasePause", "jdk.ExecuteVMOperation", "jdk.SafepointBegin", "jdk.JavaMonitorEnter",
                        "jdk.ThreadPark", "jdk.ExecutionSample", "weso.diagnostic.Call").contains(type)) continue;
                for (Outlier o : outliers) {
                    if (type.equals("jdk.ExecutionSample")) {
                        if (event.getStartTime().isBefore(o.inferredStart) || !event.getStartTime().isBefore(o.tick.end)) continue;
                        RecordedThread thread = event.getThread("sampledThread");
                        if (thread == null || !"Server thread".equals(thread.getJavaName())) continue;
                        o.ownerSamples++;
                        if (event.getStackTrace() != null && event.getStackTrace().getFrames().stream()
                                .map(f -> f.getMethod().getType().getName().replace('/', '.'))
                                .anyMatch(n -> n.startsWith("org.marshal.weaddon_lightposition.bridge.server.")
                                        || n.startsWith("org.marshal.weaddon_lightposition.bridge.paper.")
                                        || n.startsWith("org.marshal.weaddon_lightposition.bridge.protocol."))) o.bridgeSamples++;
                        continue;
                    }
                    Instant start = event.getStartTime().isAfter(o.inferredStart) ? event.getStartTime() : o.inferredStart;
                    Instant end = event.getEndTime().isBefore(o.tick.end) ? event.getEndTime() : o.tick.end;
                    if (!end.isAfter(start)) continue;
                    long overlap = Duration.between(start, end).toNanos();
                    if (type.equals("jdk.GCPhasePause")) o.gcPause += overlap;
                    else if (type.equals("jdk.ExecuteVMOperation")) o.vmOperation += overlap;
                    else if (type.equals("jdk.SafepointBegin")) o.safepointBegin += overlap;
                    else {
                        RecordedThread thread = event.getThread();
                        if (thread == null || !"Server thread".equals(thread.getJavaName())) continue;
                        if (type.equals("weso.diagnostic.Call")) {
                            if (event.getString("operation").equals("drain")) {
                                o.drainCount++; o.drainOverlap += overlap;
                                o.drainMax = Math.max(o.drainMax, event.getLong("wallNanos"));
                            }
                        } else if (type.equals("jdk.JavaMonitorEnter")) o.ownerMonitor += overlap; else o.ownerPark += overlap;
                    }
                }
            }
        }
        for (Outlier o : outliers) {
            Tick t = o.tick;
            double bracket = Duration.between(t.start, t.end).toNanos() / 1_000_000.0;
            // Numeric-only NDJSON; no player identities or recording stack names leave this process.
            System.out.printf(Locale.ROOT, "{\"window\":%d,\"markerCount\":%d,\"sample\":%d,\"paperTick\":%d,"
                    + "\"endEpochSecond\":%d,\"endNano\":%d,\"paperMillis\":%.6f,\"bracketMillis\":%.6f,"
                    + "\"paperMinusBracketMillis\":%.6f,\"bracketCpuMillis\":%.6f,\"bracketAllocatedBytes\":%d,"
                    + "\"inferredGcPauseMillis\":%.6f,\"inferredVmOperationMillis\":%.6f,"
                    + "\"inferredSafepointBeginMillis\":%.6f,\"inferredDrainCount\":%d,\"inferredDrainOverlapMillis\":%.6f,\"inferredDrainMaxMillis\":%.6f,"
                    + "\"inferredOwnerMonitorMillis\":%.6f,\"inferredOwnerParkMillis\":%.6f,"
                    + "\"inferredOwnerSamples\":%d,\"inferredBridgeSamples\":%d,\"phaseWallEndSkewMillis\":%.6f}%n",
                    o.window, windows[o.window].ticks.size(), t.sample, t.number, t.end.getEpochSecond(), t.end.getNano(),
                    t.paperMillis, bracket, t.paperMillis - bracket, t.bracketCpu / 1_000_000.0, t.bracketAllocation,
                    o.gcPause / 1_000_000.0, o.vmOperation / 1_000_000.0,
                    o.safepointBegin / 1_000_000.0, o.drainCount, o.drainOverlap / 1_000_000.0, o.drainMax / 1_000_000.0,
                    o.ownerMonitor / 1_000_000.0, o.ownerPark / 1_000_000.0, o.ownerSamples, o.bridgeSamples,
                    Duration.between(windows[o.window].end, windows[o.window].ticks.stream()
                            .map(Tick::end).max(Comparator.naturalOrder()).orElseThrow()).toNanos() / 1_000_000.0);
        }
    }
}

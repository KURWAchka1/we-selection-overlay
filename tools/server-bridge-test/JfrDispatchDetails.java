import java.nio.file.Path;
import java.time.*;
import java.util.*;
import jdk.jfr.consumer.*;

/** Bounded offline attribution. Cycles remain raw; nested wall intervals are not additive CPU. */
class JfrDispatchDetails {
    record Span(Instant start, Instant end, String kind, long nanos) { }
    static final class Root {
        long id, thread, wall, cpu, cycles, switches, reasons, beforeRead, afterRead, samples, nativeSamples;
        Instant start, end;
        boolean nativeMatched;
        final List<Span> methods = new ArrayList<>(), stalls = new ArrayList<>();
        final Map<String, long[]> methodTotals = new TreeMap<>(), stallTotals = new TreeMap<>();
        final Set<String> classes = new TreeSet<>();
        int classLoads;
    }
    static final Set<String> STALLS = Set.of("jdk.JavaMonitorEnter", "jdk.JavaMonitorWait", "jdk.ThreadPark", "jdk.ThreadSleep",
            "jdk.FileRead", "jdk.FileWrite", "jdk.SocketRead", "jdk.SocketWrite", "jdk.GCPhasePause", "jdk.ExecuteVMOperation", "jdk.SafepointBegin");
    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !args[1].matches("[a-z][a-z0-9-]{0,31}") || !Set.of("drain", "dispatch").contains(args[3]))
            throw new IllegalArgumentException("Expected recording phase count drain/dispatch");
        Path path = Path.of(args[0]); int expected = Integer.parseInt(args[2]);
        if (expected < 1 || expected > 2400) throw new IllegalArgumentException("Invalid count");
        Instant start = null, end = null; BitSet markers = new BitSet();
        try (var recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                var e = recording.readEvent();
                if (e.getEventType().getName().equals("jdk.DataLoss") && e.getLong("amount") > 0) throw new IllegalStateException("Lost JFR data");
                if (!e.getEventType().getName().equals("weso.fixture.Tick") || !e.getString("phase").equals(args[1])) continue;
                int sample = e.getInt("sample");
                if (sample < 0 || sample >= expected || markers.get(sample)) throw new IllegalStateException("Invalid phase marker");
                markers.set(sample); if (start == null || e.getStartTime().isBefore(start)) start = e.getStartTime();
                if (end == null || e.getEndTime().isAfter(end)) end = e.getEndTime();
            }
        }
        if (markers.cardinality() != expected) throw new IllegalStateException("Missing phase markers");
        List<Root> roots = new ArrayList<>();
        try (var recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                var e = recording.readEvent();
                if (!e.getEventType().getName().equals("weso.diagnostic.Call") || e.getStartTime().isBefore(start) || e.getEndTime().isAfter(end)) continue;
                if (!e.getString("operation").equals("drain") || e.getLong("parentId") != 0 || e.getBoolean("failed"))
                    throw new IllegalStateException("Invalid root-only evidence");
                Root r = new Root(); r.id = e.getLong("id"); r.thread = e.getThread().getJavaThreadId();
                r.start = e.getStartTime(); r.end = e.getEndTime(); r.wall = e.getLong("wallNanos"); r.cpu = e.getLong("cpuNanos");
                if (r.wall < 0 || r.cpu < 0 || roots.size() >= 2400) throw new IllegalStateException("Invalid root");
                if (roots.stream().anyMatch(old -> old.id == r.id && old.thread == r.thread)) throw new IllegalStateException("Duplicate root");
                roots.add(r);
            }
        }
        if (roots.isEmpty()) throw new IllegalStateException("No active roots");
        long traces = 0, dispatch = 0, javaSamples = 0, nativeSamples = 0;
        Set<String> tracedMethods = new TreeSet<>();
        try (var recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                var e = recording.readEvent(); String kind = e.getEventType().getName();
                if (e.getEndTime().isBefore(start) || e.getStartTime().isAfter(end)) continue;
                long thread = e.getThread() == null ? -1 : e.getThread().getJavaThreadId();
                if (kind.equals("jdk.ExecutionSample") || kind.equals("jdk.NativeMethodSample")) {
                    boolean java = kind.equals("jdk.ExecutionSample");
                    if (java) javaSamples++; else nativeSamples++;
                    var sampled = e.getThread("sampledThread");
                    if (sampled != null) for (Root r : roots) if (r.thread == sampled.getJavaThreadId()
                            && !e.getStartTime().isBefore(r.start) && e.getStartTime().isBefore(r.end)) {
                        if (java) r.samples++; else r.nativeSamples++;
                    }
                    continue;
                }
                if (kind.equals("weso.diagnostic.Dispatch")) {
                    dispatch++;
                    for (Root r : roots) if (r.thread == thread && r.id == e.getLong("id")) {
                        if (r.nativeMatched || e.getStartTime().isAfter(r.start) || e.getEndTime().isBefore(r.end))
                            throw new IllegalStateException("Duplicate or uncontained native record");
                        r.nativeMatched = true; r.cycles = e.getLong("cycles"); r.switches = e.getLong("switches"); r.reasons = e.getLong("reasons");
                        r.beforeRead = e.getLong("readBeforeNanos"); r.afterRead = e.getLong("readAfterNanos");
                        if (r.cycles < 0 || r.switches < 0 || r.beforeRead < 0 || r.afterRead < 0) throw new IllegalStateException("Invalid native delta");
                    }
                    continue;
                }
                if (kind.equals("jdk.MethodTrace")) {
                    if (++traces > 1_000_000) throw new IllegalStateException("Method trace ceiling");
                    RecordedMethod method = e.getValue("method");
                    String label = method.getType().getName() + "::" + method.getName();
                    tracedMethods.add(label);
                    if (tracedMethods.size() > 32 || label.length() > 512) throw new IllegalStateException("Unexpected method filter");
                    for (Root r : roots) if (r.thread == thread && !e.getStartTime().isBefore(r.start) && !e.getEndTime().isAfter(r.end)) {
                        long nanos = e.getDuration().toNanos(); add(r.methodTotals, label, nanos);
                        keep(r.methods, new Span(e.getStartTime(), e.getEndTime(), label, nanos));
                    }
                    continue;
                }
                if (!STALLS.contains(kind) && !kind.equals("jdk.ClassLoad")) continue;
                boolean global = Set.of("jdk.GCPhasePause", "jdk.ExecuteVMOperation", "jdk.SafepointBegin").contains(kind);
                for (Root r : roots) if (global || r.thread == thread) {
                    Instant a = r.start.isAfter(e.getStartTime()) ? r.start : e.getStartTime();
                    Instant b = r.end.isBefore(e.getEndTime()) ? r.end : e.getEndTime();
                    if (!b.isAfter(a)) continue;
                    if (kind.equals("jdk.ClassLoad")) {
                        r.classLoads++;
                        if (r.classes.size() < 8) r.classes.add(e.getClass("loadedClass").getName());
                    } else {
                        long nanos = Duration.between(a, b).toNanos(); add(r.stallTotals, kind, nanos);
                        keep(r.stalls, new Span(a, b, kind, nanos));
                    }
                }
            }
        }
        boolean nativeExpected = args[3].equals("dispatch");
        if (roots.stream().anyMatch(r -> r.nativeMatched != nativeExpected) || dispatch != (nativeExpected ? roots.size() : 0)
                || nativeExpected != (traces > 0))
            throw new IllegalStateException("Missing or unexpected dispatch/method evidence");
        System.out.printf("{\"type\":\"totals\",\"roots\":%d,\"nativeRecords\":%d,\"methodTraces\":%d,\"javaSamples\":%d,\"nativeSamples\":%d,\"methods\":[%s]}%n",
                roots.size(), dispatch, traces, javaSamples, nativeSamples, tracedMethods.stream().map(JfrDispatchDetails::q).collect(java.util.stream.Collectors.joining(",")));
        roots.sort(Comparator.comparingLong((Root r) -> r.wall).reversed());
        for (Root r : roots) {
            System.out.printf("{\"id\":%d,\"thread\":%d,\"start\":%s,\"end\":%s,\"wallNanos\":%d,\"cpuNanos\":%d,\"nativeMatched\":%s,\"cycles\":%d,\"switches\":%d,\"reasonsHex\":\"%s\",\"beforeReadNanos\":%d,\"afterReadNanos\":%d,\"ownerSamples\":%d,\"ownerNativeSamples\":%d,\"classLoads\":%d,\"classes\":[%s],\"methodTotals\":%s,\"stallTotals\":%s,\"longestMethods\":%s,\"longestStalls\":%s}%n",
                    r.id, r.thread, q(r.start.toString()), q(r.end.toString()), r.wall, r.cpu, r.nativeMatched, r.cycles, r.switches,
                    Long.toHexString(r.reasons), r.beforeRead, r.afterRead, r.samples, r.nativeSamples, r.classLoads,
                    r.classes.stream().map(JfrDispatchDetails::q).collect(java.util.stream.Collectors.joining(",")),
                    totals(r.methodTotals), totals(r.stallTotals), spans(r.methods), spans(r.stalls));
        }
    }
    static void keep(List<Span> spans, Span span) { spans.add(span); spans.sort(Comparator.comparingLong(Span::nanos).reversed()); if (spans.size() > 5) spans.removeLast(); }
    static void add(Map<String, long[]> groups, String label, long nanos) {
        long[] group = groups.computeIfAbsent(label, ignored -> new long[3]); group[0]++; group[1] += nanos; group[2] = Math.max(group[2], nanos);
    }
    static String totals(Map<String, long[]> groups) {
        return "{" + groups.entrySet().stream().map(e -> q(e.getKey()) + ":{\"count\":" + e.getValue()[0] + ",\"sumNanos\":" + e.getValue()[1] + ",\"maxNanos\":" + e.getValue()[2] + "}").collect(java.util.stream.Collectors.joining(",")) + "}";
    }
    static String spans(List<Span> spans) {
        return "[" + spans.stream().map(s -> "{\"kind\":" + q(s.kind) + ",\"start\":" + q(s.start.toString()) + ",\"end\":" + q(s.end.toString()) + ",\"nanos\":" + s.nanos + "}").collect(java.util.stream.Collectors.joining(",")) + "]";
    }
    static String q(String text) { return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""; }
}

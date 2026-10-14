import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Streaming passes; optional marker prepass, bounded method counts and selected roots. */
class JfrBridgeSlices {
    private static final Set<String> BASE = Set.of("drain", "process", "permission", "read", "send", "enqueue", "ingress");
    private static final Set<String> EDIT = Set.of("editPermission", "capture", "apply");
    private static final Set<String> OPERATIONS = Set.of("drain", "process", "permission", "read", "send", "enqueue", "ingress", "editPermission", "capture", "apply",
            "value", "wide", "fail", "pause", "nested", "object");
    private record Call(long id, long parent, long thread, String operation, Instant start, Instant end,
                        long wall, long cpu, long allocation, long before, long after, boolean failed) { }
    private static final class Group {
        final List<Long> walls = new ArrayList<>();
        long sum, cpu, allocated, failed, overTarget;
    }
    private static final class Detail {
        final Call root;
        final List<Call> children = new ArrayList<>();
        long gc, vm, safepointBegin, park, monitor, ownerSamples, deoptimizations;
        final Set<String> vmOperations = new java.util.TreeSet<>();
        final Set<String> vmCallers = new java.util.TreeSet<>();
        final Set<Long> safepointIds = new java.util.TreeSet<>();
        final Set<String> associatedVmOperations = new java.util.TreeSet<>();
        final Set<String> associatedVmCallers = new java.util.TreeSet<>();
        Detail(Call root) { this.root = root; }
    }
    public static void main(String[] args) throws Exception {
        if ((args.length != 2 && args.length != 4) || !Set.of("production", "probe", "edit").contains(args[1]))
            throw new IllegalArgumentException("Expected recording, production/probe/edit and optional phase/count");
        boolean production = !args[1].equals("probe");
        Path path = Path.of(args[0]);
        Instant phaseStart = null, phaseEnd = null;
        if (args.length == 4) {
            String phase = args[2]; int expected = Integer.parseInt(args[3]);
            if (!phase.matches("[a-z][a-z0-9-]{0,31}") || expected < 1 || expected > 2400)
                throw new IllegalArgumentException("Invalid diagnostic phase/count");
            java.util.BitSet samples = new java.util.BitSet();
            try (RecordingFile recording = new RecordingFile(path)) {
                while (recording.hasMoreEvents()) {
                    RecordedEvent event = recording.readEvent();
                    if (!event.getEventType().getName().equals("weso.fixture.Tick") || !phase.equals(event.getString("phase"))) continue;
                    int sample = event.getInt("sample");
                    if (sample < 0 || sample >= expected || samples.get(sample) || event.getEndTime().isBefore(event.getStartTime()))
                        throw new IllegalStateException("Invalid or duplicate diagnostic phase marker");
                    samples.set(sample);
                    if (phaseStart == null || event.getStartTime().isBefore(phaseStart)) phaseStart = event.getStartTime();
                    if (phaseEnd == null || event.getEndTime().isAfter(phaseEnd)) phaseEnd = event.getEndTime();
                }
            }
            if (samples.cardinality() != expected) throw new IllegalStateException("Missing diagnostic phase markers");
        }
        Map<String, Group> groups = new LinkedHashMap<>();
        List<Call> largest = new ArrayList<>(), highwaters = new ArrayList<>();
        int count = 0, seen = 0;
        long coreMax = 0;
        Map<String, Integer> threadDumpCallers = new java.util.TreeMap<>();
        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (event.getEventType().getName().equals("jdk.DataLoss") && event.getLong("amount") > 0)
                    throw new IllegalStateException("Recording reports lost JFR data");
                if (event.getEventType().getName().equals("jdk.ExecuteVMOperation") && event.getString("operation").equals("ThreadDump")) {
                    threadDumpCallers.merge(caller(event), 1, Integer::sum);
                    if (threadDumpCallers.size() > 64) throw new IllegalStateException("Too many VM callers");
                }
                if (!event.getEventType().getName().equals("weso.diagnostic.Call")) continue;
                if (++seen > 1_000_000) throw new IllegalStateException("Diagnostic event ceiling exceeded");
                Call call = call(event);
                if (phaseStart != null && (call.start.isBefore(phaseStart) || call.end.isAfter(phaseEnd))) continue;
                count++;
                Group group = groups.computeIfAbsent(call.operation, ignored -> new Group());
                group.walls.add(call.wall); group.sum += call.wall; group.cpu += call.cpu;
                group.allocated += call.allocation; if (call.failed) group.failed++;
                if (call.wall > 500_000) group.overTarget++;
                if (!production || call.operation.equals("drain")) {
                    keep(largest, call);
                    if (call.after > call.before) keep(highwaters, call);
                }
                coreMax = Math.max(coreMax, call.after);
            }
        }
        if (args.length == 2 && (count == 0 || production && (!groups.keySet().containsAll(BASE)
                || !BASE.containsAll(groups.keySet().stream().filter(s -> !EDIT.contains(s)).toList())
                || args[1].equals("edit") && !groups.keySet().containsAll(EDIT))))
            throw new IllegalStateException("Missing diagnostic method evidence");
        if (production && groups.values().stream().anyMatch(g -> g.failed != 0)) throw new IllegalStateException("Diagnostic method failed");
        List<Detail> details = new ArrayList<>();
        for (Call call : largest) details.add(new Detail(call));
        for (Call call : highwaters) if (details.stream().noneMatch(d -> d.root.id == call.id && d.root.thread == call.thread)) details.add(new Detail(call));
        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent(); String kind = event.getEventType().getName();
                for (Detail detail : details) {
                    Call root = detail.root;
                    if (kind.equals("weso.diagnostic.Call")) {
                        Call child = call(event);
                        if (child.thread == root.thread && child.id != root.id && !child.start.isBefore(root.start) && !child.end.isAfter(root.end)) {
                            if (detail.children.size() >= 256) throw new IllegalStateException("Too many nested diagnostic calls");
                            detail.children.add(child);
                        }
                        continue;
                    }
                    if (kind.equals("jdk.ExecutionSample")) {
                        var thread = event.getThread("sampledThread");
                        if (thread != null && thread.getJavaThreadId() == root.thread
                                && !event.getStartTime().isBefore(root.start) && event.getStartTime().isBefore(root.end)) detail.ownerSamples++;
                        continue;
                    }
                    if (kind.equals("jdk.Deoptimization")) {
                        if (event.getThread() != null && event.getThread().getJavaThreadId() == root.thread
                                && !event.getStartTime().isBefore(root.start) && event.getStartTime().isBefore(root.end)) detail.deoptimizations++;
                        continue;
                    }
                    if (!Set.of("jdk.GCPhasePause", "jdk.ExecuteVMOperation", "jdk.SafepointBegin", "jdk.ThreadPark", "jdk.JavaMonitorEnter").contains(kind)) continue;
                    long overlap = overlap(root.start, root.end, event.getStartTime(), event.getEndTime());
                    if (kind.equals("jdk.GCPhasePause")) detail.gc += overlap;
                    else if (kind.equals("jdk.SafepointBegin")) {
                        detail.safepointBegin += overlap;
                        if (overlap > 0) {
                            long id = event.getLong("safepointId");
                            if (id <= 0 || detail.safepointIds.size() >= 1024) throw new IllegalStateException("Invalid safepoint association");
                            detail.safepointIds.add(id);
                        }
                    }
                    else if (kind.equals("jdk.ExecuteVMOperation")) {
                        detail.vm += overlap;
                        if (overlap > 0) {
                            String operation = event.getString("operation");
                            if (!operation.matches("[A-Za-z0-9_]{1,64}")) throw new IllegalStateException("Invalid VM operation label");
                            detail.vmOperations.add(operation);
                            detail.vmCallers.add(caller(event));
                        }
                    }
                    else if (event.getThread() != null && event.getThread().getJavaThreadId() == root.thread) {
                        if (kind.equals("jdk.ThreadPark")) detail.park += overlap; else detail.monitor += overlap;
                    }
                }
            }
        }
        // JFR buffers are not time ordered. Join by ID in a separate pass, including
        // operation bodies that begin after the selected method has already returned.
        try (RecordingFile recording = new RecordingFile(path)) {
            while (recording.hasMoreEvents()) {
                RecordedEvent event = recording.readEvent();
                if (!event.getEventType().getName().equals("jdk.ExecuteVMOperation")) continue;
                for (Detail detail : details) if (detail.safepointIds.contains(event.getLong("safepointId"))) {
                    String operation = event.getString("operation");
                    if (!operation.matches("[A-Za-z0-9_]{1,64}")) throw new IllegalStateException("Invalid associated VM operation");
                    detail.associatedVmOperations.add(operation); detail.associatedVmCallers.add(caller(event));
                }
            }
        }
        System.out.printf(Locale.ROOT, "{\"type\":\"totals\",\"calls\":%d,\"coreMaxMillis\":%.6f,\"groups\":[", count, coreMax / 1e6);
        boolean first = true;
        for (var entry : groups.entrySet()) {
            Group g = entry.getValue(); g.walls.sort(Long::compare);
            if (!first) System.out.print(','); first = false;
            System.out.printf(Locale.ROOT, "{\"operation\":\"%s\",\"count\":%d,\"sumMillis\":%.6f,\"p95Millis\":%.6f,\"p99Millis\":%.6f,\"maxMillis\":%.6f,\"cpuMillis\":%.6f,\"allocatedBytes\":%d,\"failed\":%d,\"overSoftTarget\":%d}",
                    entry.getKey(), g.walls.size(), g.sum / 1e6, percentile(g.walls, .95) / 1e6,
                    percentile(g.walls, .99) / 1e6, g.walls.getLast() / 1e6, g.cpu / 1e6, g.allocated, g.failed, g.overTarget);
        }
        System.out.print("],\"threadDumpCallers\":[");
        first = true;
        for (var entry : threadDumpCallers.entrySet()) {
            if (!first) System.out.print(','); first = false;
            System.out.printf(Locale.ROOT, "{\"name\":\"%s\",\"count\":%d}", entry.getKey(), entry.getValue());
        }
        System.out.println("]}");
        for (Detail detail : details) {
            Call c = detail.root;
            System.out.printf(Locale.ROOT, "{\"type\":\"interval\",\"id\":%d,\"thread\":%d,\"operation\":\"%s\",\"start\":\"%s\",\"end\":\"%s\",\"wallMillis\":%.6f,\"cpuMillis\":%.6f,\"allocatedBytes\":%d,\"coreMaxBeforeMillis\":%.6f,\"coreMaxAfterMillis\":%.6f,\"gcOverlapMillis\":%.6f,\"vmOverlapMillis\":%.6f,\"safepointBeginOverlapMillis\":%.6f,\"parkOverlapMillis\":%.6f,\"monitorOverlapMillis\":%.6f,\"ownerSamples\":%d,\"deoptimizations\":%d,\"vmOperations\":[%s],\"vmCallers\":[%s],\"children\":[",
                    c.id, c.thread, c.operation, c.start, c.end, c.wall / 1e6, c.cpu / 1e6, c.allocation, c.before / 1e6, c.after / 1e6,
                    detail.gc / 1e6, detail.vm / 1e6, detail.safepointBegin / 1e6, detail.park / 1e6, detail.monitor / 1e6, detail.ownerSamples,
                    detail.deoptimizations, quoted(detail.vmOperations), quoted(detail.vmCallers));
            first = true;
            for (Call child : detail.children) {
                if (!first) System.out.print(','); first = false;
                System.out.printf(Locale.ROOT, "{\"id\":%d,\"parentId\":%d,\"operation\":\"%s\",\"wallMillis\":%.6f,\"allocatedBytes\":%d}", child.id, child.parent, child.operation, child.wall / 1e6, child.allocation);
            }
            System.out.printf("],\"safepointIds\":[%s],\"associatedVmOperations\":[%s],\"associatedVmCallers\":[%s]}%n",
                    detail.safepointIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),
                    quoted(detail.associatedVmOperations), quoted(detail.associatedVmCallers));
        }
    }
    private static String caller(RecordedEvent event) {
        var thread = event.getThread("caller");
        String name = thread == null ? "none" : thread.getJavaName();
        if (name == null) return "native";
        return name.matches("[A-Za-z0-9_ .-]{1,96}") ? name : "other";
    }
    private static String quoted(Set<String> values) {
        return values.stream().map(s -> "\"" + s + "\"").collect(java.util.stream.Collectors.joining(","));
    }
    private static Call call(RecordedEvent event) {
        String operation = event.getString("operation");
        long id = event.getLong("id"), parent = event.getLong("parentId"), wall = event.getLong("wallNanos");
        long cpu = event.getLong("cpuNanos"), allocated = event.getLong("allocatedBytes");
        long before = event.getLong("coreMaxBefore"), after = event.getLong("coreMaxAfter");
        if (!OPERATIONS.contains(operation) || id < 1 || parent < 0 || parent >= id || wall < 0 || cpu < 0 || allocated < 0
                || event.getThread() == null || event.getDuration().toNanos() < wall)
            throw new IllegalStateException("Invalid diagnostic call");
        if (operation.equals("drain") ? before < 0 || after < before : before != -1 || after != -1)
            throw new IllegalStateException("Invalid diagnostic high-water mark");
        return new Call(id, parent, event.getThread().getJavaThreadId(), operation, event.getStartTime(), event.getEndTime(),
                wall, cpu, allocated, before, after, event.getBoolean("failed"));
    }
    private static void keep(List<Call> list, Call call) {
        list.add(call); list.sort(Comparator.comparingLong(Call::wall).reversed());
        if (list.size() > 10) list.removeLast();
    }
    private static long percentile(List<Long> values, double fraction) {
        return values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1));
    }
    private static long overlap(Instant a, Instant b, Instant c, Instant d) {
        Instant start = a.isAfter(c) ? a : c, end = b.isBefore(d) ? b : d;
        return end.isAfter(start) ? Duration.between(start, end).toNanos() : 0;
    }
}

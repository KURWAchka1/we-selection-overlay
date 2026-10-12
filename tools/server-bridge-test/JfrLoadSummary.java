import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/** Streams local JFR data without expanding recursive classloader metadata to JSON. */
class JfrLoadSummary {
    private static final class Window {
        final Instant start, end;
        long executionSamples, ownerSamples, bridgeSamples, allocationSamples, bridgeAllocationSamples;
        long sampledAllocationWeight, bridgeAllocationWeight, gcCount, gcNanos;
        long maxGcNanos, gcPauseNanos, maxGcPauseNanos;
        long maxVmOperationNanos, maxOwnerMonitorNanos, maxOwnerParkNanos;
        Window(String start, String end) {
            this.start = Instant.parse(start);
            this.end = Instant.parse(end);
            if (!this.end.isAfter(this.start)) throw new IllegalArgumentException("Invalid window");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 33 || args.length % 2 != 1)
            throw new IllegalArgumentException("Expected recording and up to 16 start/end pairs");
        Window[] windows = new Window[(args.length - 1) / 2];
        for (int i = 0; i < windows.length; i++) windows[i] = new Window(args[i * 2 + 1], args[i * 2 + 2]);
        try (RecordingFile file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                if (!type.equals("jdk.ExecutionSample") && !type.equals("jdk.ObjectAllocationSample")
                        && !type.equals("jdk.GarbageCollection") && !type.equals("jdk.ExecuteVMOperation")
                        && !type.equals("jdk.JavaMonitorEnter") && !type.equals("jdk.ThreadPark")) continue;
                for (Window window : windows) {
                    Instant time = event.getStartTime();
                    if (time.isBefore(window.start) || !time.isBefore(window.end)) continue;
                    if (type.equals("jdk.GarbageCollection")) {
                        window.gcCount++;
                        window.gcNanos += event.getDuration().toNanos();
                        window.maxGcNanos = Math.max(window.maxGcNanos, event.getDuration().toNanos());
                        // Collection duration includes concurrent work; pauses are separate JFR fields.
                        window.gcPauseNanos += event.getDuration("sumOfPauses").toNanos();
                        window.maxGcPauseNanos = Math.max(window.maxGcPauseNanos,
                                event.getDuration("longestPause").toNanos());
                        continue;
                    }
                    if (type.equals("jdk.ExecuteVMOperation")) {
                        window.maxVmOperationNanos = Math.max(window.maxVmOperationNanos, event.getDuration().toNanos());
                        continue;
                    }
                    if (type.equals("jdk.JavaMonitorEnter") || type.equals("jdk.ThreadPark")) {
                        RecordedThread thread = event.getThread();
                        if (thread != null && "Server thread".equals(thread.getJavaName())) {
                            if (type.equals("jdk.JavaMonitorEnter"))
                                window.maxOwnerMonitorNanos = Math.max(window.maxOwnerMonitorNanos, event.getDuration().toNanos());
                            else window.maxOwnerParkNanos = Math.max(window.maxOwnerParkNanos, event.getDuration().toNanos());
                        }
                        continue;
                    }
                    boolean bridge = event.getStackTrace() != null && event.getStackTrace().getFrames().stream()
                            .map(frame -> frame.getMethod().getType().getName().replace('/', '.'))
                            .anyMatch(name -> name.startsWith("org.marshal.weaddon_lightposition.bridge.server.")
                                    || name.startsWith("org.marshal.weaddon_lightposition.bridge.paper.")
                                    || name.startsWith("org.marshal.weaddon_lightposition.bridge.protocol."));
                    if (type.equals("jdk.ExecutionSample")) {
                        window.executionSamples++;
                        RecordedThread thread = event.getThread("sampledThread");
                        if (thread != null && "Server thread".equals(thread.getJavaName())) window.ownerSamples++;
                        if (bridge) window.bridgeSamples++;
                    } else {
                        window.allocationSamples++;
                        long weight = event.getLong("weight");
                        window.sampledAllocationWeight += weight;
                        if (bridge) {
                            window.bridgeAllocationSamples++;
                            window.bridgeAllocationWeight += weight;
                        }
                    }
                }
            }
        }
        // Fixed numeric-only NDJSON: no unescaped recording/thread/class names leave the process.
        for (int i = 0; i < windows.length; i++) {
            Window w = windows[i];
            System.out.printf(Locale.ROOT, "{\"window\":%d,\"executionSamples\":%d,\"ownerSamples\":%d,"
                    + "\"bridgeSamples\":%d,\"allocationSamples\":%d,\"bridgeAllocationSamples\":%d,"
                    + "\"sampledAllocationWeight\":%d,\"bridgeAllocationWeight\":%d,\"gcCount\":%d,\"gcMillis\":%.6f,"
                    + "\"maxGcMillis\":%.6f,\"gcPauseMillis\":%.6f,\"maxGcPauseMillis\":%.6f,"
                    + "\"maxVmOperationMillis\":%.6f,\"maxOwnerMonitorMillis\":%.6f,\"maxOwnerParkMillis\":%.6f}%n",
                    i, w.executionSamples, w.ownerSamples, w.bridgeSamples, w.allocationSamples, w.bridgeAllocationSamples,
                    w.sampledAllocationWeight, w.bridgeAllocationWeight, w.gcCount, w.gcNanos / 1_000_000.0,
                    w.maxGcNanos / 1_000_000.0, w.gcPauseNanos / 1_000_000.0, w.maxGcPauseNanos / 1_000_000.0,
                    w.maxVmOperationNanos / 1_000_000.0,
                    w.maxOwnerMonitorNanos / 1_000_000.0, w.maxOwnerParkNanos / 1_000_000.0);
        }
    }
}

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import com.sun.management.ThreadMXBean;
import jdk.jfr.Recording;
import org.marshal.weaddon_lightposition.bridge.fixture.FixtureTickEvent;

/** Own-JVM recording probe; no Paper, world, window or network initialization. */
class JfrTickProbe {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        var threads = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        threads.setThreadCpuTimeEnabled(true); threads.setThreadAllocatedMemoryEnabled(true);
        try (Recording recording = new Recording()) {
            recording.enable(FixtureTickEvent.class).withThreshold(Duration.ZERO).withoutStackTrace();
            recording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO);
            recording.start();
            String start = Instant.now().toString();
            for (int i = 0; i < 2; i++) {
                var event = new FixtureTickEvent();
                event.phase = "probe"; event.sample = args.length > 1 ? 0 : i; event.paperTick = i + 1;
                long cpu = threads.getCurrentThreadCpuTime(), allocated = threads.getCurrentThreadAllocatedBytes();
                long before = System.nanoTime();
                event.begin();
                if (i == 0) System.gc();
                Thread.sleep(25);
                event.end();
                event.paperMillis = (System.nanoTime() - before) / 1_000_000.0 + 40;
                event.bracketCpuNanos = threads.getCurrentThreadCpuTime() - cpu;
                event.bracketAllocatedBytes = threads.getCurrentThreadAllocatedBytes() - allocated;
                event.commit();
            }
            String end = Instant.now().toString();
            recording.stop(); recording.dump(Path.of(args[0]));
            System.out.printf(Locale.ROOT, "%s%n%s%n", start, end);
        }
    }
}

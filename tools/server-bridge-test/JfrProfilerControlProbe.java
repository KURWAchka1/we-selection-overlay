import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;

class JfrProfilerControlProbe {
    // Deliberately invalid evidence tests the reader's lost-data rejection.
    @Name("jdk.DataLoss")
    static class LostData extends Event { public long amount; }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ExecuteVMOperation").withThreshold(Duration.ZERO);
            recording.enable(LostData.class);
            recording.start();
            Instant start = Instant.now();
            Thread sampler = new Thread(() -> {
                var threads = ManagementFactory.getThreadMXBean();
                threads.getThreadInfo(threads.getAllThreadIds(), 32);
            }, "spark-java-sampler-test");
            sampler.start(); sampler.join();
            if (args.length == 2 && args[1].equals("loss")) {
                LostData loss = new LostData(); loss.amount = 1; loss.commit();
            }
            Instant end = Instant.now();
            recording.stop(); recording.dump(Path.of(args[0]));
            System.out.println(start); System.out.println(end);
        }
    }
}

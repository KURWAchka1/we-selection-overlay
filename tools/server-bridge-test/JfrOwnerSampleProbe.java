import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.Recording;
import org.marshal.wesotest.slices.CallEvent;

/** Synthetic sampler-shaped events test attribution, not real CPU sampling. */
class JfrOwnerSampleProbe {
    @Name("jdk.ExecutionSample")
    static class Sample extends Event {
        Thread sampledThread;
        String state;
    }
    @Name("jdk.DataLoss")
    static class Loss extends Event { long amount; }

    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(CallEvent.class).withThreshold(Duration.ZERO);
            recording.enable(Sample.class).withStackTrace();
            recording.enable(Loss.class);
            recording.start();
            Thread owner = Thread.currentThread();
            sample(owner, "outside-before");
            CallEvent root = new CallEvent();
            root.operation = "drain"; root.id = 1;
            root.begin();
            switch (args[1]) {
                case "identity" -> {
                    sample(owner, "STATE_RUNNABLE");
                    Thread worker = new Thread(() -> {
                        sample(owner, "STATE_FROM_OTHER_COMMITTER");
                        sample(Thread.currentThread(), "wrong-sampled-thread");
                        sample(null, "no-sampled-thread");
                    });
                    worker.start(); worker.join();
                    recording.enable(Sample.class).withoutStackTrace();
                    sample(owner, "STATE_\"\\\n\t\u0001");
                }
                case "many" -> deep(80, owner);
                case "buffers" -> {
                    Thread worker = new Thread(() -> {
                        for (int i = 0; i < 4; i++) sample(owner, "early-" + i);
                    });
                    worker.start(); worker.join();
                    for (int i = 0; i < 8; i++) sample(owner, "late-" + i);
                }
                case "labels" -> {
                    sample(owner, "x".repeat(200));
                    sample(owner, null);
                }
                case "loss" -> { Loss loss = new Loss(); loss.amount = 1; loss.commit(); }
                case "empty" -> { }
                default -> throw new IllegalArgumentException("Unknown sample probe");
            }
            root.end(); root.commit();
            sample(owner, "outside-after");
            recording.stop(); recording.dump(Path.of(args[0]));
        }
    }
    private static void sample(Thread thread, String state) {
        Sample sample = new Sample(); sample.sampledThread = thread;
        sample.state = state; sample.commit();
    }
    private static void deep(int depth, Thread owner) {
        if (depth > 0) deep(depth - 1, owner);
        else for (int i = 0; i < 12; i++) sample(owner, "sample-" + i);
    }
}

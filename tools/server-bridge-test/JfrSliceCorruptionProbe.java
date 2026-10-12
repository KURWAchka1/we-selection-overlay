import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import org.marshal.wesotest.slices.CallEvent;

/** Deliberately invalid custom records; never a server workload. */
class JfrSliceCorruptionProbe {
    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("weso.diagnostic.Call").withThreshold(Duration.ZERO);
            recording.start();
            CallEvent event = new CallEvent();
            event.operation = "value"; event.id = 1; event.coreMaxBefore = -1; event.coreMaxAfter = -1;
            switch (args[1]) {
                case "operation" -> event.operation = "unknown";
                case "id" -> event.id = 0;
                case "parent" -> event.parentId = 1;
                case "wall" -> event.wallNanos = -1;
                case "cpu" -> event.cpuNanos = -1;
                case "allocation" -> event.allocatedBytes = -1;
                case "mark" -> event.coreMaxBefore = 0;
                case "backwards" -> { event.operation = "drain"; event.coreMaxBefore = 2; event.coreMaxAfter = 1; }
                default -> throw new IllegalArgumentException("Unknown corruption");
            }
            event.begin(); event.end(); event.commit();
            recording.stop(); recording.dump(Path.of(args[0]));
        }
    }
}

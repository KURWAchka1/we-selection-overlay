import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;
import org.marshal.weaddon_lightposition.bridge.fixture.FixtureTickEvent;
import org.marshal.wesotest.slices.CallEvent;

/** Synthetic reader evidence only; no server/client initialization. */
class JfrEditSliceProbe {
    private static long id;
    private static CallEvent begin(String operation, long parent) {
        CallEvent event = new CallEvent();
        event.id = ++id; event.parentId = parent; event.operation = operation;
        event.coreMaxBefore = operation.equals("drain") ? 0 : -1;
        event.coreMaxAfter = operation.equals("drain") ? 1 : -1;
        event.begin(); return event;
    }
    private static void finish(CallEvent event) { event.end(); event.commit(); }
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("Server thread");
        boolean parented = args.length > 1 && args[1].equals("drain-parent");
        boolean failed = args.length > 1 && args[1].equals("drain-failed");
        boolean drainOnly = args.length > 1 && args[1].equals("drain") || parented || failed;
        try (Recording recording = new Recording()) {
            recording.enable(FixtureTickEvent.class).withThreshold(Duration.ZERO).withoutStackTrace();
            recording.enable(CallEvent.class).withThreshold(Duration.ZERO).withoutStackTrace();
            recording.start();
            for (String operation : new String[]{"drain", "process", "permission", "read", "send", "enqueue", "ingress", "editPermission", "capture", "apply"})
                if ((!drainOnly || operation.equals("drain"))
                        && (args.length < 2 || !args[1].equals("missing") || !operation.equals("apply"))) finish(begin(operation, 0));
            for (String phase : new String[]{"active", "idle"}) for (int i = 0; i < 2; i++) {
                var tick = new FixtureTickEvent(); tick.phase = phase; tick.sample = args.length > 1 && args[1].equals("duplicate") ? 0 : i;
                tick.paperTick = i + 1; tick.begin();
                if (phase.equals("active") && (args.length < 2 || !args[1].equals("missing"))) {
                    var drain = begin("drain", parented ? 1 : 0); var apply = drainOnly ? null : begin("apply", drain.id);
                    drain.failed = failed;
                    Thread.sleep(5); if (apply != null) finish(apply); finish(drain);
                }
                tick.end(); tick.paperMillis = 50; tick.commit();
            }
            if (!drainOnly) finish(begin("send", 0));
            recording.stop(); recording.dump(Path.of(args[0]));
        }
    }
}

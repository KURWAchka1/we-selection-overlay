import java.nio.file.Path;
import jdk.jfr.*;
import org.marshal.wesotest.slices.CallEvent;

/** Synthetic dispatch counters validate the reader, never native-performance claims. */
class JfrDispatchProbe {
    @Name("weso.fixture.Tick") @StackTrace(false)
    static class Tick extends Event { String phase; int sample; }
    @Name("weso.diagnostic.Dispatch") @StackTrace(false)
    static class Dispatch extends Event { long id, cycles, switches, reasons, readBeforeNanos, readAfterNanos; }
    @Name("jdk.DataLoss") static class Loss extends Event { long amount; }
    public static void main(String[] args) throws Exception {
        String kind = args[1];
        try (var r = new Recording()) {
            r.enable(Tick.class); r.enable(Dispatch.class); r.enable(CallEvent.class); r.enable(Loss.class);
            if (!kind.equals("plain")) r.enable("jdk.MethodTrace").with("filter", "JfrDispatchProbe::work").withoutThreshold().withoutStackTrace();
            r.enable("jdk.ThreadSleep").withoutThreshold(); r.start();
            var tick = new Tick(); tick.phase = "probe"; tick.sample = 0; tick.begin();
            var dispatch = new Dispatch(); dispatch.id = kind.equals("wrong-id") ? 2 : 1;
            dispatch.cycles = kind.equals("negative") ? -1 : 100; dispatch.switches = 1; dispatch.reasons = 0x40;
            dispatch.begin();
            var root = new CallEvent(); root.id = 1; root.operation = "drain";
            root.failed = kind.equals("failed"); root.begin(); long began = System.nanoTime(); work();
            root.wallNanos = System.nanoTime() - began; root.end(); root.commit();
            dispatch.end();
            if (!kind.equals("missing") && !kind.equals("plain")) dispatch.commit();
            if (kind.equals("duplicate")) dispatch.commit();
            if (kind.equals("extra")) {
                var extra = new Dispatch(); extra.id = 999; extra.cycles = 100; extra.commit();
            }
            tick.end(); if (!kind.equals("markers")) tick.commit();
            if (kind.equals("loss")) { var loss = new Loss(); loss.amount = 1; loss.commit(); }
            r.stop(); r.dump(Path.of(args[0]));
        }
    }
    private static void work() throws Exception { Thread.sleep(5); }
}

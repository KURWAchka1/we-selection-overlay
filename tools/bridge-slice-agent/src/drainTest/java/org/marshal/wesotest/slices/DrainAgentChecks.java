package org.marshal.wesotest.slices;

import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;

public final class DrainAgentChecks {
    private static void rejectedAgent(String arguments) throws Exception {
        try {
            Class.forName("org.marshal.wesotest.slices.DrainAgent")
                    .getMethod("premain", String.class, java.lang.instrument.Instrumentation.class).invoke(null, arguments, null);
            throw new AssertionError("Unsafe agent invocation accepted");
        } catch (java.lang.reflect.InvocationTargetException failure) {
            if (!(failure.getCause() instanceof IllegalStateException)) throw new AssertionError("Agent touched instrumentation before guard", failure);
        }
    }

    public static void main(String[] args) throws Exception {
        rejectedAgent("unexpected");
        for (String property : new String[]{"weso.bridge.fixture", "weso.bridge.sliceDiagnostics", "weso.bridge.drainDiagnostics"}) {
            String value = System.clearProperty(property);
            try { rejectedAgent(null); } finally { if (value != null) System.setProperty(property, value); }
        }
        var target = new BridgeServer();
        try (var recording = new Recording()) {
            recording.enable("weso.diagnostic.Call").withoutThreshold().withoutStackTrace(); recording.start();
            int[] sends = {0};
            for (int i = 0; i < 2; i++) if (target.drain((id, frame) -> sends[0]++) != 17) throw new AssertionError("Return changed");
            target.failure = new IllegalArgumentException("original");
            try { target.drain((id, frame) -> { throw new AssertionError("Unexpected send"); }); throw new AssertionError("Missing failure"); }
            catch (IllegalArgumentException failure) { if (failure != target.failure) throw new AssertionError("Exception changed"); }
            if (sends[0] != 2 || target.readSelection() != 3) throw new AssertionError("Behavior changed");
            recording.stop(); recording.dump(Path.of(args[0]));
        }
        var events = RecordingFile.readAllEvents(Path.of(args[0])).stream()
                .filter(e -> e.getEventType().getName().equals("weso.diagnostic.Call")).toList();
        if (events.size() != 3 || events.stream().filter(e -> e.getBoolean("failed")).count() != 1)
            throw new AssertionError("Missing calls or exception evidence");
        for (var event : events) if (!event.getString("operation").equals("drain") || event.getLong("parentId") != 0
                || event.getLong("wallNanos") < 0 || event.getLong("coreMaxAfter") < event.getLong("coreMaxBefore"))
            throw new AssertionError("Invalid drain-only event");
        if (Class.forName("org.marshal.wesotest.slices.SliceProbe", false, null).getClassLoader() != null)
            throw new AssertionError("Recorder not isolated");
        System.out.println("DRAIN AGENT CHECKS PASS: opt-in guards, exact root-only calls, return, exception identity and bootstrap isolation");
    }
}

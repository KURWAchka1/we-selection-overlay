package org.marshal.wesotest.slices;

import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.marshal.weaddon_lightposition.bridge.server.BridgeServer;

public final class DispatchChecks {
    public static void main(String[] args) throws Exception {
        var target = new BridgeServer();
        try (var recording = new Recording()) {
            recording.enable("jdk.MethodTrace").with("filter", "org.marshal.weaddon_lightposition.bridge.server.BridgeServer::drain;org.marshal.wesotest.slices.WindowsDispatchCounter::read")
                    .withoutThreshold().withoutStackTrace();
            recording.enable("weso.diagnostic.Call"); recording.enable("weso.diagnostic.Dispatch"); recording.start();
            if (target.drain((id, bytes) -> { try { Thread.sleep(20); } catch (InterruptedException e) { throw new AssertionError(e); } }) != 17)
                throw new AssertionError("Return changed");
            target.failure = new IllegalArgumentException("original");
            try { target.drain((id, bytes) -> {}); throw new AssertionError("Failure missing"); }
            catch (IllegalArgumentException e) { if (e != target.failure) throw new AssertionError("Failure replaced"); }
            var plugin = new org.marshal.weaddon_lightposition.bridge.paper.WorldEditOverlayBridgePlugin();
            plugin.onDisable();
            if (plugin.stops != 1) throw new AssertionError("Original cleanup changed");
            recording.stop(); recording.dump(Path.of(args[0]));
        }
        var events = RecordingFile.readAllEvents(Path.of(args[0]));
        var calls = events.stream().filter(e -> e.getEventType().getName().equals("weso.diagnostic.Call")).toList();
        var dispatch = events.stream().filter(e -> e.getEventType().getName().equals("weso.diagnostic.Dispatch")).toList();
        var traces = events.stream().filter(e -> e.getEventType().getName().equals("jdk.MethodTrace")).toList();
        if (traces.size() < 5) throw new AssertionError("Built-in method traces missing");
        if (calls.size() != 2 || dispatch.size() != 2 || calls.stream().filter(e -> e.getBoolean("failed")).count() != 1)
            throw new AssertionError("Missing event/exception");
        for (var event : dispatch) {
            var call = calls.stream().filter(c -> c.getLong("id") == event.getLong("id")).findFirst().orElseThrow();
            if (event.getThread().getJavaThreadId() != call.getThread().getJavaThreadId() || event.getLong("cycles") < 0
                    || event.getLong("readBeforeNanos") < 0 || event.getLong("readAfterNanos") < 0
                    || event.getStartTime().isAfter(call.getStartTime()) || event.getEndTime().isBefore(call.getEndTime()))
                throw new AssertionError("Counter/call attribution changed");
        }
        var slept = dispatch.stream().filter(e -> e.getLong("id") == 1).findFirst().orElseThrow();
        if (slept.getLong("switches") == 0 || slept.getLong("reasons") == 0) throw new AssertionError("Sleep not observed");
        System.clearProperty("weso.bridge.dispatchDiagnostics");
        try { new WindowsDispatchCounter(); throw new AssertionError("Unguarded counter"); }
        catch (IllegalStateException expected) { }
        System.setProperty("weso.bridge.dispatchDiagnostics", "true");
        try (var counter = new WindowsDispatchCounter()) {
            var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread other = new Thread(() -> {
                try { counter.read(); throw new AssertionError("Foreign owner accepted"); }
                catch (IllegalStateException expected) { }
            });
            other.setUncaughtExceptionHandler((t, e) -> failure.set(e));
            other.start(); other.join(); counter.read();
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
        System.out.println("DISPATCH CHECKS PASS: native owner, sleep switch, exact root, original exception, close/reopen, opt-in");
    }
}

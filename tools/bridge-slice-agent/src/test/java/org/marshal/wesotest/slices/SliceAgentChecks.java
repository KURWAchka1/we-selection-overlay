package org.marshal.wesotest.slices;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

public final class SliceAgentChecks {
    public static class Subject {
        public int count;
        public int value(int input) { count++; return input + 1; }
        public long wide(long input, double ignored) { return input * 2; }
        public void fail() { throw new IllegalArgumentException("original failure"); }
        public void pause() { LockSupport.parkNanos(20_000_000); System.gc(); }
        public int nested() { return value(8); }
        public Object object(Object first, Object second, Object third) { return third; }
    }
    private static class Loader extends ClassLoader {
        Class<?> define(byte[] source) { return defineClass(null, source, 0, source.length); }
    }
    public static void main(String[] args) throws Exception {
        if (Boolean.getBoolean("weso.bridge.sliceDiagnostics")) check(SliceProbe.class.getClassLoader() == null);
        byte[] original = Subject.class.getResourceAsStream("SliceAgentChecks$Subject.class").readAllBytes();
        Map<String, String> plan = Map.of("value(I)I", "value", "wide(JD)J", "wide", "fail()V", "fail",
                "pause()V", "pause", "nested()I", "nested",
                "object(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", "object");
        byte[] transformed = SliceAgent.weave(original, plan);
        reject(() -> SliceAgent.weave(original, Map.of("missing()V", "missing")));
        reject(() -> SliceAgent.weave(transformed, plan));
        Class<?> type = new Loader().define(transformed); Object subject = type.getConstructor().newInstance();
        try (Recording recording = new Recording()) {
            recording.enable("weso.diagnostic.Call").withThreshold(java.time.Duration.ZERO);
            recording.enable("jdk.GCPhasePause").withThreshold(java.time.Duration.ZERO);
            recording.enable("jdk.SafepointBegin").withThreshold(java.time.Duration.ZERO);
            recording.enable("jdk.ExecuteVMOperation").withThreshold(java.time.Duration.ZERO);
            recording.enable("jdk.ThreadPark").withThreshold(java.time.Duration.ZERO);
            recording.start();
            check(type.getMethod("value", int.class).invoke(subject, 4).equals(5));
            check(type.getField("count").getInt(subject) == 1);
            check(type.getMethod("wide", long.class, double.class).invoke(subject, 9L, 2.0).equals(18L));
            try { type.getMethod("fail").invoke(subject); throw new AssertionError("Exception swallowed"); }
            catch (InvocationTargetException e) { check(e.getCause() instanceof IllegalArgumentException && e.getCause().getMessage().equals("original failure")); }
            check(type.getMethod("nested").invoke(subject).equals(9));
            Object token = new Object();
            check(type.getMethod("object", Object.class, Object.class, Object.class).invoke(subject, null, subject, token) == token);
            type.getMethod("pause").invoke(subject);
            recording.stop(); recording.dump(Path.of(args[0]));
        }
        var events = RecordingFile.readAllEvents(Path.of(args[0])).stream()
                .filter(e -> e.getEventType().getName().equals("weso.diagnostic.Call")).toList();
        check(events.size() == 7);
        check(events.stream().filter(e -> e.getBoolean("failed")).count() == 1);
        var nested = events.stream().filter(e -> e.getString("operation").equals("nested")).findFirst().orElseThrow();
        check(events.stream().anyMatch(e -> e.getLong("parentId") == nested.getLong("id")));
        check(events.stream().allMatch(e -> e.getDuration().toNanos() >= e.getLong("wallNanos") && e.getLong("cpuNanos") >= 0));
        System.out.println("SLICE AGENT CHECKS PASS: return values, wide slots, original exceptions, nesting, exact targets and JFR recording");
    }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Unsupported bytecode accepted");
    }
    private static void check(boolean result) { if (!result) throw new AssertionError(); }
}

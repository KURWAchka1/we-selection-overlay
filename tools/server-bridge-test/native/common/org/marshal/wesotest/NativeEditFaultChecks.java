package org.marshal.wesotest;

import java.nio.file.Files;
import java.nio.file.Path;

/** Actual native controller/transport, with faults injected outside the game JVM. */
public final class NativeEditFaultChecks {
    private final NativeBridgeChecks.Adapter adapter;
    private final Class<?> bridge;
    private final String scenario = System.getProperty("weso.native.scenario");
    private final Path control = Path.of(System.getProperty("weso.native.control"));
    private final long deadline = System.nanoTime() + 180_000_000_000L;
    private Object editor, overlay;
    private int stage, tap, quiet;
    private boolean enabled, finished;
    private long submitted, quietRequest;

    NativeEditFaultChecks(NativeBridgeChecks.Adapter adapter, Class<?> bridge) { this.adapter = adapter; this.bridge = bridge; }

    public void tick() {
        if (finished) return;
        try {
            check(System.nanoTime() - deadline < 0, "Timeout at stage " + stage);
            adapter.configure();
            if (stage == 12) {
                if (adapter.joined() || !call("status").toString().equals("DISCONNECTED")) return;
                check(!(boolean) invoke(client(), "pending") && invoke(client(), "editSelection") == null, "Disconnect retained edit data");
                finished = true; System.out.println("NATIVE PAPER BRIDGE PASS: " + scenario); adapter.stop(); return;
            }
            if (!adapter.joined()) return;
            if (overlay == null) { overlay = adapter.overlay(); if (overlay == null) return; }
            if (editor == null) editor = Class.forName("org.marshal.weaddon_lightposition.runtime.mc1_21_11.client.selection.SelectionEditController").getMethod("instance").invoke(null);
            editor.getClass().getMethod("configure", boolean.class, int.class, int.class).invoke(editor, enabled, 1, 8);
            if (enabled) adapter.focusForEditing(true);
            if (stage == 0) {
                if (!call("status").toString().equals("READY") || call("editContext") == null || region() == null) return;
                check(invoke(client(), "editSelection") == null, "Handshake captured without consumer");
                System.out.println("NATIVE PAPER CUI PASS: " + scenario); waitFor("setup", 1);
            } else if (stage == 1 && acknowledged("setup")) {
                enabled = true; setEnum("mode", "RESIZE"); setEnum("selected", "UP"); stage = 2;
            } else if (stage == 2) {
                Object shown = field(editor, "shown"); if (shown == null || !(boolean) invoke(shown, "ready")) return;
                check((int) invoke(invoke(shown, "bounds"), "maxY") == 70, "Initial source differs");
                waitFor("arm", 3);
            } else if (stage == 3 && acknowledged("arm")) { press(); stage = 4;
            } else if (stage == 4 && --tap <= 0) { release(); stage = 5;
            } else if (stage == 5 && (boolean) invoke(editor, "active")) {
                editor.getClass().getMethod("scroll", double.class).invoke(editor, 1.0);
                check((int) invoke(invoke(editor, "draft"), "maxY") == 71, "Native draft mismatch");
                press(); stage = 6;
            } else if (stage == 6 && --tap <= 0) { release(); submitted = System.nanoTime(); stage = 7;
            } else if (stage == 7) {
                if (!(boolean) invoke(editor, "pending")) return;
                System.out.println("NATIVE EDIT PENDING: " + scenario); stage = 8;
            } else if (stage == 8) {
                Object result = invoke(client(), "editResult");
                if (scenario.equals("fault-late")) {
                    if (!(result != null && invoke(result, "status").toString().equals("UNKNOWN")) && !field(editor, "reason").equals("unknown")) return;
                    check(System.nanoTime() - submitted >= 9_500_000_000L, "Premature unknown outcome");
                    check((int) invoke(invoke(region(), "boundsForAnalysis"), "maxY") == 71, "Lost ack lost normal CUI");
                    waitFor("timeout", 9);
                } else if (scenario.equals("fault-revoked")) {
                    if (!call("status").toString().equals("DENIED")) return;
                    check(call("editContext") == null && invoke(client(), "editSelection") != null
                            && !(boolean) invoke(invoke(client(), "editSelection"), "ready"), "Denied state retained a grant or snapshot");
                    check((int) invoke(invoke(region(), "boundsForAnalysis"), "maxY") == 70, "Revoked delayed commit applied");
                    waitFor("verified", 10);
                } else {
                    if (result == null || !invoke(result, "status").toString().equals("APPLIED")) return;
                    check(System.nanoTime() - submitted >= 650_000_000L, "Artificial RTT was bypassed");
                    check((int) invoke(invoke(region(), "boundsForAnalysis"), "maxY") == 71, "Slow ack lost canonical CUI");
                    waitFor("verified", 10);
                }
            } else if (stage == 9 && acknowledged("timeout")) {
                Object result = invoke(client(), "editResult");
                check(result == null || !invoke(result, "status").toString().equals("APPLIED"), "Late ack resurrected success");
                Object shown = field(editor, "shown");
                if (shown == null || !(boolean) invoke(shown, "ready")) return;
                check((int) invoke(invoke(shown, "bounds"), "maxY") == 71, "Explicit refresh did not recover current state");
                waitFor("verified", 10);
            } else if (stage == 10 && acknowledged("verified")) {
                quietRequest = (long) field(client(), "requestId"); quiet = 40; stage = 11;
            } else if (stage == 11 && --quiet <= 0) {
                check((long) field(client(), "requestId") == quietRequest, "Quiet editor retried after fault");
                Object result = invoke(client(), "editResult");
                if (scenario.equals("fault-late")) check(result == null || !invoke(result, "status").toString().equals("APPLIED"), "Late ack changed outcome");
                enabled = false; adapter.focusForEditing(false);
                System.out.println("NATIVE EDIT FAULT PASS: " + scenario);
                waitFor("disconnect", 12);
            }
        } catch (Throwable ex) {
            finished = true; ex.printStackTrace();
            System.err.println("NATIVE PAPER BRIDGE FAIL: " + scenario + " stage=" + stage + " " + ex);
            adapter.stop();
        }
    }
    private Object region() throws Exception { return invoke(field(overlay, "selectionManager"), "primaryRegionForAnalysis"); }
    private Object client() throws Exception { return field(null, bridge, "CLIENT"); }
    private Object call(String name) throws Exception { return bridge.getMethod(name).invoke(null); }
    private static Object invoke(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static Object field(Object target, String name) throws Exception { return field(target, target.getClass(), name); }
    private static Object field(Object target, Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private void setEnum(String name, String value) throws Exception {
        var f = editor.getClass().getDeclaredField(name); f.setAccessible(true); f.set(editor, Enum.valueOf((Class) f.getType(), value));
    }
    private void press() throws Exception { adapter.editKey(field(editor, "editKey"), true); tap = 2; }
    private void release() throws Exception { adapter.editKey(field(editor, "editKey"), false); }
    private void waitFor(String phase, int next) { System.out.println("NATIVE EDIT WAIT: " + phase); stage = next; }
    private boolean acknowledged(String phase) throws Exception { return Files.readString(control).trim().equals(phase); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

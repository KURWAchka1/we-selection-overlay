package org.marshal.wesotest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Native callbacks and renderer hit tests against a real authoritative remote selection. */
public final class NativeEditGestureChecks {
    private final NativeBridgeChecks.Adapter adapter;
    private final NativeGestureInput input;
    private final Class<?> bridge;
    private final Path control = Path.of(System.getProperty("weso.native.control"));
    private final long deadline = System.nanoTime() + 300_000_000_000L;
    private Object editor, overlay, original, expected, previousShown, target;
    private Object[] faces, corners;
    private int stage, index, wait;
    private boolean enabled, finished, focused = true;
    private long quietRequest;
    private double[] cursor;
    private double yaw, pitch;
    private static final String[] CANCELS = {"escape-g", "escape-direct", "focus", "chat", "stale"};

    NativeEditGestureChecks(NativeBridgeChecks.Adapter adapter, Class<?> bridge) {
        this.adapter = adapter; this.bridge = bridge; input = adapter.gestureInput();
    }
    public void tick() {
        if (finished) return;
        try {
            check(System.nanoTime() - deadline < 0, "Timeout at stage=" + stage + " case=" + index);
            adapter.configure();
            if (editor == null) editor = NativeBridgeChecks.runtimeClass("client.selection.SelectionEditController").getMethod("instance").invoke(null);
            invoke(editor, "configure", enabled, 1, 8);
            if (enabled) adapter.focusForEditing(focused);
            if (stage == 90) {
                if (adapter.joined() || !call("status").toString().equals("DISCONNECTED")) return;
                check(!(boolean) invoke(client(), "pending") && call("editContext") == null, "Disconnect retained edit context");
                finished = true; System.out.println("NATIVE PAPER BRIDGE PASS: gestures"); adapter.stop(); return;
            }
            if (!adapter.joined()) return;
            if (overlay == null) { overlay = adapter.overlay(); if (overlay == null) return; }
            if (stage == 0) {
                if (!call("status").toString().equals("READY") || region() == null) return;
                check(call("selection") == null, "Idle negotiation read selection");
                System.out.println("NATIVE PAPER CUI PASS: gestures"); waitFor("setup", 1);
            } else if (stage == 1 && acknowledged("setup")) {
                enabled = true; input.grab(); waitFor("case-0", 2);
            } else if (stage == 2 && acknowledged("case-" + index)) {
                Object shown = field(editor, "shown");
                if (shown == null || shown == previousShown || !(boolean) invoke(shown, "ready")) return;
                original = invoke(shown, "bounds"); check(bounds(original).equals("10,64,20,15,70,27"), "Case reset differs");
                check(field(editor, "source").getClass().getSimpleName().equals("RemoteSelectionEditSource"), "Not the native remote source");
                if (faces == null) {
                    faces = Class.forName(original.getClass().getName() + "$Face").getEnumConstants();
                    corners = Class.forName(original.getClass().getName() + "$Corner").getEnumConstants();
                    check(faces.length == 6 && corners.length == 8, "Unexpected cuboid domain");
                    setEnum("mode", "RESIZE");
                }
                target = index < 6 ? faces[index] : corners[index < 22 ? (index - 6) % 8 : 7];
                double[] point = index >= 14 && index < 22 || index == 23 ? coordinates(invoke(original, "corner", target)) : new double[]{13, 67.5, 24};
                input.aim(point[0], point[1], point[2]); wait = 8; stage = 3;
            } else if (stage == 3 && --wait <= 0) {
                if (index == 27) { input.key(71, 1); stage = 30; return; }
                boolean direct = direct();
                if (!direct) {
                    tap(); check(active() && (boolean) invoke(editor, "capturesCamera") && !input.grabbed(), "G did not capture resize camera");
                    cursor = project(index < 6 ? invoke(original, "center", target) : invoke(original, "corner", target));
                    input.cursor(cursor[0], cursor[1]);
                }
                yaw = input.yaw(); pitch = input.pitch();
                input.button(1);
                check((boolean) invoke(editor, "dragging"), "Native hit failed: " + label());
                check(field(editor, index < 6 ? "locked" : "lockedCorner") == target, "Wrong native handle: " + label());
                check((boolean) invoke(editor, "hidesCrosshair"), "Drag retained crosshair");
                if (direct) {
                    check(!(boolean) invoke(editor, "capturesCamera") && input.grabbed(), "Direct corner froze camera");
                    input.key(340, 1); check(!(boolean) invoke(editor, "suppressSneak"), "Direct corner suppressed flight descent"); input.key(340, 0);
                    double[] point = coordinates(invoke(original, "corner", target)); input.aim(point[0] + 2, point[1] + 1, point[2]);
                } else {
                    input.cursor(cursor[0] + 80, cursor[1] - 96);
                    if (invoke(editor, "draft").equals(original)) input.cursor(cursor[0] - 120, cursor[1] + 100);
                    check(yaw == input.yaw() && pitch == input.pitch(), "Captured drag rotated camera");
                }
                wait = 8; stage = 4;
            } else if (stage == 4 && --wait <= 0) {
                expected = invoke(editor, "draft"); check(expected != null && !expected.equals(original), "Drag did not change draft: " + label());
                if (!direct()) check(yaw == input.yaw() && pitch == input.pitch(), "Mouse processing rotated the captured camera");
                if (index == 6) screenshot();
                if (direct()) {
                    // Small camera motion should remain a small free-corner displacement.
                    Object offset = invoke(invoke(expected, "corner", target), "subtract", invoke(original, "corner", target));
                    check(Arrays.stream(coordinates(offset)).anyMatch(v -> Math.abs(v) > 0 && Math.abs(v) < 8), "Direct corner used the fast step");
                }
                quietRequest = (long) field(client(), "requestId"); wait = 12; stage = 5;
            } else if (stage == 5 && --wait <= 0) {
                check(quietRequest == (long) field(client(), "requestId"), "Mouse drag polled the server");
                check(active() && !pending(), "Unsubmitted draft applied or disappeared");
                waitFor("draft-" + index, 6);
            } else if (stage == 6 && acknowledged("draft-" + index)) {
                if (index == 26) { waitFor("stale", 20); return; }
                if (index >= 22 && index < 26) {
                    if (index == 24) { focused = false; adapter.focusForEditing(false); }
                    else if (index == 25) input.chat(true);
                    else { input.key(256, 1); input.key(256, 0); }
                    wait = 8; stage = 21; return;
                }
                if (direct()) { input.button(0); stage = 8; }
                else if (index == 0) { tap(); input.button(0); stage = 8; }
                else {
                    input.button(0); check(active() && !pending(), "LMB release applied a G draft");
                    waitFor("released-" + index, 7);
                }
            } else if (stage == 7 && acknowledged("released-" + index)) { tap(); stage = 8;
            } else if (stage == 8) {
                if (pending() || active()) return;
                Object result = invoke(client(), "editResult");
                check(result != null && invoke(result, "status").toString().equals("APPLIED"), "Commit not APPLIED: " + label());
                if (!bounds(invoke(region(), "boundsForAnalysis")).equals(bounds(expected))) return;
                restored(); wait = 12; quietRequest = (long) field(client(), "requestId"); stage = 9;
            } else if (stage == 9 && --wait <= 0) {
                check(quietRequest == (long) field(client(), "requestId"), "Acknowledgment retried or recaptured"); finishCase(true);
            } else if (stage == 20 && acknowledged("stale")) { wait = 12; stage = 21;
            } else if (stage == 21 && --wait <= 0) {
                check(!active() && !pending() && !(boolean) invoke(editor, "hidesCrosshair"), "Cancellation retained editing");
                input.button(0); input.key(71, 0);
                if (index == 24) { check(!input.grabbed(), "Focus loss retained grab"); focused = true; adapter.focusForEditing(true); input.grab(); }
                if (index == 25) { check(input.screenOpen() && !input.grabbed(), "Chat was closed or retained grab"); input.chat(false); input.grab(); }
                restored(); check(bounds(invoke(region(), "boundsForAnalysis")).equals(bounds(original)), "Cancellation changed CUI"); finishCase(false);
            } else if (stage == 30) {
                if (!(boolean) invoke(editor, "radialOpen")) return;
                double x = (double) field(editor, "radialMouseX"), y = (double) field(editor, "radialMouseY");
                input.cursor(x + input.width() * 0.25, y); input.key(71, 0);
                check(invoke(editor, "mode").toString().equals("MOVE") && !(boolean) invoke(editor, "radialOpen"), "Radial did not choose Move");
                check(!active() && !(boolean) invoke(editor, "hidesCrosshair") && input.grabbed(), "Radial left input/crosshair captured");
                tap(); check(active() && !(boolean) invoke(editor, "capturesCamera") && input.grabbed(), "Move captured camera");
                input.button(1); check((boolean) invoke(editor, "dragging"), "Move grip native hit failed");
                for (int axis = 0; axis < 3; axis++) {
                    int key = new int[]{88, 89, 90}[axis]; input.key(key, 1); input.key(key, 0);
                    check((int) field(invoke(field(editor, "carry"), "wheelAxis"), "axis") == axis, "Axis key not owned");
                    input.scroll(axis == 0 ? 2 : axis == 1 ? 1 : -2);
                }
                expected = invoke(editor, "draft"); check(bounds(expected).equals("12,65,18,17,71,25"), "XYZ wheel translation differs");
                input.aim(16, 70, 24); wait = 10; stage = 31;
            } else if (stage == 31 && --wait <= 0) {
                check(invoke(editor, "draft").equals(expected), "Camera motion translated selection");
                screenshot();
                input.button(0); check(active() && !pending(), "Move mouse release committed");
                waitFor("released-" + index, 7);
            } else if (stage == 40 && acknowledged("result-" + index)) {
                previousShown = field(editor, "shown");
                if (++index == 28) {
                    enabled = false; adapter.focusForEditing(false);
                    System.out.println("NATIVE EDIT GESTURES PASS: 28 cases, 6 faces, 8 G corners, 8 free corners, 5 cancellation boundaries and radial XYZ Move");
                    waitFor("disconnect", 90);
                } else waitFor("case-" + index, 2);
            }
        } catch (Throwable ex) {
            finished = true; System.err.println("NATIVE PAPER BRIDGE FAIL: gestures stage=" + stage + " case=" + index);
            ex.printStackTrace(); adapter.stop();
        }
    }
    private void finishCase(boolean applied) throws Exception {
        System.out.println("NATIVE GESTURE RESULT: " + index + " " + label() + " " + (applied ? "APPLIED" : "CANCELLED") + " " + bounds(applied ? expected : original));
        waitFor("result-" + index, 40);
    }
    private void restored() throws Exception {
        check(!active() && !pending() && !(boolean) invoke(editor, "hidesCrosshair") && !(boolean) invoke(editor, "capturesCamera"), "Input/crosshair not restored");
        check(input.grabbed() && !input.attacking() && !input.screenOpen(), "Grab/attack/screen leak");
        check((int) field(editor, "blockedButtons") == 0 && (int) field(editor, "ownedAxisKeys") == 0, "Input ownership leaked");
    }
    private String label() { return index < 6 ? "face-" + faces[index] : index < 14 ? "corner-g-" + corners[index - 6] : index < 22 ? "corner-free-" + corners[index - 14] : index < 27 ? CANCELS[index - 22] : "radial-xyz-move"; }
    private boolean direct() { return index >= 14 && index < 22 || index == 23; }
    private boolean active() throws Exception { return (boolean) invoke(editor, "active"); }
    private boolean pending() throws Exception { return (boolean) invoke(editor, "pending"); }
    private void tap() { input.key(71, 1); input.key(71, 0); }
    private void screenshot() { input.key(291, 1); input.key(291, 0); }
    private double[] project(Object point) throws Exception {
        Object view = field(editor, "editView"); Object offset = invoke(point, "subtract", invoke(view, "eye"));
        double depth = (double) invoke(offset, "dot", invoke(view, "forward")), tangent = (double) invoke(view, "tangent"), aspect = (double) invoke(view, "aspect");
        check(depth > .05, "Handle behind camera");
        return new double[]{(.5 + (double) invoke(offset, "dot", invoke(view, "right")) / (2 * depth * tangent * aspect)) * input.width(),
                (.5 - (double) invoke(offset, "dot", invoke(view, "up")) / (2 * depth * tangent)) * input.height()};
    }
    private Object region() throws Exception { return invoke(field(overlay, "selectionManager"), "primaryRegionForAnalysis"); }
    private Object client() throws Exception { return field(null, bridge, "CLIENT"); }
    private Object call(String name) throws Exception { return bridge.getMethod(name).invoke(null); }
    private static double[] coordinates(Object point) throws Exception { return new double[]{(double) invoke(point, "x"), (double) invoke(point, "y"), (double) invoke(point, "z")}; }
    private static String bounds(Object b) throws Exception {
        var text = new StringBuilder();
        for (String name : new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}) { if (!text.isEmpty()) text.append(','); text.append(invoke(b, name)); }
        return text.toString();
    }
    private static Object invoke(Object owner, String name, Object... args) throws Exception {
        var method = Arrays.stream(owner.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length).findFirst().orElseThrow();
        method.setAccessible(true); return method.invoke(owner, args);
    }
    private static Object field(Object owner, String name) throws Exception { return field(owner, owner.getClass(), name); }
    private static Object field(Object owner, Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
    @SuppressWarnings({"rawtypes", "unchecked"}) private void setEnum(String name, String value) throws Exception { var f = editor.getClass().getDeclaredField(name); f.setAccessible(true); f.set(editor, Enum.valueOf((Class) f.getType(), value)); }
    private void waitFor(String phase, int next) { stage = next; System.out.println("NATIVE GESTURE WAIT: " + phase); }
    private boolean acknowledged(String phase) throws Exception { return Files.readString(control).trim().equals(phase); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

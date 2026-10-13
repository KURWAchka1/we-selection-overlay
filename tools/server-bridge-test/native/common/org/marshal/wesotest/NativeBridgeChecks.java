package org.marshal.wesotest;

import java.nio.file.Files;
import java.nio.file.Path;

/** Tick-owned observer; no synthetic network implementation or production mutation. */
public final class NativeBridgeChecks {
    public interface Adapter {
        boolean joined();
        void configure();
        Object overlay();
        Object player();
        Object world();
        Object connection();
        Object game();
        void focusForEditing(boolean focused);
        void editKey(Object key, boolean held);
        boolean respawnIfDead();
        void reconnect();
        void stop();
    }
    private final Adapter adapter;
    private final String scenario = System.getProperty("weso.native.scenario");
    private final Path control = Path.of(System.getProperty("weso.native.control"));
    private final Class<?> bridge;
    private final long deadline = System.nanoTime() + 180_000_000_000L;
    private int stage, quietTicks;
    private Object overlay;
    private boolean finished;
    private boolean reportedJoin;
    private Object previousPlayer, previousWorld, previousConnection, previousEpoch;
    private long contextStarted;
    private boolean respawnRequested;
    private Object editor, editCallback;
    private boolean exercisingEditing;
    private int tapTicks;
    private long quietRequest;

    public NativeBridgeChecks(Adapter adapter) {
        this.adapter = adapter;
        try { bridge = Class.forName("org.marshal.weaddon_lightposition.runtime.mc1_21_11.client.bridge.NativeServerBridge"); }
        catch (ClassNotFoundException ex) { throw new AssertionError(ex); }
    }

    public void tick() {
        if (finished) return;
        try {
            check(System.nanoTime() - deadline < 0, "Timeout at stage " + stage + ", status=" + status());
            adapter.configure();
            if (editor == null) editor = Class.forName("org.marshal.weaddon_lightposition.runtime.mc1_21_11.client.selection.SelectionEditController").getMethod("instance").invoke(null);
            editor.getClass().getMethod("configure", boolean.class, int.class, int.class).invoke(editor, exercisingEditing, 1, 8);
            if (exercisingEditing) adapter.focusForEditing(true);
            if (stage == 9) {
                if (adapter.joined() || !status().equals("DISCONNECTED")) return;
                check(call(null, "selection") == null, "Disconnect retained selection");
                check(!(boolean) invoke(field(null, bridge, "CLIENT"), "pending"), "Disconnect retained pending request");
                finished = true;
                System.out.println("NATIVE PAPER BRIDGE PASS: " + scenario);
                adapter.stop();
                return;
            }
            if (!adapter.joined() && stage != 23 && stage != 24 && stage != 25) return;
            if (!reportedJoin) { reportedJoin = true; System.out.println("NATIVE PAPER JOIN: " + scenario + " status=" + status()); }
            if (overlay == null) { overlay = adapter.overlay(); if (overlay == null) return; }
            if (stage == 0) {
                Object region = invoke(field(overlay, overlay.getClass(), "selectionManager"), "primaryRegionForAnalysis");
                if (region == null || (int) field(overlay, overlay.getClass(), "receivedSelectionPackets") < 3) return;
                String expected = scenario.equals("allowed") ? "READY" : scenario.equals("denied") ? "DENIED" : "UNAVAILABLE";
                if (!status().equals(expected)) return;
                check(call(null, "selection") == null, "Negotiation performed unsolicited read");
                verifyBounds(invoke(region, "boundsForAnalysis"));
                System.out.println("NATIVE PAPER CUI PASS: " + scenario);
                waitFor(scenario.equals("allowed") ? "initial" : "quiet", 1);
            } else if (stage == 1 && acknowledged(scenario.equals("allowed") ? "initial" : "quiet")) {
                call(null, "requestSelection");
                if (!scenario.equals("allowed")) { stage = 8; quietTicks = 30; }
                else stage = 2;
            } else if (stage == 2 && call(null, "selection") != null) {
                verifySelection("READY");
                waitFor("editsetup", 40);
            } else if (stage == 40 && acknowledged("editsetup")) {
                check(call(null, "editContext") != null, "Server did not grant edit capability on fresh startup");
                exercisingEditing = true; setEnum("mode", "RESIZE"); setEnum("selected", "UP"); stage = 41;
            } else if (stage == 41) {
                Object shown = field(editor, editor.getClass(), "shown");
                if (shown == null || !(boolean) invoke(shown, "ready")) return;
                check(field(editor, editor.getClass(), "source").getClass().getSimpleName().equals("RemoteSelectionEditSource"), "Wrong remote provider");
                pressEdit(); stage = 42;
            } else if (stage == 42 && --tapTicks <= 0) { releaseEdit(); stage = 43;
            } else if (stage == 43 && (boolean) invoke(editor, "active")) {
                check((boolean) editor.getClass().getMethod("scroll", double.class).invoke(editor, 1.0), "Native resize input was not consumed");
                check((int) invoke(invoke(editor, "draft"), "maxY") == 71, "G resize draft differs");
                pressEdit(); stage = 44;
            } else if (stage == 44 && --tapTicks <= 0) { releaseEdit(); stage = 45;
            } else if (stage == 45 && !(boolean) invoke(editor, "pending") && !(boolean) invoke(editor, "active")) {
                Object state = field(null, bridge, "CLIENT"), reply = invoke(state, "editResult");
                check(reply != null && invoke(reply, "status").toString().equals("APPLIED"), "Native G commit was not acknowledged");
                Object shown = field(editor, editor.getClass(), "shown");
                if (shown == null || !(boolean) invoke(shown, "ready")) return;
                Object b = invoke(shown, "bounds");
                Object shifted = b.getClass().getConstructor(int.class, int.class, int.class, int.class, int.class, int.class)
                        .newInstance(11, 64, 20, 16, 71, 27);
                Object source = field(editor, editor.getClass(), "source");
                java.lang.reflect.Method commit = java.util.Arrays.stream(source.getClass().getMethods()).filter(m -> m.getName().equals("commit")).findFirst().orElseThrow();
                editCallback = null;
                commit.invoke(source, adapter.game(), shown, shifted, null, null, (java.util.function.Consumer<Object>) value -> editCallback = value);
                stage = 46;
            } else if (stage == 46 && editCallback != null) {
                check((boolean) invoke(editCallback, "ready"), "Native whole-box shift failed");
                check((int) invoke(invoke(editCallback, "bounds"), "minX") == 11, "Native whole-box shift mismatch");
                quietRequest = (long) field(field(null, bridge, "CLIENT"), field(null, bridge, "CLIENT").getClass(), "requestId");
                quietTicks = 40; stage = 47;
            } else if (stage == 47 && --quietTicks <= 0) {
                check((long) field(field(null, bridge, "CLIENT"), field(null, bridge, "CLIENT").getClass(), "requestId") == quietRequest,
                        "Cached editor reads polled the server or restarted after its own CUI");
                Object region = invoke(field(overlay, overlay.getClass(), "selectionManager"), "primaryRegionForAnalysis");
                check((int) invoke(invoke(region, "boundsForAnalysis"), "minX") == 11, "Canonical CUI did not reach the overlay");
                waitFor("editquiet", 48);
            } else if (stage == 48 && acknowledged("editquiet")) { pressEdit(); stage = 49;
            } else if (stage == 49 && --tapTicks <= 0) { releaseEdit(); stage = 50;
            } else if (stage == 50 && (boolean) invoke(editor, "active")) {
                check((boolean) editor.getClass().getMethod("scroll", double.class).invoke(editor, 1.0), "Cancellation draft input missing");
                invoke(editor, "cancel"); check(!(boolean) invoke(editor, "active") && !(boolean) invoke(editor, "pending"), "Cancellation retained edit");
                exercisingEditing = false; adapter.focusForEditing(false); waitFor("editcancel", 51);
            } else if (stage == 51 && acknowledged("editcancel")) {
                System.out.println("NATIVE PAPER EDIT PASS: G resize, remote shift, cancellation, cached reads and canonical CUI");
                rememberContext(); waitFor("respawn", 12);
            } else if (stage == 12 && acknowledged("respawn")) {
                if (!respawnRequested) respawnRequested = adapter.respawnIfDead();
                if (adapter.player() != previousPlayer) {
                    check(adapter.world() == previousWorld, "Respawn unexpectedly changed world");
                    stage = 13;
                }
            } else if (stage == 13 && recoveredContext("same-world respawn")) {
                call(null, "requestSelection"); stage = 14;
            } else if (stage == 14 && call(null, "selection") != null) {
                verifySelection("READY"); rememberContext(); waitFor("nether", 15);
            } else if (stage == 15 && acknowledged("nether") && adapter.world() != previousWorld
                    && recoveredContext("Nether transition")) {
                call(null, "requestSelection"); stage = 16;
            } else if (stage == 16 && call(null, "selection") != null) {
                verifySelection("READY", 0, 255); rememberContext(); waitFor("overworld", 17);
            } else if (stage == 17 && acknowledged("overworld") && adapter.world() != previousWorld
                    && recoveredContext("Overworld return")) {
                call(null, "requestSelection"); stage = 18;
            } else if (stage == 18 && call(null, "selection") != null) {
                verifySelection("READY"); rememberContext(); waitFor("channeloff", 19);
            } else if (stage == 19 && acknowledged("channeloff")) {
                contextStarted = System.nanoTime(); stage = 20;
            } else if (stage == 20) {
                check(System.nanoTime() - contextStarted < 5_000_000_000L, "Channel loss retained state: " + status());
                if (!status().equals("UNAVAILABLE")) return;
                verifyCleared();
                check(adapter.connection() == previousConnection && adapter.world() == previousWorld
                        && adapter.player() == previousPlayer, "Channel loss changed gameplay context");
                call(null, "requestSelection"); quietTicks = 30; stage = 27;
            } else if (stage == 27 && --quietTicks <= 0) {
                verifyCleared(); waitFor("channelon", 21); contextStarted = System.nanoTime();
            } else if (stage == 21 && acknowledged("channelon") && recoveredContext("channel re-advertisement")) {
                call(null, "requestSelection"); stage = 22;
            } else if (stage == 22 && call(null, "selection") != null) {
                verifySelection("READY"); waitFor("empty", 3);
            } else if (stage == 3 && acknowledged("empty")) { call(null, "requestSelection"); stage = 4; }
            else if (stage == 4 && call(null, "selection") != null) { verifySelection("EMPTY"); waitFor("seed", 5); }
            else if (stage == 5 && acknowledged("seed")) { call(null, "requestSelection"); stage = 6; }
            else if (stage == 6 && call(null, "selection") != null) { verifySelection("READY"); waitFor("revoke", 7); }
            else if (stage == 7 && acknowledged("revoke")) {
                call(null, "requestSelection"); stage = 10;
            } else if (stage == 10 && status().equals("DENIED")) {
                verifySelection("DENIED"); waitFor("grant", 11);
            } else if (stage == 11 && acknowledged("grant")) {
                call(null, "requestSelection"); quietTicks = 30; stage = 8;
            } else if (stage == 8 && --quietTicks <= 0) {
                check(!status().equals("READY"), "Re-grant reused revoked negotiation");
                if (!scenario.equals("allowed")) check(call(null, "selection") == null, "Unavailable bridge disclosed selection");
                else verifySelection("DENIED");
                check(invoke(field(overlay, overlay.getClass(), "selectionManager"), "primaryRegionForAnalysis") != null, "Bridge denial broke CUI");
                if (scenario.equals("allowed")) {
                    rememberContext(); waitFor("reconnect", 23);
                } else if (scenario.equals("denied")) waitFor("bridgeoff", 35);
                else waitFor("disconnect", 9);
            } else if (stage == 23 && !adapter.joined() && status().equals("DISCONNECTED")) {
                verifyCleared(); waitFor("rejoin", 24);
            } else if (stage == 24 && acknowledged("rejoin")) {
                adapter.reconnect(); contextStarted = System.nanoTime(); stage = 25;
            } else if (stage == 25) {
                check(System.nanoTime() - contextStarted < 30_000_000_000L, "Same-process reconnect did not recover");
                if (!adapter.joined() || !status().equals("READY")) return;
                check(adapter.connection() != previousConnection && !previousEpoch.equals(epoch()), "Reconnect reused old context");
                check(call(null, "selection") == null, "Reconnect read metadata without a consumer");
                Object region = invoke(field(overlay, overlay.getClass(), "selectionManager"), "primaryRegionForAnalysis");
                if (region == null) return;
                verifyBounds(invoke(region, "boundsForAnalysis"));
                System.out.println("NATIVE PAPER CONTEXT PASS: same-process reconnect");
                waitFor("rejoined", 28);
            } else if (stage == 28 && acknowledged("rejoined")) {
                call(null, "requestSelection"); stage = 26;
            } else if (stage == 26 && call(null, "selection") != null) {
                verifySelection("READY"); rememberContext(); waitFor("bridgeoff", 29);
            } else if (stage == 29 && acknowledged("bridgeoff")) {
                contextStarted = System.nanoTime(); stage = 30;
            } else if (stage == 30) {
                check(System.nanoTime() - contextStarted < 5_000_000_000L, "Silent plugin stop retained state: " + status());
                if (!status().equals("UNAVAILABLE")) return;
                verifyCleared();
                check((boolean) field(field(null, bridge, "CLIENT"), field(null, bridge, "CLIENT").getClass(), "channelWasAvailable"),
                        "Plugin stop unexpectedly withdrew the remote channel");
                check(adapter.connection() == previousConnection && adapter.world() == previousWorld
                        && adapter.player() == previousPlayer, "Plugin stop changed gameplay context");
                call(null, "requestSelection"); quietTicks = 30; stage = 31;
            } else if (stage == 31 && --quietTicks <= 0) {
                check(status().equals("UNAVAILABLE"), "Stopped service retried negotiation");
                verifyCleared();
                check(invoke(field(overlay, overlay.getClass(), "selectionManager"), "primaryRegionForAnalysis") != null,
                        "Plugin stop broke CUI selection");
                System.out.println("NATIVE PAPER CONTEXT PASS: negotiated plugin stop");
                waitFor("bridgeon", 32);
            } else if (stage == 32 && acknowledged("bridgeon")) {
                contextStarted = System.nanoTime(); stage = 33;
            } else if (stage == 33 && recoveredContext("silent plugin restart")) {
                check(adapter.world() == previousWorld && adapter.player() == previousPlayer,
                        "Restart replaced gameplay context");
                quietTicks = 30; stage = 34;
            } else if (stage == 34 && --quietTicks <= 0) {
                check(status().equals("READY") && call(null, "selection") == null, "Restart read metadata without consumer");
                waitFor("restarted", 37);
            } else if (stage == 37 && acknowledged("restarted")) {
                call(null, "requestSelection"); stage = 38;
            } else if (stage == 38 && call(null, "selection") != null) {
                verifySelection("READY"); waitFor("disconnect", 9);
            } else if (stage == 35 && acknowledged("bridgeoff")) {
                check(status().equals("DENIED"), "Shutdown changed denied session");
                waitFor("bridgeon", 36);
            } else if (stage == 36 && acknowledged("bridgeon")) {
                quietTicks = 30; stage = 39;
            } else if (stage == 39 && --quietTicks <= 0) {
                check(status().equals("DENIED") && call(null, "selection") == null,
                        "Restart hint retried or bypassed denial");
                System.out.println("NATIVE PAPER CONTEXT PASS: denied silent restart stays quiet");
                waitFor("disconnect", 9);
            }
        } catch (Throwable ex) {
            finished = true;
            System.err.println("NATIVE PAPER BRIDGE FAIL: " + scenario + " stage=" + stage);
            ex.printStackTrace();
            adapter.stop();
        }
    }

    private void verifySelection(String expected) throws Exception {
        verifySelection(expected, -64, 319);
    }

    private void verifyCleared() throws Exception {
        Object state = field(null, bridge, "CLIENT");
        check(call(null, "selection") == null && epoch() == null && field(state, state.getClass(), "nonce") == null,
                "Unavailable context retained metadata/epoch/nonce");
        check(!(boolean) invoke(state, "pending") && (int) invoke(state, "capabilities") == 0,
                "Unavailable context retained work/capabilities");
    }

    private void verifySelection(String expected, int minY, int maxY) throws Exception {
        Object selection = call(null, "selection");
        check(invoke(selection, "status").toString().equals(expected), "Selection status mismatch: " + selection);
        Object bounds = invoke(selection, "bounds");
        if (!expected.equals("READY")) { check(bounds == null && (int) invoke(selection, "permissions") == 0, "Unavailable state leaked metadata"); return; }
        verifyBounds(bounds);
        check((int) invoke(selection, "minY") == minY && (int) invoke(selection, "maxY") == maxY && (int) invoke(selection, "permissions") == 7, "Height/permissions mismatch");
        System.out.println("NATIVE PAPER METADATA PASS: " + expected);
    }

    private void rememberContext() throws Exception {
        previousPlayer = adapter.player(); previousWorld = adapter.world();
        previousConnection = adapter.connection();
        previousEpoch = epoch(); contextStarted = System.nanoTime();
    }

    private Object epoch() throws Exception {
        Object state = field(null, bridge, "CLIENT");
        return field(state, state.getClass(), "epoch");
    }

    private boolean recoveredContext(String label) throws Exception {
        check(System.nanoTime() - contextStarted < 20_000_000_000L, "Context did not recover: " + label);
        if (!status().equals("READY") || previousEpoch.equals(epoch())) return false;
        check(adapter.connection() == previousConnection, "Context recovery reconnected instead of recovering: " + label);
        check(call(null, "selection") == null, "Context retained old selection: " + label);
        check(!(boolean) invoke(field(null, bridge, "CLIENT"), "pending"), "Context retained pending request");
        System.out.println("NATIVE PAPER CONTEXT PASS: " + label);
        return true;
    }

    private void verifyBounds(Object bounds) throws Exception {
        check(bounds != null, "Missing selection bounds");
        int[] expected = {10, 64, 20, 15, 70, 27};
        String[] names = {"minX", "minY", "minZ", "maxX", "maxY", "maxZ"};
        for (int i = 0; i < names.length; i++) check((int) invoke(bounds, names[i]) == expected[i], "Incorrect inclusive " + names[i]);
    }

    private String status() throws Exception { return call(null, "status").toString(); }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void setEnum(String name, String value) throws Exception {
        var field = editor.getClass().getDeclaredField(name); field.setAccessible(true);
        field.set(editor, Enum.valueOf((Class<? extends Enum>) field.getType(), value));
    }
    private void pressEdit() throws Exception { adapter.editKey(field(editor, editor.getClass(), "editKey"), true); tapTicks = 2; }
    private void releaseEdit() throws Exception { adapter.editKey(field(editor, editor.getClass(), "editKey"), false); }
    private void waitFor(String phase, int next) { stage = next; System.out.println("NATIVE PAPER WAIT: " + phase); }
    private boolean acknowledged(String phase) throws Exception { return Files.readString(control).trim().equals(phase); }
    private Object call(Object ignored, String name) throws Exception { return bridge.getMethod(name).invoke(null); }
    private static Object invoke(Object owner, String name) throws Exception { var method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
    private static Object field(Object owner, Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

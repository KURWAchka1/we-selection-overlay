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
        boolean respawnIfDead();
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
            if (stage == 9) {
                if (adapter.joined() || !status().equals("DISCONNECTED")) return;
                check(call(null, "selection") == null, "Disconnect retained selection");
                check(!(boolean) invoke(field(null, bridge, "CLIENT"), "pending"), "Disconnect retained pending request");
                finished = true;
                System.out.println("NATIVE PAPER BRIDGE PASS: " + scenario);
                adapter.stop();
                return;
            }
            if (!adapter.joined()) return;
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
    private void waitFor(String phase, int next) { stage = next; System.out.println("NATIVE PAPER WAIT: " + phase); }
    private boolean acknowledged(String phase) throws Exception { return Files.readString(control).trim().equals(phase); }
    private Object call(Object ignored, String name) throws Exception { return bridge.getMethod(name).invoke(null); }
    private static Object invoke(Object owner, String name) throws Exception { var method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
    private static Object field(Object owner, Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}

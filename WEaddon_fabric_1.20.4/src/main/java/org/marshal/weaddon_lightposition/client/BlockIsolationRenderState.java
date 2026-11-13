package org.marshal.weaddon_lightposition.client;

public final class BlockIsolationRenderState {
    public static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    private static final ThreadLocal<Boolean> FULLBRIGHT = new ThreadLocal<>();

    private BlockIsolationRenderState() { }

    public static boolean fullbrightActive() {
        return FULLBRIGHT.get() == Boolean.TRUE;
    }

    public static boolean enter(boolean active) {
        boolean previous = fullbrightActive();
        if (active != previous) {
            if (active) FULLBRIGHT.set(Boolean.TRUE);
            else FULLBRIGHT.remove();
        }
        return previous;
    }

    public static void restore(boolean previous) {
        enter(previous);
    }
}

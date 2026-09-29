package org.marshal.weaddon_lightposition.client;

public final class SodiumIsolationRenderState {
    private static final ThreadLocal<Boolean> FULLBRIGHT_ACTIVE = ThreadLocal.withInitial(() -> false);

    private SodiumIsolationRenderState() {
    }

    public static void setFullbrightActive(boolean active) {
        FULLBRIGHT_ACTIVE.set(active);
    }

    public static boolean fullbrightActive() {
        return Boolean.TRUE.equals(FULLBRIGHT_ACTIVE.get());
    }

    public static void clear() {
        FULLBRIGHT_ACTIVE.remove();
    }
}

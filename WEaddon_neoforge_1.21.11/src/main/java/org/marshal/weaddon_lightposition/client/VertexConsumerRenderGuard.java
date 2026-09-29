package org.marshal.weaddon_lightposition.client;

public final class VertexConsumerRenderGuard {
    private static final ThreadLocal<Boolean> PACKED_VERTEX_BYPASS = ThreadLocal.withInitial(() -> false);

    private VertexConsumerRenderGuard() {
    }

    public static boolean packedVertexBypassActive() {
        return Boolean.TRUE.equals(PACKED_VERTEX_BYPASS.get());
    }

    public static void enterPackedVertexBypass() {
        PACKED_VERTEX_BYPASS.set(true);
    }

    public static void exitPackedVertexBypass() {
        PACKED_VERTEX_BYPASS.remove();
    }
}

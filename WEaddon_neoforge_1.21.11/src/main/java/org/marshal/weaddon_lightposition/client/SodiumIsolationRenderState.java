package org.marshal.weaddon_lightposition.client;

import java.util.Arrays;

public final class SodiumIsolationRenderState {
    private static final ThreadLocal<RenderState> STATE = ThreadLocal.withInitial(RenderState::new);

    private SodiumIsolationRenderState() {
    }

    public static void setFullbrightActive(boolean active) {
        STATE.get().fullbrightActive = active;
    }

    public static void pushAlpha(float alphaFactor) {
        STATE.get().pushAlpha(clampAlpha(alphaFactor));
    }

    public static void popAlpha() {
        STATE.get().popAlpha();
    }

    public static boolean fullbrightActive() {
        return STATE.get().fullbrightActive;
    }

    public static float alphaFactor() {
        return STATE.get().alphaFactor();
    }

    public static int applyAlpha(int argb) {
        float factor = alphaFactor();
        if (factor >= 0.999F) {
            return argb;
        }

        int alpha = Math.round(((argb >>> 24) & 255) * factor);
        return argb & 0x00FFFFFF | (alpha & 255) << 24;
    }

    public static void clear() {
        STATE.remove();
    }

    private static float clampAlpha(float alphaFactor) {
        if (alphaFactor <= 0.0F) {
            return 0.0F;
        }
        if (alphaFactor >= 1.0F) {
            return 1.0F;
        }
        return alphaFactor;
    }

    private static final class RenderState {
        private float[] alphaStack = new float[4];
        private int alphaDepth;
        private boolean fullbrightActive;

        private void pushAlpha(float alpha) {
            if (alphaDepth == alphaStack.length) {
                alphaStack = Arrays.copyOf(alphaStack, alphaStack.length * 2);
            }
            alphaStack[alphaDepth++] = alpha;
        }

        private void popAlpha() {
            if (alphaDepth > 0) {
                alphaDepth--;
            }
        }

        private float alphaFactor() {
            return alphaDepth == 0 ? 1.0F : alphaStack[alphaDepth - 1];
        }
    }
}

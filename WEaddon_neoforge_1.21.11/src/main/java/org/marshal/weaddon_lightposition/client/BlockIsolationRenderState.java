package org.marshal.weaddon_lightposition.client;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.ModelBlockRenderer;

import java.util.Arrays;

public final class BlockIsolationRenderState {
    public static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    private static final ThreadLocal<RenderState> STATE = ThreadLocal.withInitial(RenderState::new);

    private BlockIsolationRenderState() {
    }

    public static void pushFullbright(boolean active) {
        STATE.get().pushFullbright(active);
    }

    public static void pushAlpha(float alphaFactor) {
        STATE.get().pushAlpha(clampAlpha(alphaFactor));
    }

    public static void popFullbright() {
        RenderState state = STATE.get();
        state.popFullbright();
        removeIfEmpty(state);
    }

    public static void popAlpha() {
        RenderState state = STATE.get();
        state.popAlpha();
        removeIfEmpty(state);
    }

    public static boolean fullbrightActive() {
        return STATE.get().fullbrightCount > 0;
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

    public static float applyAlpha(float alpha) {
        float factor = alphaFactor();
        return factor >= 0.999F ? alpha : clampAlpha(alpha * factor);
    }

    public static void forceFullbright(ModelBlockRenderer.CommonRenderStorage storage) {
        if (storage == null || !fullbrightActive()) {
            return;
        }

        Arrays.fill(storage.brightness, 1.0F);
        Arrays.fill(storage.lightmap, LightTexture.FULL_BRIGHT);
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

    private static void removeIfEmpty(RenderState state) {
        if (state.fullbrightDepth == 0 && state.alphaDepth == 0) {
            STATE.remove();
        }
    }

    private static final class RenderState {
        private boolean[] fullbrightStack = new boolean[4];
        private float[] alphaStack = new float[4];
        private int fullbrightDepth;
        private int fullbrightCount;
        private int alphaDepth;

        private void pushFullbright(boolean active) {
            if (fullbrightDepth == fullbrightStack.length) {
                fullbrightStack = Arrays.copyOf(fullbrightStack, fullbrightStack.length * 2);
            }
            fullbrightStack[fullbrightDepth++] = active;
            if (active) {
                fullbrightCount++;
            }
        }

        private void popFullbright() {
            if (fullbrightDepth == 0) {
                return;
            }
            if (fullbrightStack[--fullbrightDepth]) {
                fullbrightCount--;
            }
        }

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

package org.marshal.weaddon_lightposition.client;

import java.util.ArrayDeque;

public final class BlockIsolationRenderState {
    public static final int FULL_BRIGHT_LIGHT = 0x00F000F0;

    private static final ThreadLocal<ArrayDeque<Boolean>> FULLBRIGHT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private BlockIsolationRenderState() {
    }

    public static void pushFullbright(boolean active) {
        FULLBRIGHT_STACK.get().push(active);
    }

    public static void popFullbright() {
        ArrayDeque<Boolean> stack = FULLBRIGHT_STACK.get();
        if (stack.isEmpty()) {
            FULLBRIGHT_STACK.remove();
            return;
        }

        stack.pop();
        if (stack.isEmpty()) {
            FULLBRIGHT_STACK.remove();
        }
    }

    public static boolean fullbrightActive() {
        for (Boolean active : FULLBRIGHT_STACK.get()) {
            if (Boolean.TRUE.equals(active)) {
                return true;
            }
        }
        return false;
    }
}

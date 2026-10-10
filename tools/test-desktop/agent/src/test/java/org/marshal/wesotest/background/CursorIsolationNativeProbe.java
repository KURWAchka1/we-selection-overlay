package org.marshal.wesotest.background;

import java.lang.reflect.Method;

/** Reflection keeps the probe dependency-free; the test task supplies the real cached LWJGL JARs. */
public final class CursorIsolationNativeProbe {
    public static void main(String[] arguments) throws Exception {
        Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
        if (!"active".equals(System.getProperty("weso.test.cursorIsolation"))) throw new AssertionError("Cursor guard not installed");
        if (!(boolean) glfw.getMethod("glfwInit").invoke(null)) throw new AssertionError("GLFW initialization failed");
        long window = 0;
        try {
            Method hint = glfw.getMethod("glfwWindowHint", int.class, int.class);
            hint.invoke(null, 0x20004, 0); // Invisible window, including during the native preflight.
            hint.invoke(null, 0x20001, 0);
            hint.invoke(null, 0x22001, 0); // No graphics context needed for cursor safety.
            window = (long) glfw.getMethod("glfwCreateWindow", int.class, int.class, CharSequence.class, long.class, long.class)
                .invoke(null, 240, 120, "WESO cursor isolation probe", 0L, 0L);
            if (window == 0) throw new AssertionError("Native probe window failed");
            var mode = glfw.getMethod("glfwSetInputMode", long.class, int.class, int.class);
            for (int cursor : new int[]{0x34003, 0x34002, 0x34004, 0x34001}) {
                mode.invoke(null, window, 0x33001, cursor);
                int actual = (int) glfw.getMethod("glfwGetInputMode", long.class, int.class).invoke(null, window, 0x33001);
                if (actual != 0x34001) throw new AssertionError("Native cursor is not free: " + actual);
            }
            mode.invoke(null, window, 0x33005, 1);
            glfw.getMethod("glfwSetCursorPos", long.class, double.class, double.class).invoke(null, window, 10.0, 10.0);
            Thread.sleep(750);
            System.out.println("NATIVE CURSOR ISOLATION PASS: real GLFW cursor remains NORMAL despite capture, conceal, confine and warp attempts");
        } finally {
            if (window != 0) glfw.getMethod("glfwDestroyWindow", long.class).invoke(null, window);
            glfw.getMethod("glfwTerminate").invoke(null);
        }
    }
}

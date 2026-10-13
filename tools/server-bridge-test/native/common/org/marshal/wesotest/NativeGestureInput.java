package org.marshal.wesotest;

/** Test-only in-JVM input: never sends system input or activates a native window. */
public interface NativeGestureInput {
    void key(int code, int action);
    void button(int action);
    void cursor(double x, double y);
    void scroll(double amount);
    void aim(double x, double y, double z);
    int width();
    int height();
    double yaw();
    double pitch();
    boolean grabbed();
    boolean attacking();
    boolean screenOpen();
    void chat(boolean open);
    void grab();

    static void callback(Object owner, String name, Class<?>[] types, Object... args) {
        try {
            var method = owner.getClass().getDeclaredMethod(name, types);
            method.setAccessible(true);
            method.invoke(owner, args);
        } catch (ReflectiveOperationException ex) { throw new AssertionError("Pinned native callback changed: " + name, ex); }
    }
}

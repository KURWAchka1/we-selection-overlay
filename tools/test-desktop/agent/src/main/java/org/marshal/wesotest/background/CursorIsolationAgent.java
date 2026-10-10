package org.marshal.wesotest.background;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

/** Only loaded by the disposable test JVM, never packaged in the mod. */
public final class CursorIsolationAgent {
    private static final int CURSOR = 0x33001, RAW_MOUSE_MOTION = 0x33005;

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (System.getenv("WESO_TEST_DESKTOP") == null) throw new IllegalStateException("Cursor guard requires a private test desktop");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!"org/lwjgl/glfw/GLFW".equals(name)) return null;
                try {
                    byte[] guarded = guard(bytes);
                    System.setProperty("weso.test.cursorIsolation", "active");
                    System.out.println("CURSOR ISOLATION GUARD PASS: GLFW cursor capture/warping disabled in this test JVM");
                    return guarded;
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    // Instrumentation normally ignores a failed transformer. Do not launch an unguarded client.
                    Runtime.getRuntime().halt(78);
                    return null;
                }
            }
        });
    }

    static byte[] guard(byte[] source) {
        var reader = new ClassReader(source);
        var writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        int[] patched = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var target = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals("glfwSetCursorPos") && descriptor.equals("(JDD)V")) {
                    patched[0]++;
                    target.visitCode(); target.visitInsn(Opcodes.RETURN); target.visitMaxs(0, 6); target.visitEnd();
                    return null;
                }
                if (!name.equals("glfwSetInputMode") || !descriptor.equals("(JII)V")) return target;
                patched[0]++;
                return new MethodVisitor(Opcodes.ASM9, target) {
                    @Override public void visitCode() {
                        super.visitCode();
                        for (int mode : new int[]{CURSOR, RAW_MOUSE_MOTION}) {
                            var next = new Label();
                            visitVarInsn(Opcodes.ILOAD, 2); visitLdcInsn(mode);
                            visitJumpInsn(Opcodes.IF_ICMPNE, next); visitInsn(Opcodes.RETURN); visitLabel(next);
                        }
                    }
                };
            }
        }, 0);
        if (patched[0] != 2) throw new IllegalStateException("Unsupported GLFW cursor methods: " + patched[0]);
        return writer.toByteArray();
    }
}

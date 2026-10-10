package org.marshal.wesotest.background;

import org.objectweb.asm.*;

public final class CursorIsolationAgentChecks {
    public static void main(String[] arguments) throws Exception {
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "GuardProbe", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "nativeCalls", "I", null, null).visitEnd();
        for (String[] method : new String[][]{{"glfwSetInputMode", "(JII)V"}, {"glfwSetCursorPos", "(JDD)V"}}) {
            var body = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, method[0], method[1], null, null);
            body.visitCode(); body.visitFieldInsn(Opcodes.GETSTATIC, "GuardProbe", "nativeCalls", "I");
            body.visitInsn(Opcodes.ICONST_1); body.visitInsn(Opcodes.IADD);
            body.visitFieldInsn(Opcodes.PUTSTATIC, "GuardProbe", "nativeCalls", "I");
            body.visitInsn(Opcodes.RETURN); body.visitMaxs(0, 0); body.visitEnd();
        }
        writer.visitEnd();
        byte[] guarded = CursorIsolationAgent.guard(writer.toByteArray());
        Class<?> probe = new ClassLoader() { Class<?> load() { return defineClass("GuardProbe", guarded, 0, guarded.length); } }.load();
        var input = probe.getMethod("glfwSetInputMode", long.class, int.class, int.class);
        for (int value : new int[]{0x34001, 0x34002, 0x34003, 0x34004}) input.invoke(null, 1L, 0x33001, value);
        input.invoke(null, 1L, 0x33005, 1);
        probe.getMethod("glfwSetCursorPos", long.class, double.class, double.class).invoke(null, 1L, 12.0, 34.0);
        if (probe.getField("nativeCalls").getInt(null) != 0) throw new AssertionError("Native cursor operation escaped");
        input.invoke(null, 1L, 0x33002, 1);
        if (probe.getField("nativeCalls").getInt(null) != 1) throw new AssertionError("Unrelated input mode was suppressed");
        var unsupported = new ClassWriter(0);
        unsupported.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "Unsupported", null, "java/lang/Object", null); unsupported.visitEnd();
        try { CursorIsolationAgent.guard(unsupported.toByteArray()); throw new AssertionError("Missing cursor guards accepted"); }
        catch (IllegalStateException expected) { }
        System.out.println("CURSOR GUARD UNIT PASS: capture/hidden/confined/raw/warp blocked, other modes preserved, missing methods rejected");
    }
}

package org.marshal.wesotest.slices;

import java.lang.instrument.*;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

public final class DispatchAgent {
    private static final String PREFIX = "org/marshal/wesotest/slices/";
    private static final String CORE = "org/marshal/weaddon_lightposition/bridge/server/BridgeServer";
    private static final String PAPER = "org/marshal/weaddon_lightposition/bridge/paper/WorldEditOverlayBridgePlugin";
    public static void premain(String arguments, Instrumentation instrumentation) throws Exception {
        if (arguments != null && !arguments.isEmpty() || !Boolean.getBoolean("weso.bridge.fixture")
                || !Boolean.getBoolean("weso.bridge.sliceDiagnostics") || !Boolean.getBoolean("weso.bridge.dispatchDiagnostics")
                || Runtime.version().feature() != 25)
            throw new IllegalStateException("Explicit Java 25 dispatch fixture required");
        Path directory = Path.of(DispatchAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(directory.resolve("bridge-slice-bootstrap.jar").toFile()));
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(directory.resolve("bridge-dispatch-bootstrap.jar").toFile()));
        if (Class.forName(PREFIX.replace('/', '.') + "DispatchProbe", true, null).getClassLoader() != null)
            throw new IllegalStateException("Dispatch recorder is not bootstrap-visible");
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!CORE.equals(name) && !PAPER.equals(name)) return null;
                try {
                    if (CORE.equals(name)) bytes = SliceAgent.weave(bytes, Map.of("drain(Ljava/util/function/BiConsumer;)I", "drain"));
                    ClassReader reader = new ClassReader(bytes); ClassWriter writer = new ClassWriter(reader, 0);
                    int[] changed = {0};
                    reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                        @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                                @Override public void visitCode() {
                                    super.visitCode();
                                    if (PAPER.equals(name) && method.equals("onDisable") && descriptor.equals("()V")) {
                                        super.visitMethodInsn(Opcodes.INVOKESTATIC, PREFIX + "DispatchProbe", "close", "()V", false); changed[0]++;
                                    }
                                }
                                @Override public void visitMethodInsn(int op, String owner, String target, String desc, boolean iface) {
                                    if (CORE.equals(name) && owner.equals(PREFIX + "SliceProbe")) { owner = PREFIX + "DispatchProbe"; changed[0]++; }
                                    super.visitMethodInsn(op, owner, target, desc, iface);
                                }
                            };
                        }
                    }, 0);
                    if (changed[0] != (CORE.equals(name) ? 3 : 1)) throw new IllegalStateException("Wrong dispatch targets " + changed[0]);
                    System.out.println("WESO_DISPATCH_INSTRUMENTED " + name);
                    return writer.toByteArray();
                } catch (Throwable failure) { failure.printStackTrace(); Runtime.getRuntime().halt(79); return null; }
            }
        });
    }
}

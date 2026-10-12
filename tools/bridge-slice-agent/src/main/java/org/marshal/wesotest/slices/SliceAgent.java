package org.marshal.wesotest.slices;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.*;

/** Pinned diagnostic wrappers. Never attached to a user's JVM or distributed. */
public final class SliceAgent {
    private static final String PREFIX = "org/marshal/weaddon_lightposition/bridge/";
    private static final String PROBE = "org/marshal/wesotest/slices/SliceProbe";
    private static final String EVENT = "org/marshal/wesotest/slices/CallEvent";
    private static final String CORE = PREFIX + "server/BridgeServer";
    private static final String PAPER = PREFIX + "paper/WorldEditOverlayBridgePlugin";
    private static final Map<String, Map<String, String>> PLANS = Map.of(
            CORE, Map.of("drain(Ljava/util/function/BiConsumer;)I", "drain",
                    "enqueue(Ljava/util/UUID;[B)Z", "enqueue",
                    "process(Ljava/util/UUID;L" + CORE + "$Peer;[B)[B", "process"),
            PAPER, Map.of("sendReply(Ljava/util/UUID;[B)V", "send",
                    "onPluginMessageReceived(Ljava/lang/String;Lorg/bukkit/entity/Player;[B)V", "ingress"),
            PAPER + "$SelectionHost", Map.of("mayReadSelection(Ljava/util/UUID;)Z", "permission",
                    "readSelection(Ljava/util/UUID;)L" + PREFIX + "protocol/BridgeMessage$SelectionState;", "read"));

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (!Boolean.getBoolean("weso.bridge.fixture") || !Boolean.getBoolean("weso.bridge.sliceDiagnostics"))
            throw new IllegalStateException("Slice diagnostics require an explicitly opted-in fixture JVM");
        // Paper's isolated plugin loader cannot see system-agent helpers.
        // Only our three recorder classes, not ASM/the agent, enter bootstrap search.
        try {
            Path directory = Path.of(SliceAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(directory.resolve("bridge-slice-bootstrap.jar").toFile()));
            if (Class.forName("org.marshal.wesotest.slices.SliceProbe", true, null).getClassLoader() != null)
                throw new IllegalStateException("Diagnostic recorder is not bootstrap-visible");
        } catch (Exception failure) { throw new IllegalStateException("Cannot load diagnostic bootstrap helpers", failure); }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                Map<String, String> plan = PLANS.get(name);
                if (plan == null) return null;
                try {
                    byte[] result = weave(bytes, plan);
                    System.out.println("WESO_SLICE_INSTRUMENTED " + name + " methods=" + plan.size());
                    return result;
                } catch (Throwable failure) {
                    failure.printStackTrace();
                    // A failed transformer is otherwise ignored by Instrumentation.
                    Runtime.getRuntime().halt(79);
                    return null;
                }
            }
        });
    }

    static byte[] weave(byte[] bytes, Map<String, String> plan) {
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        Set<String> remaining = new HashSet<>(plan.keySet());
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String key = name + descriptor;
                if (name.startsWith("$weso$diagnostic$")) throw new IllegalStateException("Already instrumented");
                String operation = plan.get(key);
                if (operation == null) return super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!remaining.remove(key) || (access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNCHRONIZED)) != 0)
                    throw new IllegalStateException("Unsupported diagnostic target " + key);
                String original = "$weso$diagnostic$" + name;
                wrapper(writer, reader.getClassName(), access, name, original, descriptor, signature, exceptions, operation);
                return super.visitMethod((access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE,
                        original, descriptor, signature, exceptions);
            }
        }, 0);
        if (!remaining.isEmpty()) throw new IllegalStateException("Missing pinned diagnostic methods: " + remaining);
        return writer.toByteArray();
    }

    private static void wrapper(ClassWriter writer, String owner, int access, String name, String original,
                                String descriptor, String signature, String[] exceptions, String operation) {
        MethodVisitor m = writer.visitMethod(access, name, descriptor, signature, exceptions);
        Type[] arguments = Type.getArgumentTypes(descriptor);
        Type result = Type.getReturnType(descriptor);
        int eventSlot = 1;
        Object[] locals = new Object[arguments.length + 2]; locals[0] = owner;
        for (int i = 0; i < arguments.length; i++) {
            eventSlot += arguments[i].getSize();
            locals[i + 1] = frameType(arguments[i]);
        }
        locals[locals.length - 1] = EVENT;
        int resultSlot = eventSlot + 1;
        Label start = new Label(), end = new Label(), handler = new Label();
        m.visitCode();
        m.visitLdcInsn(operation); m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE, "begin", "(Ljava/lang/String;Ljava/lang/Object;)L" + EVENT + ";", false);
        m.visitVarInsn(Opcodes.ASTORE, eventSlot);
        m.visitTryCatchBlock(start, end, handler, null);
        m.visitLabel(start); m.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1;
        for (Type argument : arguments) {
            m.visitVarInsn(argument.getOpcode(Opcodes.ILOAD), slot); slot += argument.getSize();
        }
        m.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, original, descriptor, false);
        if (result.getSort() != Type.VOID) m.visitVarInsn(result.getOpcode(Opcodes.ISTORE), resultSlot);
        m.visitLabel(end);
        finish(m, eventSlot, false);
        if (result.getSort() != Type.VOID) m.visitVarInsn(result.getOpcode(Opcodes.ILOAD), resultSlot);
        m.visitInsn(result.getOpcode(Opcodes.IRETURN));
        m.visitLabel(handler);
        m.visitFrame(Opcodes.F_NEW, locals.length, locals, 1, new Object[]{"java/lang/Throwable"});
        m.visitVarInsn(Opcodes.ASTORE, resultSlot);
        finish(m, eventSlot, true);
        m.visitVarInsn(Opcodes.ALOAD, resultSlot); m.visitInsn(Opcodes.ATHROW);
        m.visitMaxs(0, 0); m.visitEnd();
    }

    private static void finish(MethodVisitor m, int slot, boolean failed) {
        m.visitVarInsn(Opcodes.ALOAD, slot); m.visitVarInsn(Opcodes.ALOAD, 0);
        m.visitInsn(failed ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC, PROBE, "end", "(L" + EVENT + ";Ljava/lang/Object;Z)V", false);
    }

    private static Object frameType(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> Opcodes.INTEGER;
            case Type.LONG -> Opcodes.LONG;
            case Type.DOUBLE -> Opcodes.DOUBLE;
            case Type.FLOAT -> Opcodes.FLOAT;
            case Type.ARRAY -> type.getDescriptor();
            case Type.OBJECT -> type.getInternalName();
            default -> throw new IllegalArgumentException("Unsupported argument");
        };
    }
}

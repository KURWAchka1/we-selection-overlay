package org.marshal.wesotest.slices;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.jar.JarFile;

/** Separate fixture observer; keep the existing nested agent and recorder byte-identical. */
public final class DrainAgent {
    private static final String CORE = "org/marshal/weaddon_lightposition/bridge/server/BridgeServer";

    public static void premain(String arguments, Instrumentation instrumentation) {
        if ((arguments != null && !arguments.isEmpty()) || !Boolean.getBoolean("weso.bridge.fixture")
                || !Boolean.getBoolean("weso.bridge.sliceDiagnostics") || !Boolean.getBoolean("weso.bridge.drainDiagnostics"))
            throw new IllegalStateException("Drain diagnostics require an explicitly opted-in fixture JVM");
        try {
            Path directory = Path.of(DrainAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(directory.resolve("bridge-slice-bootstrap.jar").toFile()));
            if (Class.forName("org.marshal.wesotest.slices.SliceProbe", true, null).getClassLoader() != null)
                throw new IllegalStateException("Diagnostic recorder is not bootstrap-visible");
        } catch (Exception failure) { throw new IllegalStateException("Cannot load diagnostic bootstrap helpers", failure); }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!CORE.equals(name)) return null;
                try {
                    byte[] result = SliceAgent.weave(bytes, Map.of("drain(Ljava/util/function/BiConsumer;)I", "drain"));
                    System.out.println("WESO_DRAIN_INSTRUMENTED " + name + " methods=1");
                    return result;
                } catch (Throwable failure) {
                    failure.printStackTrace(); Runtime.getRuntime().halt(79); return null;
                }
            }
        });
    }
}

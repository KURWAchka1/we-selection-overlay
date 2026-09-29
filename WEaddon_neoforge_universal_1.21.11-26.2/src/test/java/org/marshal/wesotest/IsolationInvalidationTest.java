package org.marshal.wesotest;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class IsolationInvalidationTest {
    @Test
    void everyRuntimeSchedulesOnlyTheAffectedSectionRange() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            for (String runtime : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
                String path = "org/marshal/weaddon_lightposition/runtime/" + runtime
                        + "/client/BlockIsolationController.class";
                var entry = jar.getEntry(path);
                assertNotNull(entry, path);
                var calls = new ArrayList<String>();
                try (var stream = jar.getInputStream(entry)) {
                    new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                            if (!name.equals("scheduleRerender")) return null;
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override
                                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                    assertNotEquals("invalidateCompiledGeometry", name, runtime);
                                    assertNotEquals("allChanged", name, runtime);
                                    if (descriptor.equals("(IIIIII)V")) calls.add(owner + "." + name);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                }
                assertEquals(1, calls.size(), runtime + " must schedule one local section range");
                if (runtime.equals("mc26_2")) {
                    assertTrue(calls.getFirst().endsWith("/LevelExtractor.setSectionRangeDirty"), calls.toString());
                } else if (runtime.equals("mc26_1")) {
                    assertTrue(calls.getFirst().endsWith("/LevelRenderer.setSectionRangeDirty"), calls.toString());
                }
            }
        }
    }
}

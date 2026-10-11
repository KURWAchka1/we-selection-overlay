package org.marshal.weaddon_lightposition.bootstrap;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class UniversalArtifactTest {
    @Test
    void optionalBridgeIsRelocatedAndContainsNoServerOrFixtureCode() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
                String base = "org/marshal/weaddon_lightposition/runtime/" + id + "/";
                for (String type : List.of("client/bridge/BridgePayload", "client/bridge/NativeServerBridge",
                        "bridge/client/BridgeClient", "bridge/protocol/BridgeCodec", "bridge/protocol/BridgeMessage"))
                    assertNotNull(jar.getEntry(base + type + ".class"), base + type);
            }
            assertFalse(jar.stream().anyMatch(e -> e.getName().contains("/bridge/server/")
                    || e.getName().contains("/bridge/paper/") || e.getName().contains("/bridge/fixture/")));
            assertNull(jar.getEntry("org/marshal/weaddon_lightposition/bridge/protocol/BridgeCodec.class"));
        }
    }

    @Test
    void editingHudAndCrosshairHooksAreRegisteredOncePerRuntime() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
                var registered = new java.util.concurrent.atomic.AtomicInteger();
                var events = new java.util.HashMap<String, Integer>();
                var entry = jar.getEntry("org/marshal/weaddon_lightposition/runtime/" + id + "/client/selection/SelectionEditController.class");
                assertNotNull(entry);
                try (var input = jar.getInputStream(entry)) {
                    new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (!name.equals("initialize")) return null;
                            return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                    if (owner.equals("net/neoforged/bus/api/IEventBus") && name.equals("addListener")) registered.incrementAndGet();
                                }
                                @Override public void visitInvokeDynamicInsn(String name, String descriptor, org.objectweb.asm.Handle bootstrap, Object... arguments) {
                                    for (Object argument : arguments) if (argument instanceof org.objectweb.asm.Type type
                                            && type.getSort() == org.objectweb.asm.Type.METHOD && type.getArgumentTypes().length == 1) {
                                        String parameter = type.getArgumentTypes()[0].getInternalName();
                                        if (parameter.startsWith("net/neoforged/neoforge/client/event/")) events.merge(parameter, 1, Integer::sum);
                                    }
                                }
                            };
                        }
                    }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
                }
                assertEquals(2, registered.get(), id + ": expected one HUD and one crosshair hook");
                assertEquals(java.util.Map.of("net/neoforged/neoforge/client/event/RenderGuiEvent$Post", 1,
                        "net/neoforged/neoforge/client/event/RenderGuiLayerEvent$Pre", 1), events, id);
            }
        }
    }
    @Test
    void selectionEditingHooksAreActiveInEveryRuntime() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            var counts = new java.util.HashMap<String, Integer>();
            try (var input = jar.getInputStream(jar.getEntry("org/marshal/weaddon_lightposition/bootstrap/UniversalMixinPlugin.class"))) {
                new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override public void visitLdcInsn(Object value) { if (value instanceof String text) counts.merge(text, 1, Integer::sum); }
                        };
                    }
                }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            }
            for (String hook : List.of("SelectionEditMouseMixin", "SelectionEditKeyboardMixin", "SelectionEditMovementMixin", "SelectionEditPlayerMixin")) {
                assertEquals(2, counts.getOrDefault(hook, 0), hook);
                for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) assertNotNull(jar.getEntry("org/marshal/weaddon_universal_mixins/" + id + "/" + hook + ".class"));
            }
            assertEquals(1, counts.getOrDefault("SelectionEditFovAccessor", 0));
        }
    }
    @Test
    void commandMixinIsRegisteredForEveryRuntime() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            var registered = new java.util.concurrent.atomic.AtomicInteger();
            try (var input = jar.getInputStream(jar.getEntry("org/marshal/weaddon_lightposition/bootstrap/UniversalMixinPlugin.class"))) {
                new org.objectweb.asm.ClassReader(input).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                    @Override public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new org.objectweb.asm.MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                            @Override public void visitLdcInsn(Object value) { if ("ChatCommandPreviewMixin".equals(value)) registered.incrementAndGet(); }
                        };
                    }
                }, org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            }
            assertEquals(2, registered.get(), "Legacy and modern runtime lists must register chat preview");
        }
    }
    @Test
    void uiResourcesAndNoticesAreSharedOnce() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            String base = "assets/worldedit_selection_overlay/";
            for (String name : List.of("font/ui.json", "font/manrope.ttf", "font/manrope-ofl.txt",
                    "shaders/core/ui_surface.fsh", "shaders/core/ui_spectrum.fsh",
                    "textures/gui/ui/surface.png", "textures/gui/ui/lucide-license.txt",
                    "textures/gui/ui/radial-left.png", "textures/gui/ui/radial-right.png",
                    "textures/gui/ui/radial-dot.png", "textures/gui/ui/move.png", "textures/gui/ui/scaling.png")) {
                assertEquals(1L, jar.stream().filter(entry -> entry.getName().equals(base + name)).count(), name);
            }
            assertEquals(14L, jar.stream().filter(entry -> entry.getName().startsWith(base + "textures/gui/ui/")
                    && entry.getName().endsWith(".png") && !entry.getName().endsWith("/surface.png")).count());
        }
    }

    @Test
    void editingToolsAndSilentDeselectArePackagedForEveryRuntime() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
                String base = "org/marshal/weaddon_lightposition/runtime/" + id + "/";
                for (String tool : List.of("FaceDrag", "SelectionEditKey", "SelectionEditMode"))
                    assertNotNull(jar.getEntry(base + "selection/" + tool + ".class"), id + ": " + tool);
                try (var input = jar.getInputStream(jar.getEntry(base + "client/WorldEditPointOverlayClient.class"))) {
                    String bytecode = new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
                    assertFalse(bytecode.contains("message.worldedit_selection_overlay.deselect_sent"), id);
                }
            }
        }
    }

    private Config metadata(JarFile jar) throws Exception {
        var entry = jar.getEntry("META-INF/neoforge.mods.toml");
        assertNotNull(entry);
        try (var reader = new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return new TomlParser().parse(reader);
        }
    }

    @Test
    void versionIsConsistentAcrossInputsAndUniversalJar() throws Exception {
        var paths = new java.util.ArrayList<>(List.of(System.getProperty("runtime.inputs").split(java.util.regex.Pattern.quote(File.pathSeparator))));
        paths.add(System.getProperty("universal.jar"));
        for (String path : paths) {
            try (var jar = new JarFile(path)) {
                List<Config> mods = metadata(jar).get("mods");
                assertEquals(1, mods.size(), path);
                assertEquals("worldedit_selection_overlay", mods.getFirst().get("modId"));
                assertEquals(System.getProperty("release.version"), mods.getFirst().get("version"), path);
                assertEquals("GPL-3.0-or-later", metadata(jar).get("license"), path);
            }
        }
    }

    @Test
    void onlyModernRuntimeClassesNeedJava25() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            assertNotNull(jar.getEntry("META-INF/LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/THIRD_PARTY.md"));
            assertNotNull(jar.getEntry("META-INF/licenses/WorldEditCUI-Protocol-LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/licenses/WorldEditCUI-Protocol-HEADER.txt"));
            assertNull(jar.getEntry("fabric.mod.json"));
            assertNull(jar.getEntry("META-INF/jarjar/metadata.json"));
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("net/fabricmc/")),
                    "NeoForge must not bundle Fabric Loader through protocol transitive dependencies");
            assertNull(jar.getEntry("fabric-installer.json"));
            assertTrue(new File(System.getProperty("universal.jar")).length() < 2 * 1024 * 1024,
                    "Unexpected release footprint; inspect bundled dependencies");
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("org/enginehub/")),
                    "Private fallbacks must not collide with WorldEdit's shared protocol module");
            assertFalse(jar.stream().anyMatch(entry -> entry.getName().startsWith("architectury_inject_")),
                    "Public protocol service injection is not part of a private packet codec");
            for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
                assertNotNull(jar.getEntry("org/marshal/weaddon_lightposition/runtime/" + id
                        + "/internal/cui/protocol/CUIPacket.class"));
                assertNotNull(jar.getEntry("org/marshal/weaddon_lightposition/runtime/" + id
                        + "/client/WorldEditSelectionOverlayClient.class"));
                assertNull(jar.getEntry("org/marshal/weaddon_lightposition/runtime/" + id
                        + "/WorldEditSelectionOverlayMod.class"));
            }
            for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                try (var data = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xCAFEBABE, data.readInt());
                    data.readUnsignedShort();
                    int major = data.readUnsignedShort();
                    boolean modern = entry.getName().contains("/mc26_1/") || entry.getName().contains("/mc26_2/");
                    assertTrue(major <= (modern ? 69 : 65), entry.getName() + ": " + major);
                }
            }
        }
    }
}

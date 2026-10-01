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
    void uiResourcesAndNoticesAreSharedOnce() throws Exception {
        try (var jar = new JarFile(System.getProperty("universal.jar"))) {
            String base = "assets/worldedit_selection_overlay/";
            for (String name : List.of("font/ui.json", "font/manrope.ttf", "font/manrope-ofl.txt",
                    "shaders/core/ui_surface.fsh", "shaders/core/ui_spectrum.fsh",
                    "textures/gui/ui/surface.png", "textures/gui/ui/lucide-license.txt")) {
                assertEquals(1L, jar.stream().filter(entry -> entry.getName().equals(base + name)).count(), name);
            }
            assertEquals(9L, jar.stream().filter(entry -> entry.getName().startsWith(base + "textures/gui/ui/")
                    && entry.getName().endsWith(".png") && !entry.getName().endsWith("/surface.png")).count());
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
            for (String id : List.of("mc1_21_11", "mc26_1", "mc26_2")) {
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

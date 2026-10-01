package org.marshal.weaddon_lightposition.universal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class UniversalArtifactTest {
    @Test
    void uiResourcesAndNoticesAreSharedOnce() throws Exception {
        try (var jar = artifact()) {
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

    private JarFile artifact() throws Exception {
        return new JarFile(System.getProperty("universal.jar"));
    }

    private JsonObject json(JarFile jar, String name) throws Exception {
        assertNotNull(jar.getEntry(name), name);
        try (var reader = new InputStreamReader(jar.getInputStream(jar.getEntry(name)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    @Test
    void selectsOnlySupportedGameFamilies() {
        assertEquals(RuntimeTarget.MC_1_21_11, RuntimeTarget.forMinecraft("1.21.11"));
        for (String version : List.of("26.1", "26.1.1", "26.1.2")) {
            assertEquals(RuntimeTarget.MC_26_1, RuntimeTarget.forMinecraft(version));
        }
        assertEquals(RuntimeTarget.MC_26_2, RuntimeTarget.forMinecraft("26.2"));
        for (String version : List.of("1.21.1", "1.21.10", "26.3", "26.10", "26.1-rc-1")) {
            assertThrows(IllegalStateException.class, () -> RuntimeTarget.forMinecraft(version));
        }
    }

    @Test
    void hasOneModAndNoRuntimeDependenciesOrBundledFabricApi() throws Exception {
        try (var jar = artifact()) {
            var metadata = json(jar, "fabric.mod.json");
            assertEquals(System.getProperty("release.version"), metadata.get("version").getAsString());
            assertEquals("GPL-3.0-or-later", metadata.get("license").getAsString());
            assertNotNull(jar.getEntry("META-INF/LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/THIRD_PARTY.md"));
            assertNotNull(jar.getEntry("META-INF/licenses/WorldEditCUI-Protocol-LICENSE.txt"));
            assertNotNull(jar.getEntry("META-INF/licenses/WorldEditCUI-Protocol-HEADER.txt"));
            var dependencies = metadata.getAsJsonObject("depends");
            assertFalse(dependencies.keySet().stream().anyMatch(id -> id.contains("runtime")));
            assertTrue(dependencies.has("worldeditcui_protocol"));
            assertEquals(3, metadata.getAsJsonArray("jars").size());
            assertFalse(jar.stream().anyMatch(e -> e.getName().startsWith("net/fabricmc/fabric/")));
            assertFalse(jar.stream().anyMatch(e -> e.getName().startsWith("org/enginehub/")));
            for (var entries : metadata.getAsJsonObject("entrypoints").entrySet()) {
                for (var entry : entries.getValue().getAsJsonArray()) {
                    assertNotNull(jar.getEntry(entry.getAsString().replace('.', '/') + ".class"));
                }
            }
        }
    }

    @Test
    void protocolCandidatesAreMutuallyExclusiveAndKeepPublicApi() throws Exception {
        try (var jar = artifact()) {
            List<JsonObject> protocols = new ArrayList<>();
            for (var target : RuntimeTarget.values()) {
                var entry = jar.getEntry("META-INF/jars/worldeditcui-protocol-" + target.id() + ".jar");
                assertNotNull(entry);
                boolean publicPacket = false;
                try (var zip = new ZipInputStream(jar.getInputStream(entry))) {
                    for (var nested = zip.getNextEntry(); nested != null; nested = zip.getNextEntry()) {
                        if (nested.getName().equals("fabric.mod.json")) {
                            protocols.add(JsonParser.parseString(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                        }
                        publicPacket |= nested.getName().equals("org/enginehub/worldeditcui/protocol/CUIPacket.class");
                    }
                }
                assertTrue(publicPacket);
            }
            assertEquals(3, protocols.size());
            for (String game : List.of("1.21.11", "26.1", "26.1.1", "26.1.2", "26.2")) {
                int matching = 0;
                for (var protocol : protocols) {
                    assertEquals("worldeditcui_protocol", protocol.get("id").getAsString());
                    var deps = protocol.getAsJsonObject("depends");
                    if (VersionPredicate.parse(deps.get("minecraft").getAsString()).test(Version.parse(game))) {
                        matching++;
                        assertTrue(VersionPredicate.parse(deps.get("java").getAsString())
                                .test(Version.parse(game.equals("1.21.11") ? "21" : "25")));
                    }
                }
                assertEquals(1, matching, game);
            }
        }
    }

    @Test
    void inputVersionsAndPackagedMixinsMatchCurrentArtifacts() throws Exception {
        String[] inputs = System.getProperty("runtime.inputs").split(java.util.regex.Pattern.quote(File.pathSeparator));
        try (var jar = artifact()) {
            for (int i = 0; i < inputs.length; i++) {
                try (var input = new JarFile(inputs[i])) {
                    assertEquals(System.getProperty("release.version"), json(input, "fabric.mod.json").get("version").getAsString());
                    var config = json(input, "worldedit_selection_overlay.mixins.json");
                    for (var mixin : config.getAsJsonArray("client")) {
                        String name = "org/marshal/weaddon_universal_mixins/" + RuntimeTarget.values()[i].id()
                                + "/" + mixin.getAsString().replace('.', '/') + ".class";
                        assertNotNull(jar.getEntry(name), name);
                    }
                }
            }
        }
    }

    @Test
    void bootstrapAndOldRuntimeRemainJava21Compatible() throws Exception {
        try (var jar = artifact()) {
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

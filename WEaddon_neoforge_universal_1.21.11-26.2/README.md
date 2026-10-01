# WorldEdit Selection Overlay: Universal NeoForge

One NeoForge JAR for Minecraft 1.21.11, 26.1.x, and 26.2. Fabric has a separate
universal JAR. Install only the artifact for your loader.

Use Java 21 for Minecraft 1.21.11 and Java 25 for 26.x, with the corresponding
NeoForge version. Fabric API is not required on NeoForge.

The bootstrap and Minecraft 1.21.11 implementation remain Java 21 bytecode.
The 26.x implementations are loaded only when their Minecraft version is active.
Build inputs come from current `build/libs` outputs, not archived releases.
`neoforge.mods.toml` gets the version from `gradle.properties` automatically.

Run the sibling Fabric universal project's `tools/build-release.ps1` to build all
six source projects and both universal artifacts in dependency order.
`build` checks metadata and bytecode before archiving to `libs/1.8.0`.

The main mod exports no EngineHub packages. CUI uses the installed official
protocol handler when available, or a privately relocated version-matched
codec otherwise. This keeps WorldEdit's nested CUI module from colliding with
the universal mod under JPMS. The fallback is optional client networking, not
a replacement server protocol. Artifact tests enforce the package boundary.

Smoke test the packaged JAR with Java 21:

```powershell
.\gradlew.bat runSmokeClient
```

For Java 25 / NeoForge 26.x:

```powershell
.\gradlew.bat runSmokeClient '-Pneo_version=26.1.2.75' '-Psmoke_java_version=25'
.\gradlew.bat runSmokeClient '-Pneo_version=26.2.0.67' '-Psmoke_java_version=25'
```

After testing with an overridden NeoForge version, rebuild with the default
Java 21 / NeoForge 21.11.42 configuration before distributing the artifact.
A successful startup does not replace in-world visual testing with WorldEdit,
Sodium, and other optional mods.

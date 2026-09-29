# Verification

Current source verification: 2026-09-29. The test worlds and logs are local generated data and are not committed.

## Release build

The root README build command completed for all six runtimes and both universal packagers. Packager tests checked metadata, version selection, Java bytecode boundaries, and bundled license notices. The resulting Fabric and NeoForge JARs were each under 1 MiB.

## In-world isolation regression

The packaged universal JAR, not the version-specific source module, was loaded for each run. The test fixture selects diamond and gold blocks inside stone, switches isolation eight times, and checks:

- Unrelated chunk meshes and the world's section/view area remain unchanged across toggles.
- Each selected block exposes the face touching a hidden block; unselected blocks do not claim that face.
- Selected block vertices use full light and no ambient or directional shading. Fabric checks vanilla smooth/flat and Indigo paths; NeoForge checks vanilla with ambient occlusion on and off.
- Screenshots are captured for visual inspection.

| Loader | Minecraft | Result |
| --- | --- | --- |
| Fabric | 1.21.11 | Passed |
| Fabric | 26.1 | Passed |
| Fabric | 26.2 | Passed |
| NeoForge | 1.21.11 | Passed |
| NeoForge | 26.1.2 | Passed |
| NeoForge | 26.2 | Passed |

The 1.21.11 screenshots confirm that non-selected blocks inside the selection are visually hidden and both selected blocks remain visible when chosen. The WorldEdit command warnings in these isolated tests are expected because WorldEdit is intentionally absent.

## Reproduction commands

Build first, then run the Fabric game tests from each Fabric runtime directory:

```powershell
.\gradlew.bat -I ..\WEaddon_fabric_universal_1.21.11-26.2\tools\isolation-test.init.gradle testUniversalIsolation --no-daemon --console=plain
```

Run NeoForge game tests from the NeoForge universal packager:

```powershell
.\gradlew.bat -I tools\isolation-test.init.gradle runIsolationClient --no-daemon --console=plain
.\gradlew.bat -I tools\isolation-test.init.gradle runIsolationClient '-Pneo_version=26.1.2.75' '-Psmoke_java_version=25' --no-daemon --console=plain
.\gradlew.bat -I tools\isolation-test.init.gradle runIsolationClient '-Pneo_version=26.2.0.67' '-Psmoke_java_version=25' --no-daemon --console=plain
```

Set `JAVA_HOME` to JDK 21 for 1.21.11 and JDK 25 for 26.x. Run the NeoForge default build again after using an overridden version.

## Remaining coverage

This suite does not yet exercise Sodium, Iris/shaders, LabyMod, a real local WorldEdit selection, remote CUI, or clipboard command behavior. These require separate compatibility runs before release claims. It also does not replace human visual review at different GUI scales.

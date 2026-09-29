# Historical Packaged Startup Verification

Date: 2026-08-28. This records the initial packaged-startup campaign. For current in-world isolation verification and build commands, see the repository root `TESTING.md`. Tests use isolated project run directories, not the user's modpack.

## Packaged Client Startup

| Loader | Minecraft | Loader version | Java | WorldEdit | Result |
| --- | --- | --- | --- | --- | --- |
| Fabric | 1.21.11 | 0.18.4 | 21 | Absent | Startup, assets, clean shutdown passed |
| Fabric | 1.21.11 | 0.18.4 | 21 | Release 7.4.2 | Startup and clipboard API initialization passed |
| Fabric + HPShow 2.0.0 | 1.21.11 | 0.19.3 | 21 | Release 7.4.2 | Correct HPShow runtime selected; startup passed |
| Fabric | 26.1 | 0.19.3 | 25 | Absent | Startup, assets, clean shutdown passed |
| Fabric | 26.2 | 0.19.3 | 25 | Absent | Startup, assets, clean shutdown passed |
| NeoForge | 1.21.11 | 21.11.42 | 21 | Absent | Startup, assets, clean shutdown passed |
| NeoForge | 26.1.2 | 26.1.2.75 | 25 | Absent | Startup, assets, clean shutdown passed |
| NeoForge | 26.2 | 26.2.0.67 | 25 | Absent | Startup, assets, clean shutdown passed |

Fabric uses Loom's production client task and the actual distributed JAR.
NeoForge uses ModDevGradle's client runtime with the actual distributed JAR,
not version-specific mod classes from sourceSets.main.

Fabric 1.21.11 correctly selected CUI 4.0.2. The 26.x runs selected 4.0.3.
With WorldEdit installed, only one public CUI protocol was loaded and the overlay
reported `WorldEdit clipboard preview source enabled.`

Logs are in each source project's `run-universal-production/logs`, or in the
NeoForge universal project's `run-smoke-<loader-version>/logs`.

## Automated Artifact Checks

Five Fabric tests cover game-family selection, root metadata, entrypoints,
protocol predicates and public API, runtime version consistency, mixin presence,
and Java bytecode boundaries. Two NeoForge tests cover metadata consistency,
runtime entrypoints and Java bytecode boundaries. Test reports are under each
universal project's `build/reports/tests/test`.

## Limits And Test Setup Findings

- This is a startup/packaging check, not an in-world visual, isolation, clipboard
  command, shader, Sodium, or LabyMod test. Those workflows still need game testing.
- Fabric 26.1.1 and 26.1.2 selector predicates were tested, but separate Fabric
  client launches on those two patch versions were not performed in this run.
- Offline test accounts log expected Realms/authentication errors. Minecraft 26.2
  also logged Windows performance-counter warnings; neither prevented startup.
- An initial optional WorldEdit smoke test used an unbundled Maven development
  artifact and failed because WorldEdit core classes were missing. The test setup
  was corrected to use the published Modrinth JAR and rerun successfully.
- A NeoForge 26.x smoke configuration initially requested Java 21 dependency
  variants. Dependency selection now follows the test JVM while emitted bootstrap
  bytecode remains Java 21; both 26.x smoke runs then passed.
- The user's incompatible Fabric API 26.2 was not installed on 1.21.11 or silently
  overridden. A separate test with the existing HPShow 2.0.0 universal JAR selected
  `hpshow_runtime_mc1_21_11` correctly. HPShow did not need a code change to resolve
  the originally reported runtime mismatch once the other dependencies matched.

The original artifact fingerprints are omitted because subsequent source changes
make them obsolete. Hash the exact JAR being distributed after the final build.

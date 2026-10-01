# Verification

Current source verification: 1.8.0, 2026-10-02. The test worlds, generated
fixtures, screenshots, and logs are local data and are not committed.

## Release build

The root README build command completed for all six runtimes and both universal
packagers. Packager tests checked metadata, version selection, Java bytecode
boundaries, bundled license notices, and registration of the chat-preview mixin
for every runtime. Each universal JAR is approximately 1.1 MiB; neither bundles
WorldEdit nor Fabric API.
The archived release outputs are 1,144,536 bytes (Fabric) and 1,117,219 bytes
(NeoForge), under each packager's `libs/1.8.0/` directory.
The current tests also verify that UI assets/notices are shared once. JAR and
runtime input files are declared test inputs so changed binaries invalidate
previous test results.
NeoForge tests additionally reject exported EngineHub and Architectury injection
packages, and check the private official CUI codecs. Actual WorldEdit launches
exercise its installed protocol; no-WorldEdit launches exercise the optional
private-codec fallback without duplicate channel registration or split packages.

## Live command preview

The actual packaged universal artifacts were loaded with these production
WorldEdit distributions, not just compiled against development API JARs:

| Loader | Minecraft | WorldEdit | Result |
| --- | --- | --- | --- |
| Fabric | 1.21.11 | 7.4.2 | Passed |
| Fabric | 26.1 | 7.4.3 | Passed |
| Fabric | 26.2 | 7.4.5 | Passed |
| NeoForge | 1.21.11 | 7.4.2 | Passed |
| NeoForge | 26.1.2 | 7.4.3 | Passed |
| NeoForge | 26.2 | 7.4.5 | Passed |

All six runs check live chat calculation, unchanged source blocks before Enter,
stable weighted mixtures, textured ghost geometry, default-off clipboard preview,
temporary command priority without changing its preference or cached snapshot,
Escape restoration, and normal WorldEdit execution on Enter. The NeoForge runs
also exercise the real WorldEdit CUI handshake and native clipboard adapters.

The three Fabric runs additionally perform 40 deterministic comparisons per
runtime against real WorldEdit EditSession results: eight drafts across cuboid,
ellipsoid, cylinder, polygon, and convex regions. These include walls, faces,
outline, overlay, replace with an omitted mask, and an explicit block state.
WorldEdit history is checked before/after preview, and rapid native chat edits
must immediately discard obsolete geometry.

Their lifecycle tests cover global mask replacement, source block changes with
an unchanged draft, selection changes without a CUI notification, F8 in chat,
unloaded selections, scan/change limits, unsupported flags, invalid patterns,
no-op changes, and removal markers. The shared simulation is the same domain
implementation on NeoForge, but the full 40-case comparison/lifecycle matrix
has only been executed on Fabric; it is not claimed as six equivalent suites.

Screenshot review and a separate pixel check verify actual diamond textures,
not only a nonempty geometry buffer. All six set-preview screenshots pass
the cyan surface test (more than 36,000 matching pixels in the fixed crop).
The check also caught incorrect packed UV decoding and Minecraft's hurt-overlay
UV before release; production now uses the native UV helpers and NO_OVERLAY.

```powershell
.\tools\test-command-matrix.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
.\tools\check-command-pixels.ps1
```

Optional `-Only fabric-26.2` selects a focused rerun. Logs are written to
`build/command-validation/`. NeoForge command fixtures reuse a disposable copy
of the corresponding isolation world; fixture settings are reset before each
launch to verify defaults. No player save or normal Minecraft config is used.

## In-world isolation regression

The packaged universal JAR, not the version-specific source module, was loaded for each run. The test fixture selects diamond and gold blocks inside stone, switches isolation eight times, and checks:

- Unrelated chunk meshes and the world's section/view area remain unchanged across toggles.
- Each selected block exposes the face touching a hidden block; unselected blocks do not claim that face.
- Selected block vertices use full light and no ambient or directional shading. Fabric checks vanilla smooth/flat and Indigo paths; NeoForge checks vanilla with ambient occlusion on and off.
- Screenshots are captured for visual inspection.
- The actual compact settings screen is opened and captured in every runtime,
  exercising the rounded-surface shader, font, and icon resources.

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

### NeoForge Fixture Worlds

Before the NeoForge client tests, prepare a disposable vanilla world made with
each matching game version. Copy the complete save directory, including
`level.dat`, to the packager's corresponding path:

```text
run-isolation-test-1.21.11/saves/weso-fixture/
run-isolation-test-26.1/saves/weso-fixture/
run-isolation-test-26.2/saves/weso-fixture/
```

Do not use a valued player world: the fixture overwrites a small test area and
changes player mode/location. Existing generated test worlds can be reused;
the world folders are ignored by Git. Never copy a newer-version world into an
older runtime. `tools/test-ui-matrix.ps1` checks these paths before each launch.

### UI Verification

The production Fabric universal JAR also passed the dedicated 26.2 UI fixture:
compact settings, full-screen palette/inspector, three styles, optional blur,
popup focus and confirmation, Reduced Motion, native input caret padding,
equal sidebar margins, and scrolling/bounds at 1920x1080 and 1280x720 with
requested scales 2x/3x/4x. See [UI_VALIDATION.md](UI_VALIDATION.md) for the
reproduction command, actual screenshots, measured performance, and limits.

## Remaining coverage

Sodium, Iris/shaders, LabyMod, remote-server CUI, arbitrary resource-pack reloads,
and narration/IME need separate compatibility review. Clipboard coexistence and
normal command submission are covered, but this is not a comprehensive matrix
of every clipboard transform or WorldEdit extension. Unknown patterns, flags,
and extension masks are deliberately rejected, not approximated.

The UI timing measurements in UI_VALIDATION.md belong to the earlier UI profile,
not a 1.8.0 command-preview performance benchmark. Bounded work and render caps
are safeguards, not proof of zero FPS loss in every modpack. Automated assertions
and screenshot inspection do not replace user visual acceptance.

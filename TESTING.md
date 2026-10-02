# Verification

Current source verification: 1.8.0, 2026-10-02. The test worlds, generated
fixtures, screenshots, and logs are local data and are not committed.

## Release build

The root README build command completed for all six runtimes and both universal
packagers. Packager tests checked metadata, version selection, Java bytecode
boundaries, bundled license notices, and registration of the chat-preview mixin
for every runtime. Each universal JAR is approximately 1.2 MiB; neither bundles
WorldEdit nor Fabric API.
The archived release outputs are 1,224,464 bytes (Fabric) and 1,196,772 bytes
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

The three Fabric runs additionally perform 80 deterministic comparisons per
runtime against real WorldEdit EditSession results: eight drafts across cuboid,
ellipsoid, cylinder, polygon, and convex regions, with full and clipped input
coverage. These include walls, faces,
outline, overlay, replace with an omitted mask, and an explicit block state.
WorldEdit history is checked before/after preview, and rapid native chat edits
must immediately discard obsolete geometry.

Their lifecycle tests cover global mask replacement, source block changes with
an unchanged draft, selection changes without a CUI notification, F8 in chat,
unloaded selections, scan/change limits, unsupported flags, invalid patterns,
no-op changes, and removal markers. The shared simulation is the same domain
implementation on NeoForge, but the full 80-case comparison/lifecycle matrix
has only been executed on Fabric; it is not claimed as six equivalent suites.

Screenshot review and a separate pixel check verify actual diamond textures,
not only a nonempty geometry buffer. All six set-preview screenshots pass
the cyan surface test (36,062 Fabric / 36,463 NeoForge pixels in the fixed crop).
The texture check complements native-buffer assertions; a nonempty GPU buffer
alone does not establish correct world projection or texture sampling.

```powershell
.\tools\test-command-matrix.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
.\tools\check-command-pixels.ps1
```

Optional `-Only fabric-26.2` selects a focused rerun. Logs are written to
`build/command-validation/`. NeoForge command fixtures reuse a disposable copy
of the corresponding isolation world; fixture settings are reset before each
launch to verify defaults. No player save or normal Minecraft config is used.

### Complete results and scheduling regression

After the scheduling optimization, all six production-artifact runs additionally
check a 100-edit burst against a 64,000-cell selection on Fabric or a
110,592-cell selection on NeoForge, correct counts (excluding already-matching
blocks), complete exposed surfaces, restoration to a small selection, and
stable random snapshots after repeated CUI notifications. Model templates must
survive ordinary edits, while a changed baked-model identity rebuilds geometry.
The three NeoForge runs also perform an actual built-in resource reload; the
Fabric tests inject a changed probe identity. A direct Fabric gametest reload
hit a framework teardown/Phaser stall and is not claimed as a passing reload test.
An intermittent Fabric 26.2 teardown stall was also observed after assertions;
the final matrix rerun closed its worlds normally and exited successfully.

Fabric additionally checks 274,625 affected cells / 24,578 exposed cells, a
selection bounding box spanning two million blocks on X/Z clipped to 117 actual
loaded chunks, and teleporting away/back to unload/reload the selection. Capture
is clipped to loaded selection bounds (including read-only shape/overlay
context); simulated writes and displayed results obey the original region.
Unloaded geometry is released, and chunks are not forcibly loaded.
Exact section-local marker merging reduces a solid 6x6x6 fluid fallback to one
box without filling gaps. Palette tests cover uniform storage and 8/16/32-bit
index promotion without losing cell values. In the uniform 274,625-cell case,
index arrays occupy 4,096 bytes; this is not total heap or GPU memory usage.
GPU vertex-buffer identities remain stable across unchanged frames, and old
buffers are closed when a different draft replaces the result.

Measured on this laptop (i5-13420H, Intel UHD, 1280x720, 2 GiB test JVM, 100 FPS
cap). The small case alternates gold/diamond drafts eight times in a 6x6x6
selection. Measurements include scheduling and completion of native geometry,
not only the domain calculation; the fixtures observe completion at tick/frame
boundaries, so these are practical latency samples rather than microbenchmarks.

| Loader | Minecraft | Small preview median | Cold input cells | Calculation ready |
| --- | --- | --- | --- | --- |
| Fabric | 1.21.11 | 50 ms | 64,000 | 903 ms |
| Fabric | 26.1 | 49 ms | 64,000 | 1,559 ms |
| Fabric | 26.2 | 49 ms | 64,000 | 1,595 ms |
| NeoForge | 1.21.11 | 50 ms | 110,592 | 2,220 ms |
| NeoForge | 26.1.2 | 50 ms | 110,592 | 2,114 ms |
| NeoForge | 26.2 | 51 ms | 110,592 | 1,862 ms |

Small timings include geometry completion. Cold timings measure calculation
readiness; section geometry uploads then progress within their frame budget.

Before the change, the same Fabric 26.2 small case took 503-705 ms, approximately
600 ms median. The previous timer is removed for existing configurations too.
Server work remains one shared 2 ms target per tick; no command-preview scan runs
without an active command draft. Geometry has its own bounded preparation and
no result-size or fixed-distance preview cap. Large cold results are not
promised instantly: snapshot capture, simulation, verification, and uploads
still take real work. Relevant loaded-chunk coverage changes currently rebuild
the snapshot rather than selectively reusing source tiles.

NeoForge frame sampling compares an idle weighted preview, the large calculation,
and 30 ticks of the completed full large preview:

| Minecraft | Idle median | Calculating median | Large-preview median |
| --- | --- | --- | --- |
| 1.21.11 | 10.029 ms | 10.035 ms | 10.026 ms |
| 26.1.2 | 10.074 ms | 9.995 ms | 9.967 ms |
| 26.2 | 10.039 ms | 10.018 ms | 10.023 ms |

This is a capped local responsiveness check, not proof of zero rendering cost,
unchanged uncapped FPS, or performance on every modpack. Individual external API
calls, model baking, GC, and cold initialization cannot be preempted by a cell
deadline. Raw samples appear in the command-matrix logs as `WESO ... LATENCY`,
`LARGE`, and `FRAME` lines; profiling code belongs only to the test fixture JAR.

## In-world isolation regression

This section records the preceding baseline verification. Isolation and UI
behavior were not changed by the complete-command-preview work; these dedicated
suites were not rerun for this change and are not new coverage claims.

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

Sodium, Iris/shaders, LabyMod, remote-server CUI, changed/custom resource packs,
and narration/IME need separate compatibility review. Clipboard coexistence and
normal command submission are covered, but this is not a comprehensive matrix
of every clipboard transform or WorldEdit extension. Unknown patterns, flags,
and extension masks are deliberately rejected, not approximated.

The UI timing measurements in UI_VALIDATION.md belong to the earlier UI profile;
the separate command latency/frame checks are documented above. Scheduled work,
palette storage, and cached section geometry reduce cost, but are not proof of
zero FPS loss in every modpack. Automated assertions
and screenshot inspection do not replace user visual acceptance.

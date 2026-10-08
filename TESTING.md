# Verification

Current source verification: 1.8.0, 2026-10-08. The test worlds, generated
fixtures, screenshots, and logs are local data and are not committed.

## Release build

The root README build command completed for all six runtimes and both universal
packagers. Packager tests checked metadata, version selection, Java bytecode
boundaries, bundled license notices, and registration of the chat-preview and
selection-editing mixins for every runtime. Each universal JAR is approximately
1.4 MiB; neither bundles
WorldEdit nor Fabric API.
The archived release outputs are 1,442,387 bytes (Fabric) and 1,414,003 bytes
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

The three Fabric runs additionally perform 231 deterministic comparisons per
runtime against real WorldEdit EditSession results across cuboid, ellipsoid,
cylinder, polygon, and convex regions, with full and clipped input where valid.
These include the original fill/boundary/surface operations, partial models,
legacy IDs/data, center aliases, naturalization, hollowing, thick/hollow lines
and convex splines. Four extra exterior-connected hollow cases cover thickness
0-3; an incomplete hollow source must be rejected, not approximated.
Partial-model reference edits explicitly disable native side effects: this
compares direct writes, not dropping unsupported buttons or later world physics.
WorldEdit history is checked before/after preview, and rapid native chat edits
must immediately discard obsolete geometry.

Their lifecycle tests cover global mask replacement, source block changes with
an unchanged draft, selection changes without a CUI notification, F8 in chat,
unloaded selections, scan/change limits, unsupported flags, invalid patterns,
no-op changes, and removal markers. The shared simulation is the same domain
implementation on NeoForge, but the full 231-case comparison/lifecycle matrix
has only been executed on Fabric; it is not claimed as six equivalent suites.

All six additionally test eight partial-model/legacy replacements, unchanged
native client block data, source masking/restoration, flora density 100%, and
center/naturalize/hollow/line calculation. Fabric also checks flora 0%, invalid
density, global masking, ground visibility and official generator coverage.
Convex curves are rendered in Fabric, and changing vertex order with identical
bounds/membership must recalculate the result without a CUI notification.

Screenshot review and a separate pixel check verify actual diamond textures,
not only a nonempty geometry buffer. All six set-preview screenshots pass
the cyan surface test; its color predicate excludes blue sky, not only neutral
stone. Partial replacement, flora, line and curve screenshots are also inspected.
The fixed crop contains 34,099 diamond-texture pixels on each Fabric runtime,
24,978 on NeoForge 1.21.11 and 24,981 on NeoForge 26.x in the final fixture.
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
Fabric tests inject a changed probe identity. A direct Fabric command-geometry
reload hit a framework teardown/Phaser stall and is not claimed as a passing
command-geometry reload assertion. Editor locale reloads below exercise the
actual assets/settings, not that active-preview geometry assertion.
An intermittent Fabric 26.2 teardown stall was also observed after assertions;
the final matrix rerun closed its worlds normally and exited successfully.

Fabric additionally checks 274,625 affected cells / 24,578 exposed cells, a
selection bounding box spanning two million blocks on X/Z clipped to 117 actual
loaded chunks, and teleporting away/back to unload/reload the selection. Capture
is clipped to loaded selection bounds (including read-only shape/overlay
context); region filling obeys the original region, while line/curve include
their generated thickness/overshoot inside client-loaded chunks.
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
The timing tables record the earlier command-scheduling baseline, before
interactive editing. Later functional reruns are not a new controlled editor
performance benchmark.

| Loader | Minecraft | Small preview median | Cold input cells | Calculation ready |
| --- | --- | --- | --- | --- |
| Fabric | 1.21.11 | 50 ms | 64,000 | 704 ms |
| Fabric | 26.1 | 50 ms | 64,000 | 1,709 ms |
| Fabric | 26.2 | 50 ms | 64,000 | 1,555 ms |
| NeoForge | 1.21.11 | 50 ms | 110,592 | 1,961 ms |
| NeoForge | 26.1.2 | 51 ms | 110,592 | 1,761 ms |
| NeoForge | 26.2 | 50 ms | 110,592 | 1,610 ms |

Small timings include geometry completion. Cold timings measure calculation
readiness; section geometry uploads then progress within their frame budget.

Before the change, the same Fabric 26.2 small case took 503-705 ms, approximately
600 ms median. The previous timer is removed for existing configurations too.
Server work remains one shared 2 ms target per tick; no command-preview scan runs
without an active command draft. Geometry has its own bounded preparation and
no result-size or fixed-distance preview cap. Large cold results are not
promised instantly: snapshot capture, simulation, verification, and uploads
still take real work. Relevant chunk gains extend the source after the in-flight
result, retaining displayed output during background capture. That expansion
still reads the complete loaded footprint instead of selectively reusing source
tiles. Chunk losses invalidate immediately.

NeoForge frame sampling compares an idle weighted preview, the large calculation,
and 30 ticks of the completed full large preview:

| Minecraft | Idle median | Calculating median | Large-preview median |
| --- | --- | --- | --- |
| 1.21.11 | 10.038 ms | 10.036 ms | 10.020 ms |
| 26.1.2 | 10.034 ms | 10.013 ms | 10.079 ms |
| 26.2 | 10.020 ms | 10.022 ms | 10.087 ms |

This is a capped local responsiveness check, not proof of zero rendering cost,
unchanged uncapped FPS, or performance on every modpack. Individual external API
calls, model baking, GC, and cold initialization cannot be preempted by a cell
deadline. Raw samples appear in the command-matrix logs as `WESO ... LATENCY`,
`LARGE`, and `FRAME` lines; profiling code belongs only to the test fixture JAR.

### Changing grass and chunk arrival regression

The new fixture first reproduced full-scan cancellation against the preceding
Fabric 1.21.11 artifact: one changed source cell caused the assertion
`One changing grass block restarted the entire large preview` to fail.

The corrected universal artifacts passed on all six runtimes. In a 40x40x40
grass area, an end-of-server-tick fixture keeps one source cell opposite to its
captured value while `//replace grass_block dirt` calculates. The cancellation
epoch must stay unchanged, the result must complete, and after restoring the
cell all 64,000 replacements must appear with dirt models. These are deliberate
fixture world writes, not mod preview writes. The printed mutation count is
the number of tick injections, not a claim that every setBlock changed the world.

The chunk fixture stages nine load notifications backed by actually loaded
native chunks. Gains must not change the cancellation epoch, clear a displayed
snapshot or lose any of the final 64,000 cells. This verifies the source's event
handling, not network timing under every real streaming pattern. The established
teleport unload/reload checks still pass on Fabric.

The three Fabric runs additionally compare isolated point refreshes to fresh
domain results for literal set/replace, masks, removals and legacy IDs. Weighted
refreshes must retain the exact previous choices at all other cells. Source
validation compares states; dynamic NBT-only data is outside supported syntax.
Dependent providers still rebuild for meaningful source-state changes rather
than using the set/replace repair path. No larger tick budget is used.

## Interactive selection editing

The command/editing matrix loads the production universal JARs on the same six
WorldEdit-enabled targets listed above. Each runtime checks all six cuboid faces
through distant native LMB selection and wheel input. UP is the initial choice;
re-entry remembers the clicked face, and looking around does not retarget it.
Native handle dragging and release commit remain available.
All eight corners additionally drag/commit without a hold key, with alternating
standard/extending selectors. Native mouse motion changes camera yaw, sideways
movement and upward flight remain available, and the live draft follows the
moving camera. The initial grab does not jump. Held face/corner drags still
release the cursor and suppress movement for precise projection-based editing.
The opposite corner and the native initial
Pos1/Pos2 ordering are preserved; extending selectors may start with a different
ordering from standard selectors. Normal off-marker clicks/wheel remain uncaptured.
The server's region stays unchanged during drafting. Fast contraction
stops at one block, preserves ExtendingCuboidRegionSelector, and a native
transaction retains the identity of reversed primary/secondary corners.

The default Shift modifier clears the native sneak input during editing; the
flying fixture's height stays unchanged. Outside editing, vanilla sneak input
is retained. A free corner gesture also checks Shift after flight and verifies
that pressing the hold key halfway through does not switch gesture modes.
Corner Escape cancels without leaking an attack or committing.
Native Escape cancels without opening the pause menu. Releasing the editing key
during a drag restores the captured cursor without applying the draft or leaking
an attack. Opening chat cancels. Replacing the selector with an identical-looking
one rejects the obsolete transaction instead of overwriting it. Isolation's
selected material IDs and the clipboard geometry-list identity/preference survive
an acknowledged resize. The fixture compares 1,000 full block states and the
WorldEdit session history before/after the sequence; selection editing must
change neither. Those observations cover the fixture area, not every block in
the save; the production transaction never creates an EditSession.
An additional client fixture replaces the stored world identity while a pending
context exists and asserts that pending/origin/shown/draft references are cleared.
It checks the source's weak cache as well; it is not a real network-disconnect
or garbage-collection benchmark.

Both languages and requested GUI scales 2x/3x/4x are captured for the compact
editing settings, with every widget checked against its panel bounds. The actual
native keybind menu is also captured at those scales/languages; both shortened
editing labels have bounded measured widths and do not reach assignment buttons.
Screenshots include passive corner markers, thin draft/old contours, face handles
and the dimensions/offsets HUD. NeoForge additionally has a bytecode regression
test that requires exactly one editing HUD registration per packaged runtime.
The existing command regressions run alongside these checks, including large
grass replacement and incremental chunk arrival; no command-engine changes were
needed for editing. Tests and temporary configs are confined to disposable worlds.

The shared domain suite passes 41 cases covering face signs, inside/outside rays,
axis dragging from either camera side, end-on rejection, world boundaries,
minimum size, integer overflow, large volume, configuration defaults/clamps,
all corner signs/opposite-point preservation, step snapping, captured plane
projection, live-camera translation/turning at captured depth, off-center grabs,
invalid rays and huge corner offsets without arithmetic overflow.
Both packagers also assert the editing classes and explicit active-mixin registry
for each relocated runtime, including the 1.21.11 FOV accessor.

The 26.2 Fabric layout fixture passes without installed WorldEdit as well; the
editing toggle is unavailable and ordinary menus still work. Editing is off and
its native hold key defaults to G, without overwriting saved assignments. All six
WorldEdit-enabled fixtures check the native default key. Only complete native cuboid/extending
selections in the integrated local WorldEdit server are supported at this stage.

No new uncapped FPS benchmark is claimed for editing. Its geometry and hit tests
are fixed-size work, independent of selection volume; it does not scan blocks.
Live focus loss, disconnect during a pending commit, third-person camera changes,
custom mouse/controller bindings, modpacks, and remote-server editing still need
separate manual review. Remote CUI selection display is not remote editing.

## In-world isolation regression

This suite was rerun because command source suppression reuses the isolation
visibility hooks. With no command draft, the ordinary isolation and no-WorldEdit
startup paths must retain their established behavior. The broader responsive UI
fixture has a fresh layout-only pass; the six-runtime isolation rendering suite
below remains an earlier baseline, not a new isolation coverage claim.

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

## Manual Launcher Verification

The interactive `Test-Mod.cmd` / `tools/manual-test.ps1` launcher was verified
on Windows PowerShell 5.1 on 2026-10-07. This is developer tooling, not a change
to the mod's simulation/rendering code or a rerun of the in-world suites above.

- Unit checks passed for all six plans, invalid menu input/Q, property values
  containing `=`, mixed mod versions, JDK major validation, harmless native stderr,
  failed build exit codes, and environment/working-directory restoration.
- All six `-Offline -PrepareOnly` targets completed native and packager builds,
  resolved their mod dependencies, and validated `runManualTestClient`'s task
  graph. Default packager builds were restored after NeoForge version overrides.
- Real manual launches passed on Fabric 1.21.11/Java 21 and NeoForge 26.2/Java 25.
  The loader logs identify overlay 1.8.0 and WorldEdit 7.4.2/7.4.5; graphics,
  resource atlases and sound initialized, and closing each test window exited
  successfully. No world was entered or edited in these startup checks.
- A second launcher was rejected while the first client was running. The CMD
  wrapper's argument forwarding and `-PlanOnly` exit were also checked.

The initial six-target checks above preceded automatic world setup and exact
patch-version profiles. Normal worlds/profiles and automated fixtures were not
reused. Fabric's local test identity produced expected Realms/auth
warnings. `-Offline` governs Gradle, not all Minecraft/NeoForm network requests.
Logs are under `build/manual-test/logs/` and each target's `logs/latest.log`.
See [manual test instructions](tools/MANUAL_TEST.md) for usage and overrides.

### Automatic World Setup

On 2026-10-07, the expanded launcher passed actual first-create and subsequent
reopen checks on all ten exact targets, using the packaged overlay 1.8.0 JARs:

| Minecraft | Fabric | NeoForge | Local WorldEdit |
| --- | --- | --- | --- |
| 1.21.11 | Create/reopen passed | Create/reopen passed | Both loaders, 7.4.2 |
| 26.1 | Create/reopen passed | Create/reopen passed | Fabric only, 7.4.3 |
| 26.1.1 | Create/reopen passed | Create/reopen passed | Fabric only, 7.4.3 |
| 26.1.2 | Create/reopen passed | Create/reopen passed | Both loaders, 7.4.3 |
| 26.2 | Create/reopen passed | Create/reopen passed | Both loaders, 7.4.5 |

WorldEdit 7.4.3's actual NeoForge metadata requires Minecraft 26.1.2 or newer.
The two earlier NeoForge profiles deliberately run without local WorldEdit;
these checks do not claim local command/clipboard support for those profiles.

`-Loader all -Version all -VerifyWorld -Offline` created separate disposable
flat worlds under `build/manual-test/verification/<loader>-<exact-version>/`.
Each client asserted Creative and operator permission level 4, saved a diamond
probe block, switched its player to Survival and removed the explicit operator
entry, then exited normally. The repeat matrix opened the existing save,
verified the unchanged block and restored Creative/full operator permissions.
Success was checked in each actual game log against its exact save path, not
against unit-test messages in the combined Gradle output. All ten repeat runs
saved their chunks and exited successfully.

Normal interactive launch was additionally checked on Fabric 1.21.11 and
NeoForge 26.2: the game remained open after automatic entry, `ops.json` contained
level 4, no verification probe was written, and closing each test window saved
the world successfully.
Normal worlds use `build/manual-test/instances/<loader>-<exact-version>/`; neither
verification worlds nor the launcher's older directories are reused or replaced.

The bootstrap unit suite covers single creation, asynchronous setup without
restarts, preservation of existing/malformed save paths, cancellation without
retry loops, idle normal mode and separate verification shutdown. Launcher tests
cover ten exact profiles, batch plans, isolated paths and rejection of an
unrelated unit-test success marker. Both loader launch tasks are explicitly
untracked so Gradle cannot skip a subsequent game launch as up-to-date.

World setup uses native Minecraft creation/opening and integrated-server APIs
through a test-only helper. It does not edit save NBT or bypass version/recovery
checks. The helper is excluded from normal release builds; the Fabric/NeoForge
release JAR SHA-256 values remained unchanged throughout this tooling update.
This verifies launcher/bootstrap behavior, not a new full rendering/UI/command
matrix for every patch version.

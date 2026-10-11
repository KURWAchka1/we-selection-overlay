# Verification

Published baseline verification: 1.8.0, 2026-10-10. The test worlds, generated
fixtures, screenshots, and logs are local data and are not committed.

The completed first development-candidate regression cycle is tracked in
[FULL_REGRESSION_2026-10-11.md](docs/engineering/FULL_REGRESSION_2026-10-11.md).
Guarded native wrappers accept `-ModVersion 1.8.0-bridge-dev`; the default
remains published 1.8.0. Each run logs the exact JAR hash and fails if that
requested artifact is absent, rather than substituting the default.
Use that explicit development version for the current checks: the new
no-WorldEdit CUI-command regression intentionally fails on the unpatched
published baseline.

## Background native tests

Optional server-bridge foundation checks are recorded separately in
[SERVER_BRIDGE_IMPLEMENTATION.md](docs/engineering/SERVER_BRIDGE_IMPLEMENTATION.md).
`tools/test-server-bridge.ps1` verifies pure state/codec tests on Java 21 and 25
and six native payload codecs without opening a game window.
`tools/test-bridge-packaging.ps1` uses development-only artifact names and runs
both universal packager suites, without overwriting archived release 1.8.0.
`tools/test-paper-bridge.ps1` starts a loopback-only, headless disposable Paper
server after explicit EULA acceptance; it never uses normal launcher worlds.
Its TCP clients prove the Paper/application protocol boundary, not native mod
login/gameplay compatibility or measured busy-server overhead.


Both automated matrix scripts and `tools/test-ui-layout.ps1` launch on a dedicated private Win32 desktop,
not a Windows Task View virtual desktop. They never call `SwitchDesktop` or
send system-wide input. A Windows job contains the whole process tree, assigns
BelowNormal priority to every descendant, and kills remaining Gradle/Java
children on completion, failure, cancellation or watchdog timeout.
The parent checks that no job-owned window appears on the input desktop.
Initialization failure stops the test; there is no visible fallback.

**Desktop isolation alone is insufficient for mouse safety.** An initial private
desktop run still confined the user's cursor and was stopped. Every automated
client now also requires the test-only `cursor-isolation-agent.jar`: its guarded
GLFW methods suppress cursor capture/hiding/confinement, raw-mouse changes and
system cursor warps. They leave the actual native cursor in `GLFW_CURSOR_NORMAL`.
The guard is verified without Minecraft, then probed against real cached LWJGL
3.3.3 and 3.4.1 before launching a matrix. Unsupported bytecode fails closed.
Neither this agent nor its ASM dependency is packaged in the production mod.
The agent relocates ASM into its private namespace using the existing Shadow
plugin version; it must not expose a second ASM copy to Fabric Loader.

Selection tests explicitly simulate game focus and dispatch input through the
native Minecraft callbacks. They still check logical grab/camera/crosshair
transitions and focus-loss cancellation, but do **not** claim physical foreground
focus or OS cursor capture/restoration coverage. Those require an opt-in manual
playtest. Command fixtures use a 40 FPS cap and Gradle uses two workers; this
does not guarantee zero CPU/GPU load. Earlier 100 FPS timings below are historical
measurements, not new background-performance results.

```powershell
.\tools\test-desktop\probe.ps1
.\tools\test-desktop\probe-cursor.ps1 -Java21 'C:\path\to\jdk-21'
```

Matrix commands below enable both safeguards automatically. Aggregate logs go
to `build/desktop-tests/`, in addition to the existing per-runtime logs and real
framebuffer screenshots. The interactive manual launcher remains separate.

On 2026-10-10, guarded background command/editing/clipboard-transform runs passed
on all six WorldEdit-enabled targets below. Every run required both the
cursor-guard and background-input completion markers. The two NeoForge 26.x
targets were rerun after correcting their clipboard model submission stage;
the other four runtime implementations were unchanged by that stage correction.
The separate Fabric 26.2 layout-only fixture also passed on a private desktop
with the packaged, privately relocated cursor guard and a 40 FPS cap.
All six command-texture, selection-HUD and clipboard-transform screenshot pixel
checks passed.

Implementation references: [Microsoft desktops](https://learn.microsoft.com/en-us/windows/win32/winstation/desktops),
[process desktop selection](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/ns-processthreadsapi-startupinfow),
[job objects](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects),
and [GLFW cursor modes](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode).

## Release build

The root README build command completed for all six runtimes and both universal
packagers. Packager tests checked metadata, version selection, Java bytecode
boundaries, bundled license notices, and registration of the chat-preview and
selection-editing mixins for every runtime. Each universal JAR is approximately
1.5 MiB; neither bundles
WorldEdit nor Fabric API.
The archived release outputs are 1,579,903 bytes (Fabric) and 1,551,799 bytes
(NeoForge), under each packager's `libs/1.8.0/` directory.
The current tests also verify that UI assets/notices are shared once. JAR and
runtime input files are declared test inputs so changed binaries invalidate
previous test results.
The current packager suites pass 119 Fabric-side and 8 NeoForge-side executions;
the 103 shared selection-domain cases and five clipboard-draft parser cases run
on the Fabric side, not twice. The parser explicitly excludes cut/paste and checks
Y/X/Z argument order, aliases, incomplete/non-finite angles and interpolation warnings.
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
28,398 on NeoForge 1.21.11 and 28,389 on NeoForge 26.x in the current fixture.
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
Editing fixtures explicitly bind G in their disposable profile: prior keybind
screen checks may have left it unassigned. Production never overwrites saved
assignments. The matrix requires the command, editing and palette-history
completion markers, including crosshair and editing-palette checks, in the fresh
game log, not just a zero process exit code. Each target additionally runs the
real screenshot texture check after the client closes.

### Clipboard transform drafts

All six targets compare `//rotate` Y/X/Z order, negative and arbitrary angles,
tiny rotations, stacked transforms, six absolute flip directions, `//mirror`,
default/relative look directions and diagonal-direction rejection against real
WorldEdit `ClipboardCommands` in a detached reference session. An asymmetric
clipboard with an offset origin and an already rotated/reflected baseline checks
both coordinates and oriented stair/trapdoor states.

The fixture verifies unchanged world/history/real transform before Enter, source
cache identity across edits and camera changes, equivalent-angle reuse, rapid
typing, incomplete/invalid drafts, Escape/F8 restoration, normal Enter execution,
empty/disabled clipboard and explicit cut/paste exclusion. A 10,000-step build
probe verifies the 4,096-step conversion slice and obsolete-work cancellation.

Screenshot inspection caught NeoForge 26.x displaying only the clipboard bounds
despite correct calculated states. The added pixel regression reproduced zero
texture pixels before the public extraction/custom-submission event fix. Both
corrected NeoForge 26.x runs then passed the full native suite and texture check.
The rotate/mirror crops contain 4,225/1,113 warm model-texture pixels on every
target; blue sky, cyan bounds and magenta reference-command messages do not meet
the predicate. These are native framebuffer captures, not mockups.

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
A short G tap in Resize releases the cursor and freezes camera/movement, even
before a drag. Native distant face clicks and projected cursor picking use that captured
view. LMB release leaves a composed local draft active; a second corner drag
starts from the current draft without jumping or losing the first face edit.
The server bounds remain unchanged between these drags. A second short tap
applies all changed sides once and restores camera/cursor control, also when
LMB is still held. Native press/release callbacks, not tick polling alone, catch
two genuine taps shorter than a client tick.
The close-view regression grabs every face from 0.2 blocks outside and inside
the cuboid, away from center/corner handles. Two physical pixels of jitter must
not resize it; fixed 96-pixel end-on drags expand/contract by two blocks without
an initial jump or sign inversion. Surface picking does not require an on-screen
face-center handle.

Holding G for 400 ms opens the Resize/Move ring. A shorter 200 ms hold must not
open it. The fixture checks the actual GLFW hidden-cursor state, captured camera,
clamped relative replacement dot, immediate inward response at the ring edge,
center cancellation, Escape while held and cursor
restoration. Actual ring screenshots cover requested scales 2x/3x/4x. Opening
and cancelling the menu during editing must leave the composed draft unchanged;
choosing a mode must not enter editing or apply a draft automatically.
Move checks independent XYZ wheel nudges without player-displacement carry, unchanged
dimensions, reversed ordered corners and ExtendingCuboidRegionSelector identity.
Separate native transactions reject a missing shift permission, allow a
shift-only actor to move and deny that actor a resize. Both tools remain local
selection adjustments, not world-block operations.
All eight corners additionally drag/commit without a hold key, with alternating
standard/extending selectors. Native mouse motion changes camera yaw, sideways
movement and upward flight remain available, and the live draft follows the
moving camera. The initial grab does not jump. Explicit Resize face/corner drags
release the cursor and suppress movement for precise projection-based editing.
Within each captured free-corner gesture, fixed-position probes verify one-block
snapping despite configured 4/8-block steps, the fast modifier, and pressing the
edit key mid-gesture. An explicit-editor corner probe verifies configured normal
and fast steps of 4/8 blocks. Probes run together on the client thread so flight
or camera interpolation cannot change their position between assertions.
The opposite corner and the native initial
Pos1/Pos2 ordering are preserved; extending selectors may start with a different
ordering from standard selectors. Normal off-marker clicks/wheel remain uncaptured.
The server's region stays unchanged during drafting. Fast contraction
stops at one block, preserves ExtendingCuboidRegionSelector, and a native
transaction retains the identity of reversed primary/secondary corners.

The default Shift modifier clears native sneak input only in explicit Resize; the
flying fixture's height stays unchanged. In free corner dragging, the fixture
checks native Shift input and an actual height decrease after upward flight,
even with the hold key pressed midway. This does not switch gesture modes or
change one-block snapping. Free commit checks use the final release-time draft,
not an earlier frame's position while the camera/player is still moving.
Corner Escape cancels without leaking an attack or committing.
Native Escape cancels in both explicit/free modes without opening the pause menu,
applying the draft or leaking an attack. A second short G tap during an explicit
drag applies it and restores the captured cursor without leaking an attack.
Opening chat cancels. Replacing the selector with an identical-looking
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

The shared domain suite passes 103 cases covering face signs, inside/outside rays,
zero-depth face intersections, axis dragging from either camera side, world boundaries,
minimum size, integer overflow, large volume, configuration defaults/clamps,
all corner signs/opposite-point preservation, step snapping, captured plane
projection, live-camera translation/turning at captured depth, off-center grabs,
invalid rays and huge corner offsets without arithmetic overflow. New cases
cover near/end-on screen-axis mapping, stable signs, whole-box translation,
tap/hold timing, key repeats, cancellation and radial center/seam hit testing.
The 24 outline cases check all eight corners and six faces, exclusive stroke
ownership, original-ghost overlap clipping on expansion/contraction, untouched
displaced contours, single cells and exact world-border coordinates.
Both packagers also assert the editing classes and explicit active-mixin registry
for each relocated runtime, including the 1.21.11 FOV accessor.

The 26.2 Fabric layout fixture passes without installed WorldEdit as well; the
editing toggle is unavailable and ordinary menus still work. Editing is off and
its native edit/menu key defaults to G, without overwriting saved assignments. All six
WorldEdit-enabled fixtures check the native default key. Only complete native cuboid/extending
selections in the integrated local WorldEdit server are supported at this stage.

No new uncapped FPS benchmark is claimed for editing. Its geometry and hit tests
are fixed-size work, independent of selection volume; it does not scan blocks.
Live focus loss, disconnect during a pending commit, third-person camera changes,
custom mouse/controller bindings, modpacks, and remote-server editing still need
separate manual review. Remote CUI selection display is not remote editing.

### Independent Move Axes

On 2026-10-10, the final independent-axis revision passed the complete native
command/editing/palette fixture on Fabric and NeoForge for 1.21.11, 26.1 and
26.2. All 18 requested-scale compass captures passed the color, letter-center
and stroke-center checks, against both sky and stone backgrounds. The largest
measured stem-centroid deviation was 0.134 logical pixels; no frame-time or
general modpack-compatibility claim follows from this visual check.

The current native matrix requires `MOVE AXES VERIFY PASS:` on every target.
Its Creative fixture parks the player over a solid floor, grips Move, and
switches X/Y/Z without releasing LMB. Y wheel input moves the selection through
the floor without moving the player. Fractional X input survives a Y/Z switch;
axis selection alone does not transform the draft. Navigation, flight and
mouse look must leave it unchanged. A translation at unchanged camera heading
must not rotate the compass. Six cardinal and three oblique camera directions,
including straight up/down, must match the actual native camera basis while
preserving the chosen grip axis and draft. Only camera rotation changes the
miniature's viewpoint; a new grip captures a new initial direction.

Native keyboard probes verify press/release ownership, held-key repeats after
switching axes, cancellation with a key still down, an edit key rebound to X,
and a fast modifier rebound to Y. Modifier combinations and native edit,
movement and fast bindings retain priority. G taps use native callbacks, not
short synthetic key-state pulses that can fall between ticks during a hitch.
Existing G/Escape, ordered-corner, permission, isolation, clipboard and unchanged
world/history assertions still run alongside the new checks.

Actual compass captures cover requested scales 2x/3x/4x at 1280x720. Minecraft
clamps requested 4x to effective 3x at this viewport; this is not a 4K test.
The bottom-right 84 x 84 logical-pixel area stays above the hotbar and clear of
top-right tutorial toasts. The pixel check requires all three axis colors and
colored label pixels in that area; it is not OCR. Three depth-ordered thin arms
and cached regular/bold labels reuse existing GUI rendering, without endpoint
circles or backplates. Separate letter regions reject filled endpoint discs.
Stem-color centroids must stay within 0.3 logical pixels of their projected
centerlines; the earlier half-pixel-offset strokes fail this regression check.
Colored letter bounds separately check their optical center, rejecting the
old half-pixel leading offset even when the stroke itself is centered.
Coordinate changes do not scan the
selection or depend on its volume. No new controlled frame-time benchmark is
claimed.

### Move Carry Regression

Historical baseline: the 2026-10-10 independent-axis revision supersedes root
carry. These older results describe the previous interaction, not current behavior.

On 2026-10-09, all six production-artifact command/editing/palette runs passed
after the first [task-first Move slice](SELECTION_WORKFLOW_DECISION.md). Native
LMB grips work 0.2 blocks outside, 0.2 blocks inside and 256 blocks away without
a pickup jump. Native mouse look changes yaw without translating a stationary
box; actual root displacement translates it. Spectator flight descends with
Shift during a grip. This is automated native-flight coverage, not manual
Creative/Survival ergonomic acceptance.

Four fractional wheel events compose one exact signed-axis nudge. Idle/released
Move leaves the wheel native. LMB release retains the draft, radial cancellation
preserves it, Escape cancels and G applies even with LMB held. The trailing
release cannot attack. After G, the test checks both Minecraft's grab flag and
actual GLFW_CURSOR_DISABLED, covering the native grabMouse early-return case.
The existing stale-selection, permissions, isolation, clipboard and unchanged
world/history assertions also pass. No command-engine change was needed.

The 22 new shared carry cases cover re-grabbing, all six signed axes, fractional
motion, independent-axis clamping, immediate reversal after rejected overshoot,
nonfinite data, huge regions and long travel. Both packagers pass their tests:
114 Fabric-side and 8 NeoForge-side executions, including the shared suite on
Fabric (not 122 distinct domain cases). All six runtime builds also succeeded.

Logs are in `build/command-validation/`; carry screenshots are in each runtime's
`run-command-test/screenshots/` or the NeoForge packager's versioned equivalent.
The first NeoForge combined run exhausted its former 2,400-tick overall fixture
budget at the final command; all three NeoForge reruns passed with a bounded
4,800-tick budget and unchanged assertions. Individual editing awaits still
time out after ten seconds. Screenshot capture waits for the real-time HUD fade,
not only accelerated game ticks. No production delay was added.
The focused Fabric 26.1 recapture passed; all six final carry screenshots pass
`tools/check-selection-pixels.ps1` for both a dark panel and actual bright text.
This pixel predicate is a blank/covered-text regression check, not OCR or proof
of every locale/layout. The 1280x720, 2x carry images were also visually inspected.

This slice has no new controlled frame-time measurement. 4K/DPI, live server
corrections/teleports, gravity/knockback ergonomics, modpack input and human task
comparisons remain release gates. Resize refinements, numeric input, local
gesture history and mixed-operation semantics are not claimed implemented.

## Palette History

All six WorldEdit-enabled native fixtures additionally open the production
palette editor and dispatch its native mouse/key events. A 100-update color drag,
including a second mouse button press/release, creates exactly one undo step.
Ctrl+Z, Ctrl+Y and Ctrl+Shift+Z restore full palette states, including alpha and
all individual widths. Numeric edits are coalesced with the HEX field focused;
the fixture scrolls to reach that field in the small stacked layout.
New edits discard redo, while an unchanged edit does not. Popup/confirmation
states block shortcuts from changing the palette behind them. Confirmed presets
and each global reset are atomic undo steps; resize retains state/history.
A non-default legacy global width multiplier is restored too. Each runtime
captures the actual editor after the sequence. These are history/input checks,
not a new editor performance benchmark or a complete IME/paste-input matrix.

### Editing Palette and Crosshair Checks

On 2026-10-10, all six production-artifact runs completed the command, editing,
crosshair, history and editing-palette checks. All six center-pixel checks found
68 visible / 0 hidden / 68 restored crosshair pixels. The two-channel contrast
predicate rejects moving cloud rims; screenshots were also visually inspected.
The default Java 21 NeoForge packager build was restored after version overrides.

The native fixtures additionally exercise the four editing stroke roles:
common contour and X/Y/Z. Atomic color/alpha/width edits round-trip through the
profile properties and native Ctrl+Z/Y. All five presets set editing colors
and widths; per-element reset restores both without resetting another axis.
The common width includes the new roles and becomes unavailable for mixed
widths. The fixed 12-segment preview shares world stroke roles and endpoints;
all eight corner/three-axis combinations are checked. Width changes retain
the segment list, while color changes invalidate its cached colors.

The outline regression checks all eight native corner drags for nine ordinary
contour edges and three exclusive axis edges. Both the draft and original
comparison contour must have no collinear overlap with active axes. A dedicated
`selection-edit-axis-outline.png` capture uses a wide yellow contour with thin
cyan, dark and semitransparent white axes. A scoped native Gizmos collector
also checks the actual emitted corner quads: palette-colored fill, no stroke
and no dark inset. Palette-only changes retain the outline geometry cache.
The command matrix requires the additional `EDIT OUTLINE VERIFY PASS` marker.

Editing-preview and scrolled controls are captured at requested 2x/3x/4x.
Classic's appearance popup has three theme choices and a 103-pixel logical
height; modern styles retain five controls and a 155-pixel height. Switching
themes preserves the saved blur and motion preferences.

Crosshair assertions cover explicit Resize/Move, radial choice, all eight free
corner grips, LMB/G release, Escape, chat, disabled/re-enabled editing and
changed stored world identity. A controlled native focus-field probe checks
both immediate suppression release and cancellation on the next editor tick;
this is not an OS Alt-Tab/modpack-focus test. Creative screenshots look into
empty sky before, during and after editing. The separate center-pixel check
requires the real crosshair before/after and at most four contrast pixels during editing.
Pending commits and passive markers keep the normal vanilla behavior.

Both packagers inspect the actual relocated runtime bytecode. Fabric requires
one crosshair wrapper and one editing HUD registration. NeoForge requires
exactly one listener of each type, not just an aggregate listener count.
The compact radial's icon-only sectors, contextual center/cancel hint and
bounded pointer are captured for both modes and neutral center in EN/RU at
requested 2x/3x/4x. Center/seam cancellation retains the tool and draft.
These are functional/layout checks, not a new uncapped FPS benchmark.

An earlier Fabric 26.2 run passed its assertions but stalled in the game-test
API's phased world teardown; it is excluded from the completed-run claim.
The fixture now disables its own editing/clipboard reads before closing and
allows five test ticks for outstanding work. The final rerun saved its world
and exited successfully. The focus-loss fixture also waits for the fresh
authoritative snapshot before testing re-entry; no production input delay or
weakened assertion was introduced.

History is local to one editor instance, capped at 128 small immutable states;
the capture code runs on edit boundaries, not per rendered frame. It does not
intercept WorldEdit undo or record preview-camera movements. Palette edits and
history need no WorldEdit permission; remote CUI styling remains a local feature.

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

Build first, then run the guarded background matrix from the repository root:

```powershell
.\tools\test-ui-matrix.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
```

Use `-Only` for a specific loader/runtime, for example:

```powershell
.\tools\test-ui-matrix.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Only neoforge-26.2
```

The wrapper selects JDK 21 for 1.21.11 and JDK 25 for 26.x. Internal Gradle
client tasks refuse a direct unguarded launch. Run the NeoForge default build
again after using an overridden version.

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

Development bridge candidates separately passed actual Fabric/NeoForge 1.21.11
connections to Paper 1.21.11 with WorldEdit installed only on the server:
[native transport evidence](docs/engineering/NATIVE_PAPER_REGRESSION_2026-10-11.md).
That matrix covers allowed, denied and absent companion scenarios; it is not
evidence for every remote-server version, renderer modpack or remote feature.

Sodium, Iris/shaders, LabyMod, additional remote-server CUI tuples, changed/custom resource packs,
and narration/IME need separate compatibility review. Clipboard rotation/reflection,
coexistence and normal command submission are covered as specified above; this
is a block-model preview, not clipboard entity/biome/NBT rendering or support for
every WorldEdit extension. Unknown patterns, flags,
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

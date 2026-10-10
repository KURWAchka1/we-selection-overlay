# UI validation

Latest layout-only check: 2026-10-10. The earlier full UI profile and
six-runtime isolation checks remain recorded below; user visual acceptance
is pending.

## Scope

All six game runtimes share the new UI specification. Settings stay compact;
the palette and block inspector stay full-screen. No selection, clipboard,
isolation, or CUI behavior was changed for the redesign.

Real Minecraft screenshots are generated in
`WEaddon_fabric_26.2/run-ui-test/screenshots/`; they are not HTML mockups.
The harness loads the production Fabric universal JAR rather than a development
module. It checks native O/B actions, dynamic grid visibility, three styles,
optional blur, preset confirmation, focus capture, Reduced Motion, input caret
padding, scrolling, widget bounds, and symmetric shape-row margins.
The inspector's rail uses equal 24-pixel logical insets. RGBA labels follow
their fields' visibility, and the thickness group has an additional 10-pixel
gap from the alpha control. The font uses a static 450 weight, not fake bold.

English is checked at 1920 x 1080 / 2x. Russian is checked at 1920 x 1080 and
1280 x 720 with requested scales 2x, 3x, and 4x. Minecraft may clamp the effective
scale to keep its minimum logical resolution. Both the top and scrolled color
controls are captured.

## Reproduce

Build both universal artifacts using the root README command. Then, with Java
25, run from `WEaddon_fabric_26.2`:

```powershell
.\gradlew.bat -I ..\tools\ui-test.init.gradle testUniversalUi --offline --no-daemon --console=plain
```

For layout-only rechecks, add `-Pui_fast=true`. This skips timing samples;
it must not be cited as a performance benchmark.

The six-runtime isolation and settings-render checks can be run sequentially
from the repository root:

```powershell
.\tools\test-ui-matrix.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
```

The NeoForge fixtures need their generated `weso-fixture` quick-play worlds,
as documented in `TESTING.md`. Logs are written to `build/ui-validation/`.

## Verification Results

The 2026-10-10 layout-only rerun passed with the final 1.8.0 Fabric universal
artifact and no installed WorldEdit. It includes the new selection-editing
row, which is unavailable without local WorldEdit, alongside the existing
42-screenshot matrix. The dedicated editing settings screen is checked in
both languages at requested 2x/3x/4x on all six WorldEdit-enabled runtimes by
the command/editing fixture; see TESTING.md. Its controls stay inside the
compact panel and use matching horizontal insets. These are layout checks,
not new FPS measurements. The new Editing palette group participates in the
sidebar's minimum-height calculation, with stacked/scrolling layout at smaller
logical sizes. The rerun captures 42 images; after a locale reload it waits for
the native resource-loading overlay to disappear, not just for its future to
complete. Screenshot review includes the additional group and Russian controls.

The 2026-10-10 editing runs also capture the real native keybind menu in EN/RU
at requested 2x/3x/4x on all six targets. The shorter face-edit/fast-step names
do not overlap the assignment buttons. Passive corner markers, free-camera corner
dragging during flight and the precise face-drag HUD are captured in-world, using native gizmos and
the actual camera projection, not a separate UI illustration. NeoForge's HUD
is registered once per runtime; packaging tests enforce that invariant.

The editing fixture also captures the two-sector Resize/Move ring at requested
2x/3x/4x. It uses cached anti-aliased bitmap masks and Lucide icons, not a second
screen or a blur target. The cursor is hidden; a bounded dot supplies selection
feedback outside the center. Sectors contain only icons; the center displays the
hovered tool name, or Cancel for the neutral center/seams. Translated captions
fit without breaking words. The pointer yields to the hint inside the dead zone.
Short hover fades honor Reduced Motion.
GLFW captures a hidden virtual cursor; only the dot is clamped, without system
cursor warps. The ordinary editing HUD pauses while this mode-choice menu is open.
Screenshot review caught chat drawing over the large-scale ring; Fabric's HUD
registration now places the editing layer after chat. NeoForge uses its existing
post-GUI layer. The ring sectors and compact editing HUD use opaque surfaces
after their short opening fade. The center and outside remain unfilled; the
central caption uses native text shadow for contrast against the world. These
are input/layout checks, not a new FPS benchmark.

The compact ring is capped at a 76-pixel logical radius rather than 92. Both
tool hints and neutral Cancel are captured in EN/RU at requested 2x/3x/4x on
all six WorldEdit-enabled runtimes. The editing-palette preview/controls and
Classic's shorter three-theme appearance popup are also captured and checked.
The native crosshair remains visible for passive markers and pending applies,
hides only for active tools/grabs/radial choice, and returns on exit. All six
Creative screenshot triplets pass the separate 68/0/68 center-pixel check.
See TESTING.md for interruption coverage and framework teardown rerun details.

The same six runs verify palette Ctrl+Z/Y history with native screen events,
including a focused HEX field, one-step drags, popup/confirmation blocking,
resets/presets and resize preservation. History adds no new layout widgets or
preview-camera recording. See TESTING.md for exact coverage; neither these
checks nor the no-WorldEdit layout rerun are new FPS measurements.

The 1.8.0 layout-only rerun includes the new Command Preview settings row,
actual on/off clicks, styled label translations, and the existing 42-screenshot
EN/RU and 2x/3x/4x matrix. Screenshot inspection caught a missing styled-label
translation that widget bounds alone could not detect; it is now an explicit
regression assertion. This rerun is not a new timing benchmark.

The full Fabric 26.2 profile and the final layout-only rerun passed. Forty-two
real game screenshots cover the three styles, menus, confirmation dialog,
checked/unchecked block selectors, EN/RU, scrolling, and requested GUI scales.
The final rerun includes the added gap above element thickness and input-caret
checks. Screenshot review confirmed equal sidebar insets, in-bounds alpha/hue
markers, and the compact inspector summary above the list instead of below it.

| Loader | Minecraft | Settings Render | Eight Isolation Toggles |
| --- | --- | --- | --- |
| Fabric | 1.21.11 | Passed | Passed |
| Fabric | 26.1 | Passed | Passed |
| Fabric | 26.2 | Passed | Passed |
| NeoForge | 1.21.11 | Passed | Passed |
| NeoForge | 26.1.2 | Passed | Passed |
| NeoForge | 26.2 | Passed | Passed |

Both packagers' unit suites passed, including shared UI assets/notices,
metadata, supported version selection, runtime mixins, isolation invalidation,
and Java 21/25 bytecode boundaries. Font, shader, and icon resources are shared
once per universal artifact, not duplicated for each game version.

## Move Axis Compass

The 2026-10-10 Move revision adds a small unframed compass only during an LMB
grip. It occupies a safely inset 84 x 84 logical-pixel bottom-right area above
the hotbar, clear of vanilla tutorial/advancement toasts, with fixed
label sizes, thin arms and palette-colored XYZ letters. Only the active letter
is bold; there are no circles, dots, rings or backplates. The grip establishes
its directions once; the current camera orientation determines their screen
projection. Translation without turning does not rotate the miniature, and
looking around does not redefine the grip directions or move the selection.
Existing GUI submissions render three depth-ordered arms and cached labels,
without a second scene.
One-pixel strokes are centered before rotation, including their rounded caps;
their directions use the same rounded endpoints as the letters. This avoids
half-pixel offsets between the junction, arms and labels at GUI scales 2x/3x.
Uppercase letters are centered by their seven-pixel cap height, not the
nine-pixel line box; half-pixel text origins preserve alignment at odd widths.

The command/editing fixture captures `selection-edit-axes-x2/x3/x4.png`, not
the separate 42-image layout-only fixture. `check-selection-pixels.ps1` verifies
all three axis colors, actual colored glyph pixels and centered thin strokes
within the corner area. Filled endpoint discs and the old half-pixel stroke
offset fail its region/centroid checks.
The colored glyph bounds also check the optical center against the endpoint,
independently of the stem centroid, excluding the native text shadow.
Native-game coverage/results are recorded in TESTING.md. This addition has no
new controlled CPU/GPU frame-time benchmark.

## Performance Method

CPU measurement is opt-in (`-Dweso.ui.profile=true`); release play does not
allocate a timing buffer or collect samples. The benchmark warms each screen,
collects at least 100 render samples, and reports median/p95 GUI content
extraction times. FPS is sampled over five seconds with a 120 FPS cap and
VSync off, using a small four-chunk test world on this computer.

CPU extraction is not GPU frame time. Capped FPS, resource reloads, chunk
generation, focus, temperature, and driver scheduling can affect results.
These tests cannot prove zero FPS loss in every modpack. Blur remains disabled
by default and its cost is not included in the blur-off comparisons.

### Measured Results

Full profile: Fabric 26.2 / Java 25, 2026-10-01. The production universal JAR
with the 450-weight font was used; each screen collected about 950 samples.

| Screen | Style | CPU median (ms) | CPU p95 (ms) | Mean FPS |
| --- | --- | --- | --- | --- |
| Settings | Classic | 0.110 | 0.180 | 119.0 |
| Settings | Glass | 0.080 | 0.115 | 118.8 |
| Palette | Classic | 1.695 | 1.930 | 119.0 |
| Palette | Glass | 2.350 | 2.655 | 118.8 |
| Inspector | Classic | 0.139 | 0.228 | 118.6 |
| Inspector | Glass | 0.170 | 0.287 | 118.8 |

The palette remains the heaviest screen: its live preview is present in both
styles. Glass adds approximately 0.655 ms median / 0.725 ms p95 in this sample;
the capped FPS comparison shows no substantial difference. UI-closed world
samples were 114.4 FPS with periodic chunk-generation/server stalls, so they
are not a clean basis for claiming an FPS gain from opening menus.

## Remaining Coverage

No Sodium/Iris/LabyMod compatibility pack was loaded for this UI pass.
Remote CUI, arbitrary resource packs, and narration/IME usability still need
separate live compatibility review. The 1.8.0 local WorldEdit command and
clipboard-coexistence runs are recorded separately in TESTING.md; this UI
fixture intentionally runs without WorldEdit.
The isolation fixtures cover eight material toggles, surrounding chunk meshes,
exposed selected faces, and fullbright vertices; they do not replace that review.

The Kawaii binary was only inspected as a reference. Its optimizations were
not copied or benchmarked, and none of its code/assets are redistributed.

# UI validation

Date: 2026-10-01. Status: automated UI and six-runtime verification passed;
user visual acceptance is pending.

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
WorldEdit commands, remote CUI, clipboard operations, arbitrary resource packs,
and narration/IME usability still need separate live compatibility review.
The isolation fixtures cover eight material toggles, surrounding chunk meshes,
exposed selected faces, and fullbright vertices; they do not replace that review.

The Kawaii binary was only inspected as a reference. Its optimizations were
not copied or benchmarked, and none of its code/assets are redistributed.

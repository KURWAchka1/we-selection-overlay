# UI redesign research and implementation contract

Status: shader-backed prototype implemented in all six runtimes; automated UI,
packaging, and in-world regression checks passed. User visual acceptance pending.
Reviewed: 2026-10-01. Recheck version-specific API details before writing code.

## Prototype rejection and supplied JAR inspection (2026-10-01)

The user rejected the primitive-based Glass/Studio prototype: drawing stepped
corners and repeated borders with GUI fills did not deliver the soft, polished
reference. Do not resume that implementation or treat a recolor as a solution.
The supplied JAR was inspected before choosing the new renderer described below.

Inspection was read-only: ZIP entries, resource metadata, and `javap -p -c`.
The reference mod was not executed, installed, decompiled into this repository,
or used as a source of redistributable code/assets.

- Supplied filename: `kawaii-privacy-0.3.33.jar`; its `fabric.mod.json` reports
  Kawaii `0.3.32`, targeting Fabric/Minecraft `1.21.11` and Java 21. Conclusions
  apply to this exact binary, not necessarily other Kawaii releases.
- SHA-256: `6635543BEA1A69B4B745E883AFFE1DC6FFC2C724B2D2C4D5E0B8AC37ED4234B1`.
- `api.ui.UI` extends `BaseScreen`, which extends Minecraft's screen class.
  Its `renderPanel` calls `RectUtil.drawClientRect`. That helper constructs
  `BuiltGlass` and passes it to `Render2D.glass`; this is an active menu path,
  not merely an unused effect bundled in the archive.
- `GlassRenderer.submit` requests a backdrop capture from `BlurFramebuffer`
  and submits `GlassRenderState` into Minecraft's GUI render state. The renderer
  uses custom Blaze3D `RenderPipeline`s, GPU buffers, masks, and scissors.
- `BlurFramebuffer` exposes Kawase down/up pipelines, reusable target pools,
  stable backdrop storage, capture limits, and preparation/binding methods.
  This is actual image processing, not a translucent rectangle pretending to
  be blurred glass. Pooling/batching are present; their real performance was
  not benchmarked in this inspection.
- `CoreShaders`, `GlassShaders`, and `KawaseShaders` embed GLSL in class
  constants. The missing external `.fsh`/`.vsh` resources therefore do not mean
  that the mod has no shaders. The core sources include rounded-edge/SDF
  blending, `smoothstep`, and derivative-based smoothing operations.
- Menu bytecode calls the custom `Fonts.draw` and `Fonts.msdf` paths.
  Font PNG/JSON atlases include Montserrat and other families; inspected
  Montserrat metadata declares `mtsdf`, a distance range, glyph metrics, and
  atlas dimensions. This typography differs from vanilla bitmap text.
- `GuiMotionAnimation` tracks alpha, scale, blur radius, closing state, and
  interaction eligibility using `System.nanoTime`. The inspected animation
  uses a 500 ms duration. `BaseScreen` supports a detached closing overlay.
  Copying the architecture does not require copying its duration, parallax,
  screen shattering, or motion blur.
- `UiBatch` and the font renderers group work and retain GPU/resource caches.
  Conversely, `GuiRendererMixin`, `GuiRenderStateMixin`, and
  `ShaderManagerEmbedMixin` show that this full renderer hooks engine internals.
  Its implementation cannot simply be dropped into every supported runtime.
- The archive is about 41.4 MiB. Font assets alone contribute about 8.6 MiB
  compressed; it also contains cosmetics, audio, native libraries, and nested
  dependencies unrelated to our UI. The archive size is not an estimate of the
  minimum cost of a small rounded-panel renderer.

Decision: a new, reduced rendering approach is appropriate for this mod.
Keep the stable old screens as the implementation baseline; do not reapply the
rejected primitive chrome. First validate a single compact settings screen with
smooth rounded surfaces, deliberate typography, real hover/open/close states,
and a readable non-blurred fallback before migrating the inspector and palette.
Use a small independently implemented rounded-surface pipeline and licensed
font/icon resources. Do not copy the supplied mod's classes, GLSL, atlases, or
branding, and do not import its general rendering engine or unrelated effects.

The documented native GUI state/pipeline route is supported by
[NeoForge's GUI element documentation](https://docs.neoforged.net/docs/1.21.11/rendering/screens/#guielementrenderstate).
Fabric's [GUI drawing documentation](https://docs.fabricmc.net/develop/rendering/gui-graphics)
also describes pipeline-backed texture drawing, scissors, and text. Verify
submission and shader-resource hooks separately for Fabric/NeoForge and each
Minecraft version; a shared visual specification is not a shared binary API.
Start with the supported single background blur, not Kawaii's multi-layer
capture machinery. Keep settings compact and inspector/palette full-screen.

The rejected primitive prototype was removed before implementing the current
shader-backed UI. Its recovery patch remains outside the repository.

## Implemented prototype (2026-10-01)

- Glass uses restrained translucent charcoal surfaces and a mint accent;
  Studio uses opaque work surfaces, an amber accent, and a top inspector toolbar.
  Classic retains native buttons as a fallback. The appearance menu is in compact settings.
- Rounded surfaces use a 32 x 32 signed-distance texture, a small fragment shader,
  and native GUI pipeline submissions. There are no new renderer mixins, raw
  OpenGL calls, per-panel framebuffers, or bundled general-purpose GUI engines.
- Manrope at a subtle 450 weight is rendered and cached by Minecraft's TTF provider. Small
  Lucide PNG icons are separately licensed. Native input editing, focus,
  narration, selection, IME, and tooltips remain on Minecraft widgets.
- Settings remain at most 286 x 304 logical pixels, with internal scrolling
  on shorter screens. The inspector and palette are full-screen unframed layouts,
  not nested bordered panels. Palette shape and element rows share symmetric
  sidebar margins; RGBA labels sit beside their own fields; sliders share track
  widths and contained thumbs. Isolation checkboxes have no black backing,
  and preset samples have no small panel borders.
- Opacity transitions use a monotonic clock: 190 ms opening, 130 ms closing,
  100 ms hover. They do not move hitboxes or animate world geometry. Reduced
  Motion and Classic resolve states immediately; a second close is immediate.
- Backdrop blur is optional and off by default. The native screen wrapper calls
  the background pass; the mod must not call it again from its content renderer.
  A real-client test caught and removed that double submission.
- The HSV plane is one GPU quad. Selection preview projection is reused until
  the segment list, camera, zoom, or viewport changes. Geometry and rendering
  budgets are not invalidated by hover/open/close animation.
- Popovers capture clicks, wheel input, keyboard focus, and underlying widget
  hover. Preset confirmation still requires the Apply click; Escape cancels.
- Shared helper mappings are maintained in `tools/sync-ui.mjs`; version-specific
  screens retain their platform APIs. A universal JAR ships shared assets once,
  from its 1.21.11 runtime.

See `UI_VALIDATION.md` for screenshots, measured timings, reproducible test
commands, and remaining coverage. Visual acceptance by the user is separate
from passing compilation and runtime tests.

## User intent

The visual reference supplied in the conversation is a translucent, softly edged
in-game interface with a clear navigation hierarchy, restrained highlighting,
contextual preview, and smooth transitions. Use it as a direction, not as an asset
or layout to copy. Do not copy another mod's branding, textures, or source code.

- The `O` settings screen stays approximately its current compact size. It must
  not become a full-screen dashboard.
- The `B` block inspector stays full-screen and must use the available space for
  search, sorting, filtering, counts, and isolation.
- The overlay palette editor stays full-screen with its existing real selection
  preview and controls.
- These screens should look related, while retaining their different layouts.
- The user must be able to switch between at least two genuinely different UI
  concepts in-game for comparison; a simple recolor does not count. Preserve the
  old look as a fallback until the new UI is validated.
- Open, close, hover, selection, and menu transitions should feel smooth, but
  they must never delay an urgent Escape or make controls feel unresponsive.

## Baseline Before Implementation

The paths below describe the six version-specific runtimes; the Fabric 26.2
files are convenient reference copies, not the sole implementation target.

| Area | Current implementation | Consequence |
| --- | --- | --- |
| Compact settings | `WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/screen/OverlaySettingsScreen.java` has a fixed 286 x 304 panel, vanilla `Button` widgets, and a dark blue-gray fill. | Its size is right for ordinary GUI scale, but its styling is not the requested design. A 304-pixel panel cannot fit a 180-pixel logical screen at 720p/4x without clipping or internal scrolling. |
| Block inspector | `WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/screen/SelectionBlocksScreen.java` draws a full-height centered panel capped at 980 logical pixels and about 72% of wide screens, with the game visible behind it. | It is not yet truly full-width. `panelWidth()` also forces at least 420 logical pixels even when the screen is narrower. Fix both issues before claiming full-screen or 3x/4x support. Its filter popover must not pass input to underlying rows. |
| Palette editor | `WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/screen/OverlayColorSettingsScreen.java` is full-screen, has a stacked layout below 700 x 420, and renders the actual selection preview. | Restyle its chrome without replacing the preview renderer, rebuilding preview geometry every frame, or reintroducing the earlier editor lag. |
| Persistence | `WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/WorldEditPointOverlayClient.java` loads and saves a simple key/value settings file. | Add version-tolerant UI style, blur, and reduced-motion keys there; missing keys must preserve existing configurations. |
| Packaging | `README.md` and `TESTING.md` describe one universal JAR per loader, each containing three game runtimes. | Any UI change must be ported and tested on Fabric and NeoForge for 1.21.11, 26.1.x, and 26.2. |

The existing selection renderer, block-isolation logic, clipboard preview,
WorldEdit/CUI handling, and configuration semantics are out of scope for a visual
redesign. Keep their behavior unchanged.

## Lessons from existing mods

1. [Meteor's `GuiTheme` and widget API](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/gui/GuiTheme.java) separates a component's behavior from its visual theme. [Its theme registry](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/gui/GuiThemes.java) persists the selected theme. Borrow the separation, not Meteor's implementation or its entire UI framework.
2. [Meteor's module widget](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/gui/themes/meteor/widgets/WMeteorModule.java) uses two bounded progress values for hover/active effects. [Its screen](https://github.com/MeteorDevelopment/meteor-client/blob/79a30a7c9ad459b9cfc1155c598aa58947db7fa5/src/main/java/meteordevelopment/meteorclient/gui/WidgetScreen.java) fades a root widget tree for opening/closing. Borrow the state-machine idea, but use elapsed monotonic time so a short transition has the same duration at 30 and 144 FPS.
3. [Axiom's editor documentation](https://github.com/Moulberry/AxiomDocumentation/blob/a09e479a0485e1b7a17f5547a713440bfa2f03f2/src/editor/windows/intro.md) keeps the world as the main view and makes other panels contextual. That is relevant to the full-screen inspector and palette editor. Its free docking system is not justified for our small number of screens.
4. [Wurst's ClickGUI screen](https://github.com/Wurst-Imperium/Wurst7/blob/deeeaff4c4fdbc641e173aa4f9f6cd4b169bb392/src/main/java/net/wurstclient/clickgui/screens/ClickGuiScreen.java) explicitly avoids blur. This shows that a custom, coherent widget style does not depend on a costly blur pass. It is a useful fallback, not a visual target.
5. [YACL's project documentation](https://github.com/isXander/YetAnotherConfigLib) demonstrates tabs, custom controls, keyboard access, and rich configuration descriptions. Its vanilla aesthetic and additional dependency do not solve our reference style by themselves. Do not add it solely to make this redesign.

These are architectural observations from public sources, not permission to
reuse their art or code. The supplied KAWAII screenshot is visual reference only;
no source or license for it was established.

## Chosen visual direction

Build a restrained tool UI rather than a cloned cheat client:

- **Glass concept:** translucent neutral-charcoal surface, a single soft
  background treatment, thin light border, modest corners (at most 8 logical
  pixels), off-white primary text, and a muted mint or cool-green active accent.
  The world remains recognizable; text must remain readable over bright terrain.
- **Studio concept:** more opaque, higher-contrast surface and denser controls.
  Its inspector uses a top toolbar rather than Glass's narrow navigation rail.
  This is a genuinely different layout for in-game comparison, not merely another
  color palette. Both share the same underlying settings/actions.
- **Legacy fallback:** keep the current screen available while validating the
  prototypes. A user can return to it instantly if a new design breaks in a
  modpack or at an unusual GUI scale.

Candidate colors are starting tokens, not approved final values: surface
`#24272C`, raised surface `#30363B`, primary text `#F3F5F2`, secondary text
`#B8C2C3`, active `#8FD7C1`, warning `#E4B875`. Do not let one hue dominate.
Tune opacity against light snow, dark caves, water, and shaders in actual game
screenshots. Use subtle separation and typography; avoid nested decorative cards,
large glowing outlines, gratuitous gradients, and permanently animated effects.

## Layout contracts

### Compact settings (`O`)

- Target the current 286-pixel width and 304-pixel maximum height in logical GUI
  coordinates. Clamp both dimensions to the actual screen bounds with a margin.
- At high GUI scale, keep the panel compact; use short sections or internal
  scrolling, not off-screen buttons. Never shrink text until it becomes unreadable.
- Preserve the existing overlay/grid parent-child relationship, clipboard toggle,
  ruler axes, render style, diagonal mode, tracking, palette access, and Done.
- Style binary controls as clear toggles or checkboxes, the render/mode choices as
  compact segmented or cycle controls, and palette access as an icon with tooltip.
- Do not change behavior when switching UI concepts. A menu style preference is
  not an overlay color preset.

### Full-screen block inspector (`B`)

- Use the whole safe area with small margins. No minimum panel width may exceed
  `screenWidth - 2 * margin`.
- Put search, filter, sort, and refresh in a stable toolbar. Keep the reset
  isolation command immediately above/near the left side of the list as requested.
- Keep selected block types pinned above other types regardless of sort mode.
  Preserve item icons, counts, loaded/unloaded state, and existing checkbox logic.
- The filter menu is a modal popover: hover, click, scroll, and keyboard focus
  cannot leak through to the list or controls behind it.
- At narrow logical widths, wrap/stack toolbar controls and keep the list
  scrollable. Do not merely scale the entire interface down.

### Full-screen palette editor

- Keep the real selection preview, shape switching, color/alpha fields, line
  widths, presets, and reset actions. New chrome must not alter preview geometry
  or input hit-testing.
- Keep the existing stacked layout for narrow/short logical screens, then audit
  every popover and numeric field at 3x/4x.
- The preview can remain live, but its cached geometry and rendering budget are
  independent of UI animations. Never rebuild it merely because a hover or
  opening animation advanced.

## Rendering and animation rules

Use the version's supported `Screen`/`GuiGraphicsExtractor`/widget APIs. Fabric
documents [custom screens](https://docs.fabricmc.net/develop/rendering/gui/custom-screens),
[custom widgets](https://docs.fabricmc.net/develop/rendering/gui/custom-widgets), and
[GUI drawing/scissors](https://docs.fabricmc.net/develop/rendering/gui-graphics).
NeoForge's [1.21.11 screen documentation](https://docs.neoforged.net/docs/1.21.11/rendering/screens/)
warns that `blurBeforeThisStratum` may be used only once per frame. Verify each
game runtime's method signatures before sharing source; do not rely on a class
that links an incompatible Minecraft API on another version.

- A small theme-token/style layer may render chrome; actual controls stay real
  widgets with focus, narration, tooltip, and keyboard support. Do not replace
  all interaction with untracked rectangle hitboxes.
- Prefer batched/small textures or simple GUI primitives for soft corners and
  borders. Avoid raw OpenGL calls, a new framebuffer per panel, runtime texture
  creation, or a shader dependency just for rounded corners.
- Treat backdrop blur as optional enhancement: at most one documented blur pass
  in the correct stratum, with a readable tinted fallback when unavailable or
  expensive. Measure it before enabling it by default. Never blur separately
  behind every row, tooltip, or panel.
- Animate a small number of properties: panel opacity and at most an 8-pixel
  entrance displacement, 140-220 ms; close, 100-170 ms; hover/selection,
  80-150 ms. These are prototype ranges, not evidence that any value feels right.
  Use a monotonic clock, normalized [0,1] progress, clamping, and one easing
  function. Do not update state proportionally to FPS without a real time delta.
- No continuous pulsing, parallax, large zoom, flashing highlight, or motion in
  the world preview. Preserve stable widget hitboxes during animation; if a
  moving panel temporarily makes them inaccurate, gate pointer input until it
  reaches its final location while still allowing Escape.
- On close, disable duplicate actions, finish cleanup exactly once, and restore
  the previous screen. A second Escape may close immediately. Preserve progress
  sensibly on resize/re-init and avoid a transition replay when returning from
  the palette editor.
- Add a persistent Reduced Motion / Animations Off option. In that mode, content
  and focus must be immediately available. This follows [W3C guidance on
  interaction-triggered animation](https://www.w3.org/WAI/WCAG21/Understanding/animation-from-interactions).

## Performance and dependency policy

- Keep the JARs small and keep one universal artifact **per loader**. Do not
  import a full GUI framework or a blur library without measured need and a
  verified build/loader matrix.
- Do not allocate a framebuffer, load textures, rebuild widgets, rescan blocks,
  regenerate preview meshes, or recalculate layout on every render frame.
  Recompute layout only on init/resize/content changes; interpolate only scalar
  animation state per frame.
- Scroll only visible inspector rows. Clip list content with the version's
  scissor API; make sure scissor state is always restored.
- Compare frame-time distributions with UI closed, Legacy open, Glass open with
  blur off, and Glass open with blur on. Test idle and while scrolling. Treat a
  reproducible >2 ms p95 UI regression at 1080p/2x as a reason to optimize or
  ship blur disabled, not as an acceptable cosmetic cost.

## Implementation order and gates

1. Capture baseline screenshots and frame times for all three current screens
   at GUI scale 2x, 3x, and 4x. Record the smallest logical screen encountered.
2. Add style selection and motion/blur preferences to the existing config with
   backward-compatible defaults. Introduce reusable tokens and small custom
   widgets, not a parallel general-purpose GUI framework.
3. Prototype Glass and Studio for compact settings. Both must retain every
   control and fit the smallest supported logical viewport. Add in-game switching.
4. Apply the chosen concepts to the full-screen inspector. Fix width clamping,
   toolbar wrapping, popover capture, and scrolling before polishing animation.
5. Restyle the palette editor chrome without changing preview logic. Check that
   preview rendering and UI transitions cannot amplify each other's workload.
6. Port/adapt each screen to all six runtimes, build both universal JARs, and run
   automated plus manual in-world checks. Do not publish a release on compile
   success alone.

## Acceptance checklist

- Settings remain compact; inspector and palette editor remain full-screen.
- Glass and Studio switch in-game, persist across restart, and differ in layout.
- Legacy remains available until the two prototypes pass manual review.
- All existing actions and configuration values survive style switching.
- Open/close/hover feel smooth at 30, 60, and high FPS, with no flicker.
- Reduced Motion turns off nonessential motion without hiding any controls.
- No click-through under filter, preset, or reset popovers.
- No clipping or overlap at 1280x720 and 1920x1080 with 2x/3x/4x GUI scales,
  in English and Russian, including long translated names.
- Tab/focus/Escape, mouse wheel, tooltip, and narration work with custom widgets.
- Test in worlds with no WorldEdit, local WorldEdit, remote CUI, and with
  representative Sodium/Iris/LabyMod combinations when available.
- Both universal JARs launch on the supported version matrix; render/selection
  behavior and current isolation/clipboard tests remain unchanged.

Do not mark this design implemented until visual screenshots and in-world input
tests verify it. Update this file when the user accepts or rejects a prototype.

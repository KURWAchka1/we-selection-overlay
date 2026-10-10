# WorldEdit Selection Overlay 1.8.0

## Added

- Optional in-world cuboid editing: eight directly draggable corner markers,
  six face handles, wheel nudging and snapped dragging, with a compact HUD.
- Select and drag a face anywhere on its surface, including up close or from
  inside the cuboid. The selected side stays highlighted and remembered.
- Grab a corner without the edit key and move it while freely looking/flying.
  Free grabs always use one-block steps; the explicit editor uses configurable steps.
- Move the whole selection without resizing it or moving world blocks, with
  independent cardinal wheel nudges and one-block snapping. While gripping the
  boundary or center marker, X/Y/Z immediately chooses the wheel axis, including
  vertical movement through terrain. Walking, flying and looking around never
  move the box; idle wheel retains native hotbar behavior.
- A compact axis triad appears in the corner during a Move grip. Thin lines and
  palette-colored X/Y/Z letters follow the camera orientation while grip
  directions stay fixed; the selected letter is emphasized without circles.
- Tap G to enter the chosen tool; tap again to apply the complete draft and exit.
  Hold G for 0.4 seconds to choose Resize or Move in a centered radial menu.
  Icon-only sectors show their names in the center; a bounded pointer replaces
  the cursor. Release over the center's Cancel hint to keep the current tool/draft.
- Configurable edit/menu key, normal step and fast step. Editing starts disabled
  with G as the default key; the fast modifier defaults to Left Shift.
  Existing custom bindings remain unchanged.
- Precise Resize freezes the camera; Move retains normal look, movement and
  Shift descent. LMB release finishes only a local explicit-editor drag;
  Escape cancels the complete uncommitted draft in both tools.
- The crosshair hides during active editing, free corner grabs and the radial
  menu, then returns to normal HUD behavior on apply, cancel or interruption.
- Shift accelerates precise Resize without lowering the player in flight.
  Free corner dragging retains normal flight descent and one-block steps.
- One authoritative selection adjustment on apply, Escape/interruption
  cancellation and stale-selection protection through WorldEdit's public API.
- Palette editor undo/redo with Ctrl+Z / Ctrl+Y (also Ctrl+Shift+Z), including
  colors, transparency, thickness, resets and confirmed presets. Each slider
  drag or numeric-field edit is one step; preview-camera motion is not recorded.
- Editing palette group with live preview, common contour color and X/Y/Z axis
  colors, transparency and thickness. Includes global thickness, presets,
  per-element/all resets and palette undo/redo.
- Standard and extending cuboids retain their selector type and ordered corners.
  Local drafts do not write blocks, scan the selection or add undo entries.
- Live in-world command preview while typing `//set`, `//replace`, `//walls`,
  `//faces` / `//outline`, `//overlay`, `//center` / `//middle`, `//naturalize`,
  `//hollow`, `//line`, `//curve`, and `//flora` in normal chat.
- Line/curve thickness and hollow shells (`-h`), hollowing thickness/fill,
  and flora density, with WorldEdit selection-type rules.
- Block states, legacy numeric IDs/data values and percentage mixtures through WorldEdit's parser,
  with a stable sample and a warning that execution rolls random placements again.
- Full-bright ghost textures, removal markers, and compact change statistics.
- Render-only suppression of replaced source blocks, including partial models;
  original world data/collisions stay unchanged and return when the draft closes.
- Command Preview toggle in settings and a configurable keybind (default `F8`).
- Complete previews inside the selection's loaded chunks, without block-count
  or fixed-distance caps; cancelled stale drafts and source revalidation.
- Immediate draft scheduling with coalesced input, shared-budget calculation,
  compact section palettes, reusable models, and persistent section GPU buffers.
- Incremental source-cell refresh for set/replace, stable unchanged random choices,
  and background expansion when more selection chunks become loaded.

## Changed

- Classic appearance shows only theme choices; blur/motion preferences are
  available in Glass/Studio and remain saved when switching themes.
- The deselect keybind no longer adds a "//deselect sent" overlay message.
- Clipboard preview is retained, off by default, and temporarily yields to a
  command draft without losing its setting, transforms, or cached clipboard.
- Selection editing temporarily pauses clipboard rendering and the regular grid;
  confirmed resizes preserve the chosen isolation block types.
- One universal Fabric JAR and one universal NeoForge JAR support Minecraft
  1.21.11, 26.1.x, and 26.2.

## Fixed

- NeoForge CUI packaging no longer collides with WorldEdit's shared protocol.
- NeoForge 26.x clipboard access uses its official NeoForge player/block adapter.

Clipboard/command preview requires integrated singleplayer WorldEdit. Remote
server CUI continues to support selection visualization, not command simulation
or clipboard access.
Preview never edits the world or history; Enter executes the unchanged command.
Interactive editing also requires integrated WorldEdit and currently supports
complete cuboid/extending-cuboid selections only.

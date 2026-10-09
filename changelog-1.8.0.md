# WorldEdit Selection Overlay 1.8.0

## Added

- Optional in-world cuboid editing: eight directly draggable corner markers,
  six face handles, wheel nudging and snapped dragging, with a compact HUD.
- Select and drag a face anywhere on its surface, including up close or from
  inside the cuboid. The selected side stays highlighted and remembered.
- Grab a corner without the edit key and move it while freely looking/flying.
  Free grabs always use one-block steps; the explicit editor uses configurable steps.
- Move the whole selection without resizing it or moving world blocks, with
  snapped view-plane dragging and wheel nudges along the camera's dominant axis.
- Tap G to enter the chosen tool; tap again to apply the complete draft and exit.
  Hold G for 0.4 seconds to choose Resize or Move in a centered radial menu.
  A bounded pointer replaces the cursor; release over the empty center to cancel.
- Configurable edit/menu key, normal step and fast step. Editing starts disabled
  with G as the default key; the fast modifier defaults to Left Shift.
  Existing custom bindings remain unchanged.
- The explicit editor freezes the camera. LMB release finishes only a local drag;
  Escape cancels the complete uncommitted draft in both tools.
- Shift accelerates explicit editing without lowering the player in flight.
  Free corner dragging retains normal flight descent and one-block steps.
- One authoritative selection adjustment on apply, Escape/interruption
  cancellation and stale-selection protection through WorldEdit's public API.
- Palette editor undo/redo with Ctrl+Z / Ctrl+Y (also Ctrl+Shift+Z), including
  colors, transparency, thickness, resets and confirmed presets. Each slider
  drag or numeric-field edit is one step; preview-camera motion is not recorded.
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

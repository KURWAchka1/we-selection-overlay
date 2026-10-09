# Changelog

## 1.8.0

### Added

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

### Changed

- The deselect keybind no longer adds a "//deselect sent" overlay message.
- Clipboard preview is retained, off by default, and temporarily yields to a
  command draft without losing its setting, transforms, or cached clipboard.
- Selection editing temporarily pauses clipboard rendering and the regular grid;
  confirmed resizes preserve the chosen isolation block types.
- One universal Fabric JAR and one universal NeoForge JAR support Minecraft
  1.21.11, 26.1.x, and 26.2.

### Fixed

- NeoForge CUI packaging no longer collides with WorldEdit's shared protocol.
- NeoForge 26.x clipboard access uses its official NeoForge player/block adapter.

Clipboard/command preview requires integrated singleplayer WorldEdit. Remote
server CUI continues to support selection visualization, not command simulation
or clipboard access.
Preview never edits the world or history; Enter executes the unchanged command.
Interactive editing also requires integrated WorldEdit and currently supports
complete cuboid/extending-cuboid selections only.

## 1.7.1

### Added

* Added an in-game interface design preview with Classic, Workbench, and Focus layouts.

### Changed

* Air block ids are ignored by isolation while the air-visualization design is being reworked.
* Reduced allocations in hot isolation render-state tracking.
* Release version is **1.7.1** for Minecraft **1.21.11**.

## 1.7.0

### Added

* Added WorldEdit clipboard preview for copied structures in singleplayer/local Fabric WorldEdit sessions.
* Added textured in-world preview for copied blocks before pasting.
* Added automatic preview updates when clipboard transforms change, including `//rotate` and `//flip`.
* Added a Clipboard Preview toggle in the overlay settings screen.

### Changed

* Clipboard preview reads clipboard, transform, and placement through the official WorldEdit API when the WorldEdit mod is loaded locally.
* Large previews use incremental scanning, capped rendering, and surface filtering to reduce freezes and frame cost.
* Clipboard preview is disabled by default and can be enabled only when needed.
* Release version is **1.7.0** for Minecraft **1.21.11**.

### Fixed

* Clipboard preview bounds use a thin outline only, without translucent blue walls that can flicker.

## 1.6.1

### Added

* Added a configurable keybind for quickly running WorldEdit `//deselect`.

### Fixed

* Hardened Sodium terrain/block entity rendering compatibility for Better Block Entities.

## 1.6.0

### Added

* Added the full-screen overlay palette editor with live preview, presets, per-element colors, transparency, and thickness.
* Added adaptive editor layout for high Minecraft GUI Scale values.

### Changed

* Documented recommended Minecraft GUI Scale: 2x.

## 1.5.1

### Added

* Added a nested overlay settings toggle to hide the internal selection grid while keeping the outer boundary and ruler visible.

### Changed

* Selected isolated blocks and fluids now force full brightness through vanilla chunk rendering and final vertex paths, not only shader/Sodium-like paths.

### Fixed

* Fixed block isolation in LabyMod/LabyFabric environments that bypass vanilla block render calls.
* Fixed selected isolated blocks staying too dark in non-Iris rendering.

## 1.5.0

### Added

* Added client-side block isolation mode in the block inspection screen.
* Added checkbox controls for selecting one or more block types to keep visible.
* Added a reset button above the block list to clear the isolation selection.
* Added selected block groups pinned above all other groups, regardless of sorting mode.
* Added fluid-aware hiding so water, lava, and similar rendered fluids disappear when they are not selected.
* Added bucket icons for fluid entries in the block list and filter menu without changing the stored block ids.
* Added Sodium support for block and fluid isolation rendering.

### Changed

* In isolation mode, non-selected blocks inside the WorldEdit selection are hidden visually on the client only.
* In isolation mode, selected blocks remain visible with their normal textures.
* In isolation mode, the internal selection grid is hidden while the outer selection boundary and ruler can remain visible.
* Isolation rendering now works even when normal overlay visibility is disabled.
* Selected blocks are brightened enough to keep their textures readable.

### Fixed

* Fixed selection overlay visibility by using the current Fabric world render event path and automatic selection source handling.
* Fixed overlay line flickering caused by an incorrectly short Gizmo lifespan.
* Fixed visual holes on the boundary between hidden blocks and neighboring blocks outside the selection.
* Fixed hidden block entities so block entity renderers disappear with their hidden blocks.
* Fixed fluid rendering paths so liquids do not remain visible after surrounding blocks are hidden.

## 1.4.3

### Baseline

* Stable Fabric client overlay for WorldEdit selections.
* Supports cuboid, polygon 2D, ellipsoid/sphere, cylinder, and polyhedron/convex selections.
* Includes Blocky and Smooth render styles.
* Includes dimension ruler, optional cuboid diagonals, overlay settings, and block inspection.
* Includes searchable block list with filters for `#air`, `#fluid`, and `#solid`.

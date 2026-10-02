# Changelog

## 1.8.0

### Added

- Live in-world command preview while typing `//set`, `//replace`, `//walls`,
  `//faces` / `//outline`, and `//overlay` in normal chat.
- Block-state patterns and percentage mixtures through WorldEdit's parser,
  with a stable sample and a warning that execution rolls random placements again.
- Full-bright ghost textures, removal markers, and compact change statistics.
- Command Preview toggle in settings and a configurable keybind (default `F8`).
- Bounded incremental calculation, cancelled stale drafts, source revalidation,
  and separate scan/render caps for large selections.
- Immediate draft scheduling with coalesced input, shared-budget calculation,
  reusable model templates, and complete-batch rendering without partial rebuilds.

### Changed

- Clipboard preview is retained, off by default, and temporarily yields to a
  command draft without losing its setting, transforms, or cached clipboard.
- One universal Fabric JAR and one universal NeoForge JAR support Minecraft
  1.21.11, 26.1.x, and 26.2.

### Fixed

- NeoForge CUI packaging no longer collides with WorldEdit's shared protocol.
- NeoForge 26.x clipboard access uses its official NeoForge player/block adapter.

Command-result preview requires integrated singleplayer WorldEdit. Remote
server CUI continues to support selection visualization, not command simulation.
Preview never edits the world or history; Enter executes the unchanged command.

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

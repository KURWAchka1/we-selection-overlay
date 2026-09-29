# Changelog

## 1.7.1

### Added

* Added an in-game interface design preview with Classic, Workbench, and Focus layouts.

### Changed

* Air block ids are ignored by isolation while the air-visualization design is being reworked.
* Reduced allocations in hot isolation render-state tracking.
* Release version is **1.7.1** for Minecraft **26.1.x**.

## 1.7.0

### Added

* Added WorldEdit clipboard preview for copied structures in singleplayer/local Fabric WorldEdit sessions.
* Added textured in-world preview rendering that follows the current WorldEdit placement position.
* Added automatic preview updates for clipboard transforms such as `//rotate` and `//flip`.
* Added a Clipboard Preview toggle in the overlay settings screen.

### Changed

* Clipboard preview uses the official WorldEdit clipboard/session API when the WorldEdit mod is loaded locally.
* Large clipboard previews are scanned and rendered with caps, caching, surface filtering, and model reuse to reduce frame cost.
* Clipboard preview is disabled by default and can be enabled in overlay settings.
* Release version is now **1.7.0** for Minecraft **26.1.x** without a `+26.1` suffix.

### Fixed

* Removed translucent clipboard preview bounds fill to avoid flickering blue walls around copied structures.

## 1.6.1+26.1

### Changed

* Ported the mod to Minecraft **26.1**.
* Updated the build target to Java **25**, Fabric Loader **0.19.3**, Fabric API **0.145.1+26.1**, and Fabric Loom **1.15**.
* Migrated the codebase to the current Minecraft 26.1 official namespace/build flow.
* Updated vanilla rendering hooks for the Minecraft 26.1 renderer changes.

### Fixed

* Fixed the packaged WorldEdit CUI protocol jar so the release includes the runtime jar instead of the dev-only jar.
* Fixed a world-entry crash caused by the brightness cache mixin targeting an old method name.

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

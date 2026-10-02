# Changelog

## 1.8.0

### Added

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
* Release version is **1.7.1** for Minecraft **1.21.11** on NeoForge.

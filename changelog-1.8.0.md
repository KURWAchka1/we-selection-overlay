# WorldEdit Selection Overlay 1.8.0

## Added

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

## Changed

- Clipboard preview is retained, off by default, and temporarily yields to a
  command draft without losing its setting, transforms, or cached clipboard.
- One universal Fabric JAR and one universal NeoForge JAR support Minecraft
  1.21.11, 26.1.x, and 26.2.

## Fixed

- NeoForge CUI packaging no longer collides with WorldEdit's shared protocol.
- NeoForge 26.x clipboard access uses its official NeoForge player/block adapter.

Command-result preview requires integrated singleplayer WorldEdit. Remote
server CUI continues to support selection visualization, not command simulation.
Preview never edits the world or history; Enter executes the unchanged command.

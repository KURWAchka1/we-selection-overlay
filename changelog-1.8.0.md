# WorldEdit Selection Overlay 1.8.0

## Added

- Optional in-world cuboid editing: eight directly draggable corner markers,
  six face handles, wheel nudging and snapped dragging, with a compact HUD.
- Click a face at a distance while holding the edit key. The selected side is
  highlighted and remembered; looking around does not switch it.
- Configurable hold key, normal step and fast step. Editing starts disabled
  with no assigned hold key; the fast modifier defaults to Left Shift.
- Shift accelerates editing without lowering the player in flight. Normal
  Shift behavior returns outside editing.
- One authoritative selection adjustment on release, Escape/interruption
  cancellation and stale-selection protection through WorldEdit's public API.
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

- Clipboard preview is retained, off by default, and temporarily yields to a
  command draft without losing its setting, transforms, or cached clipboard.
- Selection editing temporarily pauses clipboard rendering and the regular grid;
  confirmed resizes preserve the chosen isolation block types.
- One universal Fabric JAR and one universal NeoForge JAR support Minecraft
  1.21.11, 26.1.x, and 26.2.

## Fixed

- NeoForge CUI packaging no longer collides with WorldEdit's shared protocol.
- NeoForge 26.x clipboard access uses its official NeoForge player/block adapter.

Command-result preview requires integrated singleplayer WorldEdit. Remote
server CUI continues to support selection visualization, not command simulation.
Preview never edits the world or history; Enter executes the unchanged command.
Interactive editing also requires integrated WorldEdit and currently supports
complete cuboid/extending-cuboid selections only.

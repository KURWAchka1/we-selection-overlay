# UI tooling

The checked-in assets are ready to build. Normal release builds do not need
Node, sharp, Python, fontTools, or network access to regenerate UI resources.

- `sync-ui.mjs`: ports the canonical Fabric 26.2 UI helpers and compact settings
  to the other five runtimes, accounting for each Minecraft API.
- `polish-ui.mjs`: synchronizes targeted palette/inspector adjustments and
  translations while preserving each JSON key's existing formatting.
- `build-ui-assets.mjs`: regenerates the SDF tile and licensed icon/font assets.
  Needs Node with sharp; `WESO_TOOL_NODE_MODULES` can locate an installed sharp.
  Downloads upstream resources, so review their diffs and notices before use.
- `finalize-ui-font.py`: instantiates Manrope at weight 450 in all six modules.
  Needs fontTools. Run after asset generation; `--source <variable-font.ttf>`
  accepts an external upstream variable font. Do not package the variable TTF
  without this step: Minecraft does not apply its weight axis.
- `ui-test.init.gradle` and `ui-test/`: real Fabric 26.2 client UI fixture using
  the packaged universal JAR. Not included in releases.
- `test-ui-matrix.ps1`: sequential six-runtime settings/isolation regression
  checks. Needs JDK 21/25 and the NeoForge fixture worlds.

See `../UI_VALIDATION.md` for commands, screenshots, measurements, and limits.
For layout-only iterations, add `-Pui_fast=true` to the UI test command; this
skips CPU/FPS sampling but retains the interaction and screenshot checks.

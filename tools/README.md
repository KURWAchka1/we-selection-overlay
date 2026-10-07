# UI tooling

For interactive game testing, use the root `Test-Mod.cmd` or
`manual-test.ps1`. See [manual test instructions](MANUAL_TEST.md). This builds
and loads the current universal JAR, creates/reopens an independent flat test
world and grants the test player Creative/operator rights. `manual-world/`
contains its test-only bootstrap helper; it is never included in release JARs.
Normal manual runs do not execute the automated world-editing fixtures.

The checked-in assets are ready to build. Normal release builds do not need
Node, sharp, Python, fontTools, or network access to regenerate UI resources.

- `sync-ui.mjs`: ports the canonical Fabric 26.2 UI helpers and compact settings
  to the other five runtimes, accounting for each Minecraft API.
- `sync-selection-editing.mjs`: maps the canonical face-editing/input/HUD adapter
  to the other five runtimes and shares its translations/domain source set.
  `--restore-format` restores JSON whitespace from HEAD without changing values.
- `test-command-matrix.ps1`: checks both command preview and selection editing
  against the production universal JARs, including native input and EN/RU menus.
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

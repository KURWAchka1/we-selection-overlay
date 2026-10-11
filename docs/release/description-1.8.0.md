<p align="center">
<iframe width="560" height="315"
src="https://www.youtube.com/embed/nZlb22H432E"
title="WorldEdit Selection Overlay 1.8 overview"
frameborder="0" allowfullscreen>
</iframe>
</p>

[Watch the full overview on YouTube](https://www.youtube.com/watch?v=nZlb22H432E)

# WorldEdit Selection Overlay

**See your selection. Check your edits before Enter.**

A client-side WorldEdit addon for inspecting builds, previewing supported
commands and adjusting selection bounds directly in the world.

*The video covers 1.8.0. Check the download's version: older releases do not
include every feature below.*

## Preview Before You Commit

Type a supported command and inspect its textured preview inside the selection.
Change the material or press **Escape** to return to the original view.
Nothing changes in the world or WorldEdit history until you execute the command.

Includes `//set`, `//replace`, `//walls`, `//faces` / `//outline`, `//overlay`,
`//center`, `//naturalize`, `//hollow`, `//line`, `//curve` and `//flora`, with
supported block states, legacy numeric IDs and percentage mixtures.
Random mixtures are samples, not an exact prediction of the next roll.
Previews use loaded chunks; hollowing needs its complete source area loaded.

![Command preview: inspect a material change before Enter. Schematic illustration.](https://media.forgecdn.net/attachments/2038/575/01-command-preview-png.png)

## See Inside Your Build

Press **B** to search materials, compare counts and check loaded/unloaded cells.
Filters include `#air`, `#fluid` and `#solid`.

Tick one or several materials to hide everything else **only inside the
selection**. Chosen materials stay bright and appear first in the list.
Reset the checkboxes to restore the view. Hidden blocks still exist and collide.

![Material isolation: selected materials stay visible inside the selection. Schematic illustration.](https://media.forgecdn.net/attachments/2038/595/02-material-isolation-png.png)

## Adjust the Selection

Enable editing, then tap **G** to begin. Hold G briefly to choose **Resize** or
**Move**. Drag corners or faces; in Move, hold the box and scroll along
**X / Y / Z**. The 3D axis indicator follows your camera.

Tap G to apply; Escape cancels. **Only bounds move, not the building.**
Supports cuboid and extending-cuboid selections. Editing starts off.

## Check a Copy's Orientation

Enable **Clipboard Preview** to see your copy at its placement position.
While typing `//rotate` or `//flip` / `//mirror`, inspect the new orientation
without changing the real buffer. Escape restores its current orientation.
Clipboard Preview starts off; region-command previews take priority temporarily.

## Make It Your Own

- Cuboid / Extend, Polygon 2D, Ellipsoid / Sphere, Cylinder and Convex / Polyhedron.
- Blocky or Smooth selection rendering, optional ruler and independently hidden grid.
- Live 3D palette editor: colors, opacity, line thickness, presets and per-element resets.
- **Ctrl+Z / Ctrl+Y** in the palette; Glass, Studio and Classic menu styles.

## Installation and Compatibility

Choose **one JAR for your loader**, Fabric or NeoForge, and place it in `mods`.
Supported Minecraft versions: **1.21.11, 26.1, 26.1.1, 26.1.2 and 26.2**.
Java **21** for 1.21.11; Java **25** for 26.x. Fabric also requires Fabric API.

[Install WorldEdit](https://www.curseforge.com/minecraft/mc-mods/worldedit)
for local previews and editing. NeoForge's local WorldEdit on the 26.1 branch
requires **26.1.2 or newer**.

On servers, WorldEdit CUI provides selection visualization and local inspection;
it does not supply command simulation or the server clipboard. The game can
launch without WorldEdit, but selection data is needed to display an overlay.

Default keys: **O** settings, **B** inspector, **N** deselect, **F8** command
preview, **G** editing. All keybinds are configurable. Recommended GUI scale: **2x**.

Showcase location: [Celestia Town by Fireless007 / leolerenard](https://www.curseforge.com/minecraft/worlds/celestia-town).

License: **GPL-3.0-or-later**. [Source code and build instructions](https://github.com/KURWAchka1/we-selection-overlay/tree/1.8.0).
Contact: [ngocchaub81@gmail.com](mailto:ngocchaub81@gmail.com).

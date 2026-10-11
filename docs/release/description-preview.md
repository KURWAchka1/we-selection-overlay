<p align="center">
<iframe width="560" height="315"
src="https://www.youtube.com/embed/nZlb22H432E"
title="WorldEdit Selection Overlay 1.8 overview"
frameborder="0" allowfullscreen>
</iframe>
</p>

[Watch the full overview on YouTube](https://www.youtube.com/watch?v=nZlb22H432E)

# WorldEdit Selection Overlay

**See your selection. Look inside your build. Check an edit before Enter.**

A client-side WorldEdit addon with clear selection shapes, a dimension ruler,
block inspection and a live palette editor.

**1.8.0 preview:** the video also shows upcoming command previews and selection
editing. That update is not downloadable yet. Choose an available release under
**Files / Versions** for your exact Minecraft version and loader.

## See Inside Your Build

Press **B** to search materials, compare counts and inspect loaded/unloaded
cells. Use filters such as `#air`, `#fluid` and `#solid`.

Tick one or several materials to hide the others **only inside the selection**.
Chosen materials stay bright and appear first in the list. Reset the checkboxes
to restore the view. Hidden blocks still exist and collide.

![Material isolation: the same build with selected materials visible. Schematic illustration.](https://media.forgecdn.net/attachments/2038/595/02-material-isolation-png.png)

## Coming in 1.8.0

**Live command previews.** Type a supported command to inspect a textured draft
before running it. Change the material or press **Escape** to return to the
original view. Until Enter, the world and WorldEdit history stay unchanged.
Includes `//set`, `//replace`, shape and surface commands, block states, legacy
numeric IDs and percentage mixtures. Random mixtures show a sample, not an
exact prediction. Previews use loaded chunks.

![Command preview: inspect the proposed material change before Enter. Schematic illustration.](https://media.forgecdn.net/attachments/2038/575/01-command-preview-png.png)

**Edit selection bounds in the world.** Tap **G** to begin; hold it briefly to
choose **Resize** or **Move**. Drag corners or faces, or move along **X / Y / Z**
with the mouse wheel. A 3D axis indicator follows your camera. Tap G to apply;
Escape cancels. Only cuboid bounds move, not the building. Editing starts off.

**Check a copy's orientation.** Enable Clipboard Preview to see a copy at its
placement position. Typing `//rotate` or `//flip` / `//mirror` previews the
orientation without changing the real buffer. Clipboard Preview starts off.

## Make It Your Own

- Cuboid / Extend, Polygon 2D, Ellipsoid / Sphere, Cylinder and Convex / Polyhedron.
- Blocky or Smooth rendering, optional ruler and independently hidden grid.
- Live 3D palette: colors, opacity, thickness, presets and per-element resets.
- In 1.8.0: palette **Ctrl+Z / Ctrl+Y** and Glass, Studio or Classic menu styles.

## Get Started

Install the JAR matching your Minecraft version and loader into `mods`.
Fabric also needs Fabric API. Install
[WorldEdit](https://www.curseforge.com/minecraft/mc-mods/worldedit) for local
selection data and editing; on servers, WorldEdit CUI supplies the overlay and
local inspection, not command simulation or the server clipboard.

The game can launch without WorldEdit. An overlay needs selection data.
For 1.8.0, the prepared Fabric and NeoForge builds target **1.21.11 through
26.2**; Java **21** is needed for 1.21.11 and Java **25** for 26.x.
NeoForge's local WorldEdit on the 26.1 branch needs **26.1.2 or newer**.

Default keys: **O** settings, **B** inspector, **N** deselect.
In 1.8.0: **F8** command preview and **G** editing.
All keybinds are configurable. Recommended GUI scale: **2x**.

Showcase: [Celestia Town by Fireless007 / leolerenard](https://www.curseforge.com/minecraft/worlds/celestia-town).
Contact: [ngocchaub81@gmail.com](mailto:ngocchaub81@gmail.com).

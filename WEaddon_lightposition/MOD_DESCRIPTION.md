![weaddon](https://media.forgecdn.net/attachments/1592/332/worldedit_selection_overlay_300-png.png)

# WorldEdit Selection Overlay

Make your WorldEdit selections visible, readable, and predictable.

[Download WorldEdit](https://www.curseforge.com/minecraft/mc-mods/worldedit)

***

![Overview](https://media.forgecdn.net/attachments/1589/327/main1-png.png)

***

## What is this?

WorldEdit Selection Overlay is a **client-side visual addon** that shows your selection **exactly as it is** before you run any destructive commands.

No more guessing shapes.  
No more wrong sizes.  
No more accidental `//set air`.

You see everything in advance.

***

## Why you actually need this

WorldEdit gives you power, but **almost no visual feedback**.

This mod fixes that:

* See the real shape of your selection
* Read dimensions directly in-game
* Inspect blocks before editing
* Preview copied structures before pasting
* Isolate specific block types inside a selection
* Avoid mistakes that cost time

***

## In-Game Preview

![Selection Overlay1](https://media.forgecdn.net/attachments/1586/93/sphere-png.png) ![Selection Overlay2](https://media.forgecdn.net/attachments/1586/89/cyl-png.png) ![Selection Overlay1](https://media.forgecdn.net/attachments/1586/87/poly-png.png) ![Selection Overlay1](https://media.forgecdn.net/attachments/1586/91/convex-png.png)

The overlay renders your selection **directly in the world**:

* Exact bounds
* Real geometry
* Clear corner points

***

![Dimension Ruler](https://media.forgecdn.net/attachments/1592/55/2026-03-21_18-52-36-png.png)

## Built-in Dimension Ruler

Measure selections like in CAD tools:

* Clean readable size labels
* Always visible from your current angle
* Can follow your camera or stay fixed

***

## Clipboard Preview

Copy something with WorldEdit and preview the clipboard before pasting:

* Shows copied blocks as a textured in-world ghost
* Follows WorldEdit placement position
* Updates after clipboard transforms like `//rotate` and `//flip`
* Uses capped, cached rendering for large copies
* Disabled by default; enable it in overlay settings when needed

Clipboard preview requires the WorldEdit mod on the client/integrated server. Server-only CUI data can still show selections, but it does not expose clipboard contents.

***

## Live Command Preview

See a ghost of the result while typing `//set`, `//replace`, `//walls`,
`//faces` / `//outline`, or `//overlay`, before pressing Enter.

* Textured changed blocks, removal markers, and compact change counts
* Block states and percentage mixtures, with a stable random sample
* Quick toggle in settings or with configurable `F8`
* Clipboard preview pauses temporarily, then returns when chat closes

Requires integrated singleplayer WorldEdit. Random execution rolls again;
the displayed sample is not a promise of exact placements. Preview does not
edit the world; Enter runs your normal WorldEdit command.

***

## Overlay Palette Editor

Customize how the overlay looks directly in-game:

* Full-screen color editor with live selection preview
* Per-element color, transparency, and line thickness
* Ready-made presets for different visibility needs
* Reset controls for one element, thickness, colors, or everything

***

![Selection Blocks Screen](https://media.forgecdn.net/attachments/1592/63/2026-03-21_18-56-04-png.png)

## Inspect Before You Edit

Open the block viewer and see exactly what is inside:

* All blocks in the selection, including `air`
* Grouped and searchable
* Fast filtering with `#air`, `#fluid`, and `#solid`
* Chunk-aware loaded/unloaded counts
* Fluid entries use bucket icons for easier reading

Perfect before using:

* `//replace`
* `//set`
* `//stack`

***

## Block Isolation Mode

Need to find only one material inside a huge selection?

Select one or more block types in the block viewer. The mod hides every other block inside the selected WorldEdit region **on the client only**, leaving the real world untouched.

Useful for:

* Finding hidden ores, liquids, containers, or mistakes
* Checking what a `//replace` command will affect
* Inspecting complex builds layer by layer
* Viewing selected blocks with their normal textures

Details:

* Multiple block types can be selected at the same time
* Selected block groups stay pinned above the rest of the list
* A reset button clears the isolation selection quickly
* Fluids and fluid-like rendered content are hidden when they are not selected
* Selected blocks and fluids are rendered full-bright so their textures stay readable
* The selection boundary and ruler can stay visible while the internal grid is hidden
* Works even when the normal selection overlay visibility is disabled

Everything is visual only. Hidden blocks still exist, still collide, and are never changed by the mod.

***

## Features

* Real-time selection overlay
* Two render styles: **Blocky / Smooth**
* Optional internal selection grid toggle
* Textured WorldEdit clipboard preview, off by default
* Live command-result preview while typing in singleplayer
* Full-screen palette editor with live preview
* Custom colors, transparency, thickness, and presets
* Adaptive performance for large selections
* Optional diagonals for cuboid shapes
* Built-in dimension ruler
* Block inspection screen
* Client-side block isolation mode
* Quick `//deselect` keybind
* Fluid-aware rendering and bucket icons
* Full-bright isolated blocks and fluids for readable textures
* Glass, Studio, and Classic interface styles, with short animations and optional blur

***

## Supported Selection Types

* Cuboid
* Polygon 2D
* Ellipsoid / Sphere
* Cylinder
* Polyhedron / Convex

Works with everything WorldEdit sends via CUI.

***

## Controls

| Key | Action |
| --- | --- |
| `O` | Open overlay settings |
| `B` | Open block inspection screen |
| `N` | Run WorldEdit `//deselect` |
| `F8` | Toggle command-result preview |

All keybinds are configurable.

***

## Compatibility

* Singleplayer
* Fabric + WorldEdit
* NeoForge + WorldEdit
* LabyMod/LabyFabric when Fabric mods are enabled
* Paper / Spigot / Purpur if WorldEdit CUI data is available
* Sodium support for block and fluid isolation rendering
* Better Block Entities compatibility hardening

If the server does not send CUI data or supported WorldEdit selection updates, the overlay cannot know what to render.
Clipboard preview is available when the local WorldEdit mod exposes its clipboard API.

***

## Important

* Client-side only, safe to install anywhere
* Does not modify the world
* Hidden blocks are only hidden visually
* Recommended Minecraft GUI Scale: **2x**
* Requires WorldEdit selection data
* Clipboard preview requires local WorldEdit clipboard access

***

## Requirements

* Minecraft **1.21.11**, **26.1.x**, or **26.2**
* Fabric Loader + Fabric API, or NeoForge
* WorldEdit

***

## Installation

1. Install Fabric Loader and Fabric API, or NeoForge
2. Choose the matching Fabric or NeoForge mod JAR
3. Drop that JAR into `/mods`
4. Launch the game

***

## Quick Start

1. Make a WorldEdit selection
2. Press `O` to configure the overlay
3. Use the palette button to customize colors and thickness
4. Press `B` to inspect blocks
5. Use the checkboxes in the block viewer to isolate specific blocks
6. Enable clipboard preview in settings if you want to preview `//copy`, `//rotate`, and `//flip`

***

## Troubleshooting

* Make sure WorldEdit is installed
* Make sure CUI is enabled when playing on servers that require it
* Rejoin the world if the overlay does not appear
* If using Sodium, restart the game after replacing the mod jar

***

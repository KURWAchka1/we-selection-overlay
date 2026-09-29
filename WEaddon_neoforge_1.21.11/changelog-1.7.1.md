# Changelog 1.7.1

## Added

* Added an in-game interface design preview with Classic, Workbench, and Focus layouts.

## Changed

* Block isolation now dims non-selected blocks to about 30% alpha while keeping selected blocks bright.
* `minecraft:air`, `minecraft:cave_air`, and `minecraft:void_air` are ignored by block isolation instead of being rendered as a special contour.
* Replaced boxed render-state stacks with reusable primitive storage to reduce allocations in block rendering.
* Release version is **1.7.1** for Minecraft **1.21.11** on NeoForge.

# Changelog 1.7.1

## Added

* Added an in-game interface design preview with Classic, Workbench, and Focus layouts.

## Changed

* Block isolation now dims non-selected blocks to about 30% alpha while keeping selected blocks bright.
* `minecraft:air`, `minecraft:cave_air`, and `minecraft:void_air` are ignored by block isolation instead of being rendered as a special contour.
* Replaced boxed render-state stacks with reusable primitive storage to reduce allocations in block rendering.
* Release version is **1.7.1** for Minecraft **26.1.x**.

## Fixed

* Reworked the `VertexConsumer` fullbright/alpha mixin to avoid bootstrap crashes caused by fragile local-variable injections and interface-mixin fields.
* Disabled the unstable `VertexConsumer` interface mixin from Fabric/NeoForge mixin configs; the remaining brightness hooks stay on concrete renderer/world classes.

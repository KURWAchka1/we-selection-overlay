# Changelog 1.7.1

## Added

* Added Glass and Studio interface styles alongside Classic, selectable in settings.
* Added short open/close and hover animations, a Reduced Motion option, and optional background blur (off by default).

## Changed

* Block isolation hides non-selected blocks while keeping selected blocks bright.
* Settings remain compact; the block inspector and palette use the full screen with aligned controls, uncluttered sections, and responsive scrolling.
* Palette preview projection is cached until its geometry, camera, or viewport changes.
* `minecraft:air`, `minecraft:cave_air`, and `minecraft:void_air` are ignored by block isolation instead of being rendered as a special contour.
* Replaced boxed render-state stacks with reusable primitive storage to reduce allocations in block rendering.
* Release version is **1.7.1** for Minecraft **1.21.11**, **26.1.x**, and **26.2**, with one universal JAR per loader.

## Fixed

* Reworked the `VertexConsumer` fullbright/alpha mixin to avoid bootstrap crashes caused by fragile local-variable injections and interface-mixin fields.
* Disabled the unstable `VertexConsumer` interface mixin from Fabric/NeoForge mixin configs; the remaining brightness hooks stay on concrete renderer/world classes.

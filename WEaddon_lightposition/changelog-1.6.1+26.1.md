# Changelog 1.6.1+26.1

## Changed

- Ported WorldEdit Selection Overlay to Minecraft **26.1**.
- Updated the build target to Java **25**.
- Updated Fabric Loader to **0.19.3**.
- Updated Fabric API to **0.145.1+26.1**.
- Updated the build to Fabric Loom **1.15** and the current Minecraft 26.1 official namespace flow.
- Updated vanilla rendering hooks for Minecraft 26.1 renderer classes, including block, fluid, brightness, and fullbright paths.
- Kept the mod release version as **1.6.1** with the **+26.1** compatibility suffix.

## Fixed

- Fixed the bundled WorldEdit CUI protocol dependency so the release jar includes the runtime `worldeditcui-protocol-fabric-mc26.1-4.0.3.jar`.
- Fixed the `BlockModelLighter$Cache` brightness mixin for Minecraft 26.1 by targeting `getLightCoords`.
- Fixed the crash on world entry caused by the old brightness cache method target.

## Build

- Release jar: `worldedit_selection_overlay-1.6.1+26.1.jar`.
- Sources jar: `worldedit_selection_overlay-1.6.1+26.1-sources.jar`.

# Third-party code and APIs

- **WorldEdit** (EngineHub): optional runtime integration and compile-time API, not embedded in the Selection Overlay release JAR. WorldEdit is licensed under [GPL-3.0](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/LICENSE.txt).
- **WorldEditCUI-Protocol** (EngineHub): embedded as a version-scoped runtime dependency. Its source is licensed under [EPL-2.0](https://github.com/EngineHub/WorldEditCUI-Protocol/blob/main/LICENSE); EngineHub's [header](https://github.com/EngineHub/WorldEditCUI-Protocol/blob/main/HEADER) also permits GPL version 3 or later as a secondary license when the EPL conditions are met. The upstream license and header are included in [licenses](licenses/) and in both distributed JARs.
- **Minecraft, Fabric API, NeoForge, and other build dependencies** retain their respective upstream terms. They are not relicensed by this project.
- **Manrope UI (weight 450)**: a static instance of [Manrope from Google Fonts](https://github.com/google/fonts/tree/main/ofl/manrope), bundled under the SIL Open Font License 1.1. The upstream notice is packaged as `assets/worldedit_selection_overlay/font/manrope-ofl.txt`.
- **Lucide icons**: nine small PNG icons rasterized from [Lucide](https://github.com/lucide-icons/lucide), with its ISC/MIT notices packaged as `assets/worldedit_selection_overlay/textures/gui/ui/lucide-license.txt`.

The project-owned source files are offered under GPL-3.0-or-later. The license does not change the terms of third-party code.

The supplied Kawaii JAR was inspected as a rendering reference. No Kawaii code, shaders, fonts, textures, or other assets are redistributed.

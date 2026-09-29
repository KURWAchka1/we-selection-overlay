# Changelog 1.7.0

## Added

- WorldEdit clipboard preview for copied structures in singleplayer/local Fabric WorldEdit sessions.
- Textured in-world ghost rendering for copied blocks before pasting.
- Automatic preview updates when WorldEdit clipboard transforms change, including `//rotate` and `//flip`.
- Clipboard Preview toggle in the overlay settings screen.

## Changed

- Clipboard preview reads clipboard, transform, and placement through the official WorldEdit API when the WorldEdit mod is loaded locally.
- Large previews use incremental scanning, capped rendering, surface filtering, and cached block models to reduce freezes and frame cost.
- Clipboard preview is disabled by default and can be enabled in overlay settings.
- Release version is **1.7.0** for Minecraft **26.1.x**.

## Fixed

- Removed translucent clipboard preview bounds fill to avoid flickering blue walls around copied structures.

## Notes

- Server-only CUI data still supports selection overlay, but it does not expose clipboard contents for preview.

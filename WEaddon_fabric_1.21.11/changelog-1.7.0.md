# Changelog 1.7.0

## Added

* Added WorldEdit clipboard preview for copied structures in singleplayer/local Fabric WorldEdit sessions.
* Added textured in-world preview for copied blocks before pasting.
* Added automatic preview updates when clipboard transforms change, including `//rotate` and `//flip`.
* Added a Clipboard Preview toggle in the overlay settings screen.

## Changed

* Clipboard preview reads clipboard, transform, and placement through the official WorldEdit API when the WorldEdit mod is loaded locally.
* Large previews use incremental scanning, capped rendering, and surface filtering to reduce freezes and frame cost.
* Clipboard preview is disabled by default and can be enabled only when needed.
* Release version is **1.7.0** for Minecraft **1.21.11**.

## Fixed

* Clipboard preview bounds use a thin outline only, without translucent blue walls that can flicker.

## Notes

* Server-only CUI data still supports selection overlay, but it does not expose clipboard contents for preview.

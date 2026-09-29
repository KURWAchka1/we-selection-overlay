# 1.7.1: Universal Fabric Packaging

- One Fabric download for Minecraft 1.21.11, 26.1.x, and 26.2.
- Fixed conflicting runtime dependencies and inconsistent runtime versions.
- Loads only the matching game's implementation and CUI protocol.
- Keeps the CUI protocol shared with WorldEdit instead of registering a private copy.
- Builds from current version-specific outputs and checks release metadata and Java compatibility.

Fabric API must still match the installed Minecraft version. Use Java 21 for
1.21.11 and Java 25 for 26.x. Install the separate NeoForge artifact on NeoForge.

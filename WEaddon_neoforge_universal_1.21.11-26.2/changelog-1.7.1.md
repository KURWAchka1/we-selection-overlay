# 1.7.1: Universal NeoForge Packaging

- One NeoForge download for Minecraft 1.21.11, 26.1.x, and 26.2.
- Synchronized the displayed mod version with the actual release version.
- Builds from current version-specific outputs instead of archived JARs.
- Checks runtime metadata and keeps the universal bootstrap compatible with Java 21.

Use the NeoForge version matching Minecraft, with Java 21 for 1.21.11 and Java 25
for 26.x. Fabric API is not required. Fabric uses a separate artifact.

# WorldEdit Selection Overlay: Universal Fabric

One Fabric JAR for Minecraft 1.21.11, 26.1 / 26.1.1 / 26.1.2, and 26.2.
NeoForge has a separate universal JAR in the sibling NeoForge project. Install
only the JAR for your loader, never both.

## Requirements

- Minecraft 1.21.11: Java 21+, Fabric Loader 0.18.4+, Fabric API for **1.21.11**.
- Minecraft 26.x: Java 25+, Fabric Loader compatible with that game, Fabric API
  for **the same Minecraft version**. Loader 0.19.3 is used for 26.x smoke tests.
- Local WorldEdit is optional for startup; clipboard preview needs local WorldEdit.
- Server selections require WorldEdit CUI data. No server-side changes are bundled.

Do not install Fabric API 0.158.0+26.2 on Minecraft 1.21.11. The universal mod does
not make Fabric API, HPShow, or other installed mods cross-version compatible.
HPShow 2.0.0 was checked separately with Loader 0.19.3, the matching Fabric API,
WorldEdit 7.4.2, and this JAR: it selected its 1.21.11 runtime correctly. No HPShow
files or dependency overrides were changed.

## Packaging

The bootstrap is Java 21 bytecode. Each game implementation has its own relocated
package; only the current game's entrypoints and mixins are loaded. Assets are
stored once. Fabric API and Minecraft are never bundled.

EngineHub's CUI protocol is kept in its original public namespace as a normal
Fabric nested dependency, not a second private copy. The three small protocol
JARs have mutually exclusive Minecraft predicates and explicit Java requirements.
Only their dependency metadata is narrowed; their code and license are retained.

## Build And Check

Build the version-specific projects first, then the two universal projects:

```powershell
.\tools\build-release.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
```

`build` runs artifact tests before copying the release to `libs/1.7.1`.
They check runtime input versions, entrypoints, mixin files, Java bytecode versions,
protocol selection, and absence of old runtime dependencies or bundled Fabric API.

For a real Fabric production launch, run in a version-specific Fabric project:

```powershell
.\gradlew.bat universalProductionClient -I ../WEaddon_fabric_universal_1.21.11-26.2/tools/production-smoke.init.gradle
```

Add `-PsmokeWorldEdit=true` to include WorldEdit. Test profiles are isolated from
the user's Minecraft installation. A successful startup is not a complete
in-world visual or modpack compatibility test.

## References

- [Fabric dependency and nested-JAR metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json)
- [Fabric production run tasks](https://docs.fabricmc.net/develop/loom/production-run-tasks)
- [EngineHub WorldEditCUI Protocol](https://github.com/EngineHub/WorldEditCUI-Protocol)

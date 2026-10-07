# WorldEdit Selection Overlay

Client-side selection visualization and inspection for WorldEdit. The current release line is **1.8.0**, adding live command-result preview in integrated singleplayer WorldEdit sessions.

## Supported release targets

| Minecraft | Java | Fabric | NeoForge |
| --- | --- | --- | --- |
| 1.21.11 | 21 | Supported | Supported |
| 26.1.x | 25 | Supported | Supported |
| 26.2 | 25 | Supported | Supported |

There is **one universal JAR per loader**, not one JAR that loads on both Fabric and NeoForge. Each loader's JAR contains the three supported Minecraft runtimes and selects the matching runtime at launch. This does **not** imply support for every 1.21.x or 26.x version.

The mod can launch without WorldEdit; WorldEdit-dependent features need a compatible WorldEdit installation or CUI data from a server. Clipboard and command-result previews require an integrated/local WorldEdit session. Previews and isolation never modify world blocks or undo history. Typing a supported command temporarily takes priority over clipboard rendering without changing its preference or cache.

## Source layout

| Directory | Purpose |
| --- | --- |
| `WEaddon_fabric_1.21.11` | Fabric runtime for Minecraft 1.21.11 |
| `WEaddon_lightposition` | Fabric runtime for Minecraft 26.1.x |
| `WEaddon_fabric_26.2` | Fabric runtime for Minecraft 26.2 |
| `WEaddon_neoforge_1.21.11` | NeoForge runtime for Minecraft 1.21.11 |
| `WEaddon_neoforge_26.1` | NeoForge runtime for Minecraft 26.1.x |
| `WEaddon_neoforge_26.2` | NeoForge runtime for Minecraft 26.2 |
| `WEaddon_fabric_universal_1.21.11-26.2` | Fabric bootstrap, version selection, packaging, tests |
| `WEaddon_neoforge_universal_1.21.11-26.2` | NeoForge bootstrap, version selection, packaging, tests |
| `shared/command-preview` | Memory-only command engine shared by all six runtimes |
| `shared/neoforge-cui` | Shared-protocol bridge and private fallback registration |

The packagers keep incompatible Minecraft classes in separate relocated runtime packages. Runtime detection loads only the matching package. Fabric bundles version-scoped CUI protocol metadata. NeoForge uses the installed shared protocol or a privately relocated, version-matched fallback to avoid split-package conflicts. See the packager READMEs and `TESTING.md` for details and test coverage.

## Build on Windows

Install JDK 21 and JDK 25. Then run from this repository root:

```powershell
.\WEaddon_fabric_universal_1.21.11-26.2\tools\build-release.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
```

The script builds all six runtimes and both universal packagers, including their unit tests. Release artifacts are written under each packager's `libs/1.8.0/` and are intentionally ignored by Git. Run the in-game test harnesses described in `TESTING.md` before publishing a release.

## Try the current local mod

Double-click **`Test-Mod.cmd`**, then choose Fabric or NeoForge and a Minecraft
version. It detects JDK 21/25, builds the selected loader's three runtimes and
universal JAR, runs its packaging tests, and launches that JAR with compatible
WorldEdit. Initial setup may download dependencies. Each target has its own
persistent test directory under `build/manual-test/`; normal launcher profiles,
player worlds and automated fixture worlds are not used.

Create a disposable Creative world with cheats enabled. See
[manual test instructions](tools/MANUAL_TEST.md) for commands, logs, Java overrides
and preparation without opening a game window.

## Documentation

- [User-facing description](WEaddon_lightposition/MOD_DESCRIPTION.md)
- [Changelog](WEaddon_lightposition/CHANGELOG.md)
- [1.8.0 release notes](changelog-1.8.0.md)
- [Command preview behavior and limits](COMMAND_PREVIEW.md)
- [Feature roadmap](FEATURE_ROADMAP.md)
- [UI redesign research and implementation contract](UI_REDESIGN_RESEARCH.md)
- [UI screenshots, tests, and performance measurements](UI_VALIDATION.md)
- [Fabric packaging and tests](WEaddon_fabric_universal_1.21.11-26.2/README.md)
- [NeoForge packaging and tests](WEaddon_neoforge_universal_1.21.11-26.2/README.md)
- [Current verification](TESTING.md)

## License

Copyright (c) 2026 Karefanchik. Project-owned source code is licensed under [GPL-3.0-or-later](LICENSE). Bundled and optional dependencies retain their own licenses; see [third-party notices](THIRD_PARTY.md). The repository is currently private and should not be made public until the release and license audits are complete.

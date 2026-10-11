# Release Source Access

Project-owned code is licensed under GPL-3.0-or-later. Copyright remains with
Marshal_RF; opening the repository does not place the code in the public domain.
Third-party components retain the terms recorded in [THIRD_PARTY.md](THIRD_PARTY.md).

## Version 1.8.0

The complete project tree, runtime sources, resources, packaging transformations,
Gradle wrappers, dependency versions and build scripts are preserved at:

- [Source tree, tag 1.8.0](https://github.com/KURWAchka1/we-selection-overlay/tree/1.8.0)
- [Download the source ZIP](https://github.com/KURWAchka1/we-selection-overlay/archive/refs/tags/1.8.0.zip)

Install JDK 21 and JDK 25, then follow the root README's Windows build command.
The script builds six runtimes, runs their unit tests and builds the two loader
packagers. It downloads declared dependencies from their official repositories;
no local Minecraft save, captured video, IDE state or author credentials are
needed to build the mod. Tests that launch Minecraft are separate opt-in tools.

Expected player artifacts:

| Loader | SHA-256 |
| --- | --- |
| Fabric | `912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0` |
| NeoForge | `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c` |

WorldEdit is an optional separately installed dependency, not copied into these
JARs. The bundled CUI protocol versions and their relocation/metadata changes
are specified in the runtime and universal packager builds. Upstream protocol
source: [WorldEditCUI-Protocol](https://github.com/EngineHub/WorldEditCUI-Protocol).
The upstream EPL license and secondary-license header are retained in both JARs.

## Publication Check

On 2026-10-11, Gitleaks 8.30.1 scanned all 34 existing Git commits with its default
rules and found no secrets. A tracked-path check found no worlds, launcher
accounts, private keys, logs or production video/audio. This is a scoped check,
not a guarantee that automated scanning detects every possible disclosure.
Commit author names/emails, public project contacts and development documentation
remain part of the public source history.

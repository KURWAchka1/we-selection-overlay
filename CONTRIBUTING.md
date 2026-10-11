# Contributing

WorldEdit Selection Overlay supports six Minecraft/loader runtimes packaged into two universal JARs. A change to shared behavior should be checked across all six runtimes, not only the version used for development.

## Before changing code

- Open the root [README](README.md) for supported game versions, Java requirements, and the source layout.
- Use the codebase knowledge graph for code discovery when available; see [AGENTS.md](AGENTS.md). Re-index a runtime after moving or renaming classes.
- Keep loader- and Minecraft-specific APIs inside their respective runtime projects. The two packagers contain only bootstrapping and artifact checks.
- Do not add a hard WorldEdit dependency to client startup. Local WorldEdit integration is optional; remote servers may only provide CUI selection data.
- Do not commit downloaded dependencies, generated classes, game runs, logs, screenshots, or release JARs.
- Client JAR changes require a new verified release version; never overwrite a published artifact/tag. See the [fix-release policy](docs/release/FIX_RELEASE_POLICY.md). Experimental bridge candidates are not client fix releases.

## Verify

Build with JDK 21 and JDK 25 using the root README command. The release script builds all six runtimes and runs both packagers' artifact tests. For rendering or selection changes, also run the packaged-client tests described in each packager's `TESTING.md` and include the game version, loader version, and renderer (vanilla/Indigo/Sodium) in the report.

For a bug report, include the exact mod JAR name, Minecraft and loader versions, other rendering mods, and the relevant `latest.log` or crash report. Avoid publishing access tokens or personal paths from logs.

## Pull requests

Explain the user-visible behavior, affected runtimes, verification performed, and any known limitation. Prefer small changes with matching tests over unrelated refactors. Do not update release documentation or publish binaries before the supported configurations pass.

Project-owned source code is [GPL-3.0-or-later](LICENSE); bundled components keep their upstream terms as described in [third-party notices](THIRD_PARTY.md).

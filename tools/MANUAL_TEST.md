# Manual Test Launcher

## Quick Start

1. Install JDK 21 and JDK 25. Both are needed to build a universal loader JAR.
2. Double-click `Test-Mod.cmd` in the repository root.
3. Choose Fabric or NeoForge (or both), then an exact Minecraft version.
   The test world is created and opened automatically: flat terrain, Creative,
   peaceful difficulty, commands enabled and operator permissions.

The launcher builds current local sources, including uncommitted changes. It
does not download a published overlay release, switch Git branches, overwrite
normal launcher profiles, or run the destructive automated world fixtures.
Incremental Gradle builds reuse unchanged outputs, but their inputs are still
checked on every launch. The selected loader's three native runtimes and its
universal packager must share `mod_version`; mismatches stop the launch.

Normal invocation builds and then opens Minecraft automatically. `BUILD
SUCCESSFUL` messages for the individual modules do not mean the launcher has
finished. Only `-PrepareOnly` builds without opening the game; `-PlanOnly`
prints the plan without building. The console prints the selected mode.
The game launch task always runs, including repeated launches with unchanged
sources. Compilation and dependency preparation still use Gradle's caches.

| Minecraft | Java | Fabric WorldEdit | NeoForge WorldEdit |
| --- | --- | --- | --- |
| 1.21.11 | 21 | 7.4.2 | 7.4.2 |
| 26.1 | 25 | 7.4.3 | Not installed: compatibility-only test |
| 26.1.1 | 25 | 7.4.3 | Not installed: compatibility-only test |
| 26.1.2 | 25 | 7.4.3 | 7.4.3 |
| 26.2 | 25 | 7.4.5 | 7.4.5 |

These are exact game versions, not interchangeable menu labels. The version
catalog is `manual-test-versions.json`; defaults come from the native modules'
`gradle.properties`. Dependencies are pinned, not automatically upgraded.
The official WorldEdit 7.4.3 JAR requires Minecraft 26.1.2+ on NeoForge, despite
its broader distribution-page labels. Early NeoForge profiles therefore test
addon startup only; local WorldEdit previews cannot be tested there. The launcher
does not remove dependency restrictions. Earlier 1.21.x games are not supported.
Selecting both loaders or all versions launches clients sequentially, never together.

Fabric uses Loom's [production run task](https://docs.fabricmc.net/develop/loom/production-run-tasks).
NeoForge uses the project's existing packaged-JAR runtime classpath and the
official [ModDevGradle run API](https://github.com/neoforged/ModDevGradle#runs).
Both load the universal artifact, not the native mod's source-set output.
These are local test clients, not authenticated launcher profiles; this tool
does not log in to Microsoft or promise authenticated remote-server access.

## In Game

- `O`: compact overlay settings.
- `B`: selection block inspector and isolation.
- `F8`: command preview toggle.
- Select two points with `//wand`, or use `//pos1` and `//pos2`.
- Type `//set diamond_block` or `//replace grass_block dirt` without submitting
  to inspect the live result. Escape cancels the draft; Enter really edits the world.
- Use `//copy` and enable clipboard preview in settings to check copied content.

These are default keybinds; saved test settings may override them. Command and
clipboard previews need the local singleplayer WorldEdit session. Clipboard
preview starts disabled. Do not use a valued world to test editing commands.

## Files And Performance

Each exact loader/version has its own persistent directory:
`build/manual-test/instances/<loader>-<version>/`. Its automatic save is
`saves/weso-test-world/`. For example, Fabric 26.1 and Fabric 26.1.1 never share
a world; Fabric and NeoForge also use independent saves. Later launches reopen
the same save and keep edits, inventory, position and game settings. The test
player's Creative mode and full operator permissions are restored each launch.

The small test-only helper uses Minecraft's native create/open flows, including
save locking, recovery and version checks. Existing paths are opened, never
replaced or silently regenerated. Cancelling a recovery dialog does not cause
an automatic retry loop. Returning to the title screen after playing stays there.
The helper becomes idle once setup finishes; it has no rendering or world scans.
It is not included in the overlay's release JAR.

Older `build/manual-test/<loader>-<family>/` directories remain untouched. New
instance directories deliberately avoid their saves, because the old NeoForge
`26.1` directory actually contained a 26.1.2 game. No world is downgraded or
migrated automatically. The launcher is single-instance to avoid overlapping
builds and opening the same save twice.

Game `latest.log` and crash reports are inside that target directory. Combined
build/launch logs are timestamped under `build/manual-test/logs/`. The console
prints the built JAR's SHA-256 and exact paths. A failed build stops the launch
instead of silently running the previous JAR. These local files are Git-ignored.

The test window starts at 1280x720 with a 2 GiB heap. On the first launch only,
the launcher initializes a 100 FPS cap, GUI scale 2, and view/simulation distances
8. Later changes to `options.txt` are preserved. No extra render effect or
profiling fixture is installed. Automated regression suites are still separate;
see the root `TESTING.md`.

## PowerShell Options

From the repository root:

```powershell
# Same interactive launcher without the double-click wrapper.
.\tools\manual-test.ps1

# Choose the exact version directly, with compatible local WorldEdit.
.\tools\manual-test.ps1 -Loader neoforge -Version 26.1.2

# Sequentially test every listed version on both loaders.
.\tools\manual-test.ps1 -Loader all -Version all

# Inspect the target/build plan; do not build, download, write settings or launch.
.\tools\manual-test.ps1 -Loader fabric -Version 26.2 -PlanOnly

# Build, resolve mod dependencies and validate the launch task without opening Minecraft.
.\tools\manual-test.ps1 -Loader fabric -Version 1.21.11 -PrepareOnly

# Ask Gradle to reuse cached dependencies.
.\tools\manual-test.ps1 -Loader neoforge -Version 26.2 -Offline

# Override autodetection for custom JDK locations.
.\tools\manual-test.ps1 -Java21 'C:\JDKs\jdk-21' -Java25 'C:\JDKs\jdk-25'

# Script unit checks, without launching Minecraft.
powershell.exe -NoProfile -File .\tools\test-manual-launcher.ps1

# Automated bootstrap/save checks. Uses separate verification worlds, then exits.
# Run twice to verify reopening, saved blocks and restored Creative/operator rights.
.\tools\manual-test.ps1 -Loader all -Version all -VerifyWorld
```

JDK discovery checks `WESO_JAVA_21` / `WESO_JAVA_25`, `JAVA_HOME`, the Java on
`PATH`, common Program Files installations, and `%USERPROFILE%\.jdks`. A JRE or
wrong major version is rejected. Explicit paths take priority. The script
restores `JAVA_HOME`, `PATH` and its working directory after each Gradle call.

`-Offline` applies to Gradle dependency resolution, not an operating-system
network block. Minecraft services, NeoForm setup and mod update checks may
still contact their servers. Local Fabric test identities can produce expected
Realms/authentication warnings; they do not prevent singleplayer testing.

If PowerShell's execution policy prevents running the `.ps1`, use `Test-Mod.cmd`.
The wrapper changes the policy only for its child PowerShell process, not the
computer or user policy. Managed-machine policies may still prevent execution.

`-VerifyWorld` never uses manual instance saves. It creates disposable worlds
under `build/manual-test/verification/`, checks permissions, leaves a probe block,
switches the test player to Survival, removes its explicit operator entry and
closes normally. A second run must recover the same block and restore Creative
and operator rights. Do not move valued saves into these verification directories.

# Fabric 1.20.4 Client Port

The 1.8.0 Fabric 1.20.4 artifact is a standalone client JAR based on the
published 1.8.0 client, not the development bridge runtime. It deliberately
does not depend on any experimental bridge source set. The modern universal
JARs stay unchanged.

## Exact Build And Test Tuple

| Component | Version |
| --- | --- |
| Minecraft | 1.20.4 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.97.3+1.20.4 |
| Yarn | 1.20.4+build.3 |
| Loom / Gradle JVM | 1.15.5 / 21 |
| Player JVM | 17 |
| Local WorldEdit | 7.3.0 (Modrinth version `ZOhVauWn`) |

WorldEdit is not included in the JAR. Its 7.3.0 release lists Fabric and Forge;
this port only targets Fabric. No claim is made for NeoForge, Forge, or other
Minecraft versions.

## Compatibility Decisions

- Use the legacy Fabric packet-buffer API for `worldedit:cui`. The UTF-8
  `v|3` handshake and pipe-delimited replies follow the pinned EngineHub
  [server receiver](https://github.com/EngineHub/WorldEdit/blob/7.3.0/worldedit-fabric/src/main/java/com/sk89q/worldedit/fabric/net/handler/WECUIPacketHandler.java)
  and [event sender](https://github.com/EngineHub/WorldEdit/blob/7.3.0/worldedit-fabric/src/main/java/com/sk89q/worldedit/fabric/FabricPlayer.java).
  Do not register the modern typed CUI codec on this runtime.
- Retain shared geometry, command simulation and selection transactions. Replace
  Java 21 collection/clamp conveniences with Java 17 equivalents where shared.
- Use Fabric's [1.20.4 world-render events](https://github.com/FabricMC/fabric/blob/1.20.4/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents.java),
  legacy `VertexBuffer` for cached command meshes, and
  [core shader registration](https://github.com/FabricMC/fabric/blob/1.20.4/fabric-rendering-v1/src/client/java/net/fabricmc/fabric/api/client/rendering/v1/CoreShaderRegistrationCallback.java)
  for UI surfaces. The glass style offers dimming on this runtime.
- Isolation changes only the render view. Selected vanilla/Indigo meshes use
  full-bright lighting; alternate terrain renderers need separate validation.

## Reproduction

Run `tools/test-fabric-1204.ps1 -Java21 <jdk-21> -Java17 <jdk-17>` on Windows.
It verifies the LWJGL 3.3.2 cursor guard before running the remapped JAR on a
private desktop. Both profiles create fresh disposable worlds. Logs and
framebuffer captures are under `build/fabric-1204-test/`.

## Verification

On 2026-11-13, the production Fabric launcher ran the remapped client in
fresh integrated worlds, with and without WorldEdit. Verified the settings
and palette screens, automatic legacy CUI handshake, block counts and visual
isolation, `//replace 1 stone_button` without a world edit, `//copy`, typed
`//rotate 90` with changed preview bounds, typed `//flip east`, a cuboid
selection transaction without block movement, and `//deselect`. The native
LWJGL cursor probe passed and desktop cursor confinement stayed unchanged.

Screenshots confirmed visible selection edges and ruler, isolation that
brightens the retained stone and hides its unselected neighbor, and a button
model replacing the hidden stone in the command preview. This was a smoke
test of the default renderer and a two-block selection, not a Sodium/Iris,
dedicated-server, every-command, or large-selection performance matrix.

The packaging gate checks the exact Minecraft/Java metadata, Java 17 class
versions, mixin entries, and absence of WorldEdit, test classes, nested JARs
and server bridge code.

Final test run: `build/fabric-1204-test/203a045d05d4455caefd1b90c06e4ffd/`.
Both profiles passed on the same 568,729-byte client JAR, originally labeled
1.8.1, SHA-256
`ba70e04afc95381d678b43c7f1f0eabca3725e0005b9719ab0dfb4b26bd2288a`.
The 1.8.0 rebuild passed the packaging gate, and a comparison of both JARs
found no changes to their entries except the version in `fabric.mod.json`.
The 1.8.0 Fabric 1.20.4 JAR has SHA-256
`d1b43b99fd01f7679b91230bcb661493c29ea3f5797d315971b27f8d2d1401ad`.
The published Fabric and NeoForge 1.8.0 artifact hashes still match
[the release record](../../SOURCE_ACCESS.md#version-180).

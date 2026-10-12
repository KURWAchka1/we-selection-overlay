# Engineering Source Ledger

Checked: **2026-10-11**. Only upstream documentation/source is used as technical
authority below. Search results, third-party wikis and generated repository
summaries are not API contracts. No external implementation code was copied.
`latest`, `current` and branch links are mutable; recheck at implementation time.

WorldEdit annotated release tags were dereferenced through GitHub's public API:

| Release | Commit |
| --- | --- |
| 7.4.2 | `eb8e82c3d8843bed3f71e82ea974122415645178` |
| 7.4.3 | `78babeb2c31b4d971a0d0a85578b901b8bbbeb25` |
| 7.4.5 | `b8dc4c1d9120065cad9abfb3add5b05f131a331f` |

These pins identify source, not binary hashes or proof of bridge compatibility.

## S01 WorldEdit API Setup

[Official API guide](https://worldedit.enginehub.org/en/latest/api/).
Use the core/platform artifacts and the installed platform adapter. Match the
resolved WorldEdit version; do not bundle a second WorldEdit instance.

## S02 WorldEdit Local Sessions

[LocalSession concepts](https://worldedit.enginehub.org/en/latest/api/concepts/local-sessions/).
Actor-owned session, missing clipboard/selection exceptions, selection world,
selector versus read-only region, synchronization after changes and player history.

## S03 WorldEdit Edit Sessions

[EditSession concepts](https://worldedit.enginehub.org/en/latest/api/concepts/edit-sessions/).
Extent stack, event stages, closing/flushing and actual edit lifecycle. Not a
documented universal command dry-run API.

## S04 WorldEdit Extents

[Extent model](https://worldedit.enginehub.org/en/latest/api/concepts/extents/).
Memory and world are different possible extents. Our isolated preview must not
delegate writes to a world-backed extent.

## S05 WorldEdit Clipboard API

[Clipboard API examples](https://worldedit.enginehub.org/en/latest/api/examples/clipboard/).
Copy operations, holders, paste configuration and transforms. API examples are
not permission/protection policies or thread-safety guarantees.

## S06 WorldEdit Internal API Boundary

[Internal API definition](https://worldedit.enginehub.org/en/latest/api/internal-apis/).
Includes `command`, `internal` and platform implementations except adapters.
Java-public visibility does not imply compatibility or upstream support.

## S07 WorldEdit Parser Context

[ParserContext at 7.4.2](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/extension/input/ParserContext.java).
Actor/session/world/extent context, restriction and legacy-ID switches;
`setWorld` also sets extent. This is not a sandbox for arbitrary extension code.

## S08 WorldEdit Clipboard Commands

[ClipboardCommands at 7.4.2](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/command/ClipboardCommands.java).
Inspected for negative Y/X/Z rotation order, transform combination, flip origin,
direction and permissions. Internal implementation reference, not an API to invoke.
Recompare the relevant methods at 7.4.3/7.4.5 when implementing their adapters.

## S09 WorldEdit Random Pattern

[RandomPattern at 7.4.2](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/function/pattern/RandomPattern.java).
Independent RNG and relative weighting; no promise our preview sample equals a
later independent execution. Do not reflectively seed the real command's pattern.

## S10 EngineHub CUI Wire Packet

[CUIPacket source](https://github.com/EngineHub/WorldEditCUI-Protocol/blob/6882a090b7cad2a3cb418a8bd1f21dc1549692ab/worldeditcui-protocol-common/src/main/java/org/enginehub/worldeditcui/protocol/CUIPacket.java).
Inspected upstream commit dated 2026-09-16: `worldedit:cui`, protocol 4,
UTF-8 event/argument encoding. This source pin is not asserted to identify both
bundled 4.0.2/4.0.3 release artifacts; no release tags were returned by the tag API.

## S11 CUI Projects And Licensing

[EngineHub protocol README](https://github.com/EngineHub/WorldEditCUI-Protocol/blob/main/README.md)
and [WorldEditCUI](https://github.com/EngineHub/WorldEditCUI).
Shared protocol ownership and EPL-2.0 notices. Keep our channel independent and
retain notices for existing bundled dependencies.

## S12 Folia Ownership

[Paper/Folia scheduling](https://docs.papermc.io/paper/dev/folia-support/)
and [WorldEdit 7.4.2 FoliaExtentListener](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/folia/FoliaExtentListener.java).
Region/entity ownership differs from a conventional main thread. WorldEdit's
guard restricts block/biome writes to owned regions; it does not prove our
cross-region reads and shared session scheduling are safe.

## S13 Paper Plugin Messaging

[Messaging guide](https://docs.papermc.io/paper/dev/plugin-messaging/),
[Messenger 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/messaging/Messenger.html)
and [1.21.11 constant](https://jd.papermc.io/paper/1.21.11/constant-values.html#org.bukkit.plugin.messaging.Messenger.MAX_MESSAGE_SIZE).
Namespaced channel registration, player connection and documented maximum of
1,048,576 bytes. Verify direction/loader/proxy limits separately. The BungeeCord
examples explain the transport, not a channel to reuse for our application.

## S14 Paper Chunk Snapshot

[ChunkSnapshot 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/ChunkSnapshot.html)
and [Paper scheduling](https://docs.papermc.io/paper/dev/scheduler/).
Snapshot data is static/thread-safe; live world access is not generally async-safe.
This supplies neither anti-xray filtering nor per-player disclosure permission.

## S15 Fabric Networking

[1.21.11 guide](https://docs.fabricmc.net/1.21.11/develop/networking)
and [current 26.2 guide](https://docs.fabricmc.net/develop/networking).
Typed payload registration and direction-specific send/receive. Registration
names differ across these guides; resolve exact signatures against the installed API.
Documentation examples can use a different mappings namespace from our adapter.

## S16 Fabric Callback Thread

[Client handler at API 0.141.3+1.21.11](https://maven.fabricmc.net/docs/fabric-api-0.141.3%2B1.21.11/net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking.PlayPayloadHandler.html).
Documents client render-thread callback. Server handler Javadoc URLs for our
exact pins were not retrievable during this research; inspect resolved sources
before relying on their thread guarantees. Do not infer a guarantee from a
different Fabric version or a raw Netty callback.

## S17 NeoForge Networking

[1.21.11 payload guide](https://docs.neoforged.net/docs/1.21.11/networking/payload/),
[current guide, labeled 26.1](https://docs.neoforged.net/docs/networking/payload/)
and [26.1.x PayloadRegistrar source](https://github.com/neoforged/NeoForge/blob/26.1.x/src/main/java/net/neoforged/neoforge/network/registration/PayloadRegistrar.java).
Optional registration, main/network handlers, explicit enqueue and Neo-only
registrar version negotiation. The branch is mutable; check exact resolved
21.11.42/26.1.2.75/26.2.0.67 sources before implementation. A distinct 26.2
documentation page was not available; this guide is not 26.2 compatibility proof.

## S18 Paper Profiling

[Paper profiling guide](https://docs.papermc.io/paper/profiling/).
Use spark while the actual workload occurs; inspect CPU, GC and tick behavior,
not only an idle TPS reading.

## S19 Java Executors

[ThreadPoolExecutor, Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).
Queue bounds, rejection and lifecycle. `CallerRunsPolicy` executes on the
submitting thread, which is unsuitable for our expensive work on tick/network callers.

## S20 Java Virtual Threads

[Oracle virtual-thread guide, Java 21](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html).
Virtual threads address throughput for blocking work, not faster CPU calculations
or safe access to game state. Do not generalize Java 21 pinning details to Java 25.

## S21 Java Flight Recorder

[JFR package, Java 25](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/package-summary.html).
Record application events and diagnose JVM/thread behavior. Capture metadata
without embedding commands, world contents or other private session data by default.

## S22 Gradle Toolchains

[JVM toolchain guide](https://docs.gradle.org/current/userguide/toolchains.html).
Separate Gradle JVM, compiler language level, `--release` and test launchers;
using Java 25 to run a build does not make its output Java 21-compatible.

## S23 Velocity Plugin Messaging

[Official messaging guide](https://docs.papermc.io/velocity/dev/plugin-messaging/).
Source direction and forwarding are security decisions. Our client-originated
protocol must never be mistaken for a privileged proxy-control channel.

## S24 Servux Fragmentation Case Study

[PacketSplitter, upstream historical commit](https://github.com/maruohon/servux/blob/643d053ae9b0b44ad07f78d97442020406c2d369/src/main/java/fi/dy/masa/servux/network/PacketSplitter.java).
Commit dated 2023-06-10 on upstream default `fabric_1.19.x`: useful evidence that
large mod payloads can be fragmented. **Not** a current API/dependency or audited
ready-made codec for our Minecraft versions. Review ownership, total-size bounds,
timeouts and cleanup independently; do not transplant old constants or buffers.

## S25 Servux Security Advisory

[Maintainer advisory GHSA-4x67-52jx-vr7m](https://github.com/sakura-ryoko/servux/security/advisories/GHSA-4x67-52jx-vr7m).
Schematic transfer path traversal/arbitrary file-write case. Design lesson: our
preview bridge needs no client-controlled filename or server disk-write operation.
This is not a claim that every current Servux version remains affected.

## S26 WorldGuard Protection Query

[Protection query API](https://worldguard.enginehub.org/en/latest/developer/regions/protection-query/).
Region authorization is contextual and distinct from WorldEdit command permission.
A WorldGuard integration would be explicit and tested, not a blanket guarantee
for every claims plugin or for hidden-block disclosure.

## S27 Paper Plugin Metadata

[plugin.yml guide](https://docs.papermc.io/paper/dev/plugin-yml/).
Dependency order, API version and permission declarations. Use the appropriate
WorldEdit dependency and independently test reload/disable behavior; do not mark
Folia support or widen Minecraft compatibility solely through metadata.

## S28 Paper Download And Wire Fixture

[Official download service](https://docs.papermc.io/misc/downloads-service/) and
its versioned build API establish the Paper 1.21.11 build-132 artifact/hash.
[PrismarineJS protocol library](https://github.com/PrismarineJS/node-minecraft-protocol)
and [upstream API](https://github.com/PrismarineJS/node-minecraft-protocol/blob/master/docs/API.md)
establish test-only headless client support for 1.21.11; the fixture pins 1.68.0.
Neither the library nor its dependencies are bundled in client/companion JARs.
[WorldEdit 7.4.2 CUI handler](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/CUIChannelListener.java)
and the pinned LocalSession initialization path establish the independent CUI
round trip. Actual observations/hashes are in the foundation implementation record.

## S29 Java Monotonic Time

[System.nanoTime, Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()).
Checked 2026-10-11. Elapsed time uses subtraction, not an absolute deadline;
values have an arbitrary origin and may be negative. Deterministic bridge tests
cover discovery/request deadlines and token refill across the signed-long wrap.
These are arithmetic/lifecycle checks, not a latency benchmark.

## S30 Paper Plugin Metadata API

[Plugin, Paper 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/Plugin.html#getPluginMeta()).
Checked 2026-10-11. `getDescription()` is deprecated and may be inaccurate for
different plugin implementations. The Paper-only companion reads the pinned
WorldEdit version through `getPluginMeta()`; this does not broaden host support.

## S31 Brigadier Command Discovery

[Mojang CommandNode source](https://raw.githubusercontent.com/Mojang/brigadier/master/src/main/java/com/mojang/brigadier/tree/CommandNode.java)
and [Fabric Yarn 1.21.11 client handler API](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/network/ClientPlayNetworkHandler.html).
Checked 2026-10-11. `getChild()` reads the existing children map; absence need
not trigger a command request or autocomplete round trip. The pinned native
26.x client JAR also exposes `ClientPacketListener.getCommands()`. Our inference:
wait for an advertised `we` root before automatic `/we cui`; packet CUI remains
independent. Native tests cover presence, absence and existing retry gates.

## S32 NeoForge Bidirectional Handler Order

[Official PayloadRegistrar source, 1.21.11 branch](https://raw.githubusercontent.com/neoforged/NeoForge/1.21.11/src/main/java/net/neoforged/neoforge/network/registration/PayloadRegistrar.java).
Checked 2026-10-11 against resolved official sources JARs **21.11.42**,
**26.1.2.75** and **26.2.0.67**, not only the mutable branch. All three four-argument
`playBidirectional` overloads take **serverHandler, clientHandler**, in that order.
The shared private CUI fallback must deliver through the second handler.
The EngineHub shared-handler path is independent and is retained unchanged.

A real NeoForge 1.21.11 client without local WorldEdit negotiated the Paper bridge
but received no CUI before this fix. That failed native run is regression evidence,
not a failed bridge handshake. Packaged-bytecode checks cover handler order in all
three runtimes; native Paper results are recorded separately.

## S33 Paper Lifecycle Events

[PlayerUnregisterChannelEvent 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerUnregisterChannelEvent.html),
[PlayerRespawnEvent 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerRespawnEvent.html)
and [PluginDisableEvent 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/server/PluginDisableEvent.html).
Checked 2026-10-11. Observe real platform events; do not construct internal event
objects or infer a respawn from a timer. Our host drops session tokens/caches on
respawn/unregistration, retains the live connection's admission budget, and
disables when WorldEdit disables. Actual Paper lifecycle evidence is recorded
separately from native-client recovery, which still needs its own matrix.

## S34 Bounded Test Measurements

[ServerTickEndEvent 1.21.11](https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/event/server/ServerTickEndEvent.html),
[ThreadMXBean Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.management/com/sun/management/ThreadMXBean.html)
and [ChunkLoadEvent 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/world/ChunkLoadEvent.html).
Checked 2026-10-11. Paper tick duration is milliseconds; owner-thread CPU is a
cumulative counter, and allocation is an approximation, not retained heap.
The fixture samples a bounded array and writes results after the measured window.
Chunk-load events distinguish generation. The fixture and its monitoring hooks
are not bundled in production; event/counter/recorder overhead exists in every
comparison condition. A loopback idle-world experiment is not a busy-server SLA.

## S35 JFR Command

[Official Java 21 jfr command](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html).
Checked 2026-10-11. `summary` reports recording/event statistics; `print --json`
supports structured event filtering and explicit stack depth. Use recorded
measurement intervals to separate startup from workload samples. Sample counts
and allocation weights are not exact per-call CPU/allocation attribution, and
zero observed samples cannot establish zero overhead. Local recordings are not
automatically uploaded to a profiling service or committed to Git.

[RecordingFile Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html)
and [RecordedEvent](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordedEvent.html)
provide streaming event access. Our analysis uses them instead of materializing
all recursive classloader/stack metadata as a giant JSON document, keeps a
bounded set of time windows and excludes the test fixture from bridge attribution.

## S36 Native Player Context And Respawn

Checked **2026-10-12**. [MinecraftClient 1.21.11](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/MinecraftClient.html)
exposes nullable local player/world fields; [ClientPlayerEntity](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/network/ClientPlayerEntity.html#requestRespawn())
exposes the normal respawn request used only by the test fixture. The resolved
official NeoForge **21.11.42 sources JAR**, entry
`net/neoforged/neoforge/client/event/ClientPlayerNetworkEvent.java`, documents
`Clone` as replacement of the old local-player instance on respawn. Mutable
GitHub branch URLs for this source returned 404; the resolved artifact, not an
invented branch/tag URL, was inspected. The production adapter observes player
identity on its existing owning-thread tick; it does not instantiate an event.

The [Paper Entity API 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Entity.html#teleport(org.bukkit.Location))
documents normal player teleportation and its success result. Our isolated
fixture checks success, then reseeds that player's selector in the destination
world. Native tests, not API documentation alone, establish same-world respawn
and dimension-recovery behavior; see the [lifecycle report](NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md).

## Local Evidence And Refresh Rules

Build tuple evidence: runtime `gradle.properties` / `build.gradle`, root README.
Behavior evidence: symbol links in the knowledge base, COMMAND_PREVIEW.md and
the current selection editing design. Existing verification is in TESTING.md;
the bridge-specific gate document distinguishes remaining requirements from
[completed foundation checks](SERVER_BRIDGE_IMPLEMENTATION.md).

For every future entry record checked date, supported tuple, direct source,
commit/artifact hash when available, observed fact, our inference and test status.
Revisit mutable links, failed exact-version lookups, parser internals and open
design questions before coding. No source was accepted solely because it ranked
high in search or because an example compiled for a different Minecraft release.

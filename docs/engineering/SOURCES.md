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

## S37 Channel Advertisements And Native Reconnect

Checked **2026-10-12**. The resolved Fabric Networking API
**5.1.5+ae1e07683e sources JAR** documents `ClientPlayNetworking.canSend` as
whether the remote server declared that it can receive the channel. The exact
Fabric API 0.141.3+1.21.11 online Javadoc was inaccessible through the web tool;
the cached official source artifact was inspected instead. Native NeoForge
21.11.42 uses the existing public `hasChannel` check; actual REGISTER/UNREGISTER
behavior was verified on both native clients, not inferred from Fabric alone.

[Paper StandardMessenger, ver/1.21.11](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-api/src/main/java/org/bukkit/plugin/messaging/StandardMessenger.java)
reserves `minecraft:register` and `minecraft:unregister` and rejects ordinary
plugin registrations for them. [CraftPlayer in the same branch](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftPlayer.java)
shows the actual supported-channel advertisement packet construction. The
test-only `ChannelFaults` fault injector sends that real wire packet through
the pinned Paper 1.21.11-132 server; it uses version-specific reflection only
because no public Bukkit API sends these reserved packets. It is not inside
the companion or clients and is not a production networking pattern.

[Yarn ConnectScreen 1.21.11+build.4](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/gui/screen/multiplayer/ConnectScreen.html)
exposes the normal six-argument `connect` method. The resolved NeoForge
21.11.42 Minecraft source entries `ConnectScreen.java` and `ServerData.java`
expose `startConnecting` and `ServerData.Type.OTHER`. Test adapters use these
ordinary client connection flows, not a replacement TCP implementation or OS input.

## S38 Controlled Baseline Worlds

Checked **2026-10-12**. [Paper GameRules, ver/1.21.11](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-api/src/main/java/org/bukkit/GameRules.java)
provides the renamed `SPAWN_MOBS`, `ADVANCE_TIME`, `ADVANCE_WEATHER` and
`RANDOM_TICK_SPEED` constants. [World.setGameRule 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/World.html#setGameRule(org.bukkit.GameRule,T))
returns whether the rule was set. The console-only test fixture checks these
results and controls loaded entities and stationary synthetic players before
warmup/measurement. None of this runs in the production companion.
Restoring byte-identical saved worlds reduces world-state drift; it does not
control OS scheduling/JIT, recreate busy gameplay or prove zero server overhead.

## S39 JFR GC Duration Versus Pauses

Checked **2026-10-12**. [OpenJDK 21u JFR metadata](https://github.com/openjdk/jdk21u/blob/master/src/hotspot/share/jfr/metadata/metadata.xml)
defines `jdk.GarbageCollection.sumOfPauses` and `longestPause` separately from
the collection event's duration. The streaming reader retains legacy duration
fields for compatibility and reports these pause fields separately; a concurrent
collection's elapsed time must not be labelled a stop-the-world pause. Events
are selected by their start timestamp inside the measured interval. This is
not the same accounting as fixture MXBean deltas and does not attribute a slow
tick without temporal correlation. Reader compatibility was checked against
all four frozen-baseline and 14 existing pilot windows, without rerunning servers.

## S40 Plugin Lifecycle And Test Process Completion

Checked **2026-10-12**. [PluginManager](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/PluginManager.html)
distinguishes a loaded plugin (`getPlugin`) from an enabled plugin; [Plugin.isEnabled](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/Plugin.html#isEnabled())
provides the runtime check. The inspected [Paper instance manager](https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-server/src/main/java/io/papermc/paper/plugin/manager/PaperPluginInstanceManager.java)
does not revalidate dependencies in `enablePlugin`. A real fixture reproduced
our companion accepting re-enable while WorldEdit was disabled; the startup
guard now checks enabled state before any registrations/session access.

[HandlerList](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/HandlerList.html),
[Messenger registrations](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/messaging/Messenger.html)
and [BukkitScheduler](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html)
expose the ownership checks used by the test-only fixture. Zero registered
handlers/tasks is not a heap-retention or native hot-reload proof. Repeated
disable/enable is fault testing, not a recommendation to use `/reload`.

[Node child-process documentation](https://nodejs.org/api/child_process.html#event-close)
states that `close` follows process termination and stdio closure; `exit` alone
does not establish drained output, and a successful `kill` call does not prove
termination. The current docs resolved to Node 26; behavior was independently
tested on local **Node 24.21.0**, including real child processes. The attempted
24.x web documentation URL was inaccessible. Our shared test helper waits for
`close`, rejects spawn/stdin errors, premature/nonzero/signal exits and forced
termination, and is idempotent. It never kills by process name or touches an
unowned process.

## S41 Negotiated Graceful Session Close

Checked **2026-10-12**, Paper build 132 commit `c5eb079`. The pinned [Paper instance manager](https://github.com/PaperMC/Paper/blob/c5eb079/paper-server/src/main/java/io/papermc/paper/plugin/manager/PaperPluginInstanceManager.java)
dispatches `PluginDisableEvent` before `JavaPlugin.setEnabled(false)`. The
[StandardMessenger validator](https://github.com/PaperMC/Paper/blob/c5eb079/paper-api/src/main/java/org/bukkit/plugin/messaging/StandardMessenger.java)
rejects plugin messages from a disabled plugin. Consequently our owning-thread
handler sends negotiated close notifications in that event; `onDisable` only
performs final cleanup. The existing dependency-disable path disables this
plugin through the same public manager, reaching its own disable event.

This is a graceful-stop mechanism, not a heartbeat or a hot-reload guarantee.
Neither source supplies the missing client-visible re-advertisement on plugin
restart. Minor/capability negotiation and nonce/epoch matching are our protocol
policy, verified separately by pure, wire and native tests; documentation of an
API ordering alone is not cross-version runtime evidence.

## S42 Opt-In Restart Announcement

Checked **2026-10-12**, pinned Paper commit `c5eb079`. The public
[PluginMessageRecipient contract](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/messaging/PluginMessageRecipient.html)
exposes the listening-channel set and plugin message send operation. The pinned
[StandardMessenger](https://github.com/PaperMC/Paper/blob/c5eb079/paper-api/src/main/java/org/bukkit/plugin/messaging/StandardMessenger.java)
requires outgoing registration and an enabled sending plugin. Our experimental
host registers a normal, separate namespaced outgoing channel and sends only to
already-online listeners during successful enable, while enabled. It never
manufactures reserved REGISTER packets or adds a polling task.

The public [PlayerRegisterChannelEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerRegisterChannelEvent.html)
exists, but this design deliberately adds no reply handler for registration:
normal joins already negotiate, and repeated registration should not amplify
startup traffic. One-shot client arming and fresh hello authorization are our
protocol policy, not guarantees supplied by these Bukkit APIs. Native evidence
must establish that Fabric and NeoForge advertise the optional S2C receiver and
deliver its unchanged bytes; compilation alone does not establish this.

## S43 Admission Is Not Fair Scheduling

Checked **2026-10-12**. Informational [RFC 3290, Appendix A.2 and section 7.1.2](https://www.rfc-editor.org/rfc/rfc3290)
describes rate/burst token-bucket conformance separately from scheduling service
disciplines. Our application counts frames, not IP packet bytes: the analogy
supports distinguishing admission from fair service, not a claim of Diffserv
compliance or a Minecraft tick-time guarantee. A passing bounded workload does
not establish starvation freedom for every sender ordering.

[Paper profiling guidance](https://docs.papermc.io/paper/profiling/), rechecked
today, prefers its bundled spark profiler for Paper workloads. Our bounded local
JFR measurements complement this; sparse samples are not exact call costs.
Dedicated busy-server cost acceptance still requires representative workload
profiling, not simply a 20 TPS result or zero sampled bridge stacks.

## S44 Public Tick Brackets And JFR Timestamps

Checked **2026-10-12**, Paper **1.21.11-132 / c5eb079**, Temurin **21.0.11+10**.
The public [ServerTickStartEvent](https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/event/server/ServerTickStartEvent.html)
and [ServerTickEndEvent](https://jd.papermc.io/paper/1.21.11/com/destroystokyo/paper/event/server/ServerTickEndEvent.html)
are consumed by our separate fixture; internal constructors are not called.
The inspected [pinned Paper tick patch](https://github.com/PaperMC/Paper/blob/c5eb079/paper-server/patches/sources/net/minecraft/server/MinecraftServer.java.patch)
places queued start-of-tick work before the public start event. Its reported
duration must not be equated to the fixture's narrower event bracket. Reading
that patch for interpretation does not introduce an internal production hook.

The public [JDK 21 JFR Event API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/Event.html)
supports explicitly timed custom events; [RecordedEvent](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/consumer/RecordedEvent.html)
exposes recorded fields and timestamps. The inspected [OpenJDK 21.0.11+10 TimeConverter](https://github.com/openjdk/jdk21u/blob/jdk-21.0.11%2B10/src/jdk.jfr/share/classes/jdk/jfr/internal/consumer/TimeConverter.java)
derives epoch timestamps from recording chunk clock data. An observed sub-ms
skew from separately obtained `Instant.now()` values led us to join tick markers
by unique phase/sample IDs instead of clipping to wall-clock bounds; this is our
tested policy, not an API guarantee of a particular skew or its exact cause.

The actual local `jfr metadata --events jdk.GCPhasePause` confirms the top-level
pause event used by the new overlap reader; nested GC phases are not summed.
[ThreadMXBean](https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html)
distinguishes nanosecond precision from accuracy. Zero short-tick CPU deltas or
missing sampled bridge stacks do not prove zero work. See the
[complete marker/GC/measurement-limit evidence](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md).

## S45 Bounded Fair Service Research

Checked **2026-10-12**. Experimental [RFC 8290, sections 3 and 6.1](https://www.rfc-editor.org/rfc/rfc8290)
describes flow isolation and a modified deficit round-robin discipline, while
noting that the desired fairness unit may be something other than a flow.
Together with [RFC 3290](#s43-admission-is-not-fair-scheduling), this supports
separating metering from service order. It does **not** make network byte quanta,
CoDel targets or packet-flow identity appropriate Minecraft CPU budgets.

Our inference is to prototype bounded per-authenticated-actor round robin for
the current fixed metadata operation; variable-cost deficit accounting is a
later option only after measurement. No network scheduler source was copied.
The public [BukkitScheduler 1.21.11](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html)
provides owning-thread task scheduling, but by itself supplies neither fairness
nor cost guarantees. The [31-sender deterministic counterexample](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md#adversarial-admission-ordering)
reproduces the pre-dispatch core's progress failure on Java 21/25. A future scheduler
must independently prove deadlines, authority/duplicate semantics, cleanup,
aggregate memory/CPU/bytes and zero idle work before any production claim.

## S46 Owning-Thread Fair Dispatch

Checked **2026-10-12**, Paper **1.21.11-132 / c5eb079**. The public
[BukkitScheduler.runTaskTimer](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html#runTaskTimer(org.bukkit.plugin.Plugin,java.lang.Runnable,long,long))
provides synchronous repeating execution after a tick delay; the separate
asynchronous methods explicitly prohibit unsafely accessing Bukkit APIs.
[BukkitTask](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitTask.html)
exposes cancellation of the owned task. Our Paper adapter schedules at delay 1,
period 1 only when work first queues, retains one handle, and cancels it on idle
and teardown. It does not use scheduler internals, worker threads or world polls.

Unique actor slots, FIFO service, global tokens at dispatch, soft 0.5 ms slices
and a 5-second service-eligibility deadline are **our experimental policy**, not
guarantees supplied by those APIs or RFC 8290. A single external call can exceed
the soft target. Queue payload bounds exclude object/map overhead; ingress-copy
metering differs from global decode/read metering. See the
[fair-dispatch report](BRIDGE_FAIR_DISPATCH_2026-10-12.md) for deterministic,
real-network and native evidence. No broader performance support is inferred.

## S47 Controlled Mixed-Gameplay Fixture

Checked **2026-10-12**, Paper **1.21.11-132 / c5eb079**, WorldEdit **7.4.2**.
Public [PlayerMoveEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerMoveEvent.html)
provides accepted/cancelled movement observations; teleport events are a separate
subclass and are excluded from our workload counts. Public
[RegionAccessor.spawn](https://jd.papermc.io/paper/1.21.11/org/bukkit/RegionAccessor.html)
and [LivingEntity AI controls](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/LivingEntity.html)
support explicit fixture entities. These APIs do not promise deterministic AI
decisions or establish CPU cost from entity count/lifetime alone.

The [WorldEdit edit-session lifecycle](https://worldedit.enginehub.org/en/latest/api/concepts/edit-sessions/)
requires closing/flushing each completed session. Our test-only host workload
creates/finishes one bounded edit session per batch; it is not a bridge feature
and never writes into a user's installed world or actor history.

The [PrismarineJS 1.21.11 packet definitions](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.21.11/protocol.json)
were cross-checked against installed **minecraft-data 3.117.0**, protocol 774,
used by pinned **minecraft-protocol 1.68.0**. The local `position` serverbound
packet has x/y/z doubles and `MovementFlags` (`onGround`,
`hasHorizontalCollision`); clientbound position carries a teleport ID. Our
headless clients acknowledge teleports and send bounded flight positions.
No client class, packet decoder or server internals were copied into production.
The [mixed-gameplay method and evidence](BRIDGE_GAMEPLAY_LOAD_2026-10-12.md)
distinguishes sent packets, server-observed moves, exact block edits and timing.

## S48 Queued Lifecycle Faults And Test-Client States

Checked **2026-10-12**, Paper **1.21.11-132 / c5eb079**. Public
[PermissionAttachment.setPermission](https://jd.papermc.io/paper/1.21.11/org/bukkit/permissions/PermissionAttachment.html)
and [Player.kick(Component)](https://jd.papermc.io/paper/1.21.11/org/bukkit/entity/Player.html)
support actual permission changes and disconnects. Existing public plugin-manager
disable/enable operations provide dependency/companion faults. The opt-in fixture
observes our own pending frame through reflection before these calls; no request
is injected and no production reflection, timer or fault API is added.

Pinned [minecraft-protocol 1.68.0 play-state source](https://raw.githubusercontent.com/PrismarineJS/node-minecraft-protocol/1.68.0/src/client/play.js)
distinguishes configuration from play. Its [public client API](https://raw.githubusercontent.com/PrismarineJS/node-minecraft-protocol/1.68.0/docs/API.md)
documents `client.state`, per-packet events and `playerJoin`. Our test client now
requires both a received play-login packet and current play state before movement.
Inspection found the previous reconnect timer could send movement before play;
the failed run's decoder error alone does not establish its exact wire cause.
Guard/negative tests and subsequent real repeats are recorded in the
[active-lifecycle evidence](BRIDGE_ACTIVE_LIFECYCLE_2026-10-12.md).

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

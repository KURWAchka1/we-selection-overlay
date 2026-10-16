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

## S49 Test-Only Method Interval Instrumentation

Checked **2026-10-12**, Temurin **21.0.11+10 / 25.0.2+10**, ASM **9.9**.
The public [ClassFileTransformer contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html)
provides load-time instrumentation. A failed transformer normally leaves loading
to continue, so the opt-in diagnostic agent fails its disposable JVM loudly on
missing pinned targets. [Bootstrap search append](https://docs.oracle.com/en/java/javase/21/docs/api/java.instrument/java/lang/instrument/Instrumentation.html#appendToBootstrapClassLoaderSearch(java.util.jar.JarFile))
supports isolated helper visibility: only the three recorder classes enter that
search, never ASM, Minecraft, WorldEdit or the companion. This corrected an actual
Paper plugin-loader failure in the first diagnostic attempt.

The [ASM FAQ](https://asm.ow2.io/faq.html) describes computed stack limits and
verification failures. Our narrow wrappers preserve existing method bodies and
frames and add a catch/rethrow interval boundary. Return values, wide locals,
original exceptions, nesting, missing/double targets and actual JFR round trips
are independently checked. ASM remains relocated and test-only, using the
existing repository's pin/license pattern; no external implementation was copied.
The public [JFR Event API](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/Event.html)
supports the interval records. These wrappers themselves add time/allocation;
their results are diagnostic evidence, not uninstrumented production cost.

## S50 Safepoints And Background Profiler Control

Checked **2026-10-12**, pinned Paper **c5eb079** and OpenJDK **21.0.11+10**.
The [JFR event schema](https://github.com/openjdk/jdk21u/blob/jdk-21.0.11%2B10/src/hotspot/share/jfr/metadata/metadata.xml)
distinguishes `SafepointBegin` from `ExecuteVMOperation` and exposes the VM
operation's caller and safepoint ID. A short VM-operation body does not exclude
a longer synchronization interval. Their overlaps are reported separately, not
summed/subtracted as exact per-thread CPU. The local `profile.jfc` enables both
with zero thresholds, but not every detailed safepoint subphase. Short CPU
MXBean deltas remain quantized; see [S44](#s44-public-tick-brackets-and-jfr-timestamps).

Pinned [Paper configuration](https://github.com/PaperMC/Paper/blob/c5eb079/paper-server/src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java)
defines `spark.enabled`, while [SparksFly](https://github.com/PaperMC/Paper/blob/c5eb079/paper-server/src/main/java/io/papermc/paper/SparksFly.java)
checks it before enabling the bundled profiler. The test control uses this normal
configuration, not an internal production call. Actual startup logs and recorded
`spark-java-sampler-*` VM-operation callers distinguish enabled and disabled runs.
This is a local diagnostic control, not advice to disable profiling on users'
servers or proof that all historical outliers had the same cause. Results and
remaining unknowns are in the [interval report](BRIDGE_SLICE_DIAGNOSTICS_2026-10-12.md).

## S51 Matched Comparisons And Independent Replication

Checked **2026-10-13**, unchanged Paper **c5eb079** / Temurin **21.0.11+10**.
OpenJDK's [JMH forking example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java)
explains mixed JVM optimization profiles, and its [run-to-run example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_13_RunToRun.java)
demonstrates variation across fresh processes. These are methodological references,
not a claim that our whole-server workload is JMH or that a fixed warmup removes
all JVM startup effects. No example implementation is copied.

NIST's [blocking guidance](https://www.itl.nist.gov/div898/handbook/pri/section3/pri332.htm)
describes controlling nuisance variables. Our fixed absent/installed then
installed/absent order exposes gross order bias but is not randomized blocking
or a confidence-backed causal estimate. Individual ticks in the same JVM are
not independent server-run replicates. Preserve all paired signs, GC/outliers
and whole-run variation; do not pool ticks into an artificially precise claim.

The [Paper profiling guide](https://docs.papermc.io/paper/profiling/)
requires the relevant workload to be occurring for useful diagnosis. The local
no-spark control retains identical JFR settings and verifies actual sampler
absence through recorded VM operations, alongside configuration/log checks.
The summary reader rejects recorded data loss; raw measurement gates and
percentiles are recomputed before accepting copied summaries. See the
[matched-control method and evidence](BRIDGE_MATCHED_CONTROL_2026-10-13.md).

## S52 Remote Cuboid Transactions And Paper Disable Boundary

Checked **2026-10-13**, Paper **1.21.11-132 / c5eb079**, official WorldEdit
**7.4.2 / eb8e82c3d8843bed3f71e82ea974122415645178**.
Pinned [CuboidRegionSelector](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/CuboidRegionSelector.java)
exposes `learnChanges` and `explainRegionAdjust`, including ordered CUI points.
[CuboidRegion](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/regions/CuboidRegion.java)
supplies expand/contract/shift. We reuse the repository's existing transaction
instead of dispatching chat or copying a command implementation. The pinned
[LocalSession](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/LocalSession.java)
may switch/clear context in `getRegionSelector(world)`: host preflight checks
selection world first. `getIfPresent` avoids creating a session during inspection.
The fixture checks the public `undo(null, actor)` returns null on its fresh
history; production never invokes undo, opens EditSession or reads block content.

Pinned [PaperPluginInstanceManager](https://github.com/PaperMC/Paper/blob/c5eb079/paper-server/src/main/java/io/papermc/paper/plugin/manager/PaperPluginInstanceManager.java)
closes and unregisters the configured plugin classloader after disable. Our real
first edit test after same-instance re-enable failed with a closed ZIP loader.
The [Paper command reference](https://docs.papermc.io/paper/reference/commands/#reload-alias-spigot-reload)
recommends a restart instead of plugin reload. **Policy:** withhold editing after
disable until full startup, do not reflectively reopen or eagerly warm the loader.
Previously verified metadata recovery is a narrower fault-handling result, not
general hot-reload support. Native tests use a fresh Paper process for each loader.

**Our protocol design, not a WorldEdit guarantee:** connection-owned opaque
single-use revisions, exact duplicate caching, current-state origin validation
and UNKNOWN/no-retry outcomes. Identical restored state on the same selector
(ABA) is not historical change detection. See the [executed remote-editing report](BRIDGE_REMOTE_SELECTION_2026-10-13.md)
for real/native evidence and remaining scope.

## S53 Test-Only Network Faults And Evidence Reconciliation

Checked **2026-10-13**, Node **24.21.0**, test-only minecraft-protocol **1.68.0**,
Temurin **21.0.11+10 / 25.0.2+10**, unchanged Paper/WorldEdit tuple from S52.
[RFC 9293](https://www.rfc-editor.org/rfc/rfc9293.html#section-2.2) specifies TCP's
ordered reliable byte stream; read/write buffers are not application messages.
Our latency relay therefore frames complete uncompressed Minecraft packets,
preserves order for its delay case and separately labels deliberate application-
acknowledgment withholding. It does not pretend that losing bytes is valid TCP
message loss or establish compatibility with a production Minecraft proxy.

The pinned [minecraft-protocol public API](https://github.com/PrismarineJS/node-minecraft-protocol/blob/1.68.0/docs/API.md)
documents PLAY readiness and `createSerializer`; [its serializer source](https://github.com/PrismarineJS/node-minecraft-protocol/blob/1.68.0/src/transforms/serializer.js)
selects the version/state/direction schema. Actual encoded custom-payload prefixes
come from that schema, not guessed packet IDs. [Node 24 net](https://github.com/nodejs/node/blob/v24.x/doc/api/net.md)
and [streams](https://github.com/nodejs/node/blob/v24.x/doc/api/stream.md) document
write backpressure, pause/resume and drain. The fixture has one connection,
bounded input/output storage and fail-closed framing; separate negative tests
exercise fragmentation, overflow and unexpected extra connections. No production
agent/interceptor, external service or new shipped dependency is introduced.

The official [JFR command reference](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html)
supports structured `print --json --events` output. The edit analyzer checks
recorded DataLoss, reuses the already-tested bounded tick reader and matches
phase/sample IDs plus raw Paper values before writing its analysis. Exact
percentiles are recomputed. This prevents a copied success flag or stale summary
from becoming cost evidence. Two same-policy functional runs are still not an
absent/installed causal performance comparison; see the [executed report](BRIDGE_EDIT_FAULTS_AND_LOAD_2026-10-13.md).

## S54 Native Gesture Callbacks And Authority Checks

Checked **2026-10-13**, Minecraft **1.21.11**, the same Paper/WorldEdit tuple as
S52. Fabric's pinned [Mouse mapping reference](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/Mouse.html)
and [Keyboard mapping reference](https://maven.fabricmc.net/docs/yarn-1.21.11+build.4/net/minecraft/client/Keyboard.html)
give exact callback signatures/intermediary names. The test-only production-
namespace Fabric adapter uses those names; the compiled NeoForge adapter uses
the corresponding Mojang-mapped methods already used by the local test harness.
Reflection here is exclusively a native input test boundary, not a supported
Minecraft public API or precedent for production server/reflection integration.
Callbacks run on the game's owning thread without OS input or window activation.

The pinned [WorldEdit LocalSession](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/LocalSession.java)
and [Region](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-core/src/main/java/com/sk89q/worldedit/regions/Region.java)
provide the independent fixture's actual selection and minimum/maximum points.
The fixture does not reuse the client draft as its authority observation.
Existing cursor guards and server shutdown observer remain mandatory.
See [executed gesture evidence and limitations](BRIDGE_NATIVE_GESTURES_2026-10-13.md).

## S55 Exact 26.x Bridge Tuples

Checked **2026-10-13**. Paper's [download service contract](https://docs.papermc.io/misc/downloads-service/)
requires an identifying User-Agent and distinguishes stable builds. Its official
v3 responses select [26.1.2 build 74](https://fill.papermc.io/v3/projects/paper/versions/26.1.2/builds/74)
and [26.2 build 133](https://fill.papermc.io/v3/projects/paper/versions/26.2/builds/133).
Downloaded bootstrap JARs match the returned SHA-256 values. The exact `26.1`
lookup returns `version_not_found`; it is not silently replaced by a newer server.
The new native matrix deliberately uses **26.1.2**, not a claim about 26.1/26.1.1.

Official WorldEdit [7.4.3](https://github.com/EngineHub/WorldEdit/releases/tag/7.4.3)
and [7.4.5](https://github.com/EngineHub/WorldEdit/releases/tag/7.4.5) tags identify
the tested distributions. Their [7.4.3 LocalSession](https://github.com/EngineHub/WorldEdit/blob/7.4.3/worldedit-core/src/main/java/com/sk89q/worldedit/LocalSession.java)
and [7.4.5 LocalSession](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-core/src/main/java/com/sk89q/worldedit/LocalSession.java)
retain the public own-player session/selector contracts used here. The publisher's
Modrinth version records (`yDUBafTJ`, `F5ea2ov3`) provide exact Bukkit files,
game versions and SHA-512 digests, checked before launch. Tuple/digest pins are
stored in [gesture-tuples.mjs](../../tools/server-bridge-test/gesture-tuples.mjs).

Current [Fabric networking documentation](https://docs.fabricmc.net/develop/networking)
uses `clientboundPlay/serverboundPlay`; [NeoForge payload documentation](https://docs.neoforged.net/docs/networking/payload/)
documents optional payloads and owner-thread handling. Mutable documentation is
not a substitute for the resolved **0.145.1+26.1 / 0.158.0+26.2** and
**26.1.2.75 / 26.2.0.67** APIs. Versioned Fabric Javadoc lookups failed in this
cycle; compiled adapters and real round trips are the version-specific checks.

Resolved Mojang/NeoForge Minecraft source JARs show `isWindowActive()` delegates
to `Window.isFocused()` in 26.1.2/26.2; the private `Window.onFocus(long, boolean)`
callback owns that state. Stock Fabric has no `Minecraft.setWindowActive`.
Only the test adapters invoke the pinned callback in-JVM. 26.2 moves screen
access to `Minecraft.gui`; separate compiled test adapters preserve that boundary.
No production private callback/reflection API or operating-system input is added.

## S56 Native 26.x Lifecycle And Negative Evidence

Checked **2026-10-14**. The official Fabric networking guides for
[26.1.2](https://docs.fabricmc.net/26.1.2/develop/networking) and
[26.2](https://docs.fabricmc.net/develop/networking),
[NeoForge optional payload registration](https://docs.neoforged.net/docs/networking/payload/)
and [Paper plugin messaging](https://docs.papermc.io/paper/dev/plugin-messaging/)
describe the platform payload boundaries. Registration/channel availability is
not application authorization or an acknowledgment that a selection changed.
Our inference is to test channel advertisement, negotiated state and actual
WorldEdit/server counters independently; no new platform API is introduced.

The public Paper compile-boundary events
[PlayerRespawnEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerRespawnEvent.html)
and [PlayerChangedWorldEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/player/PlayerChangedWorldEvent.html)
identify different lifecycle transitions. The respawn contract warns that
player state can reset before post-respawn. The fixture therefore observes
actual native player/world identity and fresh protocol epochs, not just a
console command or a simulated event. These compile-boundary docs alone are
not 26.x evidence; exact S55 hosts must pass real native transitions.

The existing Java 21 Paper test fixture joins `Weso*` actors with explicit
permission attachments, seeds actual WorldEdit selectors and uses only public
host APIs to kill/teleport/kick them. No OP grant, client WorldEdit, system input,
new production polling or provided-dependency packaging is needed. The extended
client fixture explicitly rejects unavailable reads and old-context commits
without advancing request IDs. The independent
[lifecycle evidence verifier](../../tools/server-bridge-test/native-lifecycle-evidence.mjs)
requires complete unique loader/scenario coverage, exact phase/context order,
canonical apply counts, zero denied source work and empty final queues/tasks.
Its negative tests are not a substitute for running the native matrix.

Channel withdrawal/re-advertisement is a distinct **test-only wire injection**:
Bukkit's [Messenger reserved-channel contract](https://jd.papermc.io/paper/1.21.11/org/bukkit/plugin/messaging/Messenger.html#isReservedChannel(java.lang.String))
reserves `REGISTER/UNREGISTER`, so this pre-existing fixture uses pinned
packet reflection rather than production plugin messaging. The first 26.2 run
correctly refused its old 1.21.11-only guard. `javap` of both exact resolved Paper
server JARs confirms `Identifier.parse`, `DiscardedPayload(Identifier, byte[])`,
`ClientboundCustomPayloadPacket(CustomPacketPayload)`, the player `connection`
field and inherited `send(Packet)` signatures. The injector now permits those
three exact versions only, the fixture JVM flag, loopback IP and fixture ports.
This exception is not permission to add Minecraft internals to production code
or a claim about untested server builds. No injector classes ship to users.

## S57 Exact Native Fault Packet Discovery

Checked **2026-10-14**. [Paper plugin messaging](https://docs.papermc.io/paper/dev/plugin-messaging/)
uses the player's Minecraft packet stream; channel names do not identify packet
numbers across versions. The [PrismarineJS protocol implementation](https://github.com/PrismarineJS/node-minecraft-protocol)
and the pinned local **minecraft-protocol 1.68.0** advertise 26.1 but not 26.2.
The actual cached `minecraft-data` lookup returns 26.1 data for 26.1.2 and no
data for 26.2. We do not upgrade or alias this dependency to invent coverage.

Resolved Mojang/NeoForge source JARs **26.1.2.75 / 26.2.0.67** expose
`GameProtocols.SERVERBOUND_TEMPLATE/CLIENTBOUND_TEMPLATE` and
`ProtocolInfo.DetailsProvider.details().listPackets(PacketVisitor)`. The visitor
reports each registered `PacketType` and its network ID from the codec order;
`CommonPacketTypes.*_CUSTOM_PAYLOAD` identifies the desired entries. `FriendlyByteBuf`
encodes the VarInt packet ID and UTF channel identifier. `javap` of the exact
Paper JARs verifies these reflective signatures; actual native interception,
not source similarity, is required to validate the complete packet prefix.
Source-JAR SHA256: `299322cea45afd74dc0eb65afc826f7e828a0a32121639080ff908de401c2c1a`
and `73a39ae9ee8c0497efe797e35c05827c839cf3c83092e6ead9a5caf723aefa85`.

Our test-only [PacketPrefixes](../../server-bridge/paper-test-fixture/src/main/java/org/marshal/weaddon_lightposition/bridge/fixture/PacketPrefixes.java)
reads those public members of internal Minecraft classes in the owned server
JVM. It requires explicit fixture/fault flags, offline mode, loopback port 25580
and one of the three exact approved versions. It frees its temporary Netty
buffer and fails closed on missing/duplicate entries or API mismatch. The Node
supervisor verifies the reported host, canonical VarInt and exact channel
encoding before arming the existing bounded relay. No packet IDs are hardcoded
or inferred from a neighbouring release. This is an explicit **test-fixture
exception**, not a production Paper API or an arbitrary-proxy compatibility claim.
No Minecraft internals, reflection or extra dependencies enter the companion.

The independent [fault evidence validator](../../tools/server-bridge-test/native-fault-evidence.mjs)
requires complete unique loader/scenario coverage, ordered phases, pending-state
observation, exact capture/apply/relay counts, server WorldEdit bounds, world/undo
audit, pre-delivery revocation and empty final queues. Eight positive/negative
self-tests cover corrupted evidence and malformed prefixes. They validate the
test harness, not native compatibility without running its real clients.

## S58 Exact 26.x Concurrent Edit Load

Checked **2026-10-14**. The official
[PrismarineJS 26.2 integration PR](https://github.com/PrismarineJS/node-minecraft-protocol/pull/1496)
is still open and describes integration scaffolding, not a supported released
26.2 stack. The npm registry reports **minecraft-protocol 1.68.0** as the latest
release at inspection. Its cached data resolves 26.1.2 to wire protocol **775**,
but cannot resolve 26.2. Version metadata alone is not evidence that a complete
wire decoder exists; no protocol number/table is patched or aliased for this test.

[GeyserMC MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) documents its
public client/session API and official OpenCollab Maven repository. The exact
[26.2 snapshot metadata](https://repo.opencollab.dev/main/org/geysermc/mcprotocollib/protocol/26.2-SNAPSHOT/maven-metadata.xml)
resolves **26.2-20261012.101744-21**. Its inspected source JAR declares codec
**776 / 26.2**; current master already declares 777 / 26.3 and is not the source
pin used here. The artifact's `ClientNetworkSessionFactory`, `MinecraftProtocol`,
`SessionAdapter`, payload packet classes and `ClientListener` establish login,
configuration, compression and automatic keepalive handling. Transfers follow
another host by default; our fixture explicitly disables `FOLLOW_TRANSFERS`.
Position/chunk-batch/ping acknowledgments use those public packet classes.
No game wire packet is reimplemented or reflected into existence.

Pinned source-JAR SHA256:
`ab40ad3424c375f9cfc847cab9cac99a5c136c82be9652b05a142cd2ce6dc217`;
binary: `5757ef29742d75b4af90fcdaf3d997e4b6dcf23928e601b4869487286854779a`.
The separate [Java load helper](../../tools/bridge-load-clients/README.md)
has explicit loopback/identity/channel restrictions and bounded memory/IPC.
Its dependencies never enter the companion or client JARs. Runtime codec checks,
actual 32-actor joins, payload exchanges and server-side authority assertions
must pass before this library becomes load-test evidence for the exact host.

[Gradle locking](https://docs.gradle.org/current/userguide/dependency_locking.html#sec:ignoring_dependencies)
permits excluding one component without excluding its transitives. The first
offline rebuild exposed timestamp-to-base-SNAPSHOT normalization in generated
lock state. Only the directly timestamp-pinned MCProtocolLib component now uses
that exclusion; every transitive remains locked. Committed SHA256 verification
also pins its actual metadata/JAR, and the launcher rechecks the protocol JAR.
This is not a switch to mutable `26.2-SNAPSHOT` or lenient lock mode. These are
HTTPS integrity pins, not verified upstream signatures.

The public [Paper 26.2 World contract](https://jd.papermc.io/paper/26.2/org/bukkit/World.html)
documents clockless-world time getters. The first real 32-client run reproduced
`Cannot set time in world without world clock` while preparing all three vanilla
fixture dimensions, before measurement. `javap` of the exact resolved Paper
server confirms the clock check in `CraftWorld.setFullTime`. The fixture now
uses public `World.isFixedTime()` to avoid resetting fixed-time vanilla dimensions;
weather/gamerules remain controlled. This is a scoped vanilla-fixture policy,
not a claim that fixed-time status universally describes all custom world clocks.
Failed server/helper logs and JFR are retained, and no failed attempt writes a
successful proof. Production bridge code does not call this preparation path.

## S59 Edit Method And Safepoint Attribution

Checked **2026-10-14**. Oracle's Java 25
[RecordingFile contract](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/consumer/RecordingFile.html)
provides streaming event reads and warns against `readAllEvents` for large files.
The [jfr command reference](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jfr.html)
documents `configure`, `event#setting=value` and `metadata`. The new diagnostic
fixture derives one JFC from the installed JDK's default settings, then enables
zero-threshold `ExecuteVMOperation` and `SafepointBegin` in **both** arms. It
retains Paper's default spark policy; this is not an uninstrumented production
benchmark or advice to disable user profiling. Paper's
[profiling guide](https://docs.papermc.io/paper/profiling/) requires observing the
workload in which a suspected problem actually occurs.

Inspected Temurin **25.0.2+10** `lib/jfr/default.jfc`: both events have a **10 ms**
threshold. `jfr metadata --events jdk.ExecuteVMOperation` exposes the operation,
caller and `safepointId`; `SafepointBegin` has the matching ID. Matching IDs in a
separate streaming pass avoids assuming file-order equals timestamp-order, or
losing an associated operation whose body starts after a selected call returns.
An associated operation/caller identifies the requested VM operation, **not**
the reason a particular thread was slow to rendezvous or the CPU cost of a call.
Overlapping GC/VM/synchronization intervals must not be added as disjoint costs.

Reinspection of the retained 26.2 edit-load run finds four `SafepointBegin`
intervals (IDs **4619..4622**) overlapping the 60.4271 ms tick, totaling
**55.0482 ms**. The old tick reader omitted this event category. Its original
zero recorded VM-body/GC overlap was not evidence of no JVM synchronization.
The thresholded old recording cannot establish the missing operation/caller;
the new diagnostic settings cannot retroactively recover discarded events.

The exact unchanged companion was inspected with Java 21 `javap -p -s` before
adding test-only wrappers for `mayEditSelection`, `captureEdit` and `applyEdit`.
These retain real descriptors, object identity, exceptions and owner-thread
execution. The agent still touches only companion classes, never WorldEdit or
Minecraft implementation methods. All instrumentation stays out of shipped JARs.

The pinned [OpenJDK 25.0.2+10 safepoint source](https://github.com/openjdk/jdk25u/blob/jdk-25.0.2%2B10/src/hotspot/share/runtime/safepoint.cpp)
places `EventSafepointBegin` around synchronization and publishes the safepoint
ID after threads rendezvous. The initial lookup in `openjdk/jdk` failed (404);
the versioned `jdk25u` source is the inspected reference, not master.

Paper's [26.1 announcement](https://papermc.io/news/26-1/) documents the move
from three server-root world directories to dimension directories inside one
world root. The [26.2 announcement](https://papermc.io/news/26-2/) retains that
layout. A real priming run reproduced the old frozen helper's missing-Nether
directory failure before measurements. Exact 26.x diagnostic roots now snapshot
the complete `bridge-fixture` tree, requiring all three vanilla dimension
directories. Legacy fixtures retain their old schema/layout. No user world is
migrated, and a failed snapshot cannot silently become a matched-world proof.

## S60 Exact 26.2 Profiler Control

Checked **2026-10-15**. The retained Paper startup identifies build **26.2-133**
and commit **58e6e45d67c998909581f9079189ef51eb6f1267**. Its exact
[GlobalConfiguration](https://github.com/PaperMC/Paper/blob/58e6e45d67c998909581f9079189ef51eb6f1267/paper-server/src/main/java/io/papermc/paper/configuration/GlobalConfiguration.java)
defaults `spark.enabled` to true and `enableImmediately` to false. Exact
[SparksFly](https://github.com/PaperMC/Paper/blob/58e6e45d67c998909581f9079189ef51eb6f1267/paper-server/src/main/java/io/papermc/paper/SparksFly.java)
checks that setting before enabling and logs the disabled-configuration outcome.
The [public configuration reference](https://docs.papermc.io/paper/reference/global-configuration/#spark)
documents both settings. Use the normal configuration, not reflection into the
profiler or patched host code. These facts do not establish the source of any
earlier timing outlier.

The new test-only study keeps the ten-method observer, JFR settings, offered
workload, heap/processor flags and frozen starting world fixed while changing
the bundled-profiler enable bit in an on/off/off/on sequence. Both arms start
from identical minimal version-31 Paper configuration input; complete normalized
effective configurations are compared afterwards, excluding only `spark.enabled`.
The startup messages and actual whole-recording sampler ThreadDump callers must
independently agree. This is an intervention on observer policy, **not** an
absent-companion production-cost measurement or advice to disable user profiling.

Effective configuration is parsed offline with the fixture's existing pinned
**SnakeYAML 2.6** and **Gson 2.14.0**, not ad hoc YAML string editing. The
[LoaderOptions API](https://javadoc.io/static/org.yaml/snakeyaml/2.6/org/yaml/snakeyaml/LoaderOptions.html)
provides duplicate-key, collection-alias, nesting and input limits. Exact local
`javap` confirms these methods; actual Java 21/25 tests exercise `SafeConstructor`,
reject custom tags/duplicates/malformed or oversized files, and preserve the
parsed boolean types. These libraries are already present in the exact Paper
fixture and do not enter player/plugin JARs. SHA-256: SnakeYAML
`c8f7a98e7394adda02f6317249710e4d1b4c7a25aa8c7eace0c2eea52eb8bf85`,
Gson `2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f`.
The reader study is deliberately guarded to exact 26.2, not inferred support
for future configuration schemas. Wrapper source discovery and policy self-tests
precede the experiment. Fixed AB/BA on one shared laptop is not randomized
replication or a basis for universal speedup/latency claims; see S51.

## S61 Exact 26.2 Moving Gameplay Transport

Checked **2026-10-15**. The official
[MCProtocolLib project](https://github.com/GeyserMC/MCProtocolLib) documents its
public packet API and Open Collaboration repository. Reuse the exact
`org.geysermc.mcprotocollib:protocol:26.2-20261012.101744-21` from S58, not current
master or an aliased Prismarine version. The inspected
[source artifact](https://repo.opencollab.dev/main/org/geysermc/mcprotocollib/protocol/26.2-SNAPSHOT/protocol-26.2-20261012.101744-21-sources.jar)
has SHA-256 `ab40ad3424c375f9cfc847cab9cac99a5c136c82be9652b05a142cd2ce6dc217`.
Its `ServerboundMovePlayerPosPacket` fields are two booleans then three doubles;
its public constructor takes `(onGround, horizontalCollision, x, y, z)`. The
library owns serialization. Actual round-trip and loopback server acceptance
must be verified; inspecting source alone is not a successful gameplay test.

`ClientboundPlayerPositionPacket` exposes the teleport ID; the existing helper
acknowledges it with `ServerboundAcceptTeleportationPacket` and sends the public
player-loaded packet. Movement is admitted only after that initial callback.
Subsequent teleport events remain evidence, not discarded correction noise.
Server-side accepted movement and live entity/edit counters independently check
the offered route. All instrumentation belongs to test tooling, not shipped JARs.

The no-spark policy uses the [normal Paper setting](https://docs.papermc.io/paper/reference/global-configuration/#spark)
and the exact host/source/configuration checks in S60. AB/BA and frozen-world
method limitations remain those in S51, not a statistical performance guarantee.
Exact modern dimension layout follows S59. This new moving workload does not
retroactively explain the stationary profiler-study outliers.

## S62 Concurrent Moving Selection Edits And Tick Boundaries

Checked **2026-10-16**. Reuse the exact S61 MCProtocolLib 776 transport and
public Paper [PlayerMoveEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerMoveEvent.html).
Its `hasChangedPosition()` detects sub-block movement; cancellation and teleport
are distinct from successful ordinary motion. The existing fixture listens at
MONITOR with cancelled events ignored and excludes teleports, while the helper
independently retains correction packets. No synthetic Bukkit event is injected
to manufacture accepted movement.

EngineHub's [edit-session lifecycle](https://worldedit.enginehub.org/en/latest/api/concepts/edit-sessions/)
requires closing sessions to flush queued work and creating a new session for
subsequent edits. The existing fixed fixture follows that lifecycle; selection
transactions remain a separate metadata path. The initially attempted examples
URL was inaccessible; the linked concepts page is the inspected reference.

The exact Paper **58e6e45**
[MinecraftServer patch](https://github.com/PaperMC/Paper/blob/58e6e45d67c998909581f9079189ef51eb6f1267/paper-server/patches/sources/net/minecraft/server/MinecraftServer.java.patch)
calls start-of-tick task processing before `tickServer` and its public start
event; the end event duration uses `currentTickStart`, not that listener's start.
The fixture bracket is therefore not the full reported tick interval. This
source check establishes different boundaries, not the cause of the newly
observed gaps. Raw source was inspected when GitHub's rendered search omitted
the event text. Production code adds no internal timing hook. See S44/S59 and
the [moving-edit evidence](BRIDGE_26_2_MOVING_EDITS_2026-10-16.md).

The graph/snippet of `BridgeServer.editCommit` distinguishes host invocation
from early authority/revision rejection and cached replies. Tests and offline
wire reconciliation explicitly preserve those boundaries; an acknowledgment is
not by itself evidence of another call to `host.applyEdit`. No async WorldEdit
access, permission caching or quota increase is justified by this source review.

## S63 Dispatcher Clock And Thread Constraints

Checked **2026-10-16** against Oracle Java 25 and Paper's scheduler guidance.
[`System.nanoTime`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/System.html#nanoTime())
measures elapsed time within one JVM. Its origin is arbitrary; precision is not
a guarantee of resolution. Oracle recommends subtracting timestamps rather than
forming an absolute timeout by addition, avoiding overflow at the clock wrap.
This is elapsed duration, not owner-thread CPU consumption.

Paper's [scheduler documentation](https://docs.papermc.io/paper/dev/scheduler/#difference-between-synchronous-and-asynchronous-tasks)
distinguishes main-thread tasks from asynchronous work and warns that accessing
or changing world state is not safe from an asynchronous task. Oracle's
[`Thread.stop`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Thread.html#stop())
describes the old mechanism as unsafe; in Java 25 it always throws
`UnsupportedOperationException`. These are constraints, not a proposed way of
timing out WorldEdit on the server thread.

Our within-slice visit reservation is a local policy derived from a reproduced
200 us + 200 us + 200 us counterexample, not an official WorldEdit scheduler or
a hard real-time guarantee. A completed visit can inform subsequent admission,
but cannot predict or preempt a sudden atomic call or JVM/OS pause. The
[matched old/candidate experiment](BRIDGE_DISPATCH_BUDGET_2026-10-16.md)
retains raw outliers and measures the effect on actual player progress.

## S64 Observer Cost And Minimal Drain Measurement

Checked **2026-10-16**. Oracle Java 25's
[`ThreadMXBean`](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/ThreadMXBean.html)
specifies CPU-time precision separately from accuracy and notes that enabling
CPU measurement can be expensive on some JVM implementations. This is not a
claim that a particular permission/read outlier was caused by a counter call.
Oracle's [Flight Recorder API guide](https://docs.oracle.com/en/java/javase/21/jfapi/flight-recorder-api-programmers-guide.pdf)
and Java 25 [`Event`](https://docs.oracle.com/en/java/javase/25/docs/api/jdk.jfr/jdk/jfr/Event.html)
describe event begin/end, enabled checks and conditional commit. Event payload
construction/counter reads around application methods are additional observer
work; filtering short events cannot recover the unobserved execution time.

Local source inspection finds that `SliceProbe` reads CPU/allocation at every
method boundary and commits each nested call. In a wrapped drain, those child
operations execute inside its elapsed-time budget. The separate fixture-only
drain agent reuses the exact existing ASM weaver/recorder but omits child
wrappers. Original agent/bootstrap hashes remain unchanged. Both agents require
explicit fixture opt-in, exact method descriptors and bootstrap isolation;
the new mode adds its own flag and does not support attaching to user JVMs.

The [nested/drain observer experiment](BRIDGE_DRAIN_OBSERVER_2026-10-16.md)
holds the companion and workload fixed across four fresh reversed-order JVMs.
It tests observer influence, not whether all overrun causes are known. Minimal
measurement is still measurement; no time is subtracted from raw recorded calls
and no hard real-time guarantee follows from a successful local percentile.

## S65 Bounded Ordered Load Helper Callbacks

Checked **2026-10-16** against S61's exact MCProtocolLib source artifact
`26.2-20261012.101744-21`. `NetworkSession.channelRead0` submits game-thread
packet callbacks to the supplied `Executor`. The public client factory accepts
that executor. Its default implementation assigns `PACKET_EVENT_LOOP.next()`
per session; it does not force all sessions through one shared worker. The
source artifact and hash are recorded in [S61](#s61-exact-262-moving-gameplay-transport).

Oracle Java 25's
[`ThreadPoolExecutor`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html)
describes fixed worker counts, bounded queues and rejection. An unbounded queue
or discard policy would hide overload. A plain two-worker pool does not promise
per-session serial execution. The rejected striped variant instead assigned
each actor to one of two fixed single-worker stripes with bounded FIFO queues.
Its total queued capacity stayed 4,096, with no caller-runs or silent packet drop.

Stripes and paced preparation both failed and are retained, not presented as
solutions. The same exact `DefaultPacketHandlerExecutor` source explicitly
returns `Runnable::run` when its separate packet loop is disabled. The helper
now uses that supported inline executor through the public factory. Its small
callbacks run in channel order on the client's Netty loop, with no extra decoded
packet queue/threads; transport can apply normal TCP backpressure. Decoder and
callback faults are still visible. This is test-client I/O, not permission/world
work on a server network thread. The original simultaneous 32-login policy is
restored. The [four-arm repeat](BRIDGE_DRAIN_OBSERVER_2026-10-16.md) must still
pass actual packet/order/cleanup gates; it is not a general connection-storm proof.

## S66 Opaque Revision Identifiers And Authority

Checked **2026-10-16**. Oracle Java 25's
[`UUID.randomUUID`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/UUID.html#randomUUID())
uses a cryptographically strong generator. Its public `(long, long)` constructor
accepts arbitrary 128-bit data; this does not make an incrementing container an
RFC random/time-based UUID implementation. Our experimental wire already checks
opaque nonzero values, not UUID version bits.

The [OWASP IDOR guidance](https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html)
treats complex identifiers as defense in depth, not a substitute for authorization.
It does not certify our protocol. Here a revision does not select a global
resource or carry authority: the host capture belongs to the actual connection's
peer and current grants/epoch/selector/original points are revalidated. The
candidate preserves those boundaries, including cached replay and revocation;
tests deliberately guess handles and reuse mock epochs across actors/renewals.

A lazy server-wide, owner-thread 128-bit sequence is seeded from an existing
random epoch. It avoids another cryptographic generator call for each ready
result; it skips zero and carries across word boundaries, never resets on peer
renewal and fails terminally on a complete cycle. It is an opaque correlation
identifier, **not** a secret/bearer capability or a claimed UUID-v7 implementation.
Non-version-four handles also round-trip through the existing codec in tests.
Random nonce/epoch creation is unchanged. The
[matched revision experiment](BRIDGE_REVISION_IDS_2026-10-16.md) must establish
cost/progress effects before any performance claim. A sampled `HashDrbg` frame
within a long drain is evidence of executed code, not attribution of the entire
interval or proof that every remaining pause has that cause.

## S67 Request-Local WorldEdit World Adaptation

Checked **2026-10-16** at EngineHub WorldEdit **7.4.5**:
[`BukkitPlayer`](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitPlayer.java)
adapts its Bukkit world; [`BukkitWorld`](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/BukkitWorld.java)
constructs a weak reference and asks the installed adapter for native world
access. Repeated adaptation is therefore not just a primitive getter. Source
inspection is not proof of its entire sampled wall cost.

The candidate reuses one local `World` only for a single owning-thread metadata
read, keeping selection-world checks and world-height bounds on that reference.
It is not a world/session cache, chunk load, permission cache or async handoff.
Publication/context guards remain after the host boundary. The
[matched comparison](BRIDGE_WORLD_READ_2026-10-16.md) fixes observer/workload inputs
and reports both service and cost. The sampled Paper messenger path is not a
reason to bypass public `sendPluginMessage`, registration or validation.

## S68 Owning-Server Quit Barrier And ANSI Normalization

Checked **2026-10-16**. In the retained guard-band failure, client `ended`
events and a 250 ms sleep precede two owning-server player quits and final
metrics still show two connections. Client socket completion is therefore not
a proven server cleanup barrier. The repeat waits at most 5 seconds for all
32 named server quit log entries, then still requires zero actual connections,
pending slots and tasks. Logs alone do not substitute for those final metrics;
the offline analyzer independently checks their ordering. Only post-measurement
synchronization changes, not active workload, quotas or acceptance thresholds.

The first barrier parser rejected real colored player names. Node's documented
[`util.stripVTControlCharacters`](https://nodejs.org/download/release/v22.23.3/docs/api/util.html#utilstripvtcontrolcharactersstr)
removes ANSI escapes; the API dates to Node 16.11. The Node 24 fixture normalizes
a copy with that built-in API, preserving original logs. Tests cover the exact
prompt/color format, incomplete/duplicate/foreign departures and raw replay.
Both failed attempts remain in the [guard-band report](BRIDGE_GUARD_BAND_2026-10-16.md),
not converted into successful cleanup/performance evidence.

## S69 Windows Scheduling Control

Checked **2026-10-16**. Microsoft's
[Scheduling Priorities](https://learn.microsoft.com/en-us/windows/win32/procthread/scheduling-priorities)
describes ready-thread scheduling by priority and distinguishes BelowNormal from
Normal process classes. This makes the fixture's explicit BelowNormal setting a
factor worth controlling; it does **not** identify the cause of any previously
recorded pause, nor establish that Normal is a hard real-time class.

The [separate scheduling trial](BRIDGE_PRIORITY_CONTROL_2026-10-16.md) holds the
production JAR, observer and workload fixed and changes only the owned server's
class in BelowNormal/Normal/Normal/BelowNormal order. Node's public
[`os.setPriority/getPriority`](https://nodejs.org/download/release/v22.23.3/docs/api/os.html#ossetprioritypid-priority)
are round-tripped on an actual owned Windows child
in regression tests and checked at four boundaries in each server run. The
existing default studies stay BelowNormal. No user process, foreground/cursor
control, AboveNormal/High/Realtime priority or production plugin policy changes.
Keep the predeclared gate and raw negative results even if Normal improves the
local wall distribution; do not explain unknown intervals solely by zero
quantized CPU or by the documentation's general scheduling rule.

## S70 Exact-Sized Array-Backed Encoding

Checked **2026-10-16**. Oracle Java 21 documents
[`ByteBuffer.allocate`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html#allocate(int))
as an array-backed allocation and
[`array`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html#array())
as access to that backing storage. Initial byte order is big-endian. The
[`Arrays.copyOf`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Arrays.html#copyOf(byte%5B%5D,int))
contract copies into a new array. Direct buffers are not justified for these
small, short-lived frames; they have a separate allocation/lifetime tradeoff.

The current sealed bridge schema permits exact 30..81-byte envelope sizes.
Allocate once at that exact size, leave writes unchanged, require a filled
buffer and return its owned array. Never share a mutable static/thread-local
buffer across calls or retain a caller-owned frame to avoid a required copy.
The remaining ingress/transport ownership boundaries are unchanged. All status
variants, exact lengths, golden bytes, mutation isolation, fuzz/truncation and
six native adapters must pass before retaining the shared codec change. The
[matched encoding experiment](BRIDGE_ENCODING_2026-10-16.md) isolates that change
with the same observer, priority, workload and predeclared performance gates.
Source-level array removal is not itself proof of universal wall/CPU speedup.

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

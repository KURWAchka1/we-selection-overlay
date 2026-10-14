# Java And Minecraft Engineering Knowledge Base

Research date: **2026-10-11**. Inspected repository baseline: `b79193c`.
Purpose: a working reference for maintaining this Java mod and designing its
optional WorldEdit server companion, not a generic Java tutorial.

## Read This First

- **Inspected** means source/configuration was read, not that a new server test passed.
- **Policy** means an engineering constraint for our implementation.
- **Proposal** means a design awaiting a prototype and measurements.
- **Unverified** means we must not advertise support yet.

An experimental **metadata transport foundation** and **Paper cuboid editing**
are implemented; see the [foundation evidence](SERVER_BRIDGE_IMPLEMENTATION.md)
and [remote-editing scope/tests](BRIDGE_REMOTE_SELECTION_2026-10-13.md). Development
Fabric/NeoForge consumers now cover all six runtime adapters. The additional
[26.x native gesture proof](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md) is limited to
exact Paper/WorldEdit pairs: 26.1.2/7.4.3 and 26.2/7.4.5. It does not extend the
1.21.11 fault/load evidence to those hosts. The later
[26.x negative/lifecycle matrix](BRIDGE_26X_LIFECYCLE_2026-10-14.md) passes the
named denied/absent, respawn/world/reconnect and graceful-stop/re-enable scenarios;
ungraceful loss, network-delay and load gates remain separate. Clipboard
synchronization, command simulation and other dedicated hosts remain unimplemented.
Published client 1.8.0 is unchanged and has no bridge protocol support.
Existing CUI-only servers must keep working without installing a companion.

| Reference | Use |
| --- | --- |
| [Bridge design](SERVER_BRIDGE_DESIGN.md) | Ownership, protocol, permissions, scheduling, feature boundaries |
| [Verification gates](SERVER_BRIDGE_VERIFICATION.md) | Security, correctness, dedicated-server and performance evidence |
| [Implementation evidence](SERVER_BRIDGE_IMPLEMENTATION.md) | Executed tests, exact scope and remaining gates |
| [Lifecycle/load pilot](BRIDGE_LOAD_AND_LIFECYCLE_2026-10-11.md) | Reproduced cleanup fixes, 16-player workload/JFR evidence and measurement limits |
| [Native lifecycle regression](NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md) | Same-world respawn regression, connection/world/player identity guard and current verification status |
| [Channel/reconnect and controlled baseline](BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md) | Reproduced native channel-loss defect, one-shot renewal, real reconnect and frozen-world comparison |
| [Server lifecycle and harness audit](BRIDGE_SERVER_LIFECYCLE_2026-10-12.md) | Disabled-dependency startup regression, repeated cleanup, silent-stop evidence and fail-closed process checks |
| [Negotiated session close](BRIDGE_SESSION_CLOSE_2026-10-12.md) | Minor-1 graceful-stop invalidation, minor-zero compatibility, terminal core cleanup and verification boundaries |
| [One-shot restart recovery](BRIDGE_RESTART_RECOVERY_2026-10-12.md) | Opt-in startup hint, fresh negotiation after graceful close, denied/overloaded fallback and native evidence |
| [32-player restart/load proof](BRIDGE_32_PLAYER_RESTART_LOAD_2026-10-12.md) | Concurrent graceful recovery, flood admission, failed-measurement checks and unaccepted performance outliers |
| [Tick attribution and admission research](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md) | Complete public-event/JFR markers, observed GC overlap, deterministic starvation counterexample and bounded fair-dispatch proposal |
| [Bounded fair dispatch](BRIDGE_FAIR_DISPATCH_2026-10-12.md) | Deferred metadata/control service, adversarial order/offset regression, real queue/latency checks and remaining cost gates |
| [Mixed gameplay load](BRIDGE_GAMEPLAY_LOAD_2026-10-12.md) | Frozen-world comparisons with moving non-consumers, live entities and exact WorldEdit work; method and evidence limits |
| [Active lifecycle load](BRIDGE_ACTIVE_LIFECYCLE_2026-10-12.md) | Observed queued wire requests before revocation/disconnect/disable, repeated recovery during active load, test-client state guard and evidence limits |
| [Method interval diagnostics](BRIDGE_SLICE_DIAGNOSTICS_2026-10-12.md) | Test-only method/JFR attribution, profiler interference, independent counter reconciliation and unchanged production artifacts |
| [Matched profiler control](BRIDGE_MATCHED_CONTROL_2026-10-13.md) | Same profiler policy in absent/installed gameplay runs, longer windows, raw-evidence reconciliation and independent-replication limits |
| [Remote cuboid editing](BRIDGE_REMOTE_SELECTION_2026-10-13.md) | Minor-2 capture/commit, live authority, single-use revisions, cached native source, real Paper/native evidence and fresh-start boundary |
| [Edit network faults and concurrent load](BRIDGE_EDIT_FAULTS_AND_LOAD_2026-10-13.md) | Native RTT/late-ack/revocation checks, 32-actor edit pressure, raw/JFR reconciliation and remaining production gates |
| [Native remote gestures](BRIDGE_NATIVE_GESTURES_2026-10-13.md) | Real callback/hit-test coverage, all faces/corners, cancellation, radial XYZ Move, server authority and native screenshots |
| [26.x remote editing](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md) | Exact host tuples, four native connections, shared remote source, screenshots and remaining cross-version gates |
| [26.x negative/lifecycle regression](BRIDGE_26X_LIFECYCLE_2026-10-14.md) | Native allowed/denied/absent joins, context renewal, old-token rejection, graceful companion lifecycle and bounded evidence |
| [Primary sources](SOURCES.md) | Versioned API/source links, dates, limitations |
| [Command preview](../../COMMAND_PREVIEW.md) | Existing local semantics, random samples, loaded-chunk policy |
| [Selection editing](../../SELECTION_EDITING_DESIGN.md) | Existing input, drafting, commit and cancellation behavior |

## Inspected Build Baseline

These are repository dependency pins, **not recommendations to install whatever
version is newest**. Keep Minecraft, loader, API, WorldEdit and Java as a tested tuple.

| Runtime | Java | Loader / API pin | WorldEdit compile pin | CUI library |
| --- | --- | --- | --- | --- |
| Fabric 1.21.11 | 21 | Loader 0.18.4 / API 0.141.3+1.21.11 | 7.4.2 | 4.0.2 |
| Fabric 26.1 | 25 | Loader 0.19.3 / API 0.145.1+26.1 | 7.4.3 | 4.0.3 |
| Fabric 26.2 | 25 | Loader 0.19.3 / API 0.158.0+26.2 | 7.4.5 | 4.0.3 |
| NeoForge 1.21.11 | 21 | 21.11.42 | 7.4.2 | 4.0.2 |
| NeoForge 26.1.x | 25 | 26.1.2.75 | 7.4.3 | 4.0.3 |
| NeoForge 26.2 | 25 | 26.2.0.67 | 7.4.5 | 4.0.3 |

Evidence: each runtime's `gradle.properties` and `build.gradle`.
The existing [release matrix](../../README.md#supported-release-targets) includes
26.1.1/26.1.2 checks; NeoForge 26.1/26.1.1 do not have the local WorldEdit support
claimed for 26.1.2. A version-range declaration is not a substitute for testing.
Distribution remains **one multi-version client JAR per loader**.
Client source is **not frozen at 1.8.0**. Necessary client protocol/provider/UI
changes must be implemented and tested alongside the companion. Preserve the
already published artifacts/tag, not the development source: client changes ship
in a separate fix/release rather than silently replacing an existing download.

## Actual Code Entry Points

Code discovery used the repository knowledge graph, symbol snippets and a call
trace of selection commit. Fabric 1.21.11 and NeoForge 26.2 selection adapters,
the shared transaction/preview code, and the NeoForge CUI bridge were inspected.
This was not an exhaustive audit of all six adapters.

| Existing code | What it establishes | Bridge consequence |
| --- | --- | --- |
| [WorldEditPointOverlayClient.initialize](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/WorldEditPointOverlayClient.java) | CUI receiver, connection reset, controllers and render hooks | Add capability negotiation beside CUI; do not replace its handler |
| [SelectionEditSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/selection/SelectionEditSource.java) | Read/commit boundary used by the editor | Add a remote implementation without rewriting mouse interaction |
| [WorldEditSelectionEditSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/selection/WorldEditSelectionEditSource.java) | Requires an integrated server; executes on its thread, publishes on client thread | Keep local implementation; remote data cannot use its object-identity token |
| [NeoForge selection source](../../WEaddon_neoforge_26.2/src/main/java/org/marshal/weaddon_lightposition/client/selection/WorldEditSelectionEditSource.java) | Same local-only boundary with NeoForge player adaptation | Dedicated server needs its own host adapter |
| [SelectionEditTransaction](../../shared/selection-editing/src/main/java/org/marshal/weaddon_lightposition/selection/worldedit/SelectionEditTransaction.java) | Rechecks selector, original points and expand/contract/shift permissions; updates selector/CUI | Reuse domain checks; retain original token on server, never deserialize it from a client |
| [CommandPreviewSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/preview/CommandPreviewSource.java) | Preview input/output abstraction | Transport remote results through this boundary |
| [WorldEditCommandPreviewSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/preview/WorldEditCommandPreviewSource.java) | Captures loaded source on integrated-server thread; restricted parser and bounded slices | Preserve semantics; replace client-declared chunk coverage with server-verified interest |
| [WorldEditPreviewProgram](../../shared/command-preview/src/main/java/org/marshal/weaddon_lightposition/preview/WorldEditPreviewProgram.java) | Narrow pattern syntax, official factories, explicit safe mask copies | This is not an arbitrary-command sandbox; review extension/parser hooks before server use |
| [PreviewExtent](../../shared/command-preview/src/main/java/org/marshal/weaddon_lightposition/preview/PreviewExtent.java) | Section palettes, captured-cell checks, memory-only writes | Reuse representation; it is mutable and not automatically worker-thread safe |
| [ClipboardPreviewSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/clipboard/ClipboardPreviewSource.java) | Copied-object rendering source boundary | Remote snapshots should reuse the renderer and off-by-default preference |
| [WorldEditClipboardPreviewSource](../../WEaddon_fabric_1.21.11/src/main/java/org/marshal/weaddon_lightposition/client/clipboard/WorldEditClipboardPreviewSource.java) | Budgeted capture, source cache, baseline/draft transforms and permission checks | Send content once per revision; transform metadata separately |
| [RuntimeCuiBridge](../../shared/neoforge-cui/src/main/java/org/marshal/weaddon_lightposition/client/RuntimeCuiBridge.java) | Shared EngineHub registration or private fallback; client-thread delivery | Do not introduce a second owner of `worldedit:cui` or remove this compatibility path |

The inspected clipboard source has its own scan/render safeguards. Do not confuse
these with the uncapped loaded-source policy of region-command previews.
Existing reflection in the CUI compatibility adapter is not precedent for
reflectively calling WorldEdit command internals on a server.

## Java Engineering Policy

1. Compile pure protocol/data code with a Java 21 toolchain and `--release 21`;
   keep Java 25 and Minecraft classes in matching adapters. Explicitly configure
   test JVMs as well as compilers. See [Gradle toolchains](SOURCES.md#s22-gradle-toolchains).
2. Use immutable messages with defensive copies. A `record` containing an array,
   collection, `LocalSession`, `Region`, `Mask` or `Clipboard` is not deeply immutable.
3. Assign every mutable object one owning thread. Publish snapshots through an
   explicit handoff; `volatile` visibility alone does not make mutation safe.
4. Use a bounded executor and bounded queue only for work proven independent of
   live game objects. Do not use the common ForkJoin pool or `CallerRunsPolicy`
   for expensive preview work: saturation must not move it onto the tick/network
   thread. See [executor behavior](SOURCES.md#s19-java-executors).
5. Never `join()`/`get()` a worker result on a game or network thread. Generation
   cancellation must be checked within cooperative loops, not just at completion.
6. Close streams, compressors and other owned resources on every outcome. Stop
   tasks and release session/cache references on disconnect, dimension change,
   plugin disable and server stop. Do not block the tick thread waiting for shutdown.
7. Catch expected input/compatibility errors at boundaries and report stable reason
   codes. Preserve causes in rate-limited developer logs. Do not hide an unexpected
   exception behind an empty preview or broadly catch all failures as success.
8. Measure allocations, GC and thread stalls before adding concurrency. Virtual
   threads do not accelerate CPU-bound work or authorize world access; see
   [Oracle guidance](SOURCES.md#s20-java-virtual-threads).

## WorldEdit API Rules

- Resolve the real player's actor through `BukkitAdapter`, `FabricAdapter` or
  `NeoForgeAdapter` for that platform, then obtain their `LocalSession`.
  Do not impersonate console or grant temporary OP. Missing selection/clipboard
  is a normal state. See [sessions](SOURCES.md#s02-worldedit-local-sessions).
- Respect selection world, selector type, original points, build height, masks,
  inventory mode, placement origin, permissions and configured change limits.
  Reject unsupported context; do not silently erase it to make preview work.
- Read a region without modifying it. To change selection, operate through the
  selector contract and synchronize the player. Selection changes are not block
  edits and must not manufacture a WorldEdit undo entry.
- Use WorldEdit block/pattern/mask factories with a restricted `ParserContext`.
  Set world/session/actor deliberately; `setWorld` also sets the context extent,
  so bind the isolated extent **afterward**. Legacy IDs must use WorldEdit's
  mapping, not our own table. See [ParserContext](SOURCES.md#s07-worldedit-parser-context).
- A public Java method is not necessarily public supported API. WorldEdit marks
  its `command` and `internal` packages and most platform implementation code
  internal. Our existing `CommandArgParser` dependency is a version-pinned
  compatibility risk, not an officially stable extension point.
  See [API boundaries](SOURCES.md#s06-worldedit-internal-api-boundary).
- Never invoke `ClipboardCommands` to simulate input. Inspect their pinned source
  to verify transform semantics, then use public transform/domain APIs.
- Actual future world-edit features must use a correctly attributed `EditSession`,
  close/flush it, and remember player history where appropriate. Our read-only
  preview must not create a live edit session just to later undo its changes.
  See [edit-session lifecycle](SOURCES.md#s03-worldedit-edit-sessions).

## Server Performance Policy

Low server overhead is a required product constraint, not a promise of zero cost.
No enabled consumer means no preview source scans, content hashing, simulation
or block transfers. Active work must be revision-driven where supported, bounded
globally across players, cancellable and subordinate to normal gameplay.
Rendering and interactive drafting remain on the client; async server work is
not free CPU or memory. Do not force-load chunks for previews.
Follow the [low-overhead contract](SERVER_BRIDGE_DESIGN.md#low-overhead-server-contract)
and record [baseline/load evidence](SERVER_BRIDGE_VERIFICATION.md#server-cost-acceptance)
before claiming a performance result or choosing release defaults.

## Network Facts That Affect This Project

| Verified fact | Engineering decision |
| --- | --- |
| EngineHub CUI uses `worldedit:cui`; inspected packet carries event type/arguments and protocol version 4 | Keep CUI for selection display; use our own namespace for bridge traffic |
| CUI library versions 4.0.2/4.0.3 are not our bridge protocol version | Negotiate bridge schema/capabilities independently |
| Fabric 1.21.11 docs use `playS2C`/`playC2S`; current 26.2 docs use `clientboundPlay`/`serverboundPlay` | Compile native registration separately per runtime; do not copy latest examples blindly |
| NeoForge registrar defaults to main-thread handlers and supports optional payloads; its version negotiation is Neo-to-Neo | Use optional play payloads plus our own handshake for Paper/Fabric interoperability |
| Paper 1.21.11 documents `Messenger.MAX_MESSAGE_SIZE = 1048576` | Do not hardcode old 32766-byte folklore or assume this is every transport/direction's limit |
| Paper plugin messages ride a player's game connection | Use custom plugin channels, not BungeeCord control messages or an extra TCP service |

Sources: [CUI packet](SOURCES.md#s10-enginehub-cui-wire-packet),
[Fabric versions](SOURCES.md#s15-fabric-networking),
[NeoForge](SOURCES.md#s17-neoforge-networking),
[Paper messaging](SOURCES.md#s13-paper-plugin-messaging).
Exact registration signatures, codec names and payload ceilings must be checked
against the resolved dependency artifacts before implementation.

## How To Use And Maintain This Base

Before a feature: read the applicable section, find current symbols through the
graph, check the matching source pin, and list unknowns in the design document.
Before a dependency upgrade: recheck API names, bytecode level, payload behavior,
classloading, WorldEdit semantics and the affected test matrix.
After a change: record the exact tuple, test results and remaining limitations.
Keep proposals separate from verified facts; replace outdated entries rather
than appending contradictory advice. Never treat `latest` documentation as an
immutable specification. Refresh this source ledger at the next implementation
step and on every dependency upgrade.

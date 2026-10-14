# Server Bridge Foundation: Executed Evidence

Date: **2026-10-11**. Baseline commit: `2496142356da4c7b2b65aabdd4d488dfd92273cd`;
source additions and tests are preserved in the accompanying foundation commit.
Companion version: **0.1.0-dev**. This is not a supported production release.
Published client 1.8.0, its archived JARs and Git tag are unchanged.

This initial foundation record is supplemented by the
[full regression cycle](FULL_REGRESSION_2026-10-11.md), including new denial,
clock/lifecycle and aggregate-admission checks. Its later companion hash and
native gameplay/UI evidence are recorded there, not substituted for the
original observations below.

The subsequent [native Paper regression](NATIVE_PAPER_REGRESSION_2026-10-11.md)
passes six actual Fabric/NeoForge 1.21.11 connections, including denied and absent
companions. It also records the standalone NeoForge CUI fix, stronger protocol
fuzzing, exact later candidate hashes and remaining host/performance gates.

The later [lifecycle and 16-player load pilot](BRIDGE_LOAD_AND_LIFECYCLE_2026-10-11.md)
adds reproduced old-epoch cleanup fixes, real respawn/reconnect/dependency-disable
checks, 33 pure tests per JVM and repeated metadata workloads with JFR. It records
baseline spread and an unattributed long tick; it does not close production
performance or automatic native lifecycle-recovery gates by itself.

The [2026-10-12 native lifecycle regression](NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md)
then reproduces and fixes same-world respawn recovery, verifies both native
loaders through respawn/Nether/Overworld transitions without reconnecting, and
repeats granted/denied/absent-companion checks. It records 34 pure tests per JVM,
six codec probes and six runtime builds; other lifecycle/performance gates remain
explicitly open. These are development candidates, not a public client update.

The [channel/reconnect and controlled-baseline cycle](BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md)
subsequently reproduces a native channel-loss defect and fixes the shared client
state machine. Both native loaders pass wire channel removal/re-advertisement
and same-process reconnect. Pure coverage grows to 36 tests per JVM, alongside
six codecs and 132 universal-packager tests. Its separate frozen-world baseline
does not replace the busy-server release gate or explain the earlier long tick.

## Implemented Boundary

The [edit network-fault and concurrent-load cycle](BRIDGE_EDIT_FAULTS_AND_LOAD_2026-10-13.md)
subsequently validates native delayed/late-ack/revoked commits and 32-actor edit
pressure without changing production behavior. Its 2,400 matched JFR ticks and
1,240 acknowledged active commits close only the named functional gates, not
representative performance or other hosts. Necessary client changes remain in scope.

The subsequent [native remote gesture cycle](BRIDGE_NATIVE_GESTURES_2026-10-13.md)
passes all faces/corners, free-corner release, five cancellation boundaries and
real radial XYZ Move on both native 1.21.11 loaders, repeated with frame-camera
checks and inspected screenshots. Actual WorldEdit bounds/counters reconcile;
these bounded callback tests do not establish manual hardware ergonomics or
broader host/version/performance support.

**Current scope update, 2026-10-13:** the [remote cuboid editing stage](BRIDGE_REMOTE_SELECTION_2026-10-13.md)
adds separately authorized minor-2 capture/commit, initially on Paper 1.21.11.
The later [26.x stage](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md) ports the same remote
source to the four 26.x adapters and passes 112 native gesture cases on exact
26.1.2/7.4.3 and 26.2/7.4.5 Paper/WorldEdit pairs. It reuses the existing
transaction/controller and does not edit world blocks. Historical read-only
foundation observations below retain their original scope. Clipboard, simulation,
other server implementations and general hot reload are still unimplemented.

The [2026-10-14 26.x negative/lifecycle cycle](BRIDGE_26X_LIFECYCLE_2026-10-14.md)
adds twelve native allowed/denied/absent runs on those two exact hosts, including
actual respawn/world changes, channel withdrawal, reconnect and graceful companion
stop/re-enable. Old-context edit snapshots and unavailable reads fail locally.
The initial test-injector guard failure is retained and corrected after exact
server-class inspection. Production client/companion artifacts are unchanged;
network-delay, ungraceful loss and load evidence are not widened by implication.

The [server lifecycle and harness audit](BRIDGE_SERVER_LIFECYCLE_2026-10-12.md)
adds an enabled-WorldEdit startup guard after a real failing fixture, 14 wire
check groups including three plugin cycles, and 16 automated Node harness tests.
It changes only the experimental companion and test tools, not client JARs or
the wire schema. That cycle confirms the silent-stop gap rather than closing it.

The [negotiated-close cycle](BRIDGE_SESSION_CLOSE_2026-10-12.md) adds experimental
minor-1/capability opt-in and a nonce/epoch-bound SessionClosed frame before
graceful Paper disable. Both native 1.21.11 loaders discard cached metadata,
grants and pending work while preserving CUI and their gameplay connection.
Coverage includes 42 pure tests on each JVM, 17 Node harness checks, six native
codecs and 132 packager tests. Silent restart remains unavailable until a fresh
discovery context/advertisement edge; automatic restart recovery is not claimed.

The subsequent [restart-recovery cycle](BRIDGE_RESTART_RECOVERY_2026-10-12.md)
adds a separate optional 22-byte startup hint without changing main protocol 1.1.
Both native 1.21.11 loaders now renegotiate once after a correlated graceful close,
without reconnecting or implicitly reading selection metadata. Denied clients
remain quiet; ordinary/legacy clients receive no unsupported hint. Coverage is
50 pure tests per JVM, 17 Node checks, six codecs, 17 Paper wire groups and 132
packager tests. Saturated admission fails closed without retrying; busy-server
recovery, missed/ungraceful signals and backend switches are still open gates.

The later [32-player restart/load proof](BRIDGE_32_PLAYER_RESTART_LOAD_2026-10-12.md)
checks the same production candidate with 14 measurement windows and six
concurrent graceful restarts. Each restart sends 32 hints/704 bytes and performs
zero implicit reads. Flood admission and ordinary requester progress pass;
all disconnects release tracked peers. Harness self-tests grow to 22. This is
not native gameplay evidence or restart during continuing flood. Substantial
MSPT spread and unattributed long ticks leave cost acceptance explicitly open.

The subsequent [tick attribution and admission research](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md)
changes only test tools and the separately packaged fixture. A new 32-player
frozen-world run records 4,800 complete public-event/JFR tick markers; the new
installed maxima overlap actual GC pauses without explaining older outliers.
The actual pure core reproduces ordinary-requester starvation under a 31-sender
adversarial ordering on both JVMs. Hard admission bounds pass, **fair service
does not**. Coverage is 50 pure tests per JVM, 23 general Node checks and four
actual-recording JFR checks per JVM. No client/companion behavior or protocol is
changed, and the bounded round-robin alternative is still a research proposal.

The subsequent [bounded fair-dispatch cycle](BRIDGE_FAIR_DISPATCH_2026-10-12.md)
implements that narrower metadata/control prototype in the experimental server
only. It replaces first-arrival global admission with one queued frame per
connection and fair owning-thread service. Deferred authority/context checks,
timeout compatibility, storage/age bounds and cleanup have dedicated tests.
Coverage is 68 pure tests per JVM, 25 Node checks, four JFR checks per JVM,
17 real wire groups, six actual native connections, two fair 32-player load
cycles and a separate three-cycle 32-peer restart regression. The latter
reproduced and corrected an obsolete zero-active-task fixture assumption.
Historical reports above describe their original candidates, not current code.

Pure Java 21 messages, bounded codec, client state machine and owning-thread
server service; optional native transports in all six runtimes; Paper 1.21.11
host using official WorldEdit 7.4.2 public session APIs. Only hello/capabilities,
negotiated lifecycle notification and explicit **read-only selection
bounds/height/grants** are implemented.
See [wire schema](SERVER_BRIDGE_PROTOCOL.md) and [build instructions](../../server-bridge/README.md).

There are no idle/session-polling tasks. A single owned synchronous dispatch
task runs only while pending frames exist, and is cancelled on idle or teardown.
No workers, block/chunk reads, clipboard subscriptions,
simulations, history edits or selection commits exist in this foundation.
There is no UI/provider switch to remote editing yet. CUI is a separate channel;
ordinary local sources and rendering remain unchanged. Bounds are not exact
non-cuboid geometry. Grants are opt-in even for OP, and rechecked on every read.
The server host deliberately refuses unverified Minecraft/WorldEdit versions.

## Unit And Native Codec Checks

Command: `tools/test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline`.
Java: Eclipse Temurin **21.0.11+10-LTS** and **25.0.2+10-LTS**.

| Check | Observed |
| --- | --- |
| Protocol codec | 5 tests pass on each JVM; golden framing, domain constraints, truncations/directions/trailing data, 10,000 seeded malformed samples |
| Server service | 9 tests pass on each JVM; actual connection identity, revocation, context reset, duplicate caching, aggregate/player admission, storage bounds and wrong-thread refusal |
| Client service | 7 tests pass on each JVM; one-shot discovery, absence/denial, nonce/epoch/request freshness, frame negotiation, timeout and 100,000 idle polls without extra source reads |
| Fabric 1.21.11 | Actual PacketCodec round trip passes, Java 21 |
| Fabric 26.1 / 26.2 | Actual StreamCodec round trips pass, Java 25 |
| NeoForge 1.21.11 | Actual StreamCodec round trip passes, Java 21 |
| NeoForge 26.1 / 26.2 | Actual StreamCodec round trips pass, Java 25 |

Native probes use real byte-buffer codec implementations, exact application-byte
equality, full consumption, defensive array copies and oversized/truncated buffer
handling. They do not initialize a window or world. Shared standalone modules
compile with `--release 21`; runtime copies use each native runtime's toolchain.
Logs: ignored `build/bridge-validation/`; JUnit XML/HTML: each module's `build/`.

## Development Packaging

`tools/test-bridge-packaging.ps1` completed all six native JAR builds and both
universal packagers using **1.8.0-bridge-dev** artifact names. Fabric: 120 tests
pass; NeoForge: 9 pass. Tests check the relocated bridge payload/state/codec for
every runtime and exclude server/fixture classes from clients, alongside existing
metadata, CUI isolation, bytecode, UI assets and selection-domain checks.
Production companion packaging separately checks Java 21 class headers, required
license/config/entrypoints and absence of bundled client/test/provided dependencies.

Observed footprint: 1,656,166 bytes Fabric; 1,628,365 bytes NeoForge; 44,402 bytes
Paper companion. The universal client increase is about 75 KiB per loader.
Development packaging does not call `archiveVersionArtifact` or write `libs/1.8.0/`.

## Real Paper Wire Proof

Command: `tools/test-paper-bridge.ps1` with exact artifacts below and explicit
owner EULA acceptance. Paper **1.21.11 build 132**, commit `c5eb079`, Java 21.0.11.
Official WorldEdit **7.4.2**. Headless TCP library **minecraft-protocol 1.68.0**.
Four offline test identities; loopback only; no OP or protection plugins.
Test-only permission attachments seed distinct selections using public APIs.
The separately built fixture is not inside either client or companion JAR.

- Paper, WorldEdit and companion enable successfully.
- A protocol client without the bridge joins; it receives no bridge data.
- Normal CUI cuboid/point packets arrive for both bridge-granted and denied users.
- Hello plus idle interval performs **zero selection metadata reads**.
- Two authorized users receive their own different inclusive bounds, Y=-64..319
  and appropriate expand/contract/shift flags. Another user's epoch is rejected.
- A duplicate request returns identical cached bytes without a second read.
- Revoking the opt-in node denies a duplicate cached request and invalidates epoch.
- Re-grant/renew, incomplete selection and recreated selection return proper state.
- Oversized/unknown frames and incompatible major fail safely without disconnect.
- Idle after active requests leaves read/reply counters unchanged; disconnect
  releases all three negotiated peer records (connection count returns to zero).

Observed final totals: 14 admitted frames, 2 malformed, 4 metadata reads,
11 replies, 0 retained peers after disconnect. This is a correctness scenario,
**not** a many-player load test, MSPT benchmark or zero-overhead guarantee.
Server launched headlessly with two processors, 1 GiB heap and BelowNormal
priority; it shuts down after assertions. No cursor/focus control is involved.

| Artifact | SHA-256 |
| --- | --- |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit Bukkit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Companion tested | `c1e1053055373be679775bbe9e1ac8e6bd57399ed36e867faa822f4985068a2e` |

Local evidence: `build/bridge-paper-proof/wire-proof.json`, `wire-server.log`
and ordinary Paper logs. These generated identities/worlds/logs are not published.
Downloads use [Paper's official service](https://docs.papermc.io/misc/downloads-service/)
and [WorldEdit's versioned distribution](https://modrinth.com/plugin/worldedit/version/p8T2aZ8U),
with verified checksums. [Pinned CUI source](https://github.com/EngineHub/WorldEdit/blob/eb8e82c3d8843bed3f71e82ea974122415645178/worldedit-bukkit/src/main/java/com/sk89q/worldedit/bukkit/CUIChannelListener.java)
establishes the independent initialization path used by the fixture.

## Remaining Gates

Native Fabric/NeoForge 1.21.11 dedicated joins and missing-companion fallback
passed the subsequent guarded matrix linked above. That is not proof for other
server versions or modpacks. Only Paper 1.21.11 has a host adapter; 26.x hosts, dedicated mod-server
hosts, FAWE, Folia, proxies and translators remain unimplemented/unverified.
No advertised compatibility matrix is widened by this work.

Bounded fair dispatch for metadata/control is now implemented and tested within
the scope recorded above; measured busy-server cost acceptance remains open.
The next feature after these gates is a
revision-safe remote selection transaction/source integrated with the existing
editor. Clipboard streaming and read-only command simulation follow with
aggregate budgets and authorization.
Busy-server CPU/allocation/MSPT profiles and all applicable
[release gates](SERVER_BRIDGE_VERIFICATION.md) remain required before publication.

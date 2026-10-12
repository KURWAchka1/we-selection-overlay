# Negotiated Graceful Session Close

Date: **2026-10-12**. Source baseline: `7590433`; implementation and tests are
in the accompanying commit. Companion **0.1.0-dev**, clients
**1.8.0-bridge-dev**, experimental application protocol **1.1**. No public
release or existing release artifact/tag was replaced.

## Defect And Contract

The preceding [server lifecycle audit](BRIDGE_SERVER_LIFECYCLE_2026-10-12.md)
demonstrated that unregistering Paper plugin channels did not withdraw their
remote advertisement. An otherwise idle client could retain READY metadata
after the companion stopped. An observed UNREGISTER edge, tested earlier, is
a different scenario and cannot substitute for this check.

The new **SessionClosed** notification is exactly **38 bytes**, containing
the client's nonce and the closed epoch. It requires both negotiated minor >= 1
and requested/granted capability `0x02`, alongside selection-read authorization.
Minor-zero welcomes retain minor zero, including denial/incompatibility; old,
denied and ordinary clients are not sent the new opcode. Notifications alone
cannot authorize data access. No protected selection content is in the frame.

The client accepts only the current READY session's matching nonce/epoch.
It clears selection, grants, nonce, epoch and pending work, entering UNAVAILABLE.
Late replies/welcomes, duplicate closes and another context's close cannot
restore or discard current state. A still-advertised channel is retained as
an observation, preventing a per-tick discovery retry into a stopped service.

The Paper handler sends in **PluginDisableEvent**, before send authority is
lost, not in `onDisable`. Checked against Paper build 132 commit `c5eb079`;
see [primary sources S41](SOURCES.md#s41-negotiated-graceful-session-close).
There is at most one frame per eligible bounded peer during graceful shutdown.
The core is terminally closed before callbacks, rejects later receives and
clears records even if a sender throws. The Paper sender isolates expected
per-player transport failures; final `onDisable` unregisters both directions
and releases its core reference. No server timers, tasks, worker, scan, WorldEdit
read, permission change or recovery registry was added.

## Executed Checks

Pinned host: **Paper 1.21.11-132**, official WorldEdit **7.4.2+7450-eb8e82c**,
Temurin **21.0.11+10-LTS**, loopback **127.0.0.1:25579**, offline synthetic
identities, explicit fixture grants and no OP/protection plugins. Headless TCP
tests use **minecraft-protocol 1.68.0 / Node 24.21.0**. Native clients have no
local WorldEdit. Server: 2 processors/1 GiB; native client: 2 processors/1536 MiB,
40 FPS. User worlds and foreground input are untouched.

| Verification | Observed |
| --- | --- |
| Pure tests, Java 21 and 25 | **42 per JVM**: client 14, server 16, token bucket 4, codec 8 |
| Node harness self-tests | **17 pass**, including minor-1 golden framing and close bounds |
| All six native buffer codecs | Pass Hello and SessionClosed without extra framing, with full consumption |
| Development runtime/packager regression | Six runtime JARs, **121 Fabric + 11 NeoForge tests** pass |
| Pinned real Paper wire test | **15 grouped checks pass**, including three disable/enable cycles |
| Real native Paper joins | **6 pass**: allowed/denied/absent on both 1.21.11 loaders |

Codec tests retain minor-zero golden bytes and add close round trips, every
truncated prefix, trailing/wrong-direction/zero-epoch rejection, single-bit
mutations and seeded structured fuzz for all five opcodes. Client tests cover
both cached and pending data, non-negotiated/wrong-context notices, late frames,
quiet constant advertisement and one-shot renewal after a genuine channel edge.
Server tests cover minor/capability opt-in, unknown bits, denied/invalidated
recipients, idempotence, sender failure and use after terminal close.

Wire proof: `build/bridge-paper-proof/wire-proof.json` and `wire-server.log`,
checked at **2026-10-12T12:11:47.991Z**.
Each stop sends exactly one correlated notification, including across a quiet
interval; legacy/denied/ordinary peers remain unnotified. Each restart rejects
old epochs, starts with zero peers/reads/replies, and serves an explicit fresh
hello/read/duplicate with **1 read / 3 replies**, then no idle counter changes.
Dependency disable also notifies; re-enable with disabled WorldEdit fails closed.
All three cycles still report `remoteStillAdvertised=true`.

Native proof: `build/bridge-native-proof/native-proof.json`, checked at
**2026-10-12T12:07:57.197Z**. Fabric Loader **0.18.4**, Fabric API
**0.141.3+1.21.11**; NeoForge **21.11.42**. Both allowed runs preserve same-world
respawn, Nether/Overworld, actual channel removal/restoration, revocation and
same-process reconnect checks. After obtaining metadata, graceful plugin stop
clears state without changing connection/world/player; the remote channel stays
advertised and CUI remains usable. Quiet re-enable leaves the client unavailable
and the new core idle: this proves no stale reuse/retry, **not automatic recovery**.
Each allowed run has **8 explicit reads / 15 replies before shutdown**, relative
to its baseline; the close notification is additional. Counters restart at zero
with the new core, so do not sum incompatible before/after counter epochs.
Denied runs do zero reads; absent runs preserve CUI and disconnect normally.

Native preflight: `build/desktop-tests/WesoTest-bfeabf584eb1449cb4ab6b6fe3f847fc.log`.
Matrix worker: `WesoTest-5f496dbc477449999f3be1dd01551ba5.log` in that directory.
Both private-desktop and cursor-agent guards pass; no desktop switch/OS input.

## Harness Failure And Evidence Identity

The first extended native attempt passed the Fabric in-game checks but failed
its outer script: it called an online-player-only fixture command after the
player had disconnected. It exited unsuccessfully and published no success
proof. The corrected ordering re-enables/verifies the companion while that
player is still connected, then checks quiet state before disconnecting.
The complete six-run matrix was repeated successfully. Preserved logs:
`build/bridge-native-proof/session-close-harness-before-fix-native-server.log`
and `session-close-harness-before-fix-fabric-allowed.log` in the same directory.
Native logs are drained after child close before successful proof publication;
shutdown still uses the fail-closed owned-process helper.

| Final tested artifact | SHA-256 |
| --- | --- |
| Companion, 47,213 bytes | `d3014d1fbcee76a64baefdc9abd01ef66a87b4595c9a7b860b9e032640f9d297` |
| Fabric dev universal, 1,661,820 bytes | `52ff1d7b1cb07718d9baf725430440f884cbc0cec05c76362d8c9d145ce399e4` |
| NeoForge dev universal, 1,633,967 bytes | `7e68e243d567ffff91b97efcb89599fbec8d4c91ed72e60231d29f7499208045` |
| Paper test fixture, unchanged | `90e027dffaf4df29d5e364cffde4c987ce6cfdcb4e7683e6889862e304731c24` |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

Wire/native proofs and current companion hashes agree. Repeating packaging
after native tests preserves both dev client hashes. Previous minor-zero wire
proof/log are preserved as `pre-session-close-wire-proof.json` and
`pre-session-close-wire-server.log`; previous native proof is
`build/bridge-native-proof/pre-session-close-native-proof.json`. Earlier load
results remain tied to their earlier artifacts, not relabelled for this build.

Reproduce from the root with `tools/test-server-bridge.ps1` (JDK 21/25, `-Offline`),
`tools/test-bridge-packaging.ps1` (same JDKs, `-RerunTests -Offline`),
`tools/test-paper-bridge.ps1` (pinned Paper/WorldEdit, cached Node dependencies,
`-AcceptEula -Offline`) and guarded `tools/test-native-paper-bridge.ps1`
(JDK 21, `-ModVersion 1.8.0-bridge-dev -AcceptEula`). EULA acceptance applies only
to these disposable fixtures. Unit/codec/package logs: `build/bridge-validation/`.

## Remaining Gates

Automatic discovery after silent re-enable is **not implemented**. A fresh
gameplay context, reconnect or observed channel rising edge can renew discovery;
an ungraceful loss without a signal or between-tick channel flap remains separate.
Minor-zero sessions cannot receive negotiated close notices. Full hot reload,
backend switching, heap/classloader retention and other dedicated host/version
tuples remain unverified. Repeated enable/disable is fault testing, not advice
to use `/reload` in production.

No new load benchmark was run: the earlier p95 differences and 134.4245 ms
outlier remain unattributed, and busy-server acceptance remains open. A bounded
38-byte stop notification does not prove zero overhead. Remote editing, clipboard
and simulation are still unimplemented. Client changes remain dev-only; any
shipped client fix requires a new version under [release policy](../release/FIX_RELEASE_POLICY.md).
Published 1.8.0 JAR hashes/tag are unchanged; all owned test processes stopped
and port 25579 is free at completion.

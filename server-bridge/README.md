# Optional Server Bridge

**Development foundation, not a production companion release.** Current bridge
version: `0.1.0-dev`. Client release `1.8.0` is unchanged; rebuild development
clients to include the new transport. Do not replace or move the `1.8.0` tag.

## Implemented Scope

- One bounded binary protocol shared by all six native client runtimes and the
  Paper adapter. No extra port, account, file transfer or Java serialization.
- Optional channel discovery and hello/capability negotiation, with safe
  absent/denied/incompatible states. Normal `worldedit:cui` is untouched.
- Explicit read-only requests for the requesting player's selection bounds,
  inclusive world height and expand/contract/shift permission bits.
- Owning-thread enforcement, per-connection and aggregate admission limits,
  bounded connection state, duplicate-read caching and context cleanup.
- Optional minor-1 session-close notification before graceful Paper disable;
  client metadata, grants and pending requests are discarded without polling.
- Separate opt-in 22-byte startup hint: one fresh negotiation after graceful
  close, with no automatic metadata read, permission polling or retry loop.
- One pending frame per connection, fair deferred dispatch and a single owned
  synchronous task only while the queue is nonempty. No idle polling tasks,
  workers, block reads, clipboard scans, simulation, mutations or subscriptions.

The sole implemented data capability is **selection metadata read**, not remote
selection editing. Bounds do not describe non-cuboid geometry; CUI remains its
source. Existing local previews, isolation, selection editing and UI are not
rerouted through this prototype. Clipboard synchronization, command previews,
selection commits and dedicated Fabric/NeoForge server hosts remain planned.

## Layout

| Module | Role |
| --- | --- |
| `../shared/server-bridge-protocol` | Java 21 message values and bounded codec |
| `../shared/server-bridge-client` | Loader-independent client connection state |
| `core` | Loader-independent, connection-owned server request handling |
| `paper` | Paper messaging/lifecycle and official WorldEdit metadata access |
| `paper-test-fixture` | Isolated grants/selections, measurements and opt-in mixed world workload; never distributed |

Client-native files live in each runtime's `client/bridge` package. Maintain the
Fabric 26.2 adapters and run `node tools/sync-server-bridge.mjs` from the root to
update the other adapters; application protocol/state logic is not duplicated.

## Build And Test

From the repository root, with JDK 21 and 25 installed:

```powershell
.\tools\test-server-bridge.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25'
```

Use `-Offline` only after dependencies are cached. Node.js is required for the
36 harness self-tests (owned-process shutdown, frozen-world safety, wire
helpers, mixed-workload and active-lifecycle validation); they run without Minecraft windows or third-party Node packages.
The script also runs unit/domain tests on both JVMs, builds the Paper JAR and checks the actual native payload codecs
on all six runtimes. It does **not** launch Minecraft, create windows, capture
the cursor, accept an EULA or prove a dedicated-server login.
Logs: `build/bridge-validation/`. Unit HTML/XML reports are in each module's
`build/reports/tests/test/` and `build/test-results/test/` directories.

Build only the companion:

```powershell
.\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge check assemble --no-daemon --max-workers=2
```

Artifact: `paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar`.
It contains only project-owned core/protocol classes, plugin metadata and the
license. Paper, WorldEdit, testing tools and client code are not bundled.

## Controlled Server Proof

The initial host refuses Minecraft versions other than **1.21.11** and WorldEdit
versions other than official **7.4.2** (including its `+build` suffix). This is
a guard against accidental unverified deployment, not evidence that the target
already passed a live test. No FAWE, Folia, proxy or translator support is claimed.

The real Paper/application-wire check has passed with headless protocol clients;
this does not establish Fabric/NeoForge native joins. To repeat it, obtain the
exact official Paper/WorldEdit artifacts recorded in the evidence document and
install test-only `minecraft-protocol@1.68.0` into `build/bridge-wire-deps`:

```powershell
npm install --prefix build/bridge-wire-deps minecraft-protocol@1.68.0 --ignore-scripts --no-audit --no-fund
.\tools\test-paper-bridge.ps1 -Java21 'C:\path\to\jdk-21' -PaperJar 'C:\path\to\paper.jar' -WorldEditJar 'C:\path\to\worldedit.jar' -NodeDependencies 'C:\path\to\build\bridge-wire-deps' -AcceptEula
```

Use `-AcceptEula` only after accepting the Minecraft EULA. The script builds the
test fixture separately, binds only `127.0.0.1:25579`, never grants OP, caps the
server at two processors/1 GiB and stops it after the test. Logs and generated
worlds remain under ignored `build/bridge-paper-proof/`. Do not deploy the
test-fixture JAR to an ordinary server. For development client packaging run
`tools/test-bridge-packaging.ps1` with the same two JDK arguments as above.

Native Fabric and NeoForge 1.21.11 clients subsequently passed six real Paper
connections, including denied permission and absent companion. Reproduce after
preparing the pinned wire dependencies and building the development clients:

```powershell
.\tools\test-native-paper-bridge.ps1 -Java21 'C:\path\to\jdk-21' -ModVersion 1.8.0-bridge-dev -AcceptEula
```

This uses the guarded private-desktop/cursor-agent harness, no local WorldEdit,
and a separate disposable loopback server under `build/bridge-native-proof/`.
See [native results and limits](../docs/engineering/NATIVE_PAPER_REGRESSION_2026-10-11.md).
It does not establish 26.x server hosts or performance under a busy-server load.

The extended wire fixture also exercises actual same-world death/respawn,
same-process reconnect, bridge channel unregister/register and WorldEdit disable.
It verifies that stale epochs cannot read metadata, unrelated channel changes
do not reset the bridge, and dependency shutdown unregisters both directions.
These headless lifecycle checks are not a native-client respawn/recovery claim.
The separate [native lifecycle regression](../docs/engineering/NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md)
verifies real same-world respawn and Nether/Overworld recovery on Fabric/NeoForge
1.21.11. Client context includes the local-player instance as well as the network
handler/world; renewal sends a fresh hello, not an unsolicited selection read.
The later [channel/reconnect cycle](../docs/engineering/BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md)
also verifies actual same-process reconnect and wire channel removal/restoration
on both native loaders. An observed channel loss clears pending work and data;
the next advertisement renews once. This is not permission polling or proof
of silent plugin-disable/backend-switch recovery.
The [server lifecycle and harness audit](../docs/engineering/BRIDGE_SERVER_LIFECYCLE_2026-10-12.md)
subsequently verifies three real companion disable/enable cycles, resource
cleanup and rejection of re-enable with disabled WorldEdit. It also confirms
that Paper-side channel removal does not withdraw the remote advertisement on
the pinned host; do not claim automatic native hot-reload recovery.
The [negotiated-close cycle](../docs/engineering/BRIDGE_SESSION_CLOSE_2026-10-12.md)
adds a correlated, opt-in notification before graceful disable; minor-zero and
ordinary clients are not sent the new opcode. Silent restart discovery and
ungraceful/backend loss are still separate gates, not heartbeat-based retries.

For repeated, bounded server-cost experiments (pinned wire dependencies required):

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 16 -Samples 600 -Repetitions 2 -AcceptEula
```

This uses a separate `build/bridge-load-proof/` server, the same stationary
headless players, two reversed-order absent/installed comparisons, and phases
for silent connections, negotiated idle, one/many active requesters, flood and
recovery. Each phase collects bounded tick samples and owner-thread CPU/allocation
snapshots; JFR runs in every comparison. It checks own-player data, ordinary
requester progress, admission bounds, zero idle reads/transfers, chunk generation
and disconnect cleanup. No native window or system input is involved. It is
a read-only metadata workload, not clipboard/simulation or production-gameplay
benchmarking. The test-only fixture must never be deployed to ordinary servers.

For concurrent graceful restart checks, keep the results in a third fixture:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 300 -Repetitions 2 -RestartLoad -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs restart
```

This uses `build/bridge-restart-load-proof/` and performs three 32-peer restart
cycles per installed run after flood traffic has settled. Each cycle checks one
close/hint/hello per peer, fresh epochs, rejection of stale epochs, explicit own
metadata and quiet counters. `-RestartLoad`, `-ControlledBaseline`,
`-TickTimeline`, `-FairDispatch`, `-GameplayLoad` and `-LifecycleLoad` are mutually exclusive. The [executed 32-player report](../docs/engineering/BRIDGE_32_PLAYER_RESTART_LOAD_2026-10-12.md)
records successful control/admission checks and unaccepted performance outliers.
This is not saturated-restart or native-gameplay acceptance.

For the narrower silent-installed-cost comparison, use a separate controlled
fixture and restore the same frozen saved worlds before every server start:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 16 -Samples 1200 -Repetitions 2 -ControlledBaseline -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs baseline
```

This writes only under `build/bridge-baseline-proof/`, preserves the earlier
load pilot and checks every restored world's file hashes. The console-only
fixture disables random ticks, natural mob spawning and time/weather cycles,
removes loaded non-player entities and holds synthetic players stationary before
warmup. Four reversed-order windows have no application hello/read workload.
Snapshot path/link/corruption guards have their own Node tests. These controls
reduce confounds; shared-laptop/JIT noise and busy-server acceptance remain open.

To correlate a new frozen-world run with complete test-only JFR tick markers:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 1200 -Repetitions 2 -TickTimeline -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs timeline
```

This uses another isolated directory, `build/bridge-tick-timeline-proof/`, and
does not overwrite earlier proofs. The reader validates exact phase/sample
coverage before reporting five candidates per window and actual GC pause
overlap. Public tick brackets are narrower than Paper's reported duration;
inferred full intervals and sparse profiles are not exact causal accounting.
The [research report](../docs/engineering/BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md)
records 4,800 markers, measurement limits and the earlier adversarial admission
failure. The offline driver also runs four actual-recording tests and the
deterministic admission probe on both JVMs. The updated probe now asserts all
ordinary replies, bounded wait and aggregate admission for twelve order/offset
cases per JVM; the historical failed results remain in that research report.

To exercise the fair dispatcher through 32 real loopback connections:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 200 -Repetitions 2 -FairDispatch -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs fair
```

This writes under `build/bridge-fair-dispatch-proof/`, preserving the earlier
fixtures. It adds ordinary-first/ordinary-last timer workloads, reply latency,
queue/expiry/task checks and disconnect cleanup. Separate TCP sockets cannot
guarantee application arrival order; the deterministic probe supplies that check.
See [fair-dispatch evidence and limits](../docs/engineering/BRIDGE_FAIR_DISPATCH_2026-10-12.md).

For matched mixed-world activity, use a separate frozen fixture:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 200 -Repetitions 2 -GameplayLoad -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs gameplay
```

This uses `build/bridge-gameplay-proof/`, restores hashed starting worlds, moves
all 32 headless TCP clients, ticks 16 explicit AI entities and alternates 512
blocks through closed WorldEdit sessions every 20 ticks. Four clients never
negotiate the bridge. All six phases run in both absent/installed comparisons;
server movement and exact world-work counts are checked independently of bridge
replies. Requires at least four players and samples divisible by 20. Fixture
world mutations are deliberately test-only; the companion stays read-only.
See the [predeclared method and results](../docs/engineering/BRIDGE_GAMEPLAY_LOAD_2026-10-12.md)
before interpreting this bounded scenario as ordinary production gameplay.

For lifecycle faults while gameplay and request flood remain active:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 200 -Repetitions 2 -LifecycleLoad -AcceptEula
node tools/server-bridge-test/analyze-active-lifecycle.mjs
```

This uses `build/bridge-active-lifecycle-proof/`, not production worlds or earlier
baseline recordings. The separate console-only fixture observes a real queued
request before revocation, disconnect, companion disable or WorldEdit disable.
Three restarts happen during each run's continuing consumer workload. Four actors
never negotiate; all actors move, entities tick and fixed WorldEdit batches run
until WorldEdit disables. Reconnect movement waits for play readiness. Lifecycle
and whole-window gates have negative self-tests; failed raw data is retained.
The [active-lifecycle report](../docs/engineering/BRIDGE_ACTIVE_LIFECYCLE_2026-10-12.md)
distinguishes headless functional evidence from native recovery, production
performance, ungraceful loss, heavy transfers and supported hot reload.

Use a disposable Paper server with its EULA accepted by its owner, official
WorldEdit and this development companion. Updated development Fabric/NeoForge
clients discover the channel only when advertised. No player gets the new
`weso.bridge.selection` grant by default, including OPs. Grant it explicitly
through the server's permission manager, together with a relevant WorldEdit
selection permission. Grant before joining, or reconnect after changing grants:
discovery is one-shot, not a permission-polling loop. Never grant OP automatically
for transport testing.

Without a consumer, only a hello/welcome is exchanged; no selection is read.
For an opt-in diagnostic, start the development client with JVM argument
`-Dweso.bridge.probe=true`: it sends one metadata request after successful
negotiation and writes the result to the client log. It does not enable a
preview or poll the selection. `/wesobridge` shows server counters to console
or a user with `weso.bridge.admin` (default OP); it does not scan the world.

Restart/disconnect clears connection data. World-context change, respawn and
channel unregistration drop session tokens/caches without refilling the budget
of a still-open connection. WorldEdit disable also disables the companion. Denied/absent
bridges do not disable normal CUI or local sources. Native gameplay and wire
round trips must still pass the [verification gates](../docs/engineering/SERVER_BRIDGE_VERIFICATION.md)
before distributing this as a supported companion.

## Cost And Security Boundary

Frames are at most 256 bytes in this stage. Default request admission is 4/s
per connection (burst 8) and 100/s globally (burst 100), with at most 1024 tracked
connections. Per-player metering happens at ingress; global tokens are spent
only at fair dispatch, before decoding or WorldEdit access. An occupied player
slot cannot be replaced or moved ahead by additional requests. At defaults the
queue retains at most 256 KiB of frame payloads, plus bounded object/map overhead.
Decoded/served work remains limited to 100 frames/s with burst 100; ingress frame
copies have separate per-player/storage bounds, not that global dispatch rate.

One owning-thread slice per tick visits at most 8 slots with a cooperative 0.5 ms
target. A frame waiting 5 seconds is ineligible for service and is discarded when
visited. These are experimental defaults, not guaranteed CPU cost or a hard
wall-clock reclamation deadline during a stalled server. Existing clients keep
their 10-second timeout; there is no new busy opcode, retry loop or promise of
all-player replies at arbitrary saturation. The reproduced 31-sender starvation
case now passes; representative busy-server cost acceptance remains open.
Malformed/oversized/wrong-direction messages never reach WorldEdit. Authorization
is rechecked at service time, before cached replies and after a host read;
identity comes from the actual
player connection, not a payload UUID. Duplicate request IDs return the original
read result, not a promise of a refreshed selection.

The [restart regression](../docs/engineering/BRIDGE_RESTART_RECOVERY_2026-10-12.md)
verifies graceful disable/re-enable on both native 1.21.11 loaders without
reconnecting. An already-online listener receives one optional hint per enable;
only a previously negotiated close arms one fresh handshake. Denied clients do
not retry. The hint itself adds no tasks, listeners or retained recovery maps;
renewed hellos use the same pending-frame dispatcher. Startup work is
O(online players), with separate hint byte counters.
Crowded restarts can exhaust the existing admission budget and fall back to CUI;
missed signals, ungraceful loss and backend switches are not solved by a heartbeat.

See the [wire contract](../docs/engineering/SERVER_BRIDGE_PROTOCOL.md),
[engineering design](../docs/engineering/SERVER_BRIDGE_DESIGN.md), and
[verification record](../docs/engineering/SERVER_BRIDGE_IMPLEMENTATION.md).

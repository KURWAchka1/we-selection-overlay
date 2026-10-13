# Remote Editing: Network Faults And Concurrent Load

Date: **2026-10-13**. Baseline **bdc373c**; accompanying commit adds test tools,
research and verification records. Companion **0.1.0-dev**, development client
**1.8.0-bridge-dev**, experimental protocol **1.2**. Production source/protocol
did not require a correction in these reproduced cases. That is a test result,
not a policy to avoid modifying the client: necessary client changes must be
made and shipped in a new fix/release, preserving already published downloads.

Tuple: Paper **1.21.11-132 / c5eb079**, official WorldEdit **7.4.2**, Fabric
**0.18.4 / API 0.141.3+1.21.11**, NeoForge **21.11.42**; Temurin **21.0.11+10**
and **25.0.2+10**. Node **24.21.0**, test-only minecraft-protocol **1.68.0**.
Other host/version support is not implied.

## Native Network Faults

Each of the following passes on both actual Fabric and NeoForge 1.21.11 clients,
using the existing native controller/transport and only server-side WorldEdit:

| Scenario | Independent observations |
| --- | --- |
| 400 ms delay in each direction after PLAY/capture | Actual G resize remains pending, takes at least 650 ms to acknowledge, applies once and reaches canonical CUI |
| Applied acknowledgment withheld past the ten-second client timeout, then released | UNKNOWN is observed, normal CUI already reflects the applied selection, one read refreshes current state; late acknowledgment cannot resurrect success or repeat the mutation |
| Commit retained in relay before server delivery; edit grant revoked, then frame released | Server denies without applying or disclosing ready state; client loses edit availability; normal CUI stays unchanged |

Server counters, actual intercepted frames and native assertions agree. Slow:
**one capture / one commit / one applied**. Late acknowledgment: **two captures /
one commit / one applied**, including the post-timeout state refresh. Revoked:
**one capture / zero host commits / zero applied**. Each relay sees exactly one
commit request and one reply, with no mutation retry. Quiet controller reads do
not advance request IDs. Disconnect clears peer records, queues and owned tasks.
Loaded-block audit and empty WorldEdit undo-history checks pass.

These are test-controlled application faults, not arbitrary Internet packet loss.
The slow case preserves ordered traffic. The late-ack case intentionally holds
one complete application reply while other traffic continues. The revoked case
is **before delivery**, not proof of an already-queued Paper commit being revoked
during a busy tick; the pure core separately tests that dispatch boundary.

The relay binds only `127.0.0.1:25579`, forwarding to the owned Paper server on
`127.0.0.1:25580`, with offline mode and compression explicitly disabled. Packet
prefixes are obtained from the pinned serializer; no hardcoded custom-payload ID.
One connection, a 2 MiB-minus-one packet ceiling, 8 MiB input-buffer and output-
queue/socket budgets per direction, and at most one retained fault frame keep
the fixture bounded. It is not shipped and is not a general Minecraft proxy.
Native windows remain on a guarded private desktop with cursor isolation; no
system input or desktop activation is used. The standard six allowed/denied/
absent native cases are repeated after the test-harness changes.

## Concurrent Editing

Two fresh Paper JVM runs each use **32 stationary headless players**: eight
editors, eight metadata readers, twelve high-rate metadata senders and four CUI
users that never negotiate the bridge. Editors reuse canonical acknowledgment
revisions and submit at a 250 ms target interval; metadata readers have the same
interval, pressure senders 80 ms. Default production admission limits are unchanged.
Each run measures idle, active and recovered windows of **400 ticks** each.

| Run | Sustained APPLIED commits | Per-editor counts | Reply p95 / max | Active tick p95 / max |
| --- | --- | --- | --- | --- |
| 1 | 617 | 76..78 | 279.72 / 435.19 ms | 2.0051 / 13.7092 ms |
| 2 | 623 | 77..79 | 277.75 / 432.13 ms | 2.3199 / 14.7441 ms |

All **1,240** active commits have the expected own-player bounds, canonical
revision and normal CUI. Capture count does not increase during sustained edits.
Both active runs record **1,877 throttled admissions**, demonstrating real request
pressure rather than an unused flood role. Every ordinary editor/reader progresses;
pending slots peak at **28**, the number of consumers. Four non-consumers receive
zero bridge frames. All measured windows record zero new chunks.

Before sustained traffic, an actual WorldEdit shift invalidates one captured
draft and a foreign player's revision is refused. After traffic, an exact last
commit replay returns the original bytes without another host apply/CUI sequence,
and revoked edit permission denies that user's next operation. Per-player
CUI coordinates remain inside that actor's expected range. The loaded audit
blocks and all eight WorldEdit undo histories remain unchanged.

Idle and recovered windows have zero bridge requests, metadata reads, captures,
commits or replies. After disconnect, tracked peers, pending slots and dispatcher
tasks are zero. This is resource cleanup evidence, not proof that the entire JVM
heap immediately returns to one fixed number without GC.

## Evidence Quality

The load analyzer revalidates all six raw windows, recomputes percentiles, checks
the actual measurement files against copied proof data, and uses the existing
bounded JFR reader. All **2,400 phase/sample tick markers** match; the largest
ticks' values agree with Paper's raw array. Neither recording reports DataLoss.
JFR records use default settings here, not the older custom slice-instrumentation
profile. This is not complete method-cost attribution.

Pure/domain regression: **91 tests on each JVM**, unchanged protocol/server/client
code. Node harness: **55 checks**, including thirteen new relay/load-evidence
checks. Negative checks reject malformed/split/coalesced frames, overflow, a
second relay connection, hidden mutation/re-capture, starvation, non-consumer
disclosure, idle traffic, missing roles, invalid counters and corrupt percentiles.

The first load run stopped after idle measurement because the new supervisor
used the wrong fixture data-folder name. The actual measurement was present and
the owned server shut down normally. The path now matches `WesoBridgeTestFixture`
from plugin metadata. Failed logs, JFR and measurement remain under
`build/bridge-edit-load-proof/failed-fixture-directory-20261013/`; no production
fix or relaxed correctness gate is claimed for that harness error.

## Reproduction

With pinned dependencies cached and the owner's Minecraft EULA accepted:

```powershell
.\tools\test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -SkipNativeCodecs -Offline
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -EditFaults -AcceptEula
.\tools\test-paper-bridge-load.ps1 -Java21 <jdk21> -Players 32 -Samples 400 -Repetitions 2 -EditLoad -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk21>
.\tools\test-native-paper-bridge.ps1 -Java21 <jdk21> -ModVersion 1.8.0-bridge-dev -AcceptEula
```

Ignored local evidence:

- `build/bridge-native-fault-proof/native-fault-proof.json`, six client logs,
  two server logs and `pre-bounded-relay-20261013/`: first and final native fault matrices;
  final checked at **2026-10-13T16:39:50Z**.
- `build/bridge-edit-load-proof/edit-load-proof.json`, `edit-load-analysis.json`,
  two result/log/JFR sets and `plugins/WesoBridgeTestFixture/load-edit-*.json`:
  final load proof checked at **2026-10-13T16:30:02Z**.
- `build/bridge-native-proof/native-proof.json` and six scenario logs:
  standard native regression checked at **2026-10-13T16:44:40Z**; previous evidence
  retained in `pre-fault-tools-20261013/`.
- `build/bridge-validation/`: Java21/25, Node and reader/admission regression logs.
- `build/desktop-tests/`: private desktop and cursor-agent preflight/worker logs.

Companion/client/fixture hashes remain identical to the
[remote-editing candidate](BRIDGE_REMOTE_SELECTION_2026-10-13.md#reproduction-and-evidence).
No published binary/tag or mod behavior was changed in this test-only cycle.
The knowledge graph and source ledger are refreshed with the accompanying commit.

## Remaining Gates

No absent/installed matched comparison was performed for this editing workload.
Lower active tick p95 than idle is **not a speedup claim**; warmup, JIT, GC and
whole-JVM variation remain. The bounded functional results do not establish
representative server cost, long-running heap behavior, simultaneous real block
edits/moving gameplay or mutation faults during ongoing pressure. Physical remote
mouse dragging, ungraceful connection/backend loss, production proxies, permission/
protection integrations, other server hosts and 26.x remote consumers remain
separate gates. See [network methodology sources](SOURCES.md#s53-test-only-network-faults-and-evidence-reconciliation).

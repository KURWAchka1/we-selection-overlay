# Revision Identifier Cost

## Observation And Candidate

The completed [minimal-observer study](BRIDGE_DRAIN_OBSERVER_2026-10-16.md)
retains a 13.397 ms root interval with an owner-thread sample in `HashDrbg`.
The stack is truncated and does not explain that whole interval or every
overrun. Source inspection independently finds `UUID.randomUUID()` in
`BridgeServer.retainCapture`, called for every ready capture/commit result.
Oracle documents that factory as cryptographically strong random generation.

The candidate removes that per-revision cryptographic operation. A lazy,
owner-thread `RevisionSequence` starts from the first existing random connection
epoch and increments its 128-bit value across **all** actors/contexts in that
server instance. Zero is skipped; low-word carry, signed boundaries and complete
cycle exhaustion are explicit. Exhaustion stays terminal. No additional random
seed, thread, task, queue, permission cache or world reference is introduced.
The server's nonce/epoch generation remains cryptographically random.

A revision is a correlation handle, not a bearer credential: it is looked up
only on the actual connection's peer. Authority still requires live read/edit
grants, the matching connection epoch, that peer's stored host capture, current
selector/original points, bounds/operation permissions and publication checks.
There is no global revision-to-player lookup. Tests deliberately reuse equal
mock epochs across actors/renewals and try foreign/stale/guessed handles. The
wire remains a nonzero opaque 128-bit UUID container; no client UUID-version
check or new opcode is introduced. Published 1.8.0 is untouched.

## Matched Experiment

Keep the reservation fix and all defaults; compare its exact pre-change JAR
SHA-256 `78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1`
against the sequence candidate in **previous / candidate / candidate / previous**
order. All four fresh Paper 26.2 JVMs use the same minimal drain agent, bootstrap,
JFC, inline MCProtocolLib helper, simultaneous 32-client joins, frozen world,
disabled spark and moving edit/flood/ordinary-player workload. Unlike the
observer experiment, the observer is now fixed and only the companion varies.

Require all existing raw/JFR/wire/canonical/permission/replay/cleanup gates.
Report both opposite-order pairs, progress, acknowledgment latency and raw
overruns. Removing a known per-revision cryptographic call is a source-level
fact; improved wall/CPU/allocation behavior requires measurement and must not
be claimed from a missing sampling stack or quantized zero CPU alone.
Do not raise the 0.5 ms target or silently trade away ordinary-user service.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -RevisionComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --revision-control
```

Evidence root: `build/bridge-revision-proof/26.2/`. Preserve the pre-change JAR
as `previous-companion.jar` before the launcher builds/copies the candidate.
Record results and the retention decision only after owned processes stop and
offline analysis passes. This is a server-only development change, not a new
player release or a hard real-time guarantee.

## Executed Results

Proof **2026-10-16T15:13:38.539Z** passes four normal fresh-server shutdowns,
4,800 exact raw/JFR tick pairs, 2,348 applies, 37,760 accepted moves, 122,880
fixture WorldEdit cell changes and 128 independent actor audits. All existing
wire/authority/replay/ordinary-progress/cleanup gates pass. Quiet bridge work,
measured chunk I/O/generation, request expiry and final connections/queue/tasks
are zero. No measured tick exceeds 50 ms in this experiment.

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 19 / 393 | 0.4911 / 1.4313 / 8.4616 | 586 | 353.2903 / 636.4840 |
| 2 | Sequence | 13 / 394 | 0.4488 / 0.8449 / 1.6053 | 586 | 370.7513 / 559.1652 |
| 3 | Sequence | 20 / 395 | 0.5084 / 1.1183 / 7.9226 | 591 | 373.6248 / 543.1703 |
| 4 | Previous | 26 / 396 | 0.5611 / 1.0044 / 1.9901 | 585 | 387.6451 / 544.3493 |

Both pairs lower p95 by 42.3 / 52.7 us and over-target fraction by about 1.5
percentage points. Pooled rates are **45/789 (5.70%)** versus **33/789 (4.18%)**.
Throughput is unchanged / 1.03% higher; ack p95 changes +17.461 / -14.0203 ms,
so there is no demonstrated universal latency improvement. One candidate p95
still exceeds 0.5 ms by 8.4 us; its largest 7.9226 ms active root is retained
without a recorded pause/monitor/park explanation. Do not round it into success.

Active drain allocations are 7,704,256 / 7,722,968 bytes previously and
7,564,928 / 7,632,384 in the candidate, with equal/slightly more served work.
Quantized drain CPU is 78.125 / 109.375 ms previously versus 31.25 / 15.625 ms
for the candidate. These two local pairs support removal of unnecessary work,
not a general CPU-speedup percentage or attribution of all long intervals.
Total drain wall sums and p99/max do not improve in every pair.

**Keep the sequence:** it removes the specific per-result cryptographic call,
preserves service/correctness and improves both observed p95 comparisons. It
does not close the hard-time/production release gate. The follow-up
[world-read comparison](BRIDGE_WORLD_READ_2026-10-16.md) keeps this sequence and
isolates another source-level redundant operation.

Candidate companion SHA-256:
`42528d3b606e6e2cba9142a482d5ba6e95a589ec16fc3b693a57dfe1af9186cd`.
Frozen digest: `03d605c9816a87dd4e179397ceb7a28b28d7354d59b4f417207a7c3b346d322f`.
Original fixture/codec/agents/JFC and all published/development player JARs are
unchanged. This experiment changes only the unpublished server companion.

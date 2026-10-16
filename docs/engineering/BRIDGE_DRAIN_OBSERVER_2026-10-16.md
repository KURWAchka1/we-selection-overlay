# Drain Observer Control

## Question And Fixed Inputs

Follow-up to [visit reservation](BRIDGE_DISPATCH_BUDGET_2026-10-16.md).
Remaining 0.5 ms overruns were measured inside an observer that wraps ten
methods. Every nested call creates a JFR event and reads CPU/allocation counters;
this work is inside the production dispatcher's clock interval. It is a test
effect that must be measured, not assumed to explain all remaining delays.

Run four fresh exact Paper 26.2 JVMs in **nested / drain / drain / nested** order.
The production companion is fixed at SHA-256
`78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1`.
World, workload, recorder, bootstrap, JFC and disabled-spark policy are identical.
Reuse all existing 32-actor moving-edit correctness/admission/cleanup gates,
eight edit kinds, 400-tick idle/active/recovered windows and independent audits.

The separate drain agent uses the existing exact-descriptor weaver/recorder but
wraps **only `BridgeServer.drain`**. CPU/allocation reads and JFR begin/end happen
at its boundary, not around each permission/read/apply/send within that method.
It is still instrumentation, not an unobserved production run. Do not subtract
its durations from old results or call its cost zero. The existing nested agent
and bootstrap remain byte-identical, preserving historical integrity checks.

Acceptance is assessed from all arms: exact raw/JFR/wire/canonical reconciliation,
over-target fraction and drain p95/p99/max, ordinary-reader/editor progress,
acknowledgment latency and quiet/cleanup counters. Keep all outliers. A profiler
intervention can establish its effect for this workload; it cannot guarantee
that every future WorldEdit operation or JVM/OS pause finishes within 0.5 ms.
No new post-hoc performance threshold or quota increase is introduced.

## Reproduction

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -DrainObserverControl -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --drain-control
```

Root: `build/bridge-drain-observer-proof/26.2/`. Existing evidence/failed attempts
are guarded before build/copy. No native window, cursor or remote player is used.
The test-only agent has separate Java 21/25 return/exception/isolation checks;
no diagnostic classes are added to distributed server/client artifacts.

## Evidence

### Rejected First Attempt

The first attempt failed at **2026-10-16T14:13:11.736Z**, after three completed
arms and during the fourth. MCProtocolLib reported `RejectedExecutionException`:
the helper's single shared callback worker had 4,096 queued tasks. The owned
helper/server stopped; no completed proof was generated. The entire attempt is
retained at `build/bridge-drain-observer-proof-failed-20261016/26.2/`, including
original helper artifacts, logs, JFR and a clearly rejected partial-interval
analysis. Failure JSON SHA-256:
`97d7346474d1def00c487cffef22a56794970f67dc9f64f1a8aff138194dbdf5`.

Partial root-only arms still contain outliers: 31.7653 ms overlaps 31.0728 ms of
recorded GC; 90.5022 ms has no recorded pause/park/monitor explanation. One sample
near that interval's end is in the dispatch loop, not enough to assign its cause.
Zero quantized CPU is not proof of zero CPU. These are not successful comparison
results and do not establish that instrumentation explains every overrun.

An intermediate helper used two fixed, single-thread callback stripes of 2,048 pending
tasks each. This preserves the aggregate 4,096-task bound and per-session order;
overflow still fails the run. Tests block one stripe, fill it, require explicit
rejection, verify independent progress on the other and verify FIFO drain.
This followed MCProtocolLib's per-session executor assignment rather than sharing
one global worker. Its rejected repeat used the same helper in every arm, SHA-256
`b7eb8faa1d486d6c3fc9bf95cbe1743cc376212e34481280153cc1a3b1e901c0`.
It does not compare changed-helper timings to the rejected attempt or change
the production companion, packet codec, memory bound or WorldEdit thread owner.

### Rejected Second Attempt And Preparation Boundary

The two-stripe repeat also fails in fourth-arm login preparation, with one
2,048-task stripe full. Its three completed arms are not acceptance evidence.
All raw files are retained at
`build/bridge-drain-observer-proof-failed2-20261016/26.2/`. Two stripes alone
therefore do **not** solve a simultaneous 32-login/chunk burst on this fixture.

For the next observer comparison, join four actors at a time with 250 ms between
batches, then use the unchanged 15-second moving warm-up and all 32 measured
actors. This changes preparation, not measured concurrent edit/flood/gameplay
load, and is identical in every arm. Record the preparation policy explicitly
in the proof. Do not present it as passing the distinct connection-storm gate;
that gate remains open. Queue limits, packet correctness and overflow failure
handling are unchanged. Older scenarios retain their original join policy.

### Rejected Paced Attempt And Supported Inline Callbacks

Paced preparation also fails (second arm); it is retained at
`build/bridge-drain-observer-proof-failed3-20261016/26.2/`, including the rejected
helper source. Failure SHA-256:
`7bbfe8b132e8f490b847c98087bced11dddb6a0aeb57babb0cbe41a925fcaf0e`.
Pacing and stripes are therefore not treated as solutions. The final repeat
restores the original simultaneous 32-client join policy.

The exact MCProtocolLib default executor source explicitly supports
`Runnable::run` when its separate packet event loop is disabled. The owned
headless helper now supplies that supported executor per session. Its bounded,
lightweight packet callbacks run serially on the client's existing Netty channel
loop, with no separate decoded-packet backlog or callback threads; TCP provides
transport backpressure. No packet is filtered out before the library decoder,
and parser/callback faults remain fatal to acceptance. This applies only to the
headless helper, not the server thread or WorldEdit work. Inline order/exception
guards pass. The new helper SHA-256 is
`4f2f59307f3aa7c5e5c334787d813a92de85000c11a0e0a81812a16d34397dd5`.

### Complete Inline-Helper Repeat

Proof **2026-10-16T14:50:34.119Z** passes all four normal owned shutdowns,
4,800 exact raw/JFR ticks, 1,991 applies, 37,824 accepted moves, 122,880 fixture
WorldEdit cell changes and 128 independent actor audits. Every measured actor
is present; simultaneous joins are restored. Parser/order/canonical/permission/
cached-replay/cleanup gates pass. Quiet bridge work, measured chunk I/O/generation,
expiry and final connections/queue/tasks remain zero.

| Round | Observer | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Nested | 66 / 398 | 1.2236 / 13.0571 / 19.1849 | 477 | 589.5742 / 904.3718 |
| 2 | Drain | 51 / 398 | 1.0102 / 12.4629 / 13.9300 | 530 | 527.0134 / 892.6175 |
| 3 | Drain | 54 / 393 | 0.9698 / 3.1439 / 8.0729 | 528 | 494.7154 / 846.4431 |
| 4 | Nested | 74 / 400 | 1.6701 / 6.3269 / 17.4642 | 456 | 731.2939 / 1119.1637 |

Both opposite-order interventions improve p95, throughput (11.11% / 15.79%)
and ack p95 (62.5608 / 236.5785 ms less). Minimal observation still exceeds
0.5 ms; instrumentation is a contributor, not the explanation of everything.
The adaptive dispatcher also observes child-wrapper cost, so the intervention
changes its admission decisions. Do not subtract a fixed observer duration.

Selected minimal drains have no recorded GC/VM/safepoint/park/monitor overlap.
Owner samples include `SelectionEditTransaction.read`, `HashDrbg` and
`BukkitWorld` construction, with truncated stacks. These locate executed code,
not the cause/duration of entire pauses. `observer-owner-samples.json` retains
those samples. The worst reported tick is **72.5793 ms**, with only **0.3772 ms**
overlap from its 0.3751 ms drain; most of that interval is outside the bridge.
Its cause remains unassigned. Quantized zero CPU is not treated as free work.

The companion remains byte-identical at `78ff34e...107ed1`. Original nested
agent/bootstrap hashes are unchanged; the separate drain agent is
`37e4ecdc4cb090f1d452445acec9cdcfc1a3a3bef0188a4e3875d5bb27ed7db5`.
Frozen digest: `1f0a17100ebbdfc95a64494b1bf90f4799c503dd3fdd627b5b93cb0f98eaf095`.
This closes the named helper/observer experiment, not all connection storms,
hard deadlines, native-client load or production release cost acceptance.
See [observer sources](SOURCES.md#s64-observer-cost-and-minimal-drain-measurement)
and [helper sources](SOURCES.md#s65-bounded-ordered-load-helper-callbacks).

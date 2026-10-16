# Dispatcher Visit Reservation

## Scope And Reproduced Failure

The preceding [moving-edit study](BRIDGE_26_2_MOVING_EDITS_2026-10-16.md)
did not close the 0.5 ms dispatcher soft-target finding. This follow-up changes
the unpublished companion's dispatcher, not the published 1.8.0 player JARs,
wire protocol, quotas, permissions or WorldEdit thread ownership.

Before changing production code, a deterministic test on the previous source
gave three 200 us visits a 500 us slice. The old dispatcher started its third
visit at 400 us and finished at 600 us; the new assertion expected two visits
and failed. The original failed command log is retained as
`build/bridge-dispatch-budget-proof/26.2/pre-change-counterexample.log`.

`BridgeServer.drain` now reserves the largest completed visit observed within
the current invocation before admitting another visit. That observation includes
publication and queue bookkeeping, not just `host.applyEdit`. The reservation
is two local longs, uses the existing clock reads, and resets every invocation.
There is no persistent predictor, permission cache, sleep, async WorldEdit
access, worker allocation or retry. A deferred request keeps its oldest queue
position and does not consume an aggregate token. Idle calls still return before
reading the clock. Existing raw maximum duration includes pauses and is not
clipped to the target.

New deterministic cases cover the reproduced failure, publication cost, a cold
700 us first visit followed by normal progress, an unexpectedly slow next visit,
and subtraction across `nanoTime` wrap. A 100 us visit followed by a 900 us visit
still records 1 ms: the first observation cannot predict an arbitrary later
pause, and an already executing host operation is not interrupted or replayed.
The reservation is a conservative admission policy, not a hard deadline.
See the checked primary sources in [S63](SOURCES.md#s63-dispatcher-clock-and-thread-constraints).

## Matched Experiment

The retained root is `build/bridge-dispatch-budget-proof/26.2/`. Four fresh
Paper 26.2 JVMs run **previous / candidate / candidate / previous** against
the same frozen world. The exact previous companion is preserved separately
before rebuilding. Every measured arm uses the same method agent, bootstrap
helper and JFC; spark is off in both input and effective configuration. Only the
companion changes. Priming uses the previous companion without method wrappers.

The workload is the prior moving-edit fixture: 32 headless clients, eight
editors, eight ordinary readers, twelve flood actors and four CUI-only players;
all move, sixteen cows tick, and a real 512-cell WorldEdit edit runs every twenty
ticks. Each fresh JVM has idle / active / recovered windows of 400 ticks each.
Eight target kinds cover translation and dimension changes. Recorded packets,
host counters, canonical bounds, CUI, control-area and undo audits, permissions,
cached replay, queue expiry, zero quiet-window source work and chunk-I/O checks
must still satisfy the existing gates. No native window or cursor is used.

Compare rounds 1/2 and 4/3 in opposite order, reporting active drain count,
over-target fraction, p95/p99/max, per-operation cost, completed edits,
acknowledgment latency, queue wait and ordinary-reader progress. Keep all
outliers, including GC and unknown pauses. A lower drain duration caused only
by starving players is not acceptance. The existing functional gates are not
weakened; no post-hoc throughput cutoff is presented as pre-registered.

## Executed Results

Proof timestamp **2026-10-16T13:43:16.535Z**. All four servers stopped normally.
Offline analysis passes 4,800 exact raw/JFR tick pairs with no recorded data
loss, 2,340 canonical applies, 37,696 accepted movements and 122,880 independent
fixture WorldEdit cell changes. All 128 actor CUI/canonical/control-area/undo
audits pass. Idle/recovered bridge calls, source work, measured chunk loads and
generation remain zero; final connections, pending slots and dispatch tasks
are zero. No request expires and no measured tick exceeds 50 ms.

Active windows (method-wrapper wall duration; milliseconds unless noted):

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max | Applied | Ack p95 / max |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 72 / 391 | 0.6494 / 1.5044 / 10.6662 | 620 | 281.0972 / 401.9374 |
| 2 | Candidate | 25 / 398 | 0.5413 / 1.0173 / 1.6951 | 561 | 436.4849 / 638.7045 |
| 3 | Candidate | 33 / 397 | 0.5833 / 1.1282 / 1.4519 | 538 | 496.4318 / 680.4318 |
| 4 | Previous | 79 / 395 | 0.7153 / 1.3413 / 4.3432 | 621 | 280.5993 / 388.5322 |

The opposite-order pairs both reduce over-target frequency and drain p95/p99.
Pooled observed over-target frequency falls from **151/786 (19.21%)** to
**58/795 (7.30%)**, about **62% fewer per drain**, not elimination. Candidate
p95 is still above 0.5 ms. The tradeoff is **1,099 versus 1,241** completed edits
in the two active arms, **11.44% less throughput** under this saturation fixture.
Per-editor progress is 70..71 and 67..68, versus 77..78 previously; every ordinary
reader completes 68..71 and 65..67 reads, versus 77..78 previously. The existing
minimum-30 progress gate is unchanged. Candidate ack p95 rises by 155.3877 and
215.8325 ms; maximum observed acknowledgment stays below 681 ms. Lifetime queue
wait maxima are 381.741 / 668.039 / 727.0309 / 383.5556 ms. No actor is starved,
but this is measurable backoff, not free speed or unchanged responsiveness.

Active allocation inside the drain falls from 10,044,128 / 10,394,128 bytes to
9,406,432 / 8,991,576, while fewer calls are served. Per-process allocation is
4,172.56 / 4,339.79 bytes previously and 4,340.48 / 4,364.95 in the candidate:
there is **no demonstrated per-request allocation reduction**. Quantized active
drain CPU is 31.25 / 46.875 / 62.5 / 140.625 ms in run order; opposite pairs do
not agree on CPU direction. Lower CPU or universal server overhead is not proven.

The candidate's largest active drains (1.6951 and 1.4519 ms) contain unexpectedly
slow read/process calls after initially cheap visits. Selected active applies
also reach 1.148 ms / 1.2019 ms in the two arms. Those selected intervals have
no recorded GC/VM/safepoint-begin/park/monitor overlap; their causes remain
unassigned. They are not removed, relabeled as GC, or retried. Lifetime core
maxima (including cold setup) remain 11.8671 / 6.384 / 5.5296 / 8.6886 ms.

**Retention decision:** keep the narrow reservation as deliberate server-first
backoff: it fixes the deterministic admission defect and consistently reduces
measured over-target work while retaining bounded service and correctness.
The soft-target/release cost gate remains **open**, as do native concurrent
rendering/input and independent replication. It would be incorrect to call
every overrun fixed, or improve that headline by silently raising 0.5 ms.

The only production artifact change is the unpublished companion, SHA-256
`78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1`.
Previous companion, fixture, transport helper, method agent, bootstrap and JFC
are independently pinned in the retained proof. Frozen world digest:
`683845cc87f4c7729cef5be7be0b6cf778adaf41d48f86793b182b99c98040fb`.

## Regression And Artifact Boundaries

`tools/test-server-bridge.ps1 -Offline` passes 100 pure/JVM tests on Java 21 and
25, 121 general Node groups, both diagnostic-agent guard/probe builds, the JFR
reader matrix, deterministic admission-order probes and all six native payload
codecs. The separate effective-YAML reader adds three passing groups on each
JVM. An initial invocation without its required `WESO_TEST_JAVA` was rejected
before tests; both explicit-JDK reruns pass. This is tooling invocation evidence,
not an ignored product failure.

Offline analyzers revalidate the three earlier exact-host plain edit proofs,
26.2 matched diagnostics, profiler control, moving edits and the new budget
comparison. The historical 26.2 absent/installed gameplay analysis also passes.
Each previous analysis is copied to a uniquely named `*-pre-budget-refresh-20261016.json`
before regeneration; original raw logs/JFR/proofs/world snapshots are untouched.
The new comparison output includes both opposite-order pairs and latency/
throughput deltas, rather than reporting only the favorable drain metric.

Re-launching the completed budget study is rejected before build/copy. SHA-256
of its final proof, previous/candidate copies, current companion and original
transport helper are unchanged across that guard check; port 25579 has no
listener afterward. The fixture and all four published/development universal
player JAR hashes match the preceding stage. No protocol/client source, universal
packager, in-game GUI, command/clipboard renderer or native concurrent-input
suite is changed or revalidated by this server-only experiment.

## Reproduction

Use a new evidence directory only; the launcher refuses to overwrite a completed
or failed study before building or copying artifacts. Preserve the exact old JAR
at `previous-companion.jar`, SHA-256
`7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956`.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -BudgetComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --budget-control
```

Do not run competing builds or offline JFR readers during measured windows.
This instrumented comparison does not estimate absolute uninstrumented overhead
or prove native client rendering, other host versions, FAWE or Folia support.

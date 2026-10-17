# Bounded Dispatch Batch Trial

## Predeclared Candidate And Gates

The latest exact-sized encoder reduces allocations, but one minimal-observer
candidate active p95 is 0.5054 ms against the unchanged 0.5 ms target. It is not
accepted as a complete cost fix. The largest intervals have no recorded cause;
do not invent GC/OS/WorldEdit attribution from quantized zero CPU counters.

Test **one** bounded-batch adjustment: default maximum visits per tick **8 -> 6**.
At nominal 20 TPS this is 120 possible visits/second versus the unchanged
100/second global token rate. That is a capacity argument for this candidate,
not measured throughput or a guarantee during server lag. Per-player/global
rates, bursts, 0.5 ms soft target, five-second pending lifetime, within-slice
observed-visit reservation, FIFO, actual-peer ownership, live permission checks,
public WorldEdit operations and public send path remain unchanged. No static
time floor, carry-over pause penalty, worker-thread world access or priority boost.

Before server measurements, add deterministic tests for all 32 queued actors in
FIFO order with exact six-visit maxima, no expiry and bounded wait; also sustain
100 requests/second for ten simulated seconds at 20 drains/second with no lost
work or hidden reads. Keep the configured eight-visit no-static-floor regression
explicitly configured, not weakened to conceal the batch change.

Compare the exact current companion
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`
against the batch-only candidate in four fresh **previous / candidate /
candidate / previous** Paper 26.2-133 + WorldEdit 7.4.5 JVMs. Keep BelowNormal,
heap/processors/JFC, identical minimal root observer, disabled spark, frozen
starting world, 32 simultaneous logins, moving/edit/flood workload and owning-
server quit barrier fixed. No competing builds or offline readers in measurement.

Retain the previously declared strict gates in both pairs: candidate active drain
p95 <=0.5 ms; every ordinary editor/reader >=95% paired baseline service; ack-p95
growth <=100 ms; all functional/raw/JFR/canonical/CUI/authority/replay/no-chunk-I/O/
no-expiry/quiet-window/cleanup checks pass. Preserve raw maxima and failed runs.
Do not round a failure down, increase quotas/targets or retry the same candidate
until a favorable result appears. Reject/revert if the strict comparison fails.

This changes the unpublished **server** default, not the shared codec or client.
Published 1.8.0 and current development universal JAR hashes must stay unchanged.
If retained, rerun the Java-21/25 regression and native network fault/lifecycle
checks affected by changed scheduling. No public release follows from this trial.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -BatchComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --batch-control
```

Root: `build/bridge-batch-proof/26.2/`. The exact old companion is preserved as
`previous-companion.jar` before editing/building; completed/failed study roots
must not be overwritten. Java's sequenced-map snapshots and native Windows
cycle counters were also researched: neither is introduced as an unmeasured
optimization, and cycle counts must not be converted into elapsed time.
Primary-source scope and rejected alternatives: [S71](SOURCES.md#s71-bounded-batch-capacity-and-diagnostic-alternatives).

## Pre-Measurement Regression

The first harness run reports 145/146 Node groups: the new snapshot guard
correctly requires modern dimensions, but its test-data builder omitted the new
root and created a legacy layout. Add the named 26.2 root to that **test-only**
builder; retain `build/bridge-validation/harness-node-first-batch-failure.log`.
No server measurement had started. Production guard requirements are unchanged.

Before measurement, both Java 21/25 pure suites pass **114 tests** including
the new FIFO/rate cases. The six-visit companion is pinned in both runner and
offline analyzer as SHA-256
`f6b0e0829cce2f5d0bafba5246d1f3554edd8f3bd9aa2a87b1e3d1f42aa120e2`.
No further production source changes are permitted inside this comparison.

## Executed Result: Rejected

Proof **2026-10-17T04:21:02.919Z** passes all functional/raw/JFR gates:
2,344 applies, 4,800 reconciled ticks, 37,824 accepted moves, 122,880 fixture
WorldEdit cell changes and 128 actor audits. Quiet work, measured chunk I/O/
generation, expiry and final connections/queue/tasks are zero; all owned servers
stop normally. No measured tick exceeds 50 ms. This is not performance acceptance.

| Round | Visits | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | 8 | 11 / 395 | 0.4401 / 1.0230 / 1.7023 | 598 | 326.2582 / 481.2471 |
| 2 | 6 | 15 / 400 | 0.4572 / 0.9839 / 1.8850 | 578 | 417.7878 / 586.5760 |
| 3 | 6 | 16 / 400 | 0.4590 / 0.6450 / 2.6941 | 586 | 386.2398 / 542.5983 |
| 4 | 8 | 32 / 390 | 0.6362 / 1.1713 / 2.3875 | 582 | 372.2631 / 601.1565 |

Candidate p95 passes both absolute gates. Ack-p95 deltas are +91.5296 / +13.9767
ms. But the first pair fails the **individual** progress gate: readers
`WesoLoad08` and `WesoLoad15` complete 71 versus 75 reads (**94.6667%**), and
`WesoLoad13` 72 versus 76 (**94.7368%**). The second pair's minimum ratio is 100%.
Do not round the first three actors up to 95% or replace them with aggregate
progress. The analyzer exits **2**. The six-visit default is **rejected/reverted**.

Both original eight-visit and candidate six-visit JARs remain in the evidence
root, together with the exact candidate production/test sources as
`six-visit-candidate-BridgeServer.java.txt` and
`six-visit-candidate-BridgeDispatchTest.java.txt`. The frozen digest is
`b0fb96de01684cd0f4b1c3786566b421f40d43534b5cd6c10afd38e3b6db9edd`.
All 114 pure tests/JVM, 146 Node groups, both diagnostic agents/JFR/configuration
readers, admission probes and six native codecs passed before measurement; they
do not override the failed performance criterion.

There is exactly one intermediate integer batch size between six and the
original eight. A separate, predeclared seven-visit trial may test that remaining
capacity/latency tradeoff, using the **original eight-visit baseline**, its own
root, unchanged gates and no repetition of this six-visit candidate. Neither
candidate is retained just because nominal rate arithmetic is sufficient.

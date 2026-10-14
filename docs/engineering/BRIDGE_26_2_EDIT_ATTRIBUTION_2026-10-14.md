# Paper 26.2 Edit Attribution

Date: **2026-10-14**. Source baseline **46ae4ca** plus accompanying test-only
changes. Companion **0.1.0-dev**, application protocol **1.2**. No production
source or distributed JAR changes. This is a diagnostic study, not performance
or release acceptance.

## Method

Use exact Paper **26.2-133**, official WorldEdit **7.4.5**, Temurin
**25.0.2+10**, and the same pinned MCProtocolLib **776** helper from the previous
[edit-load cycle](BRIDGE_26X_EDIT_LOAD_2026-10-14.md). Java 21 builds the companion,
fixture and isolated diagnostic agent. Server heap 256 MiB initial / 1 GiB max,
two active processors, below-normal priority, loopback only. No native game
window, OS input, external account or user world is involved.

Four fresh server JVMs run in fixed **control / instrumented / instrumented /
control** order. One stopped priming server creates the starting world. The
complete modern `bridge-fixture` tree, including all three dimensions and player
data, is checksum-verified and restored before each launch. WorldEdit plugin
session storage is not part of that snapshot; each join fixture resets and
independently verifies its actor's cuboid. This is not an absent-companion cost
baseline: both groups use the same companion and offered workload.

Each run has 32 headless connections: eight editors, eight metadata readers,
twelve flood senders and four CUI-only non-consumers. Target cadence remains
250 ms for editors/readers and 80 ms for flood senders, five seconds initial
settling, and 400 ticks each idle/active/recovered. Actual admissions and applies
may differ with scheduling; the count is reported, not normalized after the
fact. Acknowledgments/counter snapshots include the bounded post-active drain.
There is no moving-player or live-block-writing workload in this series.

Paper's default spark background Java sampler remains enabled in both arms.
Both use the same checksum-pinned JFC derived from the installed JDK default,
with zero-threshold `ExecuteVMOperation` and `SafepointBegin`. This differs from
the historical thresholded recording, so timing comparisons to that earlier
series are not observer-matched. No competing test suite or JFR analysis runs
during the four measured launches on this shared Windows laptop.

The agent wraps exactly ten methods in three unchanged companion classes:
ingress, enqueue, drain, process, read permission, metadata read, send, edit
permission, edit capture and edit apply. Original object identity, return values
and exceptions remain intact. No live WorldEdit call moves off its owning
thread. ASM and bootstrap recording helpers stay outside production artifacts.

Analysis joins phases by actual JFR fixture markers, not millisecond-rounded
wall clocks. Lifetime method totals reconcile against independent server
counters; active distributions and largest calls are separate from setup.
Top tick intervals additionally report root-drain overlap and safepoint-begin
overlap. A separate streaming pass joins synchronization IDs to associated VM
operation/caller records, even when the operation body starts after the selected
method returns. Inclusive nested times, GC, VM and synchronization overlap are
not added or subtracted to manufacture application CPU cost. Short Windows CPU
readings remain quantized. Instrumentation and JFR have their own overhead.

## Historical Correction

Reanalysis of the original **26.2 run 1**, Paper tick **816**, active sample
**228**, finds four safepoint-begin intervals **4619..4622**, totaling **55.0482
ms** overlap inside the inferred **60.4271 ms** tick. The old reader omitted
this event category. Its zero GC/VM-body/monitor/park observations were not
evidence that no JVM synchronization occurred.

The installed JDK's original default JFC has a **10 ms** threshold for both
VM-operation bodies and safepoint-begin intervals. A short VM operation after a
long synchronization can therefore be absent. The old data cannot establish
the missing caller/operation, or identify which thread delayed rendezvous.
No new recording can recover events discarded by that older threshold.
The previous analysis is retained as
`build/bridge-edit-load-proof/26.2/edit-load-analysis-pre-safepoint-reader.json`;
its raw recording/ticks have not been altered. See [S59](SOURCES.md#s59-edit-method-and-safepoint-attribution).

## Preparation Failure

The initial priming run stopped normally, then the old frozen helper failed
before measurements because it expected `bridge-fixture_nether` beside the main
world. Paper 26.x stores dimensions inside one world tree. The helper now selects
the documented layout only for exact named 26.x fixtures, requires all three
vanilla dimensions, and checks the snapshot layout before restore. Legacy
fixture layout/schema remains unchanged. Self-tests cover exact restoration of
all dimensions, missing dimension/layout, invalid version roots, corruption and
junction rejection before any live-world deletion.

Complete failed log/JFR/failure JSON and server files remain under
`build/bridge-edit-diagnostics/failed-world-layout-20261014/26.2/`. No successful
load proof was written for that attempt. This corrects test tooling, not mod
behavior or a user-world migration.

## Results

All four runs passed the functional/evidence gates, completing at
**2026-10-14T19:24:04.645Z**. Analysis reconciles **4,800** complete raw/JFR ticks,
**48,295** lifetime diagnostic calls in the two instrumented runs, source/apply/
process/send counters, enqueue/ingress counts and original core high-water.
The five successful Paper JVMs (including priming) and four Java wire-helper
JVMs all stop normally before the successful proof is published.

| Run | Mode | Applied | Active tick p95 / max ms | Commit reply p95 / max ms | Lifetime core max slice ms |
| --- | --- | ---: | --- | --- | ---: |
| 1 | Control | 624 | 2.0151 / 21.7162 | 267.37 / 313.83 | 7.1587 |
| 2 | Instrumented | 620 | 3.1035 / 16.1175 | 277.88 / 387.62 | 14.8963 |
| 3 | Instrumented | 616 | 3.0415 / 7.5135 | 279.10 / 388.36 | 6.1774 |
| 4 | Control | 623 | 2.2171 / 17.8007 | 278.59 / 392.91 | 17.2317 |

All **2,483** sustained applies have canonical actor-local acknowledgments.
Every editor progresses, with **76..79** acknowledgments; 1,842..1,866 admissions
per run are throttled. Queue peak is **28**. All 32 loaded-block/empty-undo audits
pass; stale commands, foreign revisions, exact replay and live revocation retain
their original gates. Four non-consumers receive zero bridge payloads per run.
All twelve measured windows have **zero chunk loads and generated chunks**.
Idle/recovered windows have zero source work or transfers, including **zero
instrumented calls**. Final connections/pending/tasks are zero on every host.

### Phase-Specific Methods

These are instrumented wall-time distributions strictly inside each actual
active JFR marker span, not the whole startup/drain lifetime. Calls crossing a
span boundary are excluded, not silently attributed to an adjacent phase.
The transaction/counter totals above include the separately documented drain.

| Operation | Run 2 p95 / p99 / max ms | Run 3 p95 / p99 / max ms |
| --- | --- | --- |
| Drain | 0.6604 / 2.9458 / 14.8983 | 0.6677 / 1.3925 / 1.7432 |
| Apply | 0.2064 / 0.3757 / 3.1200 | 0.2027 / 0.4325 / 1.5046 |
| Read | 0.0257 / 0.0705 / 14.7746 | 0.0348 / 0.0827 / 0.9285 |
| Edit permission | 0.0027 / 0.0075 / 0.2371 | 0.0031 / 0.0138 / 0.6695 |
| Send | 0.0427 / 0.1052 / 2.8754 | 0.0380 / 0.0847 / 0.8249 |

**89/399** and **94/393** measured drain calls exceed the soft **0.5 ms** target.
No captures occur in either active span. Cold capture maxima **6.2269 / 3.9095
ms** occur outside measurement and include roughly 1.3 MiB of allocations on
the first capture, including initialization/observer cost. Cold and active
maxima must not be conflated; the second diagnostic run's lifetime maximum is
its cold first drain, not an active steady-state call.

### What Remains Unexplained

- Run 2's largest active drain, ID **20923**, is **14.8983 ms**, containing a
  **14.7746 ms** read. It overlaps only **0.0208 ms** of safepoint-begin and
  **0.0727 ms** of VM-operation bodies; no recorded GC, owner park/monitor,
  deoptimization or owner execution sample explains the remainder. Matching
  safepoint IDs **5231/5232** identify associated `ThreadDump` operations from
  `spark-java-sampler-2-thread-2`, but their small recorded overlap does **not**
  establish spark as the cause of the full delay. CPU is quantized to zero.
- Run 3's largest active tick is **7.5135 ms**, with only **0.3978 ms** of one
  root-drain interval. A tick maximum is therefore not the same thing as the
  bridge's slowest method. Its largest active drain is **1.7432 ms**, containing
  a **1.5046 ms** apply without a recorded pause explanation.
- Control run 1's largest active tick overlaps **18.0118 ms** of safepoint-begin;
  control run 4's maximum overlaps **2.7679 ms**. Control runs contain no method
  observer: zero recorded drain overlap there means **not instrumented**, not
  zero bridge work.

Whole-recording instrumented runs record **7,721 / 7,251** spark sampler
ThreadDump operations, confirming the intended observer policy. Matched active
owner-CPU deltas change sign: diagnostic-minus-control is **+265.625 ms** for
runs 2/1 and **-406.250 ms** for runs 3/4. Allocation deltas are **+2,590,312 /
+2,100,208 bytes**. These include observer overhead and shared-machine variance,
not a measured production bridge tax. Neither a smaller maximum nor a negative
CPU delta is a speedup claim. Thousands of ticks are not independent JVM trials.

Keep the production quotas, live permission checks and owner-thread boundary.
Do not cache authority, raise the budget or move WorldEdit asynchronously to
hide the wall-clock maximum. This closes the missing phase/callee observation
and corrects the historical event-category omission; **soft-budget and
representative production cost acceptance remain open**. Further attribution
needs an explicitly matched profiler-control series and/or owner-thread
scheduling evidence, not conjecture about the OS, GC or WorldEdit.

## Regression And Artifacts

**95 pure/domain tests on each Java 21/25 JVM**, **94 general Node harness
tests**, **12 actual JFR-reader checks per JVM**, both agent application/bootstrap
checks and all six actual native payload codecs pass. The final reader changes
were rechecked on both JVMs after adding safepoint-ID association; the modern
snapshot cases were rerun before the successful load series. The rerun guard
was also tested against the completed proof: it refuses launch and leaves the
proof bytes unchanged. A failed/partial diagnostic attempt likewise requires
archiving before reuse.

Published and development Fabric/NeoForge clients plus companion/fixture hashes
match the preceding cycle. Native GUI/gesture/command/clipboard rendering and
universal packaging suites were not rerun in this test-only stage; their older
evidence is not promoted into a fresh native gameplay result.

| New evidence pin | SHA-256 |
| --- | --- |
| Diagnostic agent | `452a18c8ee39c92eb9216baa2dd0a707835efab36992e04b5bc407b0ba51343a` |
| Helper-only bootstrap | `b89b6cf6ba5b29a5c37b84aba0b5131e56f61797ec27d0ff8333761a30fed60f` |
| Shared JFC | `a590b966fb30f41860dc01028ebe60125d04901b8725d049ec58370901984536` |
| Starting-world manifest digest | `463330814da9a9710d4d881bfa0ce2a37610d220505452f23741db8000365a5b` |

The proof retains all exact artifact pins and the complete helper classpath.
The root contains `edit-load-proof.json`, reconciled `edit-load-analysis.json`,
four per-run results, raw fixture measurements, server/helper logs and JFRs,
the generated JFC and immutable world snapshot.

## Reproduction

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk21' -Java25 'C:\path\to\jdk25' -Minecraft 26.2 -EditLoad -MatchedEditDiagnostics -Players 32 -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs 'C:\path\to\jdk25' 26.2 --matched-slices
```

Evidence root: `build/bridge-edit-diagnostics/26.2/`. Previous successful or
failed attempts must be archived before another cycle; raw artifacts/logs are
not pushed publicly. Published 1.8.0 remains immutable. Necessary production
changes are still permitted when supported by evidence; keeping binaries
unchanged in this diagnostic stage is not a ban on client/bridge fixes.

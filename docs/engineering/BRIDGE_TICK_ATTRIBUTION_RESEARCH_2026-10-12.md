# Bridge Tick Attribution And Admission Research

Date: **2026-10-12**. Inspected production baseline: `092b8f810c0be54492883f16128652eb0e1b2b31`.
Scope: test-only instrumentation, a controlled silent-server comparison and an
adversarial ordering experiment. **No production bridge/client behavior changed.**
Companion remains `0.1.0-dev`; published client `1.8.0` and its tag are unchanged.
This report follows the [32-player load/restart proof](BRIDGE_32_PLAYER_RESTART_LOAD_2026-10-12.md),
not a replacement for its recorded observations.

## Findings And Gate Status

1. **Fair service is not established and a starvation case is reproduced.**
   With 31 individually conforming active senders ordered before an ordinary
   requester, that requester receives **0/60** replies in the deterministic core
   experiment on both Java 21 and 25. Aggregate rate/storage bounds still hold.
   Safe rejection is not acceptable evidence of ordinary-player progress.
2. A separate real Paper run retains **4,800 complete tick markers**, across
   four byte-identical saved-world comparisons with 32 stationary clients.
   There are zero chunk loads/generation and zero application reads/transfers.
3. The longest installed-run ticks overlap actual top-level JFR GC pauses:
   23.0968 ms with 21.3832 ms of pause; 17.4646 ms with 15.8768 ms of pause.
   This establishes temporal correlation, not that the companion caused the GC.
4. Earlier 100+ ms outliers did not recur. They remain unexplained; this new
   recording cannot retrospectively attribute events in an older recording.
   Busy-server cost acceptance, native gameplay under load and saturated
   graceful-restart recovery remain open.

## Measurement Method

The **separately packaged fixture**, never the companion, records
`weso.fixture.Tick` during an explicitly started console measurement window.
Each event has a phase, unique sample index, Paper tick number/reported duration,
public start/end event bracket, owner-thread CPU delta and allocation delta.
Recording is bounded by the existing 100..2400 samples/phase and 16-phase limits.
The observer itself allocates and reads counters; zero observer cost is not claimed.

Paper 1.21.11 build 132 (`c5eb079`) reports a duration whose start can precede
`ServerTickStartEvent`. Its inspected patch executes queued start-of-tick tasks
before this public event. Consequently the fixture bracket is **not** exact full
tick/MSPT accounting. Production code uses no internal hook; the pinned source
is consulted only to interpret measurements. See [source checks](SOURCES.md#s44-public-tick-brackets-and-jfr-timestamps).

The streaming reader performs two bounded passes:

- Validate all expected phase/sample IDs, rejecting missing/duplicate markers
  before output; retain at most 16 x 2400 markers and five candidates per phase.
- For those candidates, estimate a Paper interval by subtracting reported
  duration from the JFR end timestamp. Separately report the actual public
  bracket and its difference from Paper's duration.
- Intersect the inferred interval with **top-level `jdk.GCPhasePause`**, VM
  operations, owner-thread monitor/park events and execution samples. Nested GC
  phase durations and concurrent collection elapsed time are not counted as
  additional pauses. GC/VM categories may overlap and must not be added together.
- Emit numeric-only NDJSON. Full recordings/stacks, fixture identities and
  generated worlds remain in ignored local build directories.

The inferred interval is approximate: public end-handler timing and JFR clock
conversion are not the exact Paper internal timestamp. A missing execution
sample is not evidence of zero work. A VM operation is not automatically a
stop-the-world pause.

### Reproduced Reader Boundary Defect

The initial wall-clock join discarded the last installed-run marker despite
all 1,200 records being present. Its JFR end timestamp was about **0.0830 ms**
and **0.3098 ms** later than the JSON window's `Instant.now()` end. OpenJDK's
recording reader derives epoch timestamps from recording chunk clock data;
these should not be assumed identical to separately obtained wall-clock times.
The observed skew alone does not prove which individual conversion caused it.

The corrected reader joins by the fixture's unique **phase/sample identity**,
not an arbitrary timestamp tolerance. It still checks exact coverage and
reports wall-end skew. Tests reject missing phases, wrong counts and duplicate
indices, and explicitly retain markers whose wall-clock boundary clips them.
Analysis is not published until all recording readers complete successfully.

## Controlled Paper Run

Command (the owner has accepted the EULA for this isolated fixture):

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 1200 -Repetitions 2 -TickTimeline -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs timeline
```

The experiment uses its own `build/bridge-tick-timeline-proof/`; previous load,
baseline and restart proofs are preserved. Frozen-world link/path/corruption
guards and hashes are checked on every restoration. The order is absent,
installed, installed, absent. All four saved-world digests equal
`d8d79ef0bf1e66ff392ebc53b160e5f2cc697156e38278c9f4163fba4a720bbb`.

Paper **1.21.11-132**, official WorldEdit **7.4.2**, Temurin **21.0.11+10**,
Node **24.21.0**, `minecraft-protocol` **1.68.0**. The shared laptop has an
i5-13420H / 12 logical processors. Each owned server is capped at two processors,
1 GiB heap, BelowNormal priority and a bounded 32 MiB JFR profile. Players are
stationary; random ticks, natural spawning and time/weather cycles are disabled;
loaded non-player entities are removed before the 15-second warmup. No native
Minecraft windows, cursor/focus control or OP grants are involved.

Each measurement contains 1,200 ticks, about 60 seconds. These silent clients
send no application hello/read workload; channel advertisement is separate
lifecycle traffic, not a negotiated subscription. There is no preview source
work, and all measurement windows have zero chunk loads and generated chunks.

| Run | p50 ms | p95 ms | p99 ms | Max ms | Owner CPU ms/s | Owner allocation MiB/s |
| --- | --- | --- | --- | --- | --- | --- |
| 1 absent | 0.8444 | 2.6310 | 7.6595 | 33.6724 | 14.064 | 2.379 |
| 1 installed | 1.0143 | 2.3802 | 3.7913 | 23.0968 | 5.729 | 2.261 |
| 2 installed | 1.0577 | 2.3043 | 3.7049 | 17.4646 | 27.866 | 2.258 |
| 2 absent | 0.9054 | 2.4852 | 4.7154 | 26.0770 | 7.552 | 2.249 |

Installed p95 is 0.2508/0.1809 ms lower in the paired comparisons; installed
medians are higher and owner CPU varies in opposite directions. **This is not
proof of a speedup, no overhead, or acceptable production performance.** Short
warmup, JIT/OS scheduling, observer cost and other shared-laptop activity remain
confounds. No formal statistical equivalence or dedicated busy-workload test
was performed.

### Correlated Candidates

All 4,800 expected markers were found. Twenty candidates are retained locally,
five per recording; the table shows each run's maximum.

| Run / sample | Paper ms | Public bracket ms | Difference ms | Inferred GC pause overlap ms |
| --- | --- | --- | --- | --- |
| 1 absent / 1097 | 33.6724 | 33.4653 | 0.2071 | 0 |
| 1 installed / 314 | 23.0968 | 23.0610 | 0.0358 | 21.3832 |
| 2 installed / 541 | 17.4646 | 17.4264 | 0.0382 | 15.8768 |
| 2 absent / 902 | 26.0770 | 7.4282 | 18.6488 | 0 |

Both installed maxima are dominated by overlapping GC pauses in the inferred
interval. The absent maxima have no recorded GC overlap; the second includes
a substantial portion outside the public bracket. There is no bridge stack
sample in these intervals, but sparse samples cannot determine their cause.
Some other candidates also have substantial pre-bracket time. Do not attribute
that time exclusively to packets or to the bridge merely from event ordering.

Per-candidate CPU deltas are zero in all twenty candidates. The JVM API promises
nanosecond **precision**, not nanosecond accuracy; these short deltas therefore
cannot establish zero owner CPU use. Window-level counters above remain reported
separately. All four sparse execution/allocation profiles have zero sampled
production bridge stacks, which is not a zero-cost guarantee either.

## Adversarial Admission Ordering

`AdmissionOrderingProbe.java` calls the **actual companion's pure `BridgeServer`**,
with deterministic time and an authorized, identity-separated metadata host.
It executes on one owning thread: no Paper/network/client latency is modeled.
Initial negotiations succeed; a two-second refill separates them from reads.

The ordinary sender attempts 2 reads/s. Each active sender starts with four
reads, then one per 250 ms: within the default 4/s per-connection allowance and
burst 8. With 31 such senders, aggregate demand exceeds the global 100/s budget.
Every simultaneous batch is ordered ordinary-first or ordinary-last for
29,750 simulated milliseconds. No retries or alternative production defaults
are introduced.

| Other active senders | Ordering | Ordinary replies / attempts | Admitted reads | Throttled frames |
| --- | --- | --- | --- | --- |
| 1 | Ordinary first | 60/60 | 183 | 0 |
| 1 | Ordinary last | 60/60 | 183 | 0 |
| 31 | Ordinary first | 60/60 | 3075 | 798 |
| 31 | Ordinary last | **0/60** | 3075 | 798 |

All rows reproduce identically on Temurin Java 21.0.11 and 25.0.2. The probe
asserts actor/epoch/request correlation, bounded total admissions/storage,
reads matching admissions, no malformed frames and zero retained peers after
close. **Exit 0 means the experiment executed and these invariants held, not
that fair service passed.** The pre-existing network progress test covers a
different traffic order; its success does not refute this counterexample.

## Next Experiment: Bounded Fair Dispatch

**Proposal, not implemented or accepted.** Rate admission and fair scheduling
are different mechanisms ([source comparison](SOURCES.md#s45-bounded-fair-service-research)).
The best candidate for this fixed-cost metadata stage is a bounded per-actor
pending slot and a persistent round-robin cursor, dispatched on the owning
thread. Keep the aggregate rate and memory bounds; do not raise them to hide
starvation. Traffic is keyed by real connection identity, never payload identity.

| Option | Why it is insufficient / proposed role |
| --- | --- |
| Raise the global frame limit | Adds exposure without controlling CPU or guaranteeing fair service |
| Per-connection quota alone | Already present; individually legal demand can exhaust the aggregate budget |
| Unbounded global FIFO | Retains flood history, adds memory/latency and rewards early queued traffic |
| Bounded per-actor round robin | Candidate for this small metadata operation; must prove queue-age bounds and cleanup |
| Deficit round robin | Consider only when later work has measured variable costs; network byte quanta are not Minecraft CPU budgets |

The prototype must establish all of the following before production integration:

- Separate cheap input admission from fair dispatch. Bound pending count/bytes,
  age and dispatch work **per tick**, not only average frames/s. Start one owned
  wakeup only while work exists; empty/disabled state must leave no polling job.
- Keep one bounded pending read per actor. Define whether a newer read supersedes
  a draft or is rejected; preserve request correlation, duplicate semantics and
  client timeout behavior. Never silently coalesce mutations or a handshake.
- Recheck actual connection, epoch, world and current grants at dispatch and
  publication. Disconnect, respawn, revocation and disable must cancel owned
  work without publishing stale/cached protected data.
- Bound control negotiation separately without creating an unlimited priority
  bypass. One-shot hellos/restart hints must not rely on mass retry loops. At
  1024 contenders and 100 frames/s, instantaneous all-player success cannot be
  promised; deadlines and truthful fallback remain part of the contract.
- Repeat both adversarial orderings, changing arrival offsets and 1/32/maximum
  tracked actors. Record reply ratio, maximum pending age, control progress,
  owner CPU/allocation, memory/bytes, cleanup and idle counters. Include existing
  network, native, revocation, duplicate and graceful-restart regressions.
- Use public host scheduling APIs and deterministic core tests. Any change to
  response timing/transport ownership needs explicit compatibility tests. A
  scheduler is not permission to add block scans or worker access to WorldEdit.

This is a targeted first prototype, not an invitation to copy a complete network
AQM implementation. Fairness across authenticated players does not solve fake
accounts or proxy ownership; those are separate server-policy questions.

## Verification And Evidence

The final offline verification driver passed:

- **50 JUnit tests on each JVM**, unchanged production codec/client/server suite.
- **23 Node harness tests**, including the separate frozen-timeline fixture guard.
- **Four actual-recording JFR tests on each JVM**, including explicit full-GC
  correlation, missing/duplicate marker rejection and wall-boundary skew.
- All four admission experiments on each JVM, with the failed-progress finding
  preserved numerically rather than marked as an acceptance pass.
- Companion footprint/Java 21 bytecode/license/provided-dependency exclusions.
  The new fixture event is absent from the companion artifact.

Native codecs, packagers and gameplay were **not rerun in this test-only cycle**;
their earlier evidence is historical. All owned test servers completed normal
shutdown. No server/client support matrix or public release was widened.

| Artifact | SHA-256 |
| --- | --- |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| WorldEdit Bukkit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Unchanged companion | `c1472fab548e18341de69621c75cfae56e66bd131acd344613bb01c3a889ad04` |
| Test-only fixture | `1d3e0780a3c5fea9224835c2790799cf38e832e9d5471cb7ef5cf8ff05094da6` |

Local evidence: `build/bridge-tick-timeline-proof/load-proof.json`,
`load-analysis.json`, four JFR recordings and associated fixture logs;
`build/bridge-validation/harness-node.log`, `harness-jfr-java21.log`,
`harness-jfr-java25.log`, `admission-ordering-java21.ndjson` and
`admission-ordering-java25.ndjson`. Generated profiles, worlds and identities are
not committed. Research tools and this sanitized report are reproducible source.

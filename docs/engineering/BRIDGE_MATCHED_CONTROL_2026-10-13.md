# Matched Gameplay Profiler Control

Status: **matched bounded workload and evidence gates passed; universal
production cost acceptance remains open**.
Date: **2026-10-13**. Source baseline: `0b8b34a` plus this test-only change.
Published client 1.8.0, production companion and gameplay fixture are unchanged.

## Question And Method

The previous interval cycle identified spark Java-sampler safepoints on this
Windows tuple, plus GC and unattributed outliers. It did not establish the cost
of installing or using the bridge. This cycle repeats the existing matched
gameplay workload with the same profiler setting on both absent/installed groups,
without method instrumentation. JFR remains enabled identically on every JVM.

Use Paper 1.21.11-132 / c5eb079, WorldEdit 7.4.2+7450-eb8e82c,
Temurin 21.0.11+10, two active processors, 256 MiB initial / 1 GiB maximum heap
and below-normal priority. Only loopback headless clients; no native window,
cursor capture, production world or user server is touched.

- 32 moving TCP clients, 28 bridge consumers, four never negotiate it.
- Same frozen three-world bytes restored before each fresh JVM launch.
- 16 AI cows and 512-cell closed WorldEdit edit batches every 20 ticks.
- Two comparisons, order absent/installed then installed/absent.
- Six windows per run: silent, negotiated idle, one consumer, many, flood,
  recovery. **400 ticks per window**, twice the previous window length.
- Keep the existing 15-second initial warmup and five-second phase settling;
  do not claim these prove complete JVM steady state.
- Normal pinned Paper configuration disables bundled spark in both groups.
  Check startup logs and actual whole-recording ThreadDump callers. This is a
  diagnostic control, not advice to disable profiling on users' servers.
- Preserve the previous default-profiler evidence. Store this series separately
  in `build/bridge-gameplay-control-proof/`. No competing test suites or JFR
  analysis while the measured server series runs.

Functional gates remain unchanged: exact edit work, advancing entities, every
actor moving, own-player data, no non-consumer data, zero loaded/generated
chunks, zero idle source work, bounded admission/queues, ordinary replies at
least 90% with maximum latency at most 2 seconds, and graceful process/resource
cleanup. These are fixture gates, not general network latency promises.

Report matched p95/p99, owner CPU/allocation and all >50 ms ticks; retain cold
and measured results separately. A maintained 20 TPS, negative paired delta or
zero quantized short CPU reading is not performance acceptance. Do not average
away an unfavorable comparison, exclude GC ticks or relax quotas after results.
Two pairs on a shared laptop are insufficient for a confidence-backed universal
cost/SLA claim. Broader workloads and future heavy-feature cost remain open.

## Corrections Before Measurement

The earlier load analyzer trusted a retained `validated` flag and copied
summary values without reconciling all raw evidence. The new check reruns the
functional gates, compares every retained summary field, recomputes p50/p95/
p99/max from raw ticks and rejects invalid counters/timestamps. It also checks
the exact six-phase sequence, counterbalanced run order and starting-world
digest. Negative self-tests verify failure before analysis publication.

The shared older load runner now uses the already-tested pre-play login guard
from the active-lifecycle runner before registering channels. Linkage/fixture
errors fail early. These are harness changes, not new production defects.
The JFR summary now rejects lost data and counts real sampler ThreadDump calls;
an isolated real VM-operation probe and deliberately injected lost-data event
test both branches on Java 21/25.

## Sources And Scope

OpenJDK's [forking example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_12_Forking.java)
explains JVM profile contamination, while its [run-to-run example](https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_13_RunToRun.java)
demonstrates variation between fresh JVMs. These inform the method; this is a
whole-server integration workload, **not** a JMH benchmark or copied JMH code.
NIST's [blocking guidance](https://www.itl.nist.gov/div898/handbook/pri/section3/pri332.htm)
motivates controlling nuisance factors. This fixed AB/BA schedule is not a
randomized blocked experiment and does not control thermal/OS/AI effects.
Thousands of ticks within four JVMs are not thousands of independent replicates;
no confidence interval is manufactured by pooling those ticks.
Paper's [profiling guide](https://docs.papermc.io/paper/profiling/) distinguishes
diagnosis during a real workload from a TPS-only assertion. Pinned configuration
and JFR event semantics remain in [S50](SOURCES.md#s50-safepoints-and-background-profiler-control).

## Reproduction

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -GameplayLoad -WithoutSpark -Players 32 -Samples 400 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs gameplay --without-spark
```

## Results

Completed **2026-10-13 14:26:54.854 UTC**. Four fresh measured JVMs plus the
separate world-preparation JVM shut down normally. Final checks found no Java
process and no listener on port 25579. No competing test suite or JFR analysis
ran during the measured series; ordinary shared-laptop activity is uncontrolled.

- **24 accepted windows / 9,600 unique verified tick markers**. The final
  analyzer revalidated all retained raw windows and found no summary/statistic
  mismatch, reported JFR data loss or spark sampler ThreadDump operation.
- All windows: zero chunk loads/generation, 16 advancing AI cows, 20 closed
  WorldEdit edit batches and exactly 10,240 changed cells. Across the series:
  **480 batches / 245,760 changed cells / 75,552 accepted player movements**.
  Every actor produced 98 or 99 accepted movements in each measured window.
- Four non-consumers received no bridge application data. Returned selections
  remained owned by the requesting player. This cycle did not repeat lifecycle
  fault/revocation/native-client tests; their earlier evidence stays separate.
- Installed `silent`, `idle`, `recovery`: **zero metadata reads, replies,
  reply bytes, pending slots and dispatch tasks** in every measured window.
  Installed servers did not imply zero connection/lifecycle or JVM overhead.
- Flood admitted/read **2,049 / 2,047** requests, throttled **32,879 / 32,987**,
  and sent **131,136 / 131,008 application reply bytes** in the two 20-second
  windows. These byte counts exclude Minecraft framing/transport overhead.
- Ordinary flood windows observed **38/38** and **38/39** replies/sends with
  maximum observed latency **265.270 / 249.719 ms**. The second window is not
  claimed loss-free: a window counter cannot distinguish an in-flight boundary
  reply from a missing reply. It passes the predeclared >=90% progress gate.
  Ordinary maximum in the other active windows was at most **97.092 ms**.
- Queue peak **28**, no measured expired requests, maximum lifetime wait
  **313.174 / 300.106 ms**. Flood ended with 14 / 8 queued slots and one task
  while traffic was still active; recovery and disconnect cleared both.

### Paired Cost

Installed minus absent for each matched phase. CPU/allocation describe the
whole owner-thread workload, including packet processing, not isolated bridge
methods. Windows CPU readings remain quantized.

| Pair / phase | p95 delta ms | p99 delta ms | CPU delta ms/s | Allocation delta MiB/s |
| --- | ---: | ---: | ---: | ---: |
| 1 / silent | +0.3685 | -0.9312 | +32.0379 | -0.2285 |
| 1 / idle | -2.2757 | +5.4033 | +39.0762 | -0.2829 |
| 1 / one | -2.4852 | -2.3917 | +21.8673 | -0.0985 |
| 1 / many | -0.2776 | -4.1829 | +28.1236 | +0.3464 |
| 1 / flood | -3.5523 | -6.6268 | +27.3435 | +0.5821 |
| 1 / recovery | -2.5827 | -6.0970 | +17.9639 | -0.3140 |
| 2 / silent | -0.1676 | -1.1340 | -17.1971 | +0.0433 |
| 2 / idle | -2.6198 | -2.4945 | -14.8599 | +0.3070 |
| 2 / one | +1.3685 | -1.7173 | -7.0492 | +0.3623 |
| 2 / many | -2.2250 | -2.6900 | -5.4758 | +0.3845 |
| 2 / flood | -4.2181 | -5.4060 | -17.9317 | +0.6287 |
| 2 / recovery | -3.5379 | -6.7733 | -10.1545 | +0.0477 |

Every first-pair CPU delta is positive; every second-pair delta is negative,
including phases with no bridge application work. This prevents attributing
those deltas directly to bridge CPU. Negative flood timing in both comparisons
does not prove the bridge speeds up a server. Allocation deltas are positive in
both `many` and `flood` comparisons, consistent with actual additional traffic
and metadata work, not zero cost. Do not turn their sampled allocation weights
into exact per-request allocations or retained-heap estimates.

### Outliers And Remaining Limits

| Run | Measured p99 range ms | Maximum full tick ms | Ticks >50 ms |
| --- | ---: | ---: | ---: |
| 1 absent | 14.4030-18.1352 | 24.2893 | 0 |
| 1 installed | 11.2958-19.8063 | 28.1330 | 0 |
| 2 installed | 9.9196-18.0984 | 28.8220 | 0 |
| 2 absent | 13.6793-20.2075 | 27.8284 | 0 |

Largest tick: `2-installed/one`, sample 211, full Paper duration **28.8220 ms**,
public-event bracket **2.5301 ms**. The next is `1-installed/idle`, sample 88,
**28.1330 / 1.4974 ms**, with no bridge application work in that window. Neither
inferred full interval has recorded GC/VM-operation overlap or owner execution
sample. An absent idle tick is **27.8284 ms**, bracket **27.7933 ms**, also without
that recorded attribution. These remain **unattributed**, not proven OS noise,
bridge cost, or proof that no work occurred between sparse samples.

Original cumulative dispatcher maxima remain **10.7071 / 11.3879 ms**, above
the soft 0.5 ms target. They include negotiation/phase-settling and have no
per-call interval in this uninstrumented series. The prior diagnostic cycle
cannot retrospectively assign their cause. The absence of >50 ms ticks alone
does not close cost acceptance.

**Decision:** retain quotas, live permission checks and owning-thread APIs;
no production bug or justified production optimization was established here.
The bounded metadata baseline is sufficient to proceed with the next isolated
development stage, revision-safe remote cuboid editing. It is not a bridge
release approval. Repeating the same shared-laptop comparison indefinitely
would not resolve environmental confounding. Broader controlled host workloads,
native affected-feature checks, pressure/backoff and final numerical acceptance
remain required before publication, especially for future heavy features.

## Regression And Artifacts

Before measurement, the full offline regression passed **68 JUnit tests per
Java 21/25 JVM**, **41 general Node tests**, and per JVM **3 slice-reader groups,
2 new summary-reader groups, 4 tick-reader groups**, both agent checks and 12
deterministic admission cases. The unchanged historical gameplay recordings also
passed the strengthened analysis. Native codecs/gameplay were not rerun; none
of their production sources changed. The production packaging guard passed.

| Artifact | SHA-256 |
| --- | --- |
| Unchanged companion | `be5a082a6b63a8612c993e7dc52c7b0ae498b7249ab5513e72844ced1e1572c7` |
| Unchanged fixture | `23cc20e30820b2b8f1483668c92805dc64ee2569fb2a572ff2503dfdfb6a616f` |
| Paper | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| WorldEdit | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Frozen three-world manifest | `ce01755dc7038c05cf5f6eade525318d5f21bec32ac3f8d73c489ce2acaa1309` |

Runtime: Temurin **21.0.11+10**, tooling also **25.0.2+10**; Node **24.21.0**,
minecraft-protocol **1.68.0**, minecraft-data **3.117.0**, i5-13420H / 12 logical
processors on a shared Windows laptop. Profile JFR in every JVM, no method agent.
No protection plugin, FAWE, proxy or translator claim is made.
Ignored evidence: `build/bridge-gameplay-control-proof/load-proof.json`,
`load-analysis.json`, `analysis-console.log`, raw per-window measurements, four
measured logs/JFR and the preparation log/JFR/snapshot. Regression logs are under
`build/bridge-validation/`. Do not publish raw worlds, identities or profiles.

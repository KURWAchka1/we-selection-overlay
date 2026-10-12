# Bridge Method Interval Diagnostics

Date: **2026-10-12**. Source baseline: **1d8e4df**.
Scope: test-only method attribution and a background-profiler control.
Production bridge behavior, client sources, published JARs and the 1.8.0 tag
are unchanged. This is not a release performance acceptance report.

## Question And Method

The [previous active-lifecycle cycle](BRIDGE_ACTIVE_LIFECYCLE_2026-10-12.md)
recorded a cumulative dispatcher maximum of 12.0926 ms, but had no individual
dispatcher interval. A high-water counter alone cannot identify the slow call,
its timestamp, or distinguish actual CPU from a stopped/descheduled thread.
This cycle adds that missing observation without adding production profiling.

A separate opt-in `-javaagent` wraps exactly seven pinned methods in three
companion classes: outer ingress, core enqueue, dispatcher drain, process,
permission, metadata read and reply send. Original bodies/frames are retained;
the wrapper records normal or exceptional exit and preserves the result/cause.
Events contain operation/parent IDs, timestamps, CPU/allocation deltas and the
existing core high-water value, not actor IDs, payloads, block data or sessions.
The agent does not attach to any running JVM, replace permissions, inject frames,
or instrument Minecraft/WorldEdit/Paper classes.

Recorder helpers alone are bootstrap-visible so Paper's isolated plugin loader
can resolve them. ASM 9.9 is relocated in the agent and remains outside the
bootstrap helper JAR and every production artifact. See [S49](SOURCES.md#s49-test-only-method-interval-instrumentation).

The reader makes two bounded streaming passes, rejects lost JFR data, unknown/
invalid fields, incomplete operation evidence and production method failures,
and reports distributions plus up to twenty large/high-water roots and nested
calls. It joins GC pauses, safepoint-begin synchronization, VM-operation bodies,
owner park/monitor events, deoptimizations and execution samples separately.
Inclusive nested durations and overlapping JVM events must not be added.
CPU precision is not accuracy: short Windows MXBean deltas can be zero or 15.625
ms; neither is evidence that a method did no work.

Whole-lifetime recorded enqueue, process, read and send counts are reconciled
with the four independent core snapshots preceding graceful shutdown. Sends
include negotiated closes; source reads and processing do not. The largest
recorded core high-water must exactly match those snapshots. This prevents a
rolled-off/incomplete recording from looking like a faster dispatcher.

## Test Tuple And Workload

- Paper **1.21.11-132 / c5eb079**, official WorldEdit **7.4.2+7450-eb8e82c**.
- Temurin **21.0.11+10** server; tooling additionally checked on **25.0.2+10**.
- Node **24.21.0**, minecraft-protocol **1.68.0**, minecraft-data **3.117.0**.
- Same i5-13420H laptop, 12 logical processors. Server: 2 active processors,
  256 MiB initial/1024 MiB maximum heap, below-normal priority, loopback only.
- 32 moving real TCP clients, four never negotiate the bridge; 16 ticking AI
  cows and fixed 512-cell closed WorldEdit batches every 20 ticks.
- One ordinary actor requests 2/s; 27 flood actors each offer 80 raw frames/s.
- Two fresh JVM starts per mode, eight 200-tick windows each. Real queued input
  is observed before revoke, disconnect, three companion restarts and WE stop.
- Identical functional gates to the previous lifecycle workload, including
  source ownership, denied replies without bounds, reconnect, stale-epoch
  observations, independent CUI, idle counters and fail-closed dependency loss.
- Default-profiler mode and a separate no-spark control retain different raw
  directories. The control uses normal pinned `spark.enabled: false` configuration.
  Its startup message and recorded sampler callers are checked, not assumed.

The control deliberately tests profiler interference, not a frozen-world,
reversed-order absent/installed cost comparison. Diagnostic wrappers, JFR,
background gameplay and the shared laptop all affect these timings. A short
read-only JFR analysis also ran while the control series was active; it did not
modify server state, but is another reason not to interpret the series as clean
paired performance acceptance.

## Failures Retained And Corrections

1. The initial PowerShell argument construction unwrapped a one-item array to
   a string; splatting then sent individual characters. The root-path guard
   rejected launch before a server started. The launcher now uses a typed array.
2. The first actual diagnostic JVM loaded all wrappers, but Paper could not see
   system-loader `SliceProbe`. Its real log contains `NoClassDefFoundError`, and
   the client hello timed out. No success report was generated. Raw log/JFR/
   failure JSON remain under `build/bridge-slice-diagnostics/attempt-1-loader-visibility/`.
   The helper-only bootstrap JAR fixed resolution; an actual bootstrap-mode
   self-test and subsequent real Paper runs verify this, not compilation alone.
3. The harness now rejects plugin-message linkage/verification exceptions early
   instead of waiting for a consumer timeout. The production artifact guard
   additionally rejects the diagnostic-agent namespace.
4. Attribution originally considered GC and VM-operation bodies but omitted
   safepoint synchronization. The reader now reports `SafepointBegin`, VM callers
   and deoptimizations; this exposed the profiler interference below.

These are test/measurement corrections. They are not described as user-facing
bridge defect fixes or evidence that permission/WorldEdit APIs are defective.

## Default-Profiler Results

Both complete runs passed, finishing **17:37:19.234 UTC**. They contain **3,200
verified tick markers**, **25,149 accepted movements**, **71,680 changed cells**,
and six recoveries in **250.121-288.322 ms**. All measured chunk loads/generation
were zero. Source ownership, non-consumer silence, queued cleanup, idle absence
of source work, independent CUI and normal process shutdown passed.

| Observation | Round 1 | Round 2 |
| --- | ---: | ---: |
| Diagnostic method events | 320,245 | 320,234 |
| Dispatcher intervals | 1,471 | 1,468 |
| Dispatcher p95 ms | 0.3680 | 0.3371 |
| Dispatcher p99 ms | 0.6399 | 0.5362 |
| Dispatcher wall maximum ms | 8.0677 | 7.5441 |
| Original core high-water ms | 8.0666 | 7.5432 |
| Dispatcher intervals above soft 0.5 ms | 38 | 26 |
| Permission p99 ms | 0.0044 | 0.0051 |
| Metadata read p99 ms | 0.0420 | 0.0413 |
| Reply send p99 ms | 0.0800 | 0.0677 |

These distributions include cold handshakes/warmup as well as measured windows;
they are instrumented wall time, not pure application CPU or a release SLA.
The measured full-tick maxima were **42.9475 / 37.2383 ms**, both disconnect
windows. No measured tick exceeded 50 ms, which alone does not establish an
acceptable bridge cost or attribute the full-tick maxima.

### Safepoint Observation

Actual startup logs say the bundled spark background profiler selected its Java
engine because async-profiler is unsupported on this Windows/amd64 tuple.
The recorded ThreadDump callers are `spark-java-sampler-0-0` through `-0-5`.
The first recording contains **10,945** such operations. Five large dispatcher
roots have the following overlaps:

| Run / interval | Wall ms | Safepoint-begin overlap ms | VM operation body ms |
| --- | ---: | ---: | ---: |
| 1 / 93070 | 8.0677 | 7.8925 | 0.0596 |
| 1 / 215205 | 7.0252 | 6.8829 | 0.0466 |
| 2 / 175744 | 7.5441 | 7.2999 | 0.0490 |
| 2 / 134589 | 7.3992 | 7.2559 | 0.0533 |
| 2 / 254872 | 6.8984 | 6.7847 | 0.0503 |

The VM operation is ThreadDump, with spark sampler callers, rather than a GC
pause. This is evidence of profiler-related synchronization overlapping these
specific intervals, not proof that all of their duration was bridge CPU or that
every server thread stopped for the entire begin interval. We do not subtract
the overlaps to manufacture an accepted cost. See [S50](SOURCES.md#s50-safepoints-and-background-profiler-control).

Other large intervals remain unexplained: round-1 **6.6593 ms** encloses a
**6.5603 ms** send; **6.0657 ms** encloses a **5.9165 ms** metadata read. Neither
has a corresponding recorded GC/safepoint/park/monitor overlap. Sparse execution
samples and quantized CPU cannot establish their cause. First handshakes also
include approximately 1.76 MiB of interval allocations and 4.46-4.70 ms wall
time, including cold diagnostic/JFR/class initialization, not just the codec.

## Control Results

Both no-spark runs passed, finishing **17:46:37.625 UTC**, with **3,200 verified
tick markers**, **25,117 accepted movements**, **71,680 changed cells** and six
recoveries in **247.693-284.920 ms**. Logs confirmed spark was disabled; neither
recording contains a ThreadDump operation from its Java samplers. All functional
gates and zero measured chunk loads/generation still passed. Ordinary-consumer
maximum reply latency was **273.762 / 267.014 ms**, without starvation.

| Observation | Round 1 | Round 2 |
| --- | ---: | ---: |
| Diagnostic method events | 320,533 | 320,826 |
| Dispatcher intervals | 1,471 | 1,462 |
| Dispatcher p95 ms | 0.3779 | 0.3347 |
| Dispatcher p99 ms | 0.6069 | 0.5490 |
| Dispatcher wall maximum ms | 10.8277 | 4.4621 |
| Original core high-water ms | 10.8268 | 4.4571 |
| Dispatcher intervals above soft 0.5 ms | 38 | 29 |
| Permission p99 ms | 0.0055 | 0.0049 |
| Metadata read p99 ms | 0.0406 | 0.0409 |
| Reply send p99 ms | 0.0851 | 0.0734 |
| Measured full-tick maximum ms | 40.4388 | 39.7253 |

Disabling the profiler did **not** remove all peaks or demonstrate a speedup.
Round-1 interval **208344** takes **10.8277 ms** during `restart-1`, overlapping
**10.3038 ms** of GC pause and **10.4398 ms** of a `G1CollectForAllocation` VM
operation requested by the server thread. A nested metadata read takes **10.6944
ms**. Interval **31616**, outside measured windows, takes **8.6399 ms**, with an
**8.2210 ms** GC overlap. These observations identify concurrent GC, not the
allocation source responsible for triggering it; the two overlapping pause
categories must not be added.

Round-2's maximum is the cold first dispatcher interval, outside the measured
windows, with **1,763,920 allocated bytes** including observer overhead. Other
intervals still lack an explanation: round-1 **3.1611 ms** encloses a **3.1169 ms**
send without recorded GC/safepoint/park/monitor overlap. The control therefore
supports separating profiler, GC, cold initialization and unknown intervals,
not blaming one component or claiming the soft target is now satisfied.

## Engineering Decision

Keep the existing production limits and live authorization checks. Do not add
permission caching, async live-world calls, speculative warmup or wider slices
to conceal a wall-clock counter. The soft 0.5 ms target is not changed.
Profiler overlap improves attribution; it does not explain the previous 12.0926
ms maximum retrospectively, close every outlier, or establish production cost.

Before a cost/release claim, run the corrected observations with explicit
profiler configuration and repeated matched busy-world absent/idle/active
controls. Retain raw distributions, cold/steady separation, outliers and queue
progress. Current instrumentation cannot provide a hard preemptive host-call
budget or exact short-call CPU. Remote selection mutation, clipboard streaming
and command simulation remain separate unimplemented stages.

## Reproduction And Evidence

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -LifecycleLoad -SliceDiagnostics -Players 32 -Samples 200 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-active-lifecycle.mjs --slice-diagnostics
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -LifecycleLoad -SliceDiagnostics -WithoutSpark -Players 32 -Samples 200 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-active-lifecycle.mjs --slice-diagnostics --without-spark
```

Ignored evidence roots: `build/bridge-slice-diagnostics/` and
`build/bridge-slice-diagnostics-no-spark/`. Each retains logs/JFR, complete
measurements/fault snapshots, `lifecycle-proof.json` and reconciled
`lifecycle-analysis.json`. Raw server/profile files are not pushed publicly.

SHA-256 of tested artifacts:

| Artifact | SHA-256 |
| --- | --- |
| Paper | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| WorldEdit | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Unchanged companion | `be5a082a6b63a8612c993e7dc52c7b0ae498b7249ab5513e72844ced1e1572c7` |
| Unchanged fixture | `23cc20e30820b2b8f1483668c92805dc64ee2569fb2a572ff2503dfdfb6a616f` |
| Test agent | `c724c41fd61f2e4e26b6222dc7604fff295ba2591f0192d98d3612de2ae06191` |
| Helper-only bootstrap | `b89b6cf6ba5b29a5c37b84aba0b5131e56f61797ec27d0ff8333761a30fed60f` |

General regression: **68 JUnit tests per Java 21/25 JVM**, **36 general Node
tests**, **4 existing JFR-reader groups per JVM**, **3 new slice-reader groups
per JVM** (including eight deliberately corrupt recordings), both agent
application/bootstrap checks and **12 deterministic admission orders per JVM**
passed. Native codecs/gameplay were not rerun because production was unchanged;
the report does not promote historical native evidence into a new native pass.
The three slice-reader groups also passed again on both JVMs after the final
ThreadDump-caller aggregation change. Both diagnostic analyses reconcile all
four complete recordings with independent counters before producing summaries.

# Paper 26.2 Profiler Control

Date: **2026-10-15**. Baseline **9c74c93** plus accompanying test-only changes.
No production source, published 1.8.0, development client or companion JAR is
changed. This follows the [edit-attribution cycle](BRIDGE_26_2_EDIT_ATTRIBUTION_2026-10-14.md),
whose 14.7746 ms read remained mostly unexplained.

## Predeclared Method

Exact **Paper 26.2-133 / WorldEdit 7.4.5 / Temurin 25.0.2+10**; the same
checksum-pinned MCProtocolLib 776 headless clients and ten-method diagnostic
agent. Four fresh measured server JVMs run with spark **on / off / off / on**.
The agent and zero-threshold VM-operation/safepoint-begin JFC remain enabled
identically in every measured arm. A separate stopped priming JVM creates a
frozen complete three-dimension world tree, checksum-restored before each run.
The snapshot covers world/player files, not WorldEdit's plugin session directory;
each fixture join resets and independently checks that actor's starting cuboid.

Thirty-two real loopback connections: eight editors, eight metadata readers,
twelve flood senders and four CUI-only non-consumers. The unchanged target
cadence is 250 ms for ordinary actors, 80 ms for flood, five seconds settling
and 400 ticks each idle/active/recovered. Keep stale/foreign/replay/revocation,
canonical bounds, actor-local CUI, world/undo audits, admission/queue/progress,
quiet source-work absence and final normal-shutdown gates. Actual applies and
admissions can vary; acknowledgments include bounded post-active drain.

Each Paper JVM uses 256 MiB initial / 1 GiB maximum heap, two active processors
and below-normal priority on this shared Windows laptop. No native Minecraft
window, OS input, public port, real account or user world is involved. No other
test suite or JFR analysis runs during the measured series. This is stationary
headless selection-metadata work, not live block editing or native GPU gameplay.

Use the public `spark.enabled` configuration in both arms, not a command that
changes recording halfway through a JVM. Save original input JSON and Paper's
effective YAML for every run. Only that boolean may differ after full structured
comparison of effective global configuration. Require exact startup policy and
recorded sampler ThreadDump presence/absence independently. Offline parsers are
existing checksum-pinned fixture dependencies; see [S60](SOURCES.md#s60-exact-262-profiler-control).

Separate cold/lifetime and active-window method distributions. Reconcile every
method count, high-water mark, complete tick marker and copied raw measurement
before writing analysis. Report all outliers, inclusive nested methods and
overlapping JVM events without subtracting them as exact bridge CPU. The fixed
AB/BA schedule controls neither all thermal/OS effects nor independent hardware
variation. Zero recorded spark callers is not proof that all other observers
are absent. No absent-companion cost claim or recommendation to disable spark
follows from this experiment.

## Results

All four functional/evidence runs pass, completed **2026-10-15T18:17:25.378Z**.
Analysis reconciles **4,800** complete raw/JFR tick markers, **98,052** lifetime
method events, process/read/capture/apply/send/ingress counters and original core
high-water marks. Five successful Paper JVMs including priming, and four owned
wire-helper JVMs, stop normally before the final proof is written. There was no
failed load attempt in this series; intentionally rejected helper inputs are
negative tests, not discarded server runs.

| Run | Spark | Applied | Active tick p95 / max ms | Commit reply p95 / max ms | Lifetime core max slice ms |
| --- | --- | ---: | --- | --- | ---: |
| 1 | On | 629 | 1.5550 / 19.0133 | 266.44 / 347.13 | 18.4602 |
| 2 | Off | 632 | 1.6381 / 11.6207 | 264.32 / 298.06 | 5.0554 |
| 3 | Off | 632 | 1.5247 / 6.1749 | 266.51 / 296.33 | 8.5066 |
| 4 | On | 626 | 1.6500 / 6.0163 | 278.98 / 341.67 | 8.9205 |

All **2,519** sustained applies return actor-local canonical bounds/revisions.
Every editor progresses with **78..80** acknowledgments; 1,895..1,966 admissions
are throttled per run, demonstrating offered pressure. The pending peak is
**28**, no pending request expires, and final peers/pending/tasks are zero.
All 32 loaded-world/empty-undo audits, command-invalidated drafts, foreign
revisions, exact replay without another apply/CUI change, and live revocation
checks pass. Four non-consumers per run receive zero bridge frames.

All twelve measured windows report **zero loaded/generated chunks**. Quiet
idle/recovered windows have zero new source work, transfers and diagnostic
method calls. Active capture count is unchanged in every run. Ten captures per
run are setup/conflict-refresh work, not polling during sustained edits.

### Independent Profiler Control

The original input files contain identical explicit defaults apart from
`spark.enabled`. Full parsed effective Paper global configurations also agree
after removing **only** that field. Their normalized digest is
`0efac7d6db9652c09bfb5d91640cc27f6a3c17e2540eeada6c35580503bdf285`.
Startup logs independently confirm the configured arm. Whole-recording spark
sampler ThreadDump counts are **7,307 / 0 / 0 / 7,736**, so the disabled arms
are not merely mislabeled on-paper settings. Other JVM/JFR observers remain.

Offline YAML reading rejects duplicate/custom-tag/malformed/oversized input,
non-string keys, unsupported scalar types and non-finite numbers. JSON output
preserves explicit null values, avoiding a false equality between a missing
setting and a null setting. Existing fixture parser libraries are checksum
verified; no dependency was downloaded or added to player/plugin artifacts.

### Active Methods And Outliers

These are instrumented wall times inside actual active marker spans. Boundary-
crossing and post-active drain calls remain in lifetime totals, not these
distributions. Nested inclusive times and overlapping VM events cannot be added.

| Run | Drain p95 / p99 / max ms | Drain calls above 0.5 ms | Apply p99 / max ms | Read max ms |
| --- | --- | --- | --- | --- |
| 1 on | 0.5794 / 0.7635 / 18.4637 | 62 / 398 | 0.2308 / 1.1251 | 0.3112 |
| 2 off | 0.5724 / 0.7308 / 1.5865 | 68 / 398 | 0.2449 / 1.3746 | 0.3356 |
| 3 off | 0.5880 / 0.8702 / 3.5581 | 68 / 397 | 0.2616 / 3.4107 | 0.2711 |
| 4 on | 0.5665 / 0.8244 / 2.1119 | 76 / 396 | 0.2742 / 1.6452 | 0.3755 |

- Run 1's largest active drain, ID **4481**, takes **18.4637 ms**, overlapping
  **17.6539 ms** of recorded GC pause and a **17.7276 ms** VM-operation interval.
  Associated operations include `G1CollectForAllocation` from the server thread
  and a spark `ThreadDump`. This identifies the concurrent pause, not the exact
  allocation source responsible for triggering collection. The two overlaps
  are not disjoint costs and must not be summed or subtracted into a CPU claim.
- Run 2's largest active **tick** is **11.6207 ms**, overlapping **10.6049 ms**
  of GC, while its root drain contributes only **0.1580 ms**. A slow tick is not
  automatically a slow bridge call.
- Run 3, with **no spark sampler events**, has a **3.5581 ms** active drain
  containing a **3.4107 ms** apply. It has no recorded GC/VM/safepoint-begin,
  owner park/monitor, execution sample or deoptimization explanation. Spark
  therefore cannot be a necessary explanation for every oversized interval.
- The same spark-off run has the series' worst measured tick, **38.6932 ms**,
  during **idle**, with **37.2577 ms** of recorded GC pause and **zero bridge
  calls** in that quiet phase. It is retained, not omitted because it falls
  outside active work. It does not identify the cause of all JVM allocation.
- The earlier **14.7746 ms read** was not reproduced: current active read maxima
  are 0.2711..0.3755 ms. Non-reproduction is not a fix or a retrospective cause.

Active owner CPU is **203.125 / 765.625 / 437.500 / 546.875 ms**. Off-minus-on
paired deltas change sign (**+562.500 / -109.375 ms**); allocation deltas also
change sign (**-823,752 / +1,172,896 bytes**). These whole-window figures include
game/JVM/observer work and shared-machine variation. Short method CPU is heavily
quantized on Windows and often reads zero. No speedup or exact per-player CPU
cost is inferred from either sign or a smaller maximum.

## Decision And Remaining Gates

The **0.5 ms soft target remains exceeded in both profiler arms**. The experiment
verifies the intervention and narrows individual observations, but does not
justify caching permissions, skipping authority checks, moving WorldEdit calls
off-thread, increasing quotas or disabling profiling for users. Production code
remains unchanged because no such correction is established by these results.

Close this bounded observer-control stage, not the release performance gate.
Future cost acceptance needs representative moving/native gameplay and matched
absent/idle/active companion comparisons on the exact host. Unassigned wall-time
intervals require owner scheduling/execution evidence if they recur. Independent
machines/repetitions, other server implementations, arbitrary faults and heavy
remote clipboard/command features remain separate gates. Do not indefinitely
repeat the same small stationary workload as a substitute for those next stages.

## Regression And Pins

**95 pure/domain tests on each JVM 21/25**, **101 general Node harness tests**,
**12 JFR-reader checks per JVM**, agent application/bootstrap checks, **12**
deterministic admission orders per JVM and all **six actual native payload
codecs** pass. The standalone effective-configuration reader has two additional
test groups on each JVM, including deliberately corrupt inputs and changed
non-profiler/null settings. Historical instrumentation-study analysis still
passes under the shared policy helper, and its rerun guard preserves the prior
proof without launching a server. No GUI/gesture/command/clipboard-rendering or
universal-packaging suite was rerun in this test-only cycle.

Final launcher review found that the older Node rerun guard ran **after** the
PowerShell wrapper copied fixture artifacts. A future accidental repeat could
therefore replace binaries beside an old proof before being rejected. The new
PowerShell guard refuses completed/failed edit studies **before build/copy**.
An actual attempted repeat of this completed study is rejected with proof,
companion and fixture hashes unchanged and no server launched. This is a test-
evidence preservation fix, not a client/companion behavior change.

| Evidence pin | SHA-256 |
| --- | --- |
| Unchanged companion | `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956` |
| Unchanged fixture | `4dee35f5a0d9a66194b7af721cdb01a8000cd0b5f7581ed1759ff8d6549146e2` |
| Unchanged diagnostic agent | `452a18c8ee39c92eb9216baa2dd0a707835efab36992e04b5bc407b0ba51343a` |
| Identical JFC | `a590b966fb30f41860dc01028ebe60125d04901b8725d049ec58370901984536` |
| Starting-world manifest | `e3151119cdae2a76b8c9c6120a23c4b52c51cb072f0d8a6faa72d3062f42ec4a` |

The proof retains exact Paper/WorldEdit/helper/bootstrap pins, input hashes,
run order and normal termination; the analysis additionally verifies parser
library pins, all effective configurations, raw measurements and JFR records.
Published and development client hashes remain those of the preceding cycle.

## Reproduction

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk21' -Java25 'C:\path\to\jdk25' -Minecraft 26.2 -EditLoad -EditProfilerControl -Players 32 -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs 'C:\path\to\jdk25' 26.2 --spark-control
```

Evidence root: `build/bridge-edit-profiler-control/26.2/`. Previous successful
and failed attempts must be archived before reuse. Older evidence under
`build/bridge-edit-diagnostics/26.2/` is preserved separately. Raw profiles/worlds
are not pushed publicly. The optional standalone configuration-reader tests use
`WESO_TEST_JAVA` and the retained exact 26.2 parser libraries in the older root.

# Combined Hot-Path Verification

## Predeclared Method

The [guard-band experiments](BRIDGE_GUARD_BAND_2026-10-16.md) show that a fixed
floor can sacrifice useful service without consistently meeting the soft target.
Remove it. Keep the original within-slice largest-visit reservation and the
specific [revision-ID](BRIDGE_REVISION_IDS_2026-10-16.md) and
[request-local world](BRIDGE_WORLD_READ_2026-10-16.md) unnecessary-work removals.
Do not change quotas, 0.5 ms target, FIFO, authority or the public API send path.

Compare the exact original reservation-only companion
`78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1`
against the final combined candidate
`aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`
in four fresh **previous / candidate / candidate / previous** Paper 26.2 JVMs.
All arms use the same minimal root observer, inline ordered helper, JFC, disabled
spark, frozen starting world, simultaneous 32 joins, moving/edit/flood workload
and owning-server quit barrier. No competing builds/offline analysis are allowed
during measurement. Neither old negative experiments nor maxima are discarded.

Keep the **same**, already declared quantitative performance gates in both pairs:
active drain p95 <= 0.5 ms; every ordinary editor/reader has >=95% paired progress;
ack-p95 growth <=100 ms. All raw/JFR/canonical/CUI/authority/replay/no-chunk-I/O/
no-expiry/quiet-window/cleanup gates must also pass. `hotPathAcceptance` is
independent of functional success and a failed performance gate exits nonzero.
Do not label a mixed result full performance acceptance or hard real-time safety.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -HotPathComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --hotpath-control
```

Root: `build/bridge-hotpath-proof/26.2/`. Preserve the exact original baseline as
`previous-companion.jar` before building/copying. No published/development player
JAR, client version or release tag is changed by these server-only changes.

## Full Regression

Before this final matched workload, `tools/test-server-bridge.ps1 -Offline`
passes **111 pure tests on each of Java 21 and 25**, **139 general Node groups**,
the original and minimal diagnostic-agent probes/packaging guards on both JVMs,
**13 JFR reader groups per JVM**, deterministic admission-order probes and all
**six native payload codecs**. Both sets of admission probes deliver all 60
ordinary replies in the reproduced 31-sender case. The companion is 88,798 bytes,
contains Java-21-compatible bytecode and no fixture/client/provided dependencies.

Effective-YAML reader tests additionally pass **three groups per JVM**, with
explicit `WESO_TEST_JAVA`. They were run separately immediately after the main
matrix; the combined script now includes them for future runs. Original logs
remain at `build/bridge-validation-pre-hotpath-20261016/`; current logs are in
`build/bridge-validation/`. No failed performance experiment is relabeled as a
successful regression or discarded because the pure tests pass.

## Executed Results

Proof **2026-10-16T16:37:29.192Z** passes 4,800 exact raw/JFR tick pairs,
2,221 applies, 37,856 accepted moves, 122,880 fixture WorldEdit cell changes,
128 independent actor audits, authority/replay/CUI/canonical checks and normal
owned shutdown/cleanup. Quiet bridge work, measured chunk I/O/generation,
request expiry and final connections/queue/tasks are zero. No measured tick
exceeds 50 ms in this experiment; this does not erase earlier long ticks.

| Round | Companion | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 67 / 396 | 1.2091 / 6.2984 / 17.7823 | 483 | 693.9133 / 1001.9526 |
| 2 | Combined | 11 / 397 | 0.4371 / 1.0949 / 1.4115 | 599 | 337.0138 / 545.0861 |
| 3 | Combined | 24 / 400 | 0.5348 / 1.2050 / 3.1106 | 588 | 391.3591 / 593.1211 |
| 4 | Previous | 33 / 393 | 0.6217 / 1.7989 / 12.5273 | 551 | 479.2424 / 558.7063 |

Both pairs improve throughput (+24.02% / +6.72%), every ordinary actor's
progress (minimum ratios 121.31% / 104.29%) and ack p95 (-356.8995 / -87.8833 ms).
Both drain p95 comparisons improve, but the second candidate still exceeds
0.5 ms by **34.8 us**. The retained analyzer exits **2**: functional success,
improved comparisons and all progress/latency checks are not full performance
acceptance. Do not round 0.5348 down to a passing value.

Active allocation is 6,407,448 / 7,255,560 bytes previously versus
6,776,072 / 6,672,528 in the candidate, with 1,622 / 1,850 versus 2,015 / 1,971
admitted frames. The first candidate allocates more in total while serving more
work; do not call it a reduction in total allocation. Per-admitted-frame values
fall in both pairs, with mixed-role/workload caveats. Quantized CPU also falls in
these local pairs, not a general hardware-independent speedup guarantee.

The largest candidate active intervals (1.4115 / 3.1106 ms) have no recorded
GC/VM/safepoint/owner-park/monitor explanation. A sample near the end of the
2.6959 ms run-3 interval executes `BlockVector3.add -> CuboidRegion.contract ->
SelectionEditTransaction.apply/applyAxes/commitAxes`. It identifies executed
public WorldEdit code, **not** the cost/cause of the entire interval. Do not
replace validated public region semantics from one sample or assume zero CPU.

**Retain the narrow unnecessary-work removals, keep the cost gate open.** The
rejected static floors stay removed. The separate
[scheduling control](BRIDGE_PRIORITY_CONTROL_2026-10-16.md) changes only the
fixture's process priority with production bytes fixed; it must not reinterpret
this failed strict gate as success. Native concurrent gameplay, representative
absent/installed cost baselines and unsupported platforms remain separate gates.

Final companion SHA-256:
`aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`.
Frozen digest: `8cc30105ec487119ee5577edfe2978a2ebce3deb270e1c825013688be312d685`.

# Seven-Visit Dispatch Batch Trial

## Predeclared Follow-Up

The [six-visit trial](BRIDGE_BATCH_2026-10-17.md) passes both absolute p95 gates
but fails three individual reader-progress ratios (94.6667..94.7368%). Its default
is rejected, original eight restored, and all raw evidence/source/JARs retained.
Do not relabel this as success or retry six until favorable data appear.

Test the **only intermediate integer, seven**, in a separately named root.
Nominal batch capacity becomes 140 visits/second at 20 TPS rather than six's 120;
the global 100/second token rate and all per-player rates/bursts stay unchanged.
This is a rationale for testing a less restrictive cap, not measured acceptance.
No adaptive floors, carry-over penalties, permission caching, API bypass, async
world access, protocol/client changes or scheduling-priority intervention.

Compare **original eight / seven / seven / original eight** in four fresh JVMs.
Original companion is still SHA-256
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`,
not the rejected six-visit artifact. Keep Paper 26.2-133/WE 7.4.5/Java 25,
BelowNormal, heap/processors/JFC/minimal root observer, disabled spark, frozen
world, simultaneous 32 logins, moving/edit/flood workload and server-quit barrier
fixed. No competing builds/readers during measurement. Cold calls remain visible.

The **same gates**, fixed before this new data: both candidate active drain p95
<=0.5 ms; every ordinary editor/reader >=95% paired service; ack-p95 growth <=100
ms; all functional/raw/JFR/canonical/CUI/authority/replay/no-chunk-I/O/no-expiry/
quiet-window/cleanup checks pass. Reject/revert if any gate fails. No further
integer search below six, threshold changes or favorable-repeat filtering.

Deterministic regression must verify seven-visit FIFO bounds, all 32 actors
served, 100 requests/second for ten nominal seconds, configured eight-visit
no-static-floor behavior, pause recovery, revocation and admission fairness on
Java 21/25. If retained, repeat affected native tests through the private desktop
and cursor-isolation guards. Published/development player JARs stay unchanged;
this is an unpublished server candidate, not a public release.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -BatchComparison -BatchVisits 7 -Players 32 `
  -Samples 400 -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --batch-seven-control
```

Root: `build/bridge-batch-seven-proof/26.2/`. The six-visit root remains intact
and independently analyzable with its original flag/hash. Source basis: [S71](SOURCES.md#s71-bounded-batch-capacity-and-diagnostic-alternatives).

Candidate production source changes only `DispatchLimits.defaults().maxPerSlice`
from eight to seven. Its companion SHA-256 is pinned before measurement:
`2f643ed9950c9b69a3ed524a5c6a67c69ca2ddfa63170938cce3703581dbd686`.
The targeted core suite passes before starting this follow-up; full Java-21/25
regression and harness checks precede measurement as well.

## Observed Result: Rejected

Analysis `checkedAt=2026-10-17T04:39:40.571Z`. Frozen world digest:
`fa64fc07ca2eadff5bac9c67a0271e0681a16c31f6c77a36bff57a5f8d06a151`.
Both candidate p95 values exceed the unchanged 0.5 ms gate. Individual ordinary
progress and paired ack-latency gates pass, but cannot override this failure.

| Round | Default | Applies | Drain p95 / p99 / max ms | Over target | Ack p95 / max ms |
| --- | --- | ---: | --- | --- | --- |
| 1 | Eight | 587 | 0.6383 / 1.6726 / 2.3166 | 30/398 | 401.9704 / 554.4141 |
| 2 | Seven | 597 | 0.6105 / 1.3250 / 2.3197 | 25/397 | 326.0974 / 557.7410 |
| 3 | Seven | 591 | 0.6811 / 2.6027 / 9.8028 | 36/395 | 354.9182 / 543.3725 |
| 4 | Eight | 595 | 0.5560 / 1.3098 / 3.1773 | 28/396 | 307.6761 / 574.6202 |

Paired ack-p95 deltas are -75.8730 and +47.2421 ms; minimum individual progress
ratios are 100% and 97.3333%. All four raw functional studies and cleanup pass:
2,370 applies and 4,800 reconciled ticks. The strict offline analyzer exits 2,
preserving this rejected result, not an infrastructure failure.

Full premeasurement regression passes: 114 pure tests per Java 21/25 JVM, 148
Node harness groups, JFR/YAML/agents/admission probes and six native payload
codecs. These do not establish real-client joins or timing acceptance.

Restore eight. Preserve the exact seven candidate JAR, both candidate Java
sources, raw proof/logs/JFR and failed analysis in the named root. Neither this
trial nor six supports retaining a lower default. No further batch-size search
or favorable reruns: investigate a reproducible admission flaw separately.

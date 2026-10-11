# Bridge Lifecycle And Load Pilot

Date: **2026-10-11**. Baseline: `c67825a`; changes are preserved in the
accompanying commit. This is experimental **0.1.0-dev**, not a production release
or a performance SLA. Published client **1.8.0**, its tag and both archived
JAR hashes remain unchanged. No client runtime source changed in this cycle.

## Changes And Regression Evidence

The Paper host now resets selection tokens/caches on real same-world respawn
and bridge-channel unregistration, and disables when WorldEdit disables.
World changes use the same context reset. An open connection keeps its bounded
admission record: repeatedly resetting context cannot refill its request budget.
Actual disconnect removes that record. No polling, worker or world-read task was
added. Wire framing/capability scope stays unchanged: read-only metadata only.

Negative replay used the previous companion SHA-256
`2d03373110e6726633e8cfbd3179d32cb6b182b42f23aec629749c7775b044b6`.
After unregister/register without a new hello, a stale epoch still received a
reply: the current test failed `Unexpected reply to stale/invalid request`
(`8 !== 7`). This is a reproduced regression, not an inferred compatibility issue.
The corrected artifact passes that same scenario.

`tools/test-paper-bridge.ps1` passed twelve grouped assertions on actual Paper:
connection ownership, separate CUI, granted/denied/ordinary clients, no idle
reads, duplicate caching, forged epoch rejection, live permission revocation,
empty/recreated selection, malformed/incompatible traffic, unregister/register,
actual death/respawn, same-process offline-identity reconnect and dependency
disable. Both channel directions are unregistered after WorldEdit shutdown.
The server stopped before the success JSON was written. The fixture also now
waits for a fresh join marker on reconnect rather than matching the first join.

These are real TCP protocol clients, **not** new native Fabric/NeoForge lifecycle
tests. Earlier native transport evidence remains in the
[native report](NATIVE_PAPER_REGRESSION_2026-10-11.md).

## Test Tuple

| Component | Exact value |
| --- | --- |
| Minecraft / server | Paper 1.21.11 build 132, commit `c5eb079` |
| WorldEdit | Official Bukkit 7.4.2; no FAWE/protection/proxy plugins |
| Java | Eclipse Temurin 21.0.11+10-LTS |
| Protocol client | `minecraft-protocol` 1.68.0, Node 24.21.0 |
| Server resources | Two active processors, 256 MiB initial / 1 GiB maximum heap, BelowNormal |
| Host | Windows, Intel Core i5-13420H, 12 logical processors; shared interactive laptop |
| Network / identity | 127.0.0.1:25579, offline fixture identities, no OP |
| Permissions | Explicit own-selection bridge and WorldEdit selection grants |

Paper SHA-256: `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba`.
WorldEdit SHA-256: `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06`.
Corrected companion SHA-256: `97b60640cdc7e32dd83f3f0f792c32c8bc4c25f41428a8c13b5709686d3b4f7e`.
Test fixture SHA-256: `f281690d5a3999d279f0d0093a4a3ba63fc3512e6e7ac31c41cb7fa35fcfb257`.
The fixture/monitoring code is excluded from production artifacts.

## Executed Load Experiment

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 '<jdk21>' -Players 16 -Samples 600 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs
```

Four server starts, **64 total joins**, 16 simultaneous stationary players per
start. Order: absent -> installed -> installed -> absent. Every start used the
same disposable flat world and view/simulation distance 2. World state continued
ticking/saving between starts; it was not restored to a bit-identical snapshot.
Each installed run measured silent connections, negotiated idle, one requester,
all requesters, flood and recovery. Each of the 14 windows contains 600 tick
samples (8,400 total), approximately 30 seconds. Join warm-up: 15 seconds; each
window has a further five-second settling interval.

Ordinary requesters target 2/s; in flood, fifteen peers send four requests per
50 ms scheduling interval while the first remains ordinary. Actual send counts,
not nominal timers, are saved. All responses assert the connection's distinct
expected selection, epoch, monotonically increasing ID, height and grants.
This is metadata retrieval, not block capture, simulation or clipboard transfer.

Observed in **each** installed run:

- Silent/idle/recovery: zero metadata reads, replies and application reply bytes
  during the window. Hello is outside idle measurements and performs zero reads.
- One requester: 57 reads. All sixteen: 912 reads. Flood: 1,857 admitted reads;
  27,300 / 27,180 additional frames throttled in the two runs.
- The ordinary flood requester received all 57 of its 57 requests. Each other
  peer received 120 replies; no cross-player bounds or malformed replies appeared.
- Flood replies total 118,848 application bytes per window. Per-player throttling
  was exercised; 16 sustained 4/s peers do **not** saturate the default global
  100/s quota. Aggregate saturation is covered separately by deterministic tests.
- Zero chunk loads and zero generated chunks in all measured windows, including
  selections extending outside the players' received terrain.
- Connection count returned to zero after all sixteen disconnected.

## Timing And Allocation Observations

Ranges below are the two runs' separate percentiles, **not** pooled percentiles
or confidence intervals. CPU/allocation columns are totals for the owning server
thread, not exact bridge attribution. Allocation is approximate allocation rate,
not retained heap. JFR and fixture instrumentation run in both conditions.

| Condition | p95 MSPT | p99 MSPT | Owner CPU ms/s | Owner allocation MiB/s |
| --- | --- | --- | --- | --- |
| Bridge absent | 4.779..8.439 | 6.819..14.569 | 33.918..34.966 | 4.599..5.864 |
| Installed, silent | 5.397..7.309 | 13.895..14.629 | 38.617..47.480 | 5.892..7.655 |
| Negotiated idle | 5.579..7.035 | 14.427..14.791 | 32.869..39.129 | 5.842..6.317 |
| One requester | 5.874..6.818 | 10.809..14.449 | 32.347..34.432 | 5.884..5.965 |
| Sixteen requesters | 5.455..7.852 | 10.395..15.404 | 33.388..45.388 | 5.953..6.046 |
| Flood | 4.597..6.366 | 11.569..17.777 | 36.515..39.650 | 6.175..6.356 |
| Recovery | 6.238..7.601 | 11.849..14.870 | 33.388..37.562 | 5.828..6.294 |

Paired installed-silent minus absent deltas: p95 **+0.618 / -1.131 ms**;
p99 **+7.811 / -0.674 ms**. The baseline itself varies considerably. One
installed-silent tick reached **134.425 ms** despite zero admitted bridge work
in that window. It is **not attributed** to a particular cause by this pilot.
The longest recorded VM operation in that interval was 15.482 ms, insufficient
to explain the whole outlier. A shared laptop and evolving world/JIT/cache state
make causal or production-tail claims inappropriate. No release budgets or
default quotas were raised/approved from these observations.

All four bounded JFR recordings were read through Java's streaming
`RecordingFile` API, using exact measurement start/end instants and excluding
fixture classes from bridge attribution. Bridge-stack execution samples:
one/many/flood **0/1/3** in the first installed run, **0/0/3** in the second;
silent/idle/recovery **0** in both. Those small sample counts do not prove zero
overhead. Allocation samples/weights are estimates, not exact call costs.
Recorded GC cycle duration can include concurrent work; it is not a GC pause
or tick stall. Full profiles and private process metadata remain local.

## Other Verification And Remaining Gates

- **33 pure tests pass on each of Java 21 and Java 25:** protocol 7, client 9,
  server 13 and token bucket 4. New tests cover cached state invalidation and
  1,000 resets without admission refill or starving a second connection.
- Four Node wire-helper checks pass; fixed framing matches the Java golden
  protocol, signed coordinates/large IDs and truncated replies are covered.
- Paper artifact checks pass: Java 21, license, no provided/client/fixture classes.
- No desktop, cursor capture, native game window or OS input was used.
- No client source changed; the published Fabric/NeoForge 1.8.0 archive hashes
  still match the previous release record. No fix release was silently replaced.

This completes a **foundation lifecycle/load pilot**, not Gate 6 release
acceptance. Remaining: controlled busy gameplay and pressure/backoff experiments,
longer independent baselines with frozen initial world state, slow receivers,
native respawn/dimension/backend recovery and explicit re-negotiation, 26.x and
mod-server hosts, protection integrations, and the unimplemented remote
selection/clipboard/simulation features. In particular this wire fixture sends
a fresh hello explicitly after respawn; automatic native-client recovery is not
established by that assertion. Keep remote editing gated until it is verified.

## Local Evidence

- `build/bridge-paper-proof/wire-proof.json`, `wire-server.log` (final positive).
- `build/bridge-paper-proof/lifecycle-old-epoch-negative.log` (old companion).
- `build/bridge-load-proof/load-proof.json`, `load-analysis.json`.
- `build/bridge-load-proof/measurements/<run>/<phase>.json` (all raw tick samples).
- `build/bridge-load-proof/<run>.log`, `<run>.jfr`, `<run>-jfr-summary.txt`.
- `build/bridge-validation/unit-java21.log`, `unit-java25.log`; module JUnit XML.

Primary API/measurement references: [source entries S33-S35](SOURCES.md#s33-paper-lifecycle-events).
No logs, generated worlds, recordings or local dependency caches are committed.

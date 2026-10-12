# 32-Player Restart And Admission Proof

Date: **2026-10-12**. Production-source baseline: `fa258b7`. Companion
**0.1.0-dev**, application protocol **1.1**, optional restart hint **1**.
This cycle adds test tools and evidence, not a production feature or release.
Published client **1.8.0** remains unchanged.

## Executed Scope

Pinned Paper **1.21.11-132**, commit `c5eb079`; official WorldEdit **7.4.2**;
Temurin **21.0.11+10**; Node **24.21.0** and `minecraft-protocol` **1.68.0**.
Two installed and two absent runs in **absent/installed/installed/absent** order.
Each has 32 stationary, offline loopback protocol clients with distinct
selections and test-only permission attachments; no OP or protection plugins.
These are not native Fabric/NeoForge clients or busy gameplay sessions.

Paper runs headlessly at BelowNormal priority, two logical CPUs, 1 GiB heap,
with bounded 32 MiB JFR recordings. Only `127.0.0.1:25579` is bound. The owner's
previous EULA acceptance applies to this disposable fixture. No window, desktop
switch, mouse capture or OS input occurs. All four owned servers stop normally;
the listening port is free before proof publication.

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 300 -Repetitions 2 -RestartLoad -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs restart
```

Results live separately in `build/bridge-restart-load-proof/`. The earlier
load pilot and frozen-world baseline are not overwritten or relabelled.
This workload does **not** restore byte-identical frozen worlds or disable all
random gameplay; the comparison is descriptive, not cost acceptance.

## Correctness And Traffic

**14 measurement windows / 4,200 tick samples** pass the existing acceptance
assertions. Installed runs cover silent connections, negotiated idle, one
requester, 32 requesters, 31 flooding peers plus an ordinary requester, and idle
recovery. Each window has zero chunk loads and zero generated chunks. Silent,
idle and recovery windows have zero metadata reads, replies and application
reply bytes. Disconnect leaves zero tracked peers.

| Flood window | Requests sent | Admitted reads | Throttled | Ordinary replies/sent | Application reply bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| First installed | 30,161 | 1,523 | 28,638 | 29/29 | 97,472 |
| Second installed | 30,285 | 1,498 | 28,787 | 29/29 | 95,872 |

Each flood window lasts about 14.95 seconds. The aggregate admission assertion
retains the existing 100/s + burst-100 envelope with measurement-boundary slack;
malformed frames remain zero. Every received reply is checked against its own
player's bounds/epoch/request order. Server and peer counters are collected at
different window boundaries, so total replies received need not equal reads.
The 90% ordinary-progress gate is **not weakened**; both runs observed 100%.
This schedule is not proof of fairness under every adversarial timing pattern.
Rate admission is not a per-tick CPU budget or a fair scheduler; see
[S43](SOURCES.md#s43-admission-is-not-fair-scheduling).

Both installed runs then perform **three graceful disable/enable cycles** each,
with all 32 already-online peers recovering concurrently. Flood requests have
stopped and settled before these cycles; this is **not** restart during ongoing
abuse or a burst exceeding the global allowance.

- All 32 peers receive exactly one matching close per cycle and clear old state.
- Every enable sends exactly **32 hints / 704 application bytes**, one fresh
  generation, followed by 32 fresh nonce/epoch handshakes. Zero throttling and
  zero metadata reads occur during negotiation.
- A batch of 32 previous-epoch requests performs zero reads on the new core.
- A batch of 32 explicit current-epoch requests returns only each player's own
  selection. The new core records 32 reads / 64 replies, including the hellos.
- The subsequent quiet interval leaves every diagnostic counter unchanged.

Observed end-to-end recovery times: **86.9, 92.8, 61.9 ms** and
**93.1, 93.2, 92.9 ms**. These include console commands and 25 ms polling,
not an isolated server CPU slice or an Internet latency promise. The separately
[verified native client state machine](BRIDGE_RESTART_RECOVERY_2026-10-12.md)
is not replaced by this harness's hand-driven protocol behavior.

## Performance Is Not Accepted Yet

| Window | p95 MSPT, first/second | p99 MSPT, first/second |
| --- | --- | --- |
| Absent, silent | 6.312 / 4.578 | 11.919 / 10.904 |
| Installed, silent | 4.312 / 5.391 | 13.140 / 8.161 |
| Installed, idle | 11.590 / 5.806 | 18.650 / 7.700 |
| One requester | 17.390 / 12.065 | 31.936 / 19.721 |
| Many requesters | 16.074 / 5.288 | 28.379 / 8.785 |
| Flood | 18.168 / 4.881 | 31.561 / 8.214 |
| Recovery idle | 17.711 / 5.232 | 28.340 / 7.017 |

The spread is substantial on this shared i5-13420H laptop. Single long ticks
include **136.030 ms** in installed silent, **162.306 ms** in installed idle,
and **123.744 ms** in absent silent. No attribution is established for them.
The fixture records durations but not per-tick wall timestamps, so window-level
JFR cannot correlate an individual tick conclusively with an event.

JFR records zero sampled bridge stacks in silent/idle/recovery windows. Active
many/flood windows have only 1-2 bridge execution samples each: too few to infer
exact CPU cost. Sampled allocation weights are estimates, not exact allocation
counts. The largest GC durations in the three long-tick windows are about
293-319 ms, but their largest **stop-the-world pauses** are only 13.339, 15.066
and 16.060 ms. Concurrent GC duration must not be called a pause or used to
explain those ticks. Normal tick-thread parking is also not evidence of a stall.

No zero-overhead, production-server budget or frame-time claim is made.
Busy gameplay, dedicated hardware, longer controlled warm-up, fair saturated
recovery and exact long-tick attribution remain open release gates.

## Harness Checks And Exact Evidence

The load runner now rejects mixed baseline/restart modes, keeps failed-window
raw measurements with `validated: false`, and writes an explicit failure report
after owned-process cleanup instead of publishing a success proof. The pure
validator also rejects non-finite durations, count mismatches, empty ordinary
workloads and unknown active phases. **22 Node tests pass**, including five
new validation/failed-record checks; PowerShell parsing and mixed-mode guard
pass. All 14 captured windows were rechecked against the stricter validator.
The full 32-player run used the pre-extraction inline validator; it is not
claimed to have executed the later diagnostic helper. The subsequent offline
rerun passes 50 Java tests on each of Java 21 and 25 plus all 22 Node tests.

Local proof: `build/bridge-restart-load-proof/load-proof.json`,
**2026-10-12T13:09:21.043Z**. Window JSON under `measurements/`; four server logs,
JFR recordings and summaries are adjacent. Analysis: `load-analysis.json`.
Only this reviewed report and test tools are committed, not generated worlds,
logs, identities or recordings.

| Tested artifact | SHA-256 |
| --- | --- |
| Companion | `c1472fab548e18341de69621c75cfae56e66bd131acd344613bb01c3a889ad04` |
| Test-only fixture | `401e1ea16850df552494d817c19b2b7933631c3a140ac14364da2d00bac9b576` |
| Paper | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |

Production code, native clients and the published 1.8.0 artifacts/tag are
unchanged in this stress-only cycle. Other server versions/loaders, proxies,
FAWE/Folia, remote editing, clipboard streaming and command simulation retain
their previous unimplemented/unverified status. This is not a release approval.

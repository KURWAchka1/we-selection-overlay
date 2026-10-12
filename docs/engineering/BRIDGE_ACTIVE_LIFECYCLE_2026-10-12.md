# Active-Load Lifecycle Verification

Date: **2026-10-12**. Baseline: `e9d6743`; this cycle adds a separate fixture,
harness, validation and evidence. **Production bridge/client sources and JARs
are unchanged.** This is a functional lifecycle test, not production performance
acceptance or a remote-feature release.

## Method

Pinned tuple: Paper **1.21.11-132 / c5eb079**, official WorldEdit
**7.4.2+7450-eb8e82c**, Temurin **21.0.11+10**, Node **24.21.0**,
`minecraft-protocol` **1.68.0**, `minecraft-data` **3.117.0**. The loopback-only
server uses two active processors and a 1 GiB maximum heap on an i5-13420H laptop.
No production profiles, graphical clients, desktop input or cursor capture.

32 real TCP clients move along bounded flight routes; 28 negotiate minor 1 and
four never negotiate. Ordinary requester 00 sends twice/second; 27 other
consumers offer 80 frames/second each. All use the unchanged production limits:
100/s aggregate with burst 100, 4/s per connection with burst 8, one pending
slot per actor, eight visits per soft 0.5 ms slice and 5-second pending deadline.
16 explicit AI cows tick; the fixture alternates exactly 512 cells through a
closed WorldEdit edit session every 20 ticks. Those edits are **fixture work**,
not bridge mutations, and stop before WorldEdit is disabled. Movement/entities
continue after dependency loss.

Each of two fresh server runs has eight 200-tick windows: flood, revocation,
disconnect/reconnect, three companion restarts, idle and WorldEdit disable.
Flood continues across faults for actors with a valid session. A closed session
sends no reads; its one restart hint triggers one fresh hello, without retries.
This is application recovery with available global burst capacity in a new core,
not a guarantee when handshake admission itself is saturated.

The console-only opt-in fixture observes a **real pending wire request for the
target actor** before applying each fault through public permission/player/plugin
APIs. Reflection inspects only our own core fields in the separate test JAR;
no synthetic request is injected, production API/hook added or internal Paper
event constructed. Snapshots bracket each action on the owning thread. Captured
request IDs correlate queued revocation with the actual DENIED reply.

The gates require exact resource cleanup, no read during the fault, no protected
reply after revocation, fresh session epochs, own-player bounds, consumer progress,
ordinary latency <=2 seconds, no quiet-player bridge data, ongoing movement,
entity lifetime progress, exact edit counts and zero chunk loads/generation.
Whole-cycle validation also requires every fault timestamp inside its measured
gameplay window. Negative tests reject incomplete evidence and stopped workloads.
See [primary API/state references](SOURCES.md#s48-queued-lifecycle-faults-and-test-client-states).

## Failed Attempt Preserved

The first attempt completed round 1, then stopped on round 2's reconnect before
the player joined: Paper reported failure decoding `client_information`.
No successful whole-proof file was published for that attempt. Its failure JSON,
both logs/JFR files and measurements are retained under
`build/bridge-active-lifecycle-proof/attempt-1-pre-play-movement/`.

Inspection exposed a real harness race: the replacement peer entered the movement
timer's array before configuration/login completed. The old timer checked only
`ended`, so it could send a play position packet in a non-play protocol state.
Movement now requires both a received play-login packet and current `play` state;
a deterministic negative test covers initial/configuration/reconfiguration/end
states. The exact malformed bytes of the failed attempt were not captured, so
its decoder error is **not** proof of that exact wire cause or a bridge defect.
Both full subsequent runs pass; the earlier failure is not discarded or relabelled.

## Observed Results

Completed **2026-10-12T16:23:16.336Z**. Both full runs pass:

- **3,200** measured ticks with complete, validated JFR tick markers.
- **25,081** server-observed movements; every actor progresses in every window,
  including disconnect/reconnect and the four non-consumers.
- **71,680** changed cells through 140 closed WorldEdit batches. Both final
  dependency-disabled windows perform zero batches; entities/players still tick.
- Zero measured chunk loads/generation. Captured core snapshots show no malformed
  bridge frames or queue expiry.
- Each captured revocation returns DENIED for that queued ID with no bounds;
  regrant requires a new hello. Each disconnect removes the actor and exactly
  one pending slot. Companion/dependency disable cancels every captured pending
  slot and clears all core connections without an intervening selection read.
- Each of six companion restarts emits 28 hints / **616 bytes**, and each peer
  sends exactly one restart hello. Nonces/epochs differ; every renewed requester
  receives its own bounds. CUI still responds while the companion is stopped.
- Old-epoch wire replays produce no replies; the unit and earlier wire suites
  provide the strict stale-token checks. This flood experiment does not prove
  every replay passed admission, so silence alone is not a decode-path proof.
- Two negotiated-idle windows leave all bridge diagnostics unchanged, with
  zero pending slots and zero dispatch tasks despite ongoing world workload.
- WorldEdit loss closes all negotiated sessions. Re-enabling the bridge without
  its enabled dependency fails closed, registers no resources and sends no hints.
- All owned servers shut down normally; the loopback port is free afterward.

### Recovery And Timing

| Run | Restart 1 | Restart 2 | Restart 3 | Maximum ordinary reply latency |
| --- | ---: | ---: | ---: | ---: |
| 1 | 284.7 ms | 274.7 ms | 264.3 ms | 263.7 ms |
| 2 | 314.5 ms | 281.0 ms | 287.9 ms | 275.4 ms |

Restart latency runs from the enable console action to all fresh welcomes,
not from the preceding deliberate stop/downtime. Ordinary latency excludes
requests abandoned by session close; per-window sent/received counts are retained
and allow one close loss plus measurement-boundary tolerance. All active consumers
progress. These are bounded headless observations, not an SLA.

No measured tick exceeds 50 ms. Largest ticks are **40.9696 ms** and **36.3057 ms**,
both in reconnect windows. Their public-event brackets are only 1.9968/1.9361 ms;
the inferred intervals show no GC pause or bridge execution sample. They remain
**unattributed**, not proven to be bridge-free or caused by the OS. A separate
15.449 ms tick overlaps a 12.0595 ms GC pause; this does not explain the maxima.

Captured dispatcher high-water marks reach **12.0926 ms** / **4.9848 ms**, exceeding
the soft 0.5 ms target. Those cumulative marks can include warmup, GC or a single
non-preemptible host call; this experiment lacks per-slice causal markers.
No claim of a hard slice ceiling or acceptable production overhead follows.
There is no absent/installed paired baseline in this fault-oriented run, and
CPU/allocation measurements include fixture/gameplay/lifecycle work.

## Reproduction And Regression

After preparing the pinned Paper/WorldEdit/wire dependencies and accepting EULA:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -LifecycleLoad -Players 32 -Samples 200 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-active-lifecycle.mjs
.\tools\test-server-bridge.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -SkipNativeCodecs -Offline
```

Raw artifacts remain under ignored `build/bridge-active-lifecycle-proof/`:
`lifecycle-proof.json`, `lifecycle-analysis.json`, `round-{1,2}.log/.jfr`,
`measurements/round-{1,2}/`, plus the preserved failed attempt. Analysis revalidates
the entire cycle and exact saved measurements before accepting all 3,200 markers.
Logs/worlds/profiles are not committed or uploaded. Earlier frozen-world proofs
are not overwritten.

Regression: **68** JUnit tests per JVM (Java 21 and 25), **36** general Node harness
tests, four actual-recording JFR tests and twelve deterministic admission-order
cases per JVM pass. The companion package guard passes.
The fresh **17-group Paper wire regression** also passes with the new fixture,
including legacy/minor-1 peers, CUI, context resets and dependency shutdown.
Native codec/gameplay matrices were not rerun because no production/client behavior changed; historical
native evidence is not relabelled as this cycle's test.

## Scope And Next Gates

This closes the tested headless queued-revocation/disconnect and graceful active-
load lifecycle scenarios for the exact pinned host. It does not certify native
recovery under this load, ungraceful server loss, backend/proxy switching, arbitrary
saturation, slow heavy receivers, hot-reload support or other host/version tuples.
Resource ownership checks are not a heap-retention proof. Long representative
cost profiling and numerical release acceptance remain open.

Remote selection editing, clipboard streaming and command simulation are still
unimplemented. Next product milestone is server-authoritative selection editing
through the existing client interaction, with separate authority/transaction/native
gates. Published `1.8.0`, its tag and release artifacts remain unchanged.

### Artifact Identity

| Artifact | SHA-256 |
| --- | --- |
| Paper | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| Official WorldEdit | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Unchanged companion | `be5a082a6b63a8612c993e7dc52c7b0ae498b7249ab5513e72844ced1e1572c7` |
| New test-only fixture | `23cc20e30820b2b8f1483668c92805dc64ee2569fb2a572ff2503dfdfb6a616f` |

# Bounded Fair Metadata Dispatch

Date: **2026-10-12**. Starting commit: `1f218d5d0ac4aad3d6709c496edf17c8b0ed828d`.
Candidate: experimental companion **0.1.0-dev**, application protocol **1.1**.
Only the experimental server core/host behavior changes. Client production
sources, development client JARs, published 1.8.0 archives and its tag are unchanged.
This is not a public release or representative busy-server cost acceptance.

## Failure And Decision

The [previous research](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md#adversarial-admission-ordering)
reproduced **0/60 ordinary replies** when 31 conforming senders took global
tokens before the ordinary sender. Keeping that global admission check ahead
of a queue would preserve the failure. Per-player limits alone are insufficient.

The replacement has one pending frame per actual connection, ordered by first
acceptance. Extra frames cannot replace the pending request or advance that
actor. After service removes its slot, that actor can queue again only at the
tail. The global bucket is consumed at service, before decoding or WorldEdit
access. Connection identity still comes from the platform, never a payload UUID.
No priority bypass, worker thread or copied network scheduler was introduced.

## Bounds And Lifecycle

| Policy | Experimental default |
| --- | --- |
| Frame payload | 6..256 bytes, one defensive copy per occupied slot |
| Per-player ingress | 4 frames/s, burst 8; extra traffic cannot replace a slot |
| Tracked connections / pending slots | At most 1024 each |
| Aggregate decode/service | 100 frames/s, burst 100 |
| Owning-thread slice | At most 8 visits, cooperative 0.5 ms target |
| Frame service eligibility | Discard when visited after waiting >= 5 seconds |
| Existing client timeout | 10 seconds, no automatic retry |
| Paper dispatch tasks | One while nonempty; zero when idle or disabled |

At default connection/frame bounds, pending **payloads** use at most 256 KiB;
peer, array and map overhead is additional. Ingress copies are metered per player
and bounded by storage, not by the global 100/s decode/read budget. This is a
deliberate admission/service split, not a claim of unchanged CPU cost.

The host uses public synchronous
[BukkitScheduler.runTaskTimer](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitScheduler.html#runTaskTimer(org.bukkit.plugin.Plugin,java.lang.Runnable,long,long))
at delay/period 1 tick and retains a single
[BukkitTask](https://jd.papermc.io/paper/1.21.11/org/bukkit/scheduler/BukkitTask.html)
handle. It cancels that handle on empty queue or teardown. Source interpretation
and the distinction between API guarantees and our policy are recorded in
[S46](SOURCES.md#s46-owning-thread-fair-dispatch).

No WorldEdit call occurs at enqueue. Permissions/context are checked when
actually serviced, before cached replies and again after a host read. Reentrant
context reset cannot publish/cache an old epoch. Disconnect, context reset,
channel loss and shutdown cancel owned pending frames without refilling a
still-open connection's budget. Shutdown notifies only already negotiated,
opt-in peers; an unprocessed hello receives no invented notification.

The time target is **not preemption**: one external call or JVM pause may exceed
0.5 ms. Expiry is a service-eligibility deadline, not a hard reclamation timer
during a stalled server. Full saturation may still end in the existing client
timeout/CUI fallback; no busy opcode, retry loop or wire-format change was added.

## Pure And Harness Checks

Executed on Temurin **21.0.11+10-LTS** and **25.0.2+10-LTS**:

- **68 JUnit tests per JVM:** protocol 11, server core 36, shared client 21.
- **16 new core dispatch tests:** defensive copy/one-slot ordering, visit/time
  limits, idle clock/host silence, queued and during-read revocation, reentrant
  context loss, cancellation/shutdown, expiry, 1024-slot bounds, retained quotas,
  send-failure cleanup, recursion/wrong-thread rejection and invalid limits.
- Two additional actual shared-client/core tests verify deferred correlated
  negotiation and expired queued hello falling back at the existing timeout.
- **25 general Node harness tests**; ordered latency/progress and queue/idle-task
  assertions have negative tests. Failed windows retain unaccepted raw data.
- **4 actual-recording JFR reader tests per JVM**.
- Paper packaging verifies Java 21 bytecode, license/metadata and absence of
  bundled provided/client/test dependencies.

Command:

```powershell
.\tools\test-server-bridge.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Offline -SkipNativeCodecs
```

The six unchanged native codecs and client packagers were **not rebuilt in this
cycle**; their earlier evidence is not presented as a new run. Actual native
client compatibility with the changed server is verified separately below.

### Adversarial Ordering

The stricter `AdmissionOrderingProbe` runs twelve cases per JVM: 1/31 other
senders, ordinary-first/last, and ordinary offsets 0/50/200 ms. A synthetic
50 ms tick clock controls service. Every case asserts **60/60 ordinary replies**,
max wait <= 500 ms, bounded pending state, zero expirations and aggregate quota.
With one other sender, all orders/offsets wait 0 ms in this model. With 31:

| Ordinary offset | First: max wait | Last: max wait |
| --- | --- | --- |
| 0 ms | 50 ms | 300 ms |
| 50 ms | 250 ms | 250 ms |
| 200 ms | 100 ms | 100 ms |

Both JVMs produced the same results. This closes the reproduced starvation case
and these tested offsets, not every possible saturation pattern. Synthetic time
is not CPU time, real network latency or a direct performance comparison with
the previous immediate-dispatch model.

## Real Paper Fair-Service Proof

Pinned Paper **1.21.11-132 / c5eb079**, official WorldEdit **7.4.2**, Java 21,
`minecraft-protocol@1.68.0`, 32 stationary loopback identities, no OP/protection
plugins. Server: two processors, 1 GiB heap, BelowNormal priority; JFR in every
comparison. No user world, native window or system-input control in this fixture.

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 200 -Repetitions 2 -FairDispatch -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs fair
```

All **18 windows / 3,600 sampled ticks** passed correctness/resource assertions.
Comparisons ran absent/installed, then installed/absent. Installed phases are
silent, negotiated idle, one/many requesters, flood, ordered-first/last, recovery.
This fair fixture is **not the earlier frozen-world baseline**: natural entity
activity remains a confound, and no busy-server regression threshold is accepted.

- Many: each of 32 players received 19 replies in each measured window.
- Flood: 31 actors attempt 80/s each; the ordinary actor attempts 2/s. It received
  19 replies in each measured window, max latency 317.982 / 320.909 ms.
- Ordered cases use 4/s other actors and 2/s ordinary; every active actor progresses.
  Ordinary-first received 19 / 19 replies, max latency 312.665 / 275.497 ms.
  Ordinary-last received 20 / 19, max latency 349.643 / 334.098 ms.
- Each saturated measured phase admitted 1,010 frames, within the unchanged
  aggregate bound. Flood rejected 19,107 / 19,231 frames; no malformed decode.
- Observed peak pending slots: 32. No request expiry in either installed run.
  Max observed queue wait: 348.674 / 349.176 ms, cumulative per server process.
- Silent/idle/recovery: zero selection reads/replies/bytes and zero dispatch tasks.
  Disconnect leaves zero peers, zero pending frames and zero dispatch tasks.
- Zero measured chunk loads/generation. No block scans/simulation/clipboard work.

Separate TCP sockets cannot guarantee server arrival order. Timer ordering is
only a network workload; the deterministic probe proves the exact adversarial
ordering. FIFO slot fairness also does not promise equal reply totals for actors
with different arrival timing or extra requests rejected while their slot is full.

### Cost Observations

MSPT below is Paper's reported duration. CPU/allocation totals cover the whole
owner thread, not only bridge calls. They are measurements, **not acceptance**.

| Run / phase | p95 ms | p99 ms | Max ms | Owner CPU ms/s | Owner MiB/s |
| --- | --- | --- | --- | --- | --- |
| 1 absent / silent | 5.3107 | 6.4657 | 12.2691 | 35.95 | 6.400 |
| 1 installed / silent | 5.4272 | 8.6105 | 8.8987 | 35.95 | 7.343 |
| 1 installed / idle | 5.2232 | 6.4043 | 128.7745 | 42.18 | 12.657 |
| 1 installed / one | 5.0636 | 6.2841 | 8.3390 | 20.31 | 6.978 |
| 1 installed / many | 5.0540 | 6.1527 | 11.9174 | 31.25 | 7.070 |
| 1 installed / flood | 4.3033 | 5.2289 | 11.6554 | 21.88 | 6.980 |
| 1 installed / ordered-first | 5.5824 | 14.6525 | 33.2637 | 34.37 | 7.495 |
| 1 installed / ordered-last | 5.2196 | 7.8114 | 8.6953 | 29.69 | 7.043 |
| 1 installed / recovery | 5.0936 | 6.0737 | 10.0401 | 26.56 | 6.929 |
| 2 installed / silent | 6.2002 | 7.6214 | 9.9710 | 46.89 | 7.387 |
| 2 installed / idle | 6.3775 | 7.3754 | 14.0222 | 42.19 | 7.268 |
| 2 installed / one | 5.8860 | 8.5752 | 12.9116 | 45.33 | 6.961 |
| 2 installed / many | 6.5742 | 9.8463 | 12.9778 | 31.25 | 7.616 |
| 2 installed / flood | 4.4879 | 5.9501 | 19.0472 | 26.57 | 7.673 |
| 2 installed / ordered-first | 6.1880 | 7.6793 | 10.7712 | 37.51 | 7.063 |
| 2 installed / ordered-last | 5.8616 | 8.2977 | 15.2169 | 37.49 | 7.788 |
| 2 installed / recovery | 5.3733 | 6.4421 | 8.8857 | 53.13 | 7.550 |
| 2 absent / silent | 7.1285 | 9.3127 | 11.9487 | 51.59 | 7.289 |

The silent p95 installed-minus-absent deltas are **+0.1165 / -0.9283 ms**;
p99 deltas **+2.1448 / -1.6913 ms**. Baseline spread on this shared laptop
precludes inferring a stable per-call cost or no regression from those deltas.

Cumulative maximum dispatch slice was **5.1181 / 4.9356 ms**, established during
negotiation before measured idle and unchanged in later active phases. That is
direct evidence against treating the 0.5 ms target as a hard cap; its precise
cold-call/JIT cost is not attributed by these counters.

The existing two-pass JFR reader verified all 3,600 markers and 90 top-five
outlier records. Run 1 idle sample 109 lasted 128.7745 ms, with no overlapping
top-level GC pause. One owner-thread execution sample at
`2026-10-12T17:15:57.154427+03:00` shows
`CraftBlockData.<clinit> -> reloadCache -> ConcurrentHashMap.transfer`, reached
through `EatBlockGoal.tick` and `CraftEventFactory.callEntityChangeBlockEvent`.
The marker records 57,126,168 allocated bytes in that bracket. This is evidence
of a non-bridge cold initialization path, not complete causal accounting of the
128 ms tick. Bridge reads/replies/dispatch counters stayed quiet throughout idle.
The 33.2637 ms ordered-first outlier has no overlapping GC pause or owner-thread
execution sample, so it remains unattributed. Sparse stack/allocation sampling
must not be converted into zero cost or exact call allocations.

## Wire And Actual Native Compatibility

`tools/test-paper-bridge.ps1` passed **17 real wire check groups**, including
ownership, duplicate caching/revocation, same-world respawn, reconnect,
minor-zero compatibility, three companion disable/enable cycles, opt-in hints,
resource cleanup and rejection of re-enable with disabled WorldEdit.

`tools/test-native-paper-bridge.ps1` then passed **six real Minecraft runs**:
allowed/denied/absent for Fabric and NeoForge 1.21.11, with server WorldEdit only.
Allowed cases also pass respawn, Nether/Overworld, channel re-advertisement,
same-process reconnect, negotiated stop and one-shot restart. Denied clients
stay quiet after restart; all scenarios preserve CUI and disconnect cleanup.
Private-desktop/cursor-agent preflight passed on both native LWJGL versions;
the user desktop was never switched and system cursor confinement stayed unchanged.

## Artifacts And Evidence

### Restart Fixture Regression

An additional 32-peer restart run initially **failed** at `bridgeactive` after
re-enable. The test-only resource assertion still required zero scheduled tasks
even while fresh hellos occupied the new queue. Its server log reports
`Bridge resource ownership mismatch: active=true, listeners=5`; no successful
load proof was published for that attempt. Failure JSON, server log and recording
are preserved under
`build/bridge-restart-load-proof/failures/20261012-stale-idle-assertion/`.

The fixture now checks the exact owned dispatch handle, synchronous/not-cancelled
status, queue/task correspondence, no extra tasks/workers, and zero resources
after disable. It does not simply permit arbitrary background jobs. This is a
test-fixture correction, not another change to the companion or client protocol.
The separately rebuilt fixture SHA-256 is
`883cadca1c54465884785277c082f5f4f96f55e0dcd56a0d3b9545cb9bf907d5`;
the initial fair/wire/native runs used the original fixture hash.

The corrected fixture then passed a fresh **32-peer / three-restart** run:

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk-21' -Players 32 -Samples 100 -Repetitions 1 -RestartLoad -AcceptEula
```

Seven measurement windows / 700 ticks passed. All 32 actors negotiated a fresh
epoch once per cycle in **311.909 / 211.356 / 211.248 ms**, with exactly 704 hint
bytes per cycle, zero implicit metadata reads, zero throttled/expired hellos,
rejection of old epochs and one explicit own-selection read per actor. Subsequent
idle counters stayed unchanged; disconnect released every pending slot/task.
At each immediate `bridgeactive` check the fixture observed pending=24 and exactly
one synchronous dispatch task; each disabled check observed pending=0/tasks=0.
The new evidence is `build/bridge-restart-load-proof/load-proof.json` and its
server logs. This repeats settled-traffic recovery once, not sustained-flood
restart or a repeated performance-baseline acceptance.

After this fixture correction, the full Java 21/25 offline regression was rerun
successfully, followed by another **17-group real Paper wire proof**, including
disabled-dependency rejection and zero retained task/core/channel resources.
The latest `build/bridge-paper-proof/wire-proof.json` therefore records the
corrected fixture hash, while the native and fair-load proofs retain the original.

### Tested Hashes

| Artifact | Bytes / SHA-256 |
| --- | --- |
| Companion | 53,892 / `be5a082a6b63a8612c993e7dc52c7b0ae498b7249ab5513e72844ced1e1572c7` |
| Initial fixture: fair load / native matrix | `1d3e0780a3c5fea9224835c2790799cf38e832e9d5471cb7ef5cf8ff05094da6` |
| Corrected fixture: restart / final wire proof | `883cadca1c54465884785277c082f5f4f96f55e0dcd56a0d3b9545cb9bf907d5` |
| Paper 1.21.11-132 | `5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba` |
| WorldEdit Bukkit 7.4.2 | `0ee152b1be5dfb51500505e2bf5a8c9d66f09c7fa484bf9aea384d7e7b459b06` |
| Unchanged Fabric development client | `cef0cd82a534e630309567270c7f1d53e236b053daed4ac9a5cc00177a166cb7` |
| Unchanged NeoForge development client | `16e1359e98642d579efe774c46a3255a5953abdf55cd4f0ad3502c42806fd322` |

Ignored local evidence:

- `build/bridge-validation/`: both JVM test logs, JFR self-tests and
  `admission-ordering-java21.ndjson` / `admission-ordering-java25.ndjson`.
- `build/bridge-paper-proof/wire-proof.json` and `wire-server.log`.
- `build/bridge-fair-dispatch-proof/`: `load-proof.json`, `load-analysis.json`,
  per-window raw tick/counter/latency files, four JFR recordings and server logs.
- `build/bridge-native-proof/native-proof.json`, six client logs and server log;
  guarded-desktop logs under `build/desktop-tests/`.
- `build/bridge-restart-load-proof/`: corrected-fixture restart proof and preserved
  failed-attempt evidence, distinguished by fixture hash above.

Queue/task/high-water values are snapshots, not work-counter differences. Use
`queueAfter` for absolute values: this run's raw `bridge` deltas also contain
legacy gauge differences, which may be negative and do not represent negative
work. Subsequent harness collection omits gauges from counter deltas.
Do not publish raw generated identities, worlds or profiles as source artifacts.

## Remaining Gates

The narrow starvation counterexample is fixed; no general zero-overhead or
starvation-free guarantee at every configured player count is made. Busy gameplay,
representative CPU/allocation/MSPT acceptance, sustained pressure during restart,
slow consumers and variable-cost capture/simulation scheduling remain separate
gates. Other server versions/hosts, FAWE, Folia, proxies and translators are not
newly supported. Remote editing, clipboard transfer and server command simulation
are still unimplemented. See [verification requirements](SERVER_BRIDGE_VERIFICATION.md).

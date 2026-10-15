# Exact 26.2 Moving Gameplay Comparison

Date: **2026-10-15**. Source baseline: `df16ccf` plus this test-only change.
Status: **bounded moving-metadata workload and independent evidence checks pass;
production cost/release acceptance remains open**.

## Purpose And Method

The preceding profiler-control study closes its bounded observer question, not
representative workload or production cost acceptance. This distinct stage
ports the existing 1.21.11 mixed-gameplay comparison to exact **Paper 26.2-133 /
58e6e45 / WorldEdit 7.4.5 / Temurin 25.0.2+10**. No host implementation is patched.
Production companion and all player JARs remain unchanged.

- Four fresh measured server JVMs: absent/installed, then installed/absent.
- One separate priming JVM, then the complete stopped modern world tree restored
  from identical frozen bytes before every measured run, including player data.
  The external WorldEdit plugin-session directory is not part of that snapshot;
  fixture joins establish and verify each actor's independent cuboid.
- 32 headless moving players; 28 potential metadata consumers and four CUI-only
  non-consumers. These are real TCP clients, not Fabric/NeoForge GPU clients.
- 16 live AI cows; actual closed WorldEdit sessions alternate exactly 512 cells
  between stone and dirt every 20 ticks. This is fixture workload, not a new
  bridge block-edit feature. Production bridge metadata does not write blocks.
- Six matched windows per JVM: silent, negotiated idle, one reader, many readers,
  flood, recovery. Each is 400 ticks, after 15 seconds initial warmup and five
  seconds settling per phase. Warmup is not proof of JVM steady state.
- Same 256 MiB initial / 1 GiB maximum heap, two active processors, below-normal
  priority, loopback-only fixture and fixed server configuration in every arm.
- Spark disabled through the same normal pinned Paper configuration in both
  arms; retained input and full effective configurations are compared offline.
  Actual recorded sampler callers must agree. This is diagnostic policy, not
  advice to disable profiling on production servers.
- Same profile-derived JFR settings in every arm, with VM-operation and
  safepoint-begin thresholds at zero. No method agent. Raw tick markers and
  outliers are retained; no competing test suite or analysis during measurement.

This is a fixed AB/BA local comparison, not randomized independent replication.
AI choices, packet scheduling, thermal/OS effects and ordinary shared-laptop
activity remain uncontrolled. The additional helper load in active arms can
also compete for host resources; owner-thread deltas are not isolated method
costs. Thousands of ticks are not thousands of independent experimental units.

## Transport And Evidence Changes

Reuse the exact official MCProtocolLib protocol **776** artifact from the earlier
edit-load study, not a version alias or a new wire implementation. Its public
`ServerboundMovePlayerPosPacket(false, false, x, y, z)` is checked against the
checksum-pinned source JAR. Movement is refused before an initial server
position/teleport acknowledgment, after disconnect, or outside the owned flight
route. Java self-tests include a movement codec round trip and invalid inputs;
Node tests cover readiness, malformed routes, identities and host/mode guards.
Server `PlayerMoveEvent` counters independently verify accepted motion.

The adapter retains each teleport event. Any correction during a measured window
fails the route gate rather than being ignored. Every actor must still receive
ordinary WorldEdit CUI; the four non-consumers must receive no bridge data.
Idle windows must have zero unsolicited bridge frames as well as zero source
reads/replies/bytes/queue tasks. Ordinary active replies must meet the existing
>=90% progress and <=2 second fixture-latency gates; admission and queues retain
their existing bounds. No measured chunk generation or loads are allowed.
Final review moves the new route/frame checks into the shared persisted-window
validator, so rejected windows keep enriched counters with `validated:false`.
Negative tests cover corrections, idle/non-consumer frames, invalid arrays and
that failure-evidence ordering. Final offline analysis reruns those same gates
against the completed series without changing or rerunning measured workloads.

Exact modern snapshots require all three vanilla dimension directories. The
new versioned gameplay root does not broaden old layouts or accept other hosts.
Both the PowerShell launcher and Node runner reject previous attempts before
overwriting proof artifacts. Prior edit studies retain an added copy of their
original helper classpath; their analyzer verifies the complete recorded file
list and every SHA-256 against that copy, not a subsequently rebuilt helper.
New gameplay evidence similarly retains its helper classpath beside the proof.

Independent analysis reruns the raw-window gates and percentile calculations,
checks all six phases/order/world digests, transport pins and actor-event counts,
configuration equality and every tick-marker count. CPU/allocation and all
>50 ms ticks must be reported without excluding GC or adverse comparisons.

## Reproduction

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk21' -Java25 'C:\path\to\jdk25' -Minecraft 26.2 -GameplayLoad -WithoutSpark -Players 32 -Samples 400 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-load-proof.mjs gameplay --without-spark 26.2
```

Evidence root: `build/bridge-gameplay-control-proof/26.2/`. Archive previous
attempts before reuse. Raw worlds, logs, helper binaries and JFR stay ignored.
See [S61](SOURCES.md#s61-exact-262-moving-gameplay-transport), the
[earlier matched method](BRIDGE_MATCHED_CONTROL_2026-10-13.md) and
[preceding profiler control](BRIDGE_26_2_PROFILER_CONTROL_2026-10-15.md).

## Remaining Scope

This stage cannot establish native renderer/input behavior, remote clipboard or
command streaming, arbitrary network/proxy/backend faults, other server hosts,
generalized production cost/SLA or independent-machine reproducibility. It is a
bounded moving-metadata comparison; concurrent cuboid mutation has separate
stationary and native evidence, not silently combined concurrent gameplay proof.

## Results

Proof completed **2026-10-15T18:48:35.744Z**. Four measured server JVMs, the
separate priming server and their five helper JVMs stopped normally. No failed
load attempt occurred in this series. Independent raw/JFR analysis passes.

- **24 accepted windows / 9,600 unique verified tick markers**. No reported JFR
  data loss or recorded spark sampler ThreadDump operation. The full effective
  global configurations are equal, not merely their profiler flags.
- **75,104 accepted player movements**; each of 32 actors made 97..98 accepted
  moves per window. No measured teleport/correction, invalid route, chunk load
  or chunk generation. All 16 AI cows advanced in every window.
- **480 actual WorldEdit batches / 245,760 changed cells**. Each matched window
  performed exactly 20 closed 512-cell operations. No bridge cuboid commits or
  block edits were part of this metadata workload.
- CUI remained available to every actor. Four non-consumers received no bridge
  application frame; absent-companion arms received none. Every successful
  metadata response was checked against its own player's canonical bounds.
- Installed silent/idle/recovery windows: zero metadata reads, replies, reply
  bytes, unsolicited bridge frames, pending slots and dispatch tasks.
- Installed lifetime wire reply counts/bytes reconcile exactly with independent
  host counters: **4,080 / 260,700 bytes**, then **4,051 / 258,844 bytes**. These
  include negotiation and unmeasured settling/drain, not just measured windows.
  Final connections, pending slots, dispatch tasks and expired requests are zero.
- All measured ticks were below 50 ms; the maximum was **10.1543 ms**. This is a
  local observation, not a performance guarantee or proof of zero bridge cost.

### Flood And Queue

| Installed run | Metadata reads | Throttled | Ordinary received/sent | Ordinary p95/max ms | Pending at active boundary |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 | 2,049 | 32,425 | 40/40 | 224.528 / 237.680 | 18 |
| 2 | 2,046 | 32,557 | 39/40 | 223.835 / 231.226 | 8 |

The second window is not described as loss-free: boundary counters alone cannot
distinguish an in-flight reply from a missing reply. Both pass the predeclared
>=90%/<=2 second progress gate. All admitted lifetime replies reconcile after
drain. Queue peak is **28**, with one task while active and zero after recovery.
Lifetime maximum wait is **299.735 / 298.050 ms**.

Lifetime dispatcher maxima are **8.1367 / 4.9395 ms**, exceeding the **0.5 ms
soft target**. These cumulative high-water values include cold negotiation and
settling; they are not measurements of every request or isolated active-phase
cost. This uninstrumented series cannot attribute those exact method intervals.
No quota was raised, permission cached, WorldEdit work moved off-thread, or
unfavorable observation removed to make the gate appear passed.

### Paired Cost

Installed minus absent, with whole-owner-thread CPU/allocation, not isolated
bridge costs. CPU is quantized on this Windows host.

| Pair / phase | p95 delta ms | p99 delta ms | CPU delta ms/s | Allocation delta MiB/s |
| --- | ---: | ---: | ---: | ---: |
| 1 / silent | -0.5510 | -1.4544 | -8.596 | -0.197 |
| 1 / idle | -0.2005 | -0.1739 | -9.379 | -0.218 |
| 1 / one | -0.1531 | -0.3681 | -14.060 | -0.159 |
| 1 / many | -0.7295 | -1.9388 | -3.123 | -0.022 |
| 1 / flood | -1.7840 | -0.7697 | +14.062 | +0.547 |
| 1 / recovery | -0.3027 | +0.5888 | -10.151 | -0.200 |
| 2 / silent | -0.6811 | +0.2400 | -11.727 | -0.085 |
| 2 / idle | +0.5659 | +0.8166 | -7.031 | -0.019 |
| 2 / one | +0.1156 | -0.5365 | -7.033 | -0.049 |
| 2 / many | -0.2269 | -0.0241 | -0.780 | +0.078 |
| 2 / flood | -1.1583 | +0.2382 | -7.812 | +0.787 |
| 2 / recovery | +0.5471 | +0.5461 | +2.345 | -0.192 |

In particular, flood allocation increases in both comparisons while CPU delta
changes sign. Negative tick/CPU deltas are not evidence the companion speeds up
Paper. Two pairs on one shared laptop cannot establish a universal overhead/SLA.

The largest tick, run 2 installed/many sample **199**, overlaps **7.7336 ms** GC
and **7.7663 ms** VM operation; these overlapping durations must not be added.
The **10.0685 ms** installed/idle tick has no recorded GC/VM/begin/park/monitor
explanation and zero measured bridge work. It remains unassigned, not omitted
or blamed on the companion from coincidence. No method observer was present;
zero inferred drain events is not evidence that active phases had no drains.

## Pins And Regression

| Evidence | SHA-256 |
| --- | --- |
| Frozen modern starting world | `cc59232cf071f1628927b4cabbfe34c3569b150493d818f133b7866e9485cea9` |
| Normalized effective global config | `0efac7d6db9652c09bfb5d91640cc27f6a3c17e2540eeada6c35580503bdf285` |
| Moving helper application | `d01d84bce41c37f3624919fd024492a75b4f15798e293eac906b88ac32afb669` |
| Earlier retained helper application | `dd7c6a7446f6d841a9a707c39e1f48244f6aff46aea506d13c718cf60c732b64` |
| Identical profile-derived JFC | `983bf6ad275fbe355122f2289999478897a46cea78515bf17b220efb25ec4e8f` |
| Unchanged companion | `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956` |
| Unchanged fixture | `4dee35f5a0d9a66194b7af721cdb01a8000cd0b5f7581ed1759ff8d6549146e2` |

Full Paper/WorldEdit/companion/fixture/JFC and 78 helper-classpath pins are
retained in the proof. Published 1.8.0 and both development player distributions
retain their prior SHA-256 values; no universal player package was rebuilt.

**95 pure/domain tests on each JVM 21/25**, **108 general Node harness tests**,
**12 JFR-reader checks per JVM**, agent application/bootstrap checks, 12
deterministic admission orders per JVM and all **six actual native payload
codecs** pass. The full wrapper completed before the final two new Node groups;
the complete final 108-test Node list then passed separately. Moving-helper
Java 25 guards and movement codec round trip pass. No native GUI/gesture/
command/clipboard-rendering suite was rerun in this test-only cycle.

Final independent analysis passes for this series, the retained legacy matched
gameplay series and all three previous 26.2 edit studies (plain, method observer,
profiler control). Original helper artifacts remain fully hash checked, not
accepted by skipping an integrity assertion. An actual repeat attempt is refused
before Gradle/build/copy; proof, companion, fixture and helper hashes are unchanged
and no server is launched. No owned gameplay JVM or port 25579 listener remains.

## Decision

Close this exact-host moving **metadata** functional gate, not the release
performance gate. No new production defect is established here, so player JARs
and companion behavior remain unchanged. The next distinct workload gate is
concurrent selection mutation while moving, with native behavior kept separate;
do not repeat stationary or metadata-only tests indefinitely. Soft-budget
attribution/acceptance, independent replication, heavy remote features, other
hosts and arbitrary failures remain explicitly open.

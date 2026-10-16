# Concurrent Moving Selection Edits On Paper 26.2

Date: **2026-10-16**. Baseline: `50d1422` plus this test-only change.
Status: **bounded moving-edit functional and raw/JFR reconciliation gates pass;
native concurrent gameplay and production cost acceptance remain open**.
Production companion, fixture and player JARs are unchanged.

## Distinct Question

The [preceding moving-metadata comparison](BRIDGE_26_2_GAMEPLAY_2026-10-15.md)
did not exercise concurrent cuboid mutation. Earlier edit-load tests used
stationary actors. This cycle combines actual accepted player motion, live
entities, independent WorldEdit block work and bridge selection transactions.
It does not silently treat those separate older tests as combined evidence.

Use exact **Paper 26.2-133 / 58e6e45 / WorldEdit 7.4.5+7590-b8dc4c1 /
Temurin 25.0.2+10** with the already pinned MCProtocolLib **776** helper.
No new wire codec, server API, dependency or production optimization is added.
WorldEdit/public-event source checks are recorded in [S62](SOURCES.md#s62-concurrent-moving-selection-edits-and-tick-boundaries).

## Method

- Two fresh measured server JVMs plus one separate priming server. Restore the
  complete stopped modern world tree from identical frozen bytes before each
  measured launch. WorldEdit's external plugin-session directory is not frozen;
  fixture joins establish independent actor cuboids and audits verify empty
  player undo histories. These are repeated installed runs, **not** new matched
  absent-companion cost comparisons.
- 32 moving headless TCP clients: eight editors, eight metadata readers, twelve
  flooding metadata senders and four CUI-only non-consumers. No native window,
  real account, user world, production server or system cursor is involved.
- Every actor follows the existing bounded 200 ms flight route. Server-accepted
  `PlayerMoveEvent` counts independently verify motion, and measured teleport
  corrections fail rather than disappear from evidence.
- Eight editor operation kinds: X/Y/Z translation, X/Y/Z upper-bound expansion,
  X contraction and restoration. Targets are anchored to each actor's original
  fixture cuboid, so loops do not accumulate coordinate drift. Every editor
  must complete at least three of each kind; all successful replies are checked
  against the exact target and the next commit uses the returned revision.
- Ordinary editor/reader pacing is 250 ms; flood actors offer requests every
  80 ms. Existing server limits remain global 100/s burst 100, per-player 4/s
  burst 8, one pending slot per actor, eight visits and a 0.5 ms soft slice target.
- 16 live AI cows plus exactly 512 WorldEdit cell changes every 20 ticks, using
  the existing correctly closed fixture edit sessions. Those are independent
  fixture block edits, **not** bridge selection edits or simulated commands.
- Idle, active and recovered windows are each 400 ticks after five seconds
  settling. Initial movement warmup is 15 seconds. No competing suite or offline
  analysis runs during measurement. Ordinary laptop/OS/thermal activity remains
  uncontrolled; this is not full JVM steady-state or independent replication.
- Same 256 MiB initial / 1 GiB maximum heap, two active processors and below-normal
  process priority. Spark is normally configured off in both runs; complete
  effective configurations and actual sampler absence are reconciled afterwards.
  Same default-derived JFR settings with zero-threshold VM-operation/safepoint-
  begin events in both runs. No method diagnostic agent.

Before measured windows, an actual WorldEdit shift command invalidates editor
0's captured revision and another editor attempts that foreign revision. Both
conflict; valid captures then establish the active window. After active load,
one exact commit is replayed and another editor's edit permission is revoked
before its request. Motion/entities/WorldEdit work continue during these boundary
checks, but competing request workers have stopped. This is **not** a new proof
of revocation in the middle of a saturated active queue; that earlier gate has
separate evidence.

## Independent Evidence

For every actor, compare final actual `LocalSession` bounds from a separate
fixture console read, the last CUI points and the acknowledgment/model state.
All 32 actors receive CUI; the four non-consumers must receive zero bridge frames.
All 32 also run the existing loaded-control-area hash and empty-undo audit.
The fixed hash area is **x=8..24, y=60..74, z=18..30**, disjoint from the workload
edit cube x=4..11, y=66..73, z=4..11. This is a control-area audit, **not** an
exhaustive hash of the world or of every unloaded selected cell.

Retain raw server measurements, enriched windows before validation, all incoming
helper payloads, original helper classpath hashes, full configuration inputs/
effective YAML and independent per-run JSON. Invalid windows remain marked
`validated:false`; later analysis reruns the validators rather than trusting flags.
Proof reuse is refused before build/artifact copies and again in the Node runner.

The offline reader verifies all 2,400 phase/sample markers, raw tick percentiles,
absence of recorded JFR loss/spark sampling, actor identities/roles, all operation
counts, canonical bounds/CUI, actual replay bytes and complete host/wire counters.
The operation counts are independently reconstructed from every successful raw
wire target in its exact planned sequence, excluding the single cached replay.
Counter boundaries are deliberate: the stale selector reaches `host.applyEdit`
and counts as one host commit without an apply; the foreign revision fails
before that boundary. Cached replay and early permission denial also do not call
the host. Thus wire commit replies equal **host commits + 3**, and host commits
equal **applies + 1** in this specific fixture. This is checked from inspected
core code, not by pretending every reply is a second host application.

## Results

Proof completed **2026-10-16T13:14:54.007Z**. Both measured servers, priming
server and both helper JVMs stopped normally. No failed load attempt occurred.

- **1,244 confirmed canonical applies**: 622 per fresh server. Every editor
  completed **77..78** operations, including **9..10 of every operation kind**.
- **2,400 verified ticks / six windows**; no recorded JFR loss or spark sampler.
- **18,816 accepted player movements**, **97..99 per actor per window**. No
  invalid move, measured teleport correction, chunk load or generated chunk.
- **120 closed WorldEdit batches / 61,440 changed cells**. All 16 cows advanced
  throughout every window; exact workload counters remained valid while editing.
- **64 canonical/CUI/control-area/undo audits** pass. Stale/foreign conflicts,
  byte-identical cached replay with no second apply/CUI emission, and denied
  permission with no selection disclosure pass on each server.
- Idle and recovered windows: zero new selection reads, captures, commits,
  applies, replies, unsolicited frames and pending dispatch tasks.
- Queue peak **28**. Final connections/pending/tasks/expired requests are zero.
  Twelve flood actors exercised real throttling while every ordinary actor
  continued progressing; no production limits were relaxed.

| Run | Applies | Lifetime replies / bytes | Lifetime throttled | Ack p95 / max ms | Max wait ms | Lifetime max slice ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 622 | 2,145 / 147,563 | 1,853 | 278.386 / 296.578 | 300.164 | 6.8324 |
| 2 | 622 | 2,134 / 146,859 | 1,873 | 278.934 / 422.706 | 409.585 | 6.0476 |

Lifetime counts include setup, boundary checks and bounded post-window drain.
They are not just the 400 active ticks. Ack latency is observed loopback fixture
latency, not a network guarantee. Cumulative slice maxima include cold work,
and **exceed the 0.5 ms soft target**; no per-method phase observer was present.

| Run / phase | Tick p95 ms | p99 ms | Max ms | Owner CPU ms | Owner allocated bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1 / idle | 6.1102 | 18.5699 | 39.1264 | 890.625 | 171,523,648 |
| 1 / active | 4.0641 | 9.0159 | 26.1131 | 828.125 | 180,641,880 |
| 1 / recovered | 7.5181 | 13.5370 | 30.9811 | 390.625 | 172,270,528 |
| 2 / idle | 7.2652 | 14.7148 | 19.4826 | 984.375 | 173,963,272 |
| 2 / active | 5.9114 | 10.3554 | 16.7672 | 828.125 | 181,949,360 |
| 2 / recovered | 7.1358 | 11.4853 | 21.6339 | 968.750 | 172,301,104 |

No measured tick exceeded 50 ms. Neither that observation nor a shorter active
p95 establishes zero overhead, performance acceptance or a speedup. CPU/allocation
are whole owner-thread counters including the fixture and normal game workload.

### Unassigned Time

Retain the longest tick: run 1 idle sample **383**, Paper tick **887**. Paper
reports **39.1264 ms**, while the actual public-event JFR bracket is **1.3031 ms**;
the difference is **37.8233 ms**. No recorded overlapping GC/VM/safepoint-begin/
owner park/monitor event explains that inferred interval. The recovered maximum
**30.9811 ms** similarly has a **1.8792 ms** public bracket, and active maximum
**26.1131 ms** has a **2.2136 ms** bracket. These are not discarded or attributed
to bridge methods from coincidence.

Reinspection of the exact Paper patch confirms its tick duration and public
start/end listener bracket have different boundaries: queued work may run before
the start event. That explains why the measurements need not agree, **not** what
caused these particular gaps. Earlier whole-tick reader limitations still apply;
inferred intervals are approximate, and missing samples are not zero CPU work.
No method diagnostic event was enabled, so zero inferred drain events is not a
claim that active phases executed no dispatcher calls. Owner scheduling/execution
attribution remains a separate performance gate.

## Test Changes And Regression

The change is test-only: reuse existing edit runner, moving transport and fixed
gameplay fixture with a separate guarded study policy/evidence root. Extract the
shared gameplay activity validator so both metadata and editing studies enforce
the same accepted-motion/entity/work volume. Add negative tests for operation
coverage, route failures, role swapping, counter corruption and wire/host replay
boundaries. The offline safe YAML reader now compares exactly two or four runs;
missing/unpaired/changed inputs remain rejected.

**95 pure/domain tests on each JVM 21/25**, **119 general Node tests**, **12 JFR
reader checks per JVM**, diagnostic-agent application/bootstrap guards, twelve
deterministic admission orders per JVM and all **six actual native payload
codecs** pass. The full wrapper ran with 116 Node groups; final raw-console
and raw-target negative checks raised that to 119 and the complete final list passed separately.
The offline YAML reader's **three additional groups pass on each JVM**.

No native GUI/gesture/command/clipboard rendering suite or universal player
packaging was rerun. Both published 1.8.0 and development client JARs, companion
and fixture retain their prior hashes. An actual attempted rerun is refused
before Gradle/build/copy, with proof, companion, fixture and retained helper
unchanged. No owned fixture JVM or loopback listener remains.

Retained stationary edit evidence revalidates on 1.21.11, 26.1.2 and 26.2; the
26.2 instrumentation/profiler-control and both legacy/26.2 matched-gameplay
analyses also pass. Each prior analysis JSON is archived before reanalysis;
raw profiles/proofs and original helper classpaths are preserved and hash checked.

## Pins

| Evidence | SHA-256 |
| --- | --- |
| Companion, unchanged | `7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956` |
| Fixture, unchanged | `4dee35f5a0d9a66194b7af721cdb01a8000cd0b5f7581ed1759ff8d6549146e2` |
| Moving helper, unchanged | `d01d84bce41c37f3624919fd024492a75b4f15798e293eac906b88ac32afb669` |
| Identical JFC | `a590b966fb30f41860dc01028ebe60125d04901b8725d049ec58370901984536` |
| Frozen modern starting world | `13081178f0cc350c4ad88ac9f50d3ef96be838b956e8cb9681d1a9df98b95b87` |
| Equal normalized effective global config | `0efac7d6db9652c09bfb5d91640cc27f6a3c17e2540eeada6c35580503bdf285` |

Complete exact host, helper-classpath and per-run input pins remain in local
proof. Raw worlds, JFR, logs and test binaries stay ignored, not public uploads.

## Reproduction And Decision

```powershell
.\tools\test-paper-bridge-load.ps1 -Java21 'C:\path\to\jdk21' -Java25 'C:\path\to\jdk25' -Minecraft 26.2 -EditLoad -MovingEditLoad -Players 32 -Samples 400 -Repetitions 2 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs 'C:\path\to\jdk25' 26.2 --moving-gameplay
```

Evidence: `build/bridge-moving-edit-proof/26.2/`. Archive previous attempts before
reuse. Close the bounded exact-host **concurrent moving cuboid-edit functional**
gate. No production defect is established and no production behavior is changed
just to improve a local number. Next distinct gate: native Fabric/NeoForge editor
behavior under concurrent host load. Scheduling attribution, sustained restarts,
arbitrary network/backend faults, other dedicated hosts, heavy streaming and
independent-machine performance acceptance remain open.

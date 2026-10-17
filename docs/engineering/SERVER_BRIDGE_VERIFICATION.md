# Server Bridge Verification Gates

Status: **release gates, with partial foundation evidence**.
Updated: 2026-10-17. The [implementation record](SERVER_BRIDGE_IMPLEMENTATION.md)
contains Java 21/25 tests, six native-codec checks and a real Paper wire proof.
Headless protocol clients are not Fabric/NeoForge gameplay clients. A subsequent
[six-client native Paper 1.21.11 matrix](NATIVE_PAPER_REGRESSION_2026-10-11.md)
passed allowed/denied/absent scenarios. A later
[16-player lifecycle/load pilot](BRIDGE_LOAD_AND_LIFECYCLE_2026-10-11.md)
records repeated stationary metadata workloads and JFR, not busy-server release
acceptance. The [native lifecycle regression](NATIVE_BRIDGE_LIFECYCLE_2026-10-12.md)
passes real same-world respawn and Nether/Overworld recovery on both 1.21.11
loaders after reproducing a stale-client-context defect. A subsequent
[channel/reconnect regression and controlled baseline](BRIDGE_CHANNEL_AND_BASELINE_2026-10-12.md)
passes native same-process reconnect and explicitly signalled wire channel
removal/re-advertisement. Silent dependency loss, between-tick advertisement
flaps and backend switches still need separate verification; these tests do not
establish production performance acceptance.
The [server lifecycle/harness audit](BRIDGE_SERVER_LIFECYCLE_2026-10-12.md)
adds three real companion disable/enable cycles and a reproduced/fixed guard
against startup with disabled WorldEdit. All three stops left the remote channel
advertised; automatic native silent-stop recovery remains a release blocker.
The shared process runner rejects failed/forced shutdowns before proof publication.
The subsequent [negotiated-close regression](BRIDGE_SESSION_CLOSE_2026-10-12.md)
implements opt-in minor-1 graceful-stop invalidation and tests it on both native
1.21.11 loaders while the channel remains advertised. Cached data, grants and
pending work are cleared without a retry loop; ordinary CUI survives. This closes
graceful-stop invalidation for negotiated peers, **not** automatic silent-restart
discovery, ungraceful loss, backend-switch or busy-server acceptance gates.
Other host/version joins and remote features are not marked passed.
The later [one-shot restart regression](BRIDGE_RESTART_RECOVERY_2026-10-12.md)
closes the narrow graceful-restart gate for native Fabric/NeoForge 1.21.11:
an opt-in startup hint triggers one fresh handshake after negotiated close.
It does not read metadata without a consumer or retry denied/timed-out states.
Saturated admission is tested as a safe unavailable fallback, not guaranteed
all-player recovery. Busy-server reconnect bursts, missing/ungraceful lifecycle
signals, between-tick flaps and backend switches remain unaccepted.
The [32-player load/restart cycle](BRIDGE_32_PLAYER_RESTART_LOAD_2026-10-12.md)
passes concurrent graceful recovery after traffic settles, own-player metadata,
flood admission/progress and zero idle reads in two installed runs. It does not
close saturated recovery, adversarial fairness or busy-server acceptance. New
long-tick observations remain unattributed and the performance spread is recorded,
not discarded. Failed-window diagnostics have separate automated tests.
The later [tick attribution and admission research](BRIDGE_TICK_ATTRIBUTION_RESEARCH_2026-10-12.md)
adds 4,800 complete test-only tick markers in a separate frozen-world run. Its
installed maxima overlap GC pauses, but it does not explain old outliers or
close busy-server cost acceptance. A deterministic 31-sender ordering reproduces
**0/60 ordinary replies** on both JVMs while aggregate bounds hold: fair service
is an explicitly failed gate, not implied by previous network progress tests.
That historical cycle changed no production behavior. The subsequent
[fair-dispatch cycle](BRIDGE_FAIR_DISPATCH_2026-10-12.md) implements one bounded
pending slot per actor, with global tokens spent at fair service rather than
arrival. The reproduced counterexample and additional order/offset cases now
pass on both JVMs. This closes that specific failure, not arbitrary saturation,
heavy-feature scheduling or representative busy-server cost acceptance.
Published release JARs and the 1.8.0 tag remain unchanged.
The [mixed-gameplay cycle](BRIDGE_GAMEPLAY_LOAD_2026-10-12.md) adds 4,800 verified
ticks with 32 moving headless clients (four non-consumers), live entities and
fixed WorldEdit batches, restoring identical starting worlds in reversed-order
comparisons. Work/progress/queue/cleanup gates pass without changing production
code. No tick exceeded 50 ms in this bounded run, but paired timing varies and
dispatch slices exceed their soft target. This is not representative production
cost acceptance, native gameplay or sustained-restart coverage.
The subsequent [active-lifecycle cycle](BRIDGE_ACTIVE_LIFECYCLE_2026-10-12.md)
observes real queued wire input before faults and passes 3,200 measured ticks,
queued revocation/disconnect, six concurrent graceful recoveries, independent CUI
and dependency fail-closed cleanup under ongoing headless gameplay/flood. A test-
client pre-play movement race is fixed and the failed attempt retained. This
closes those exact functional scenarios, not native saturated recovery, heavy
streaming, hot reload or production cost gates. Cumulative slices again exceed
their soft target; no production change or performance acceptance is claimed.
The [method-interval diagnostic cycle](BRIDGE_SLICE_DIAGNOSTICS_2026-10-12.md)
adds four fresh instrumented JVM runs and 6,400 verified ticks, including a
separate no-spark control. Independent counter reconciliation passes. Specific
large intervals overlap profiler safepoints or GC, while other intervals remain
unattributed; disabling spark does not establish a speedup. Test-loader/launcher
and attribution defects are corrected without changing production. This closes
the missing per-call observation, not the soft-budget or production cost gates.
The [matched profiler-control cycle](BRIDGE_MATCHED_CONTROL_2026-10-13.md)
adds 9,600 verified gameplay ticks from four fresh absent/installed JVMs, with
identical no-spark policy and twice the earlier window length. Raw summaries,
percentiles and workload gates reconcile; idle reads and queues return to zero.
CPU deltas change sign across the two pairs, while flood allocation increases
in both. Dispatcher high-water and unattributed full-tick outliers remain open;
neither negative timing deltas nor maintained TPS constitute cost acceptance.
This bounded foundation evidence permits the next isolated development stage,
not publication or claims about unimplemented remote features/other servers.

## Evidence Record

The [minimal-observer control](BRIDGE_DRAIN_OBSERVER_2026-10-16.md) verifies
that nested diagnostic instrumentation changes service; it does not explain
all remaining overruns. The [revision-ID comparison](BRIDGE_REVISION_IDS_2026-10-16.md)
and [request-local world comparison](BRIDGE_WORLD_READ_2026-10-16.md) retain
specific unnecessary-work removals with actual-peer authority and ordinary
progress preserved. All three studies independently pass 4,800 raw/JFR ticks
and actor audits. Mixed wall/CPU results and unknown pauses are retained.
The [guard-band trial](BRIDGE_GUARD_BAND_2026-10-16.md) predeclares <=0.5 ms
candidate p95 in both pairs, >=95% progress for every ordinary actor and <=100 ms
ack-p95 regression. Functional evidence alone cannot pass that performance gate.
Both static-floor candidates are rejected. The separate
[combined hot-path trial](BRIDGE_HOTPATH_2026-10-16.md) verifies only retained
work reductions against the original reservation baseline with those same gates.
The [priority control](BRIDGE_PRIORITY_CONTROL_2026-10-16.md) retains the original
fixture priority after mixed results. The later
[encoding trial](BRIDGE_ENCODING_2026-10-16.md) passes all functional and
per-actor/ack gates with lower allocation, but candidate p95 0.5054 ms fails the
unchanged 0.5 ms limit. Rebuild all six development runtimes and both universal
packages for this shared-code change; published 1.8.0 artifacts stay immutable.
The same candidate then passes **six fresh ordinary-load JVMs** across exact
1.21.11/26.1.2/26.2 Paper/WorldEdit tuples: 3,519 applies, 7,200 reconciled ticks,
48 editor audits and zero final connections/queue/tasks. That is cross-host
functional regression, not a replacement performance baseline or native test.
The later [six-visit](BRIDGE_BATCH_2026-10-17.md),
[seven-visit](BRIDGE_BATCH_SEVEN_2026-10-17.md) and
[request-kind](BRIDGE_KIND_ADMISSION_2026-10-17.md) studies preserve 12 additional
fresh-server runs / 14,400 reconciled ticks and reject all three scheduling
candidates under the unchanged numerical gates. The retained source/JAR is
restored to original eight; the mixed-cost admission counterexample remains
open, not fixed by the rejected helper. Only extra eight-visit capacity/FIFO
regression tests remain. Candidate patch and all negative evidence are retained.
The [retained-companion native regression](BRIDGE_NATIVE_REGRESSION_2026-10-17.md)
then passes all six guarded rows: 24 clients, 168 gestures, 18 fault cases, 150
applies/198 captures, twelve normal server stops and independently revalidated
proof/PNG/artifact hashes. This verifies the original companion and rebuilt
development clients on the three exact hosts, not the rejected hint/cap variants.
The [peer-local estimate trial](BRIDGE_PEER_ADMISSION_PROPOSAL_2026-10-17.md)
subsequently passes deterministic/functional checks and both p95 gates, but loses
individual progress in both pairs and exceeds +100 ms ack-p95 in one pair. It
is fully reverted. Original companion/native evidence remains the retained
baseline; neither cross-slice cost estimate is accepted as a release fix.
The separate [real-WorldEdit property matrix](BRIDGE_SELECTION_PROPERTIES_2026-10-17.md)
adds 432 passed wire cases on all three exact hosts with independent ordered-point/CUI/replay and
permission-denial checks. Its first fixture assumption failure is retained and
corrected against the official Extending constructor. Only completed exact-host
rows count. All three raw proofs/artifact hashes, 864 canonical snapshots and
12 counter snapshots independently reconcile. No production change or new cost
acceptance is inferred.
The [combined-authority comparison](BRIDGE_COMBINED_AUTHORITY_2026-10-17.md) adds
four fresh matched JVMs, 2,384 applied commits and 4,800 reconciled ticks. Its
candidate preserves every ordinary actor's progress and ack gates but fails
the second absolute p95 target (0.5557 ms). Both production changes and its
candidate tests are reverted; fewer permission evaluations/allocations do not
close the cost gate. Long, unassigned send-path intervals remain diagnostic work.
The [bounded offline sample audit](BRIDGE_OWNER_SAMPLES_2026-10-17.md) then
preserves every original metric in all four active windows. Only three of forty
selected intervals have an owner sample, insufficient for full-duration causal
attribution. Six new reader groups pass on both JVMs, including lost-data failure
and explicit truncation. No new live observer or production change is introduced.
None of these exact-host studies substitutes for native concurrent gameplay,
absent/installed production cost baselines or unsupported platform matrices.

The [2026-10-16 dispatcher comparison](BRIDGE_DISPATCH_BUDGET_2026-10-16.md)
changes only the unpublished companion after a deterministic old-code failure.
Four identically instrumented previous/candidate/candidate/previous moving-load
JVMs pass 2,340 applies, 4,800 raw/JFR ticks, 128 actor audits and cleanup gates.
Active over-target frequency falls from 19.21% to 7.30%; saturated edit throughput
falls 11.44% and candidate ack p95 rises to 436..497 ms. Candidate drain p95 still
exceeds 0.5 ms. Keep the narrow admission fix as measured backoff, not a claim of
free speed, fixed atomic/JVM outliers or production cost acceptance. Published
1.8.0 and development player artifacts remain unchanged.

The [2026-10-16 exact 26.2 concurrent moving-edit workload](BRIDGE_26_2_MOVING_EDITS_2026-10-16.md)
passes two fresh servers, 1,244 XYZ move/resize applies, 2,400 verified ticks,
18,816 accepted moves and 61,440 independent fixture WorldEdit cell changes.
All 32 actors per run pass canonical/CUI/control-area/undo audits. Stale/foreign
revisions, cached replay and pre-request edit revocation retain their boundaries;
the latter checks occur while motion/world workload continue, after competing
application workers stop, not inside a saturated active queue. Raw wire replies
and bytes reconcile with final console counters. Idle work, measured chunk
I/O/generation and final resources remain zero. Soft-budget overruns and large
reported-tick/public-bracket gaps remain unassigned performance observations.
This closes that exact headless combined functional gate, not native concurrent
gameplay, independent replication or release cost acceptance. Production remains
unchanged; the strengthened test tools do not require a player fix release.

The [2026-10-15 exact 26.2 moving-gameplay comparison](BRIDGE_26_2_GAMEPLAY_2026-10-15.md)
passes four fresh absent/installed, installed/absent JVMs, 9,600 reconciled ticks,
75,104 accepted movements and 245,760 fixture WorldEdit cell changes. Complete
effective global configuration, original helper artifacts, raw actor events and
host reply counters independently reconcile. CUI-only non-consumers receive no
bridge data; idle source work, measured chunk I/O/generation and final resources
are zero. No measured tick exceeded 50 ms, but the 0.5 ms soft dispatcher target
is still exceeded and two local pairs do not establish universal overhead.
This closes bounded exact-host moving metadata behavior, not concurrent moving
cuboid mutation, native rendering/input, heavy remote features or release cost
acceptance. Production/player artifacts remain unchanged.

The [2026-10-15 exact 26.2 profiler control](BRIDGE_26_2_PROFILER_CONTROL_2026-10-15.md)
passes four fresh, identically instrumented on/off/off/on JVMs, 2,519 canonical
applies and 4,800 raw/JFR ticks. Structured effective global configurations differ
only in the profiler bit; startup and recorded sampler presence/absence agree.
An 18.4637 ms drain overlaps GC, while a 3.4107 ms apply remains unexplained in
a spark-off arm. The quiet 38.6932 ms GC-associated tick is retained with zero
bridge work. This closes that bounded observer-control stage, not the soft
budget, representative gameplay, other hosts or production cost gates. Production
artifacts remain unchanged. Move to the distinct representative workload gates,
not repeated stationary runs presented as release acceptance.

The [26.2 edit-attribution cycle](BRIDGE_26_2_EDIT_ATTRIBUTION_2026-10-14.md)
passes four frozen-world control/instrumented JVM runs, 2,483 applies and 4,800
raw/JFR ticks without production changes. Lifetime method/counter reconciliation
and active phase separation pass. The corrected historical reader finds 55.0482
ms of safepoint-begin overlap in the old 60.4271 ms tick; it does not establish
the missing thresholded caller/operation. New short VM-operation records and
safepoint-ID joins identify observer associations, not the cause of all delay.
A 14.7746 ms read remains mostly unexplained, and instrumented drains exceed
the soft budget. Modern-world snapshot preparation is corrected with negative
tests and retained failure evidence. Production cost, arbitrary faults and
native/moving workload acceptance remain open.

The [exact concurrent edit-load cycle](BRIDGE_26X_EDIT_LOAD_2026-10-14.md) passes
six fresh 32-actor server runs on 1.21.11, 26.1.2 and 26.2. Ordinary progress,
admission pressure, own-player canonical state, stale/foreign/replay/revocation,
zero non-consumer disclosure and final resource cleanup reconcile with 7,200
raw/JFR ticks. 26.2 uses a separately pinned official MCProtocolLib 776 helper,
not a fabricated or aliased wire protocol; these are headless clients, not native
gameplay load. Two setup defects are retained/corrected without changing
production. The 60.4271 ms outlier has no recorded overlapping explanation;
cumulative slice high-water exceeds the soft target. Representative production
cost, matched 26.x baseline attribution and native/moving gameplay remain open.

The [exact native edit-fault cycle](BRIDGE_26X_EDIT_FAULTS_2026-10-14.md) separately
records 400 ms one-way delay, an applied acknowledgment retained past timeout
and edit permission revoked before commit delivery. Only completed exact tuples
in its execution table are passed. Actual native pending/UNKNOWN/denied states,
intercepted request/reply counts, independent WorldEdit bounds, world/undo audit
and final cleanup must agree. Runtime/launcher and packet discovery changes are
test-only; production JARs and protocol remain unchanged. These bounded cases
do not establish concurrent 26.x load, arbitrary TCP/proxy/backend faults,
ungraceful dependency loss or production cost acceptance.

The [26.x negative/lifecycle cycle](BRIDGE_26X_LIFECYCLE_2026-10-14.md) passes
twelve real allowed/denied/absent game runs on the exact 26.1.2/26.2 hosts.
Fresh epochs, same-world respawn, Nether/Overworld changes, channel advertisement,
same-process reconnect and negotiated graceful stop/re-enable reconcile with
server counters. Unavailable reads and old-context commits never advance request
IDs. This closes the named bounded native scenarios, not delayed/late replies,
ungraceful loss, backend switches, concurrent load or representative cost gates.
The test-only channel injector and proof validation are strengthened; no production
behavior or public artifacts change. Historical reports below retain their scope.

The [26.x remote editing cycle](BRIDGE_26X_REMOTE_EDITING_2026-10-13.md) ports the
existing consumer to all four 26.x adapters and passes 28 native gesture cases
per loader on exact Paper 26.1.2/WorldEdit 7.4.3 and Paper 26.2/WorldEdit 7.4.5
hosts. Server bounds, 92 applied commits, cancellation and quiet cache checks
reconcile; eight fresh screenshots are inspected. This closes those exact
gesture/version scenarios, not 26.x denial/absence, respawn/restart, delayed
acknowledgment, concurrent-load or production cost gates. Historical references
to untested 26.x consumers below describe the earlier cycles, not this later one.

The [native remote gesture cycle](BRIDGE_NATIVE_GESTURES_2026-10-13.md) passes
28 actual input/controller cases on each 1.21.11 loader, repeated after frame-
camera and screenshot checks are added. Actual WorldEdit bounds and server
counters agree for all faces/corners, free-corner release, cancellation and
radial XYZ Move. Four native screenshots are inspected. This closes those
bounded callback scenarios, not manual hardware ergonomics, every close-range
view, frame-time/server-cost acceptance or other remote client versions.

The [edit fault/load cycle](BRIDGE_EDIT_FAULTS_AND_LOAD_2026-10-13.md) adds six
guarded native slow/late-ack/pre-delivery-revocation cases and two fresh-JVM
32-actor edit workloads. All 1,240 sustained commits have canonical replies;
quiet captures/reads/transfers return to zero. Raw percentiles and 2,400 JFR
tick markers reconcile. These close the named functional scenarios, not arbitrary
Internet/proxy faults, live-block-edit gameplay, physical remote mouse gestures
or representative cost acceptance. Client source changes remain required whenever
needed; immutable published release artifacts are not a ban on client development.

The [2026-10-13 remote cuboid stage](BRIDGE_REMOTE_SELECTION_2026-10-13.md) passes
27 actual Paper wire groups and the six native 1.21.11 connections, including
G-driven resize, native remote-source shift, canonical CUI, cancellation and
quiet cache reads. Capture/commit use the existing fair bounded queue. Dedicated
queue/revocation/expiry/replay tests do not establish edit-load performance.
The later fault/load and native gesture reports close their named cases;
manual hardware ergonomics, arbitrary network/backend faults, other hosts,
26.x remote consumers and protection-plugin combinations remain open. Same-JVM
plugin re-enable intentionally withholds editing after the classloader closes;
fresh full startup is required. Published 1.8.0 is unchanged.

Every run must record: repository commit, exact server/client Minecraft version,
server implementation/build, loader/API, WorldEdit distribution/version/hash,
Java vendor/version, companion/client protocol versions, permissions/protection
plugins, test command, expected result, observed result, logs and profile paths.
Record runtime assertions separately from compilation/package checks.
Keep fixtures under ignored build directories, not production profiles/worlds.
Profile and artifact reports may contain private data; inspect before publication.

## Gate 0: Baseline And Protocol

- Preserve [existing local and packaging tests](../../TESTING.md) and release 1.8.0.
- Pure codec tests run on Java 21 and 25 using identical golden byte fixtures.
- Test both directions, Unicode UTF-8 byte bounds, negative/overflowing lengths,
  overlong integers, trailing data, unknown operations and truncated frames.
- Validate negative section coordinates, dimension bounds, palette indices,
  NaN/infinite transforms and multiplication/volume overflow before allocation.
- Test fragments arriving after cancellation/context change, duplicates,
  mismatched totals, oversized declared output and decompression bombs.
- Test fair admission, stalled consumers, credit exhaustion, quota recovery,
  timeout cleanup and aggregate memory bounds with many connections.
- Repeat adversarial arrival order/offsets. Token buckets alone previously
  starved an ordinary sender; require bounded queue age and control progress,
  not merely a successful aggregate-limit check or a favorable sender order.

## Gate 1: Dedicated-Server Transport Proof

Start with Paper 1.21.11 + matching official WorldEdit and each updated client.
The first deliverable is hello/capabilities/state round-trip, **not block editing**.

| Pair | Required observation |
| --- | --- |
| Updated Fabric client -> Paper companion | Same bounded application bytes decode both ways |
| Updated NeoForge client -> Paper companion | Optional non-Neo payload path works without loader handshake assumptions |
| Client -> WorldEdit server without companion | Joins normally; existing CUI overlay still works |
| Vanilla client -> companion server | Joins normally; companion sends no unsupported required payloads |
| Released 1.8.0 client -> companion server | No attempt to force bridge functionality; normal CUI remains usable |
| Incompatible/denied companion -> updated client | Clear capability state; no disconnect caused merely by missing feature |
| Dedicated Fabric / NeoForge host -> matching clients | No client class loaded on server; public lifecycle/network APIs only |

Then repeat exact supported targets: 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 where
official matching server WorldEdit artifacts exist. Do not transfer the local
NeoForge 26.1 restriction into a Paper claim without testing either direction.
Do not claim untested cross-loader dedicated mod-server joins, modpack registry
compatibility, or other 1.21.x versions. A wire-protocol proof is not permission
to bypass the server loader's normal connection requirements.

Separate follow-up matrices: Spigot/Purpur, FAWE, Velocity/BungeeCord, ViaVersion,
Folia and other protection integrations. They stay unverified until checked.
Proxy testing includes backend switch during transfers and connection-identity
binding; never trust a payload merely because it says it came from a proxy.
See [Velocity guidance](SOURCES.md#s23-velocity-plugin-messaging).

## Gate 2: Authority And Lifecycle

- Two simultaneous players must never receive each other's session data.
- Forged actor/world/context tokens cannot select another player's state.
- Revoking a grant mid-job/mid-transfer prevents further protected publication
  and apply. Capability negotiation never substitutes for live permission checks.
- Disconnect/reconnect, dimension switch, respawn, deselect, world override,
  plugin disable and backend change invalidate old tokens/results and resources.
- Server has no leaked actor/world references, worker tasks or queued buffers
  after cleanup. Repeated enable/disable cannot duplicate channels/listeners.
- No-op, invalid, cancelled or denied requests produce no world writes, history
  entries, fake commands, forced chunk loads, disk files or permission escalation.
- Test anti-xray and protected regions with a restricted player. Denied sensitive
  capabilities must not leak real states through counts, palettes or differences.
- Fuzz client requests and server responses independently. Bound error logging
  so malformed traffic cannot fill disk or consume the tick budget.

## Gate 3: Selection Editing

Test cuboid/extend with reversed pos1/pos2, every face/corner, resize and whole-box
shift, negative coordinates, build-height edges and extreme coordinate rejection.
Repeat with only expand, only contract, only shift and no relevant permissions.
Unsupported selectors and incomplete selections must fail without conversion.

Change selection with the wand or a command during a drag: stale commit must be
rejected. Send duplicate commit IDs: exactly one apply. Delay/reorder application
acks relative to CUI: no flicker, reverted selection or resurrection after clear.
Escape cancels unsent drafts; pending apply is accurately acknowledged, not
misrepresented as cancellable after the server has applied it.
Check camera control, radial menu, input release and crosshair restoration against
the current client behavior. Bridge latency must not block mouse movement.

## Gate 4: Clipboard

- Copy, cut and schematic load may change the authoritative buffer, but no new
  cut/paste command preview is introduced. Empty/cleared buffers are normal.
- With preview disabled: no content subscription, scan or transfer.
- With preview enabled: origin/placement and block-state orientation match the
  existing local source on an asymmetric fixture with stairs, logs and trapdoors.
- Type/cancel/apply `//rotate 90`, `//rotate 90 90 0`, `//flip east`, `//mirror`,
  repeated transforms and non-right-angle input. Drafting leaves holder unchanged;
  canonical server transform wins after real command execution.
- Transform-only changes do not retransmit source blocks. Clear/replace buffer
  mid-transfer cannot mix revisions. Unknown modded states are reported explicitly.
- Test supported in-place clipboard mutation detection, not only object replacement.
- Default schema transfers no chest contents, sign text, command-block commands,
  arbitrary NBT, entities or biome metadata. Inspect decoded frames to prove it.

## Gate 5: Command Simulation

Use the same fixture and input against local preview, remote preview and a real
WorldEdit execution in a disposable world. Save original blocks and history state
to prove **preview-only requests leave both untouched**.

Cover every existing supported command/alias in
[COMMAND_PREVIEW_COVERAGE.md](../../COMMAND_PREVIEW_COVERAGE.md), including legacy
IDs, properties, omitted replace masks, global masks, weighted mixtures, flora,
hollow halo, line/curve thickness and spline context. Include malformed drafts,
unsupported flags/patterns, inventory mode and insufficient permissions.
For random patterns test allowed states, relative weighting, stable sample and
truthful sample labeling, not exact equality to WorldEdit's separate random roll.

Test rapid typing, huge mostly unloaded bounds, loaded-chunk arrival/removal,
source changes during capture and completion, modded states and no-op results.
Replacing a full block with buttons/slabs/glass must suppress original terrain
correctly and restore neighboring faces without a global chunk reload.
Command drafts and clipboard transforms must keep one coherent rendering owner.
No restarting the entire job for unrelated CUI packets or unrelated chunk updates.

## Gate 6: Performance And Release

Measure idle, one player and concurrent active players with cold/warm caches,
slow links, queued changes and a nearly exhausted memory budget. Record:

- Tick/MSPT p50/p95/p99 and bridge slice duration, not only average TPS.
- Time to first usable section and to final result; stale-result discard count.
- Allocations, retained heap, GC pauses, queue depth, active tasks and cleanup.
- Bytes/second per player and globally, compression CPU, pending transfer bytes.
- Client frame times, section rebuild/upload count and source restoration cost.

Use [spark](SOURCES.md#s18-paper-profiling) during the actual workload and
[JFR](SOURCES.md#s21-java-flight-recorder) for JVM allocation/thread evidence.
Choose budgets from results; local preview's existing 2 ms target is not a
proven safe per-player allowance for a busy dedicated server. Cold massive
inputs and network latency cannot honestly be guaranteed instantaneous.

### Server Cost Acceptance

Compare repeated runs of the same world, player activity, server/JVM settings
and hardware: bridge absent, installed with no subscribers, features disabled,
one active user and concurrent active users alongside ordinary/vanilla players.
Control warm-up, cache state and competing workloads; report the baseline spread
and p95/p99 MSPT delta as well as absolute values. TPS alone can hide regressions.

- Idle/disabled runs must show zero preview source-cell reads, clipboard-content
  hashes, simulations and block-data transfers. Bounded lifecycle/control work
  is recorded separately, not misrepresented as literally zero bridge overhead.
- Instrument work admitted, source cells read, sections reused, slices per tick,
  pending/active jobs, queue age, cancellations, retained bytes and bytes sent.
  Attribute owner-thread and worker CPU/allocation costs separately.
- Camera motion and selection dragging must not produce per-frame server work.
  Repeated unchanged drafts reuse valid revisions; transform-only changes do
  not recapture/retransmit clipboard contents.
- Stress rapid typing, many clients, slow/stalled receivers, permission changes
  and disable/disconnect mid-job. Aggregate CPU/concurrency/memory/traffic bounds
  must hold, queues must stay bounded and cancelled work must stop promptly.
- Create controlled server pressure: admission/capture/send back off without
  blocking gameplay or the normal WorldEdit command path. Recovery must not
  oscillate or repeatedly restart a whole preview; record pause/recovery latency.
- Prove that no preview forces chunk loads/generation. After all consumers stop,
  source work returns to zero and owned resources return to their bounded baseline.

Choose numerical defaults and acceptable baseline regressions from these runs,
record them before release, and rerun under those exact settings. Staying below
50 ms per tick alone is not acceptance: a bridge can consume substantial spare
capacity without lowering TPS. An unmet budget requires tuning/degraded preview
service, not silently raising limits or claiming the server is unaffected.

### Release Gate

Release gates: all applicable unit/domain tests, dedicated-server round trips,
both universal client packagers, six runtime builds and affected in-game checks.
Use the non-disruptive native harness rules in [AGENTS.md](../../AGENTS.md).
Do not steal the user's cursor, activate windows or bypass cursor-isolation guards.
Publish only verified tuples/capabilities, protocol compatibility notes and known
limitations. Keep server/client artifacts distinct and preserve reproducible
source tags; do not move the published `1.8.0` tag.

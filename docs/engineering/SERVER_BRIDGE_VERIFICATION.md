# Server Bridge Verification Gates

Status: **release gates, with partial foundation evidence**.
Date: 2026-10-11. The [implementation record](SERVER_BRIDGE_IMPLEMENTATION.md)
contains Java 21/25 tests, six native-codec checks and a real Paper wire proof.
Headless protocol clients are not Fabric/NeoForge gameplay clients. A subsequent
[six-client native Paper 1.21.11 matrix](NATIVE_PAPER_REGRESSION_2026-10-11.md)
passed allowed/denied/absent scenarios. A later
[16-player lifecycle/load pilot](BRIDGE_LOAD_AND_LIFECYCLE_2026-10-11.md)
records repeated stationary metadata workloads and JFR, not busy-server release
acceptance. Other host/version joins and remote features are not marked passed.
Published release JARs and the 1.8.0 tag remain unchanged.

## Evidence Record

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

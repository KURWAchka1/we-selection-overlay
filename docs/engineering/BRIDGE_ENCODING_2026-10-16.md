# Exact-Sized Bridge Encoding

## Candidate And Predeclared Method

Source inspection finds `BridgeCodec.encode` allocating a 256-byte staging
array and then copying the actual frame into another array. Current sealed
message variants have fixed field sizes and only one size-dependent READY
selection payload. Allocate exactly the schema size through the existing JDK
`ByteBuffer`, keep all field-writing code and big-endian order unchanged, verify
the buffer is filled, and return its exclusively owned backing array. No buffer
pool, static/thread-local mutable buffer, new opcode or transport change.

Tests cover all nine envelopes, every selection and commit status, explicit
lengths, golden framing, truncation/trailing bytes, fuzzing, equal round trips
and independent arrays after caller mutation. The shared codec is used by both
unpublished companion and development clients: rebuild/retest all six native
payload adapters and both development universal packages. Never overwrite or
retag published 1.8.0; binary wire compatibility does not remove release gates.

Compare exact baseline `aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6`
against the encoding-only candidate in four fresh **previous / candidate /
candidate / previous** Paper 26.2 JVMs. Fix BelowNormal priority (not the rejected
priority intervention), minimal root observer, helper/JFC/heap/processor policy,
frozen starting world, 32 simultaneous joins, movement/edit/flood workload,
quotas, 0.5 ms target and server-quit barrier. No competing builds or offline
readers during measured windows.

The same strict gates remain, declared before data: both candidate active p95
<=0.5 ms; >=95% paired service for every ordinary actor; ack-p95 growth <=100 ms;
all functional/raw/JFR/canonical/CUI/authority/replay/no-chunk-I/O/no-expiry/
quiet-window/cleanup checks. Record total **and per-served-frame allocation**;
less serviced work is not an allocation optimization. Raw maxima/unassigned
pauses stay visible. Do not claim that eliminating one array guarantees a wall
improvement or safe preemption of an arbitrary WorldEdit/JVM operation.

```powershell
./tools/test-paper-bridge-load.ps1 -Java21 <jdk21> -Java25 <jdk25> `
  -Minecraft 26.2 -EditLoad -EncodingComparison -Players 32 -Samples 400 `
  -Repetitions 4 -AcceptEula
node tools/server-bridge-test/analyze-edit-load-proof.mjs <jdk25> 26.2 --encoding-control
```

Root: `build/bridge-encoding-proof/26.2/`. Preserve the exact baseline as
`previous-companion.jar` before build/copy. Keep original development JARs and
test logs separately before rebuilding, with published artifact hash guards.
See [S70](SOURCES.md#s70-exact-sized-array-backed-encoding).

## Executed Results

Proof **2026-10-16T20:04:52.126Z** passes 4,800 exact raw/JFR tick pairs,
2,380 applies, 37,792 accepted moves, 122,880 fixture WorldEdit cell changes,
128 independent actor audits and authority/replay/canonical/CUI checks. Quiet
bridge work, measured chunk I/O/generation, expiry and final connections/queue/
tasks are zero. All four owned servers stop normally. No measured tick exceeds
50 ms; earlier experiments' long ticks are not erased by this result.

| Round | Encoder | Drains >0.5 ms / all | Drain p95 / p99 / max (ms) | Applies | Ack p95 / max (ms) |
| --- | --- | --- | --- | --- | --- |
| 1 | Previous | 14 / 391 | 0.4696 / 1.0619 / 3.8554 | 597 | 388.7501 / 602.5426 |
| 2 | Exact-sized | 20 / 398 | 0.5054 / 1.0865 / 3.0378 | 591 | 384.8519 / 542.8209 |
| 3 | Exact-sized | 17 / 386 | 0.4725 / 1.1339 / 2.2259 | 591 | 355.6859 / 496.8400 |
| 4 | Previous | 15 / 392 | 0.4574 / 1.1043 / 2.0088 | 601 | 358.1973 / 481.2836 |

Both paired actor-progress gates pass (minimum ratios **96% / 97.33%**), as do
ack-p95 gates (-3.8982 / -2.5114 ms). Candidate drain p95 is higher in both pairs;
round 2 misses the absolute target by **5.4 us**. The analyzer deliberately sets
exit status **2** for rejected performance acceptance. Do not round 0.5054 down,
substitute mean latency, or rerun until a favorable percentile appears.

Active drain allocations are **6,748,328 / 6,762,176 bytes** previously versus
**6,155,816 / 6,152,112** with exact encoding. Respective admitted-frame counts
are **2,007 / 2,003** and **1,995 / 1,998**. Bytes per admitted frame fall from
**3,362.40 / 3,376.02** to **3,085.62 / 3,079.14**, with mixed-role/workload and
root-observer caveats. This supports retaining the unnecessary-copy removal,
not a claim that it improves wall latency. Quantized CPU is 0 / 0 ms in the old
arms and 156.25 / 46.875 ms in the candidate: zero is not free execution, and
these readings do not support a CPU-speedup claim.

The largest active candidate intervals (3.0378 / 2.2259 ms) have no recorded
GC/VM/safepoint/owner-park/monitor overlap or owner execution sample. Their cause
remains unassigned. Do not blame encoding, WorldEdit, OS scheduling or GC without
evidence. An owner-thread public API call still cannot be safely preempted.

**Retain exact-sized encoding for its verified ownership, wire equivalence and
lower allocation; keep the strict server-cost gate open.** Static guard floors
and process-priority intervention remain rejected. Native concurrent gameplay
and representative bridge-absent/installed cost baselines remain separate gates.

Candidate companion SHA-256:
`2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.
Frozen digest: `a0668cde3b477c9b77a9ab77a74982d5a7a12040ce5f9bd52c43ed9f1752eeb8`.
Raw proof SHA-256:
`ab8ef75dd1e23489697f9efc17053747e1c570279578e0f369a75ef2276ff2d5`.

## Regression And Artifact Isolation

Before post-codec regression, 24 original development JARs and the current
validation logs were copied to
`build/bridge-encoding-pre-regression-20261016/`, with exact names, sizes and
SHA-256 in `development-artifacts.json`. Both published 1.8.0 universal hashes
still match the release guards. Shared protocol code changed, so this candidate
is **not server-only**; development clients must be rebuilt, while published
1.8.0 artifacts and release tags remain immutable.

Post-codec `tools/test-server-bridge.ps1 -Offline` and
`tools/test-bridge-packaging.ps1 -RerunTests -Offline` both complete successfully:

- **112 pure tests per JVM**, Java 21.0.11 and 25.0.2, with zero failure/error/skip.
- **144 general Node groups**, original/root-only agent opt-in and packaging
  probes on both JVMs, **13 JFR reader groups per JVM** and **three effective-YAML
  groups per JVM**.
- All admission-order probes return **60/60 ordinary replies**, including the
  reproduced 31-attacker case on both JVMs.
- All **six native payload codec probes** and **six development runtime builds**.
- Both universal packages, **121 Fabric + 11 NeoForge tests**, no failures/skips.
- Companion **89,160 bytes**, Java-21-compatible bytecode, license present, no
  bundled fixture/client/provided API classes; SHA matches the measured candidate.
- No remaining measured server process or listener on loopback port 25579.

Development packages after rebuild:

| Artifact (`build/libs` in its universal module) | Bytes | SHA-256 |
| --- | --- | --- |
| `worldedit_selection_overlay-1.8.0-bridge-dev-fabric-1.21.11-26.2.jar` | 1,729,193 | `f003b66d114f3cc249cfb9f4cefc42b8c5904e11284585fd9d754c5b8f2f3384` |
| `worldedit_selection_overlay-1.8.0-bridge-dev-neoforge-1.21.11-26.2.jar` | 1,701,187 | `51df054607f4d53fc4ac5f769befccb9e2bc6e7543dda5532d8e421bc0a3fa02` |

Published 1.8.0 hashes match again **after** rebuilding:
Fabric `912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0`,
NeoForge `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c`.
Logs remain in `build/bridge-validation/`; the pre-encoding copy is separate.
These regressions do not close the failed strict p95 gate or replace native
concurrent gameplay/render/input acceptance. No public release is created.

## Post-Codec Cross-Host Regression

Before another compatibility cycle, the entire previous ordinary edit-load root
was moved without modifying its contents to
`build/bridge-edit-load-proof-pre-encoding-20261016/`. This includes the historical
1.21.11, 26.1.2 and 26.2 results; their prior successful offline revalidation and
analysis remain archived. Do not confuse those old artifacts with the candidate.

Run the same newly built companion in **two fresh JVMs for each exact tuple**:
Paper 1.21.11-132 + WorldEdit 7.4.2 on Java 21, Paper 26.1.2-74 + WorldEdit 7.4.3
on Java 25, and Paper 26.2-133 + WorldEdit 7.4.5 on Java 25. Use the existing
ordinary `-EditLoad -Players 32 -Samples 400 -Repetitions 2` harness, not a new
performance intervention or a repeat of the encoding comparison. Preserve all
raw windows, wire frames, JFR and failure files; independently analyze each
completed host. No production source, target, quota or published JAR changes
between these runs. This is functional compatibility regression, **not** a
controlled hardware-cost comparison or native GUI/input acceptance.

All six runs and all three independent offline analyses **pass**:

| Exact host | Proof UTC | Applies per fresh JVM | Raw/JFR ticks | Proof SHA-256 |
| --- | --- | --- | --- | --- |
| Paper 1.21.11-132 / WE 7.4.2 / Java 21 | 2026-10-16T20:18:17.374Z | 578 / 583 | 2,400 | `566aac45865a58950daf5763265b8567305d8108b029929abb80ed8c6b01792a` |
| Paper 26.1.2-74 / WE 7.4.3 / Java 25 | 2026-10-16T20:21:19.247Z | 586 / 577 | 2,400 | `9dbfa4402aea9ce3fec5705d160ef2960616df39a6f9177db69dd9d1fdd703c0` |
| Paper 26.2-133 / WE 7.4.5 / Java 25 | 2026-10-16T20:24:23.674Z | 601 / 594 | 2,400 | `272d0662f1a628ad9eb689dde710fefdccf301f29ce31239de1f6a60cbabc89d` |

Every proof pins the same candidate companion SHA-256 `2fc45532...877a03a`
(full value above). Totals: **3,519 applies, 7,200 exact raw/JFR tick pairs and
48 editor world/undo audits**, with actual-peer authority, stale/foreign revision,
duplicate replay, canonical bounds and CUI checks. Quiet source work, measured
chunk I/O/generation, expiry and final connections/queue/tasks are zero. Every
owned server exits normally. Original and failed studies remain preserved.

Current ordinary evidence is in `build/bridge-edit-load-proof/` for 1.21.11,
and its `26.1.2/` and `26.2/` children. Keep the ordinary host result separate
from the four-arm moving encoding study: the compatibility success does **not**
reverse its rejected performance gate. No other Paper/WorldEdit tuple, Folia,
FAWE, proxy or native concurrent render/input compatibility is certified here.

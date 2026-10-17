# Real WorldEdit Selection Properties

Status: **all three exact hosts passed and independently revalidated**. This is a correctness
study, not another scheduler tuning or performance comparison. Production client,
shared transaction and companion sources are unchanged. The fixture is test-only.

## Predeclared Matrix

Run the same 144 cases on each exact existing tuple: Paper 1.21.11-132 with
WorldEdit 7.4.2/Java 21, Paper 26.1.2-74 with WorldEdit 7.4.3/Java 25 and Paper
26.2-133 with WorldEdit 7.4.5/Java 25. Server/WorldEdit hashes come from the existing
tuple registry. One owned headless actor uses actual Minecraft custom payloads;
this does not test native concurrent gameplay or a client GUI.

For both exact Cuboid and ExtendingCuboid selectors, all eight input
Pos1/Pos2 axis orientations receive six targets: disjoint positive and negative
resize/translation, a mixed six-face adjustment, collapse to one block, unchanged
bounds and a pure shift. All 96 successful cases replay the identical request.
The ordinary Cuboid retains each input point order; the public Extending copy
constructor first normalizes to minimum/maximum points, which must then remain
canonical. Thus the Extending seeds verify normalization, not eight distinct
post-construction orders. Another 48 cases request mixed expansion/contraction under expand-only,
contract-only or shift-only authority and must refuse without partial mutation.

The expected ordered integer points are computed independently of production
drafting and WorldEdit resize calls. The fixture reads real selector class and
ordered points on the owning server thread before and after each request.
The driver checks captured bounds/permissions, acknowledgment epoch/request ID,
canonical CUI coordinates, byte-identical replay with no extra CUI, exact
capture/commit/apply totals, loaded control-area block hash, no WorldEdit undo
entry, no idle source work and zero final connections/queue/tasks. Proof is
written only after an orderly zero-exit server shutdown and helper cleanup.
Raw capture/ack/replay bytes and CUI messages remain in local evidence; the
offline checker re-decodes them rather than trusting result flags alone.

## Sources And Scope

Official versioned [CuboidRegion 7.4.2](https://github.com/EngineHub/WorldEdit/blob/7.4.2/worldedit-core/src/main/java/com/sk89q/worldedit/regions/CuboidRegion.java)
and [7.4.5](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-core/src/main/java/com/sk89q/worldedit/regions/CuboidRegion.java)
show ordered corner storage and directional expansion/contraction. The public
[selector contract](https://docs.enginehub.org/javadoc/com.sk89q.worldedit/worldedit-core/release/com/sk89q/worldedit/regions/selector/CuboidRegionSelector.html)
and [versioned implementation](https://github.com/EngineHub/WorldEdit/blob/7.4.2/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/CuboidRegionSelector.java)
establish `learnChanges` and `explainRegionAdjust`; these production calls and the
clone-before-apply validation are retained. No permission cache, new worker thread,
direct world edits or alternate CUI implementation is introduced.

The probe does not establish arbitrary-selector support, post-collapse
re-expansion UX, world-border policies, protection-plugin support, restart/proxy
behavior or production performance acceptance. Previous cost gates remain open.

## Reproduction

```powershell
./tools/test-paper-selection-properties.ps1 -Java21 <jdk21> -Java25 <jdk25> -AcceptEula
```

The launcher requires the already verified local Paper/WorldEdit/bootstrap
artifacts. It never downloads or opens a game window. Every version gets a fresh
owned fixture under `build/bridge-selection-properties/<version>`; a previous
attempt is refused, not overwritten. Servers are loopback-only, below-normal
priority, two processors and at most 1 GiB heap. The pinned 26.2 MCProtocolLib
helper remains headless. Existing worlds, native proofs and published JARs are
not used as writable test data.

## Preparation Checks

- 20 property-generator/evidence rejection groups pass after the correction below.
- Full Node harness before that correction: **171 pass**; this includes the first
  19 groups, not a claim that pure tests established WorldEdit's runtime semantics.
- Restored companion SHA-256 remains
  `2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.
- Real host results must be recorded separately after execution.

## Executed Rows

| Exact host | Cases | Applied status | Denied | Replays | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Paper 1.21.11-132 / WorldEdit 7.4.2 / Java 21 | 144 | 96 | 48 | 96 | PASS |
| Paper 26.1.2-74 / WorldEdit 7.4.3 / Java 25 | 144 | 96 | 48 | 96 | PASS |
| Paper 26.2-133 / WorldEdit 7.4.5 / Java 25 | 144 | 96 | 48 | 96 | PASS |

The 1.21.11 proof completed at **2026-10-17T06:26:08.035Z**, using
`dd342201ffa186650e425c77333b2d1b9e340414`. Its SHA-256 is
`4382224233e0fb8cc546b9da854f47a647d518c074d44438c293c666f6cc89b4`.
It was independently re-read through the offline raw-frame/CUI checker, with
all seven recorded harness hashes reconciled and a zero-exit, non-forced server
stop. Evidence is local under `build/bridge-selection-properties/1.21.11`.

It records exactly 144 captures, 144 commit attempts, 96 APPLIED responses and
432 total replies, with no throttled/malformed input. APPLIED includes **16
unchanged-bound cases**, not 96 distinct mutations. The remaining 80 successes
update CUI once; all 96 replay acknowledgments are byte-identical without another
apply. After player departure, connections, pending frames and dispatch tasks
are zero. The loaded control-area hash and empty undo audit pass; this is not a
hash of every generated chunk or a production performance measurement.

The test-only fixture SHA-256 is
`8e6e6f344f55ef890e0dfbd9c49f24b17a56d358e684d75f1502c148d6237995`;
the companion remains the unchanged hash recorded above.

All three rows have the same exact counter deltas and cleanup/audit outcomes.
Totals: **432 cases, 288 APPLIED statuses (48 no-ops), 144 refusals and 288
byte-identical replay acknowledgments**. Each exact host uses a separate fresh
world, one headless connection and one orderly server JVM. These are three
correctness runs, not 432 concurrent players or measurements of production cost.

| Minecraft | Completed (UTC) | Proof SHA-256 |
| --- | --- | --- |
| 1.21.11 | 2026-10-17T06:26:08.035Z | `4382224233e0fb8cc546b9da854f47a647d518c074d44438c293c666f6cc89b4` |
| 26.1.2 | 2026-10-17T06:28:40.858Z | `fc5b4bff64902fec0c8295946e9666d4fc40412cf31ec16f16379151b2f090fc` |
| 26.2 | 2026-10-17T06:31:14.387Z | `a2276bf87a55d69d07e7570b1a1f61f8dc1519b253178060e216c3071b337552` |

The first two rows record `dd34220`; the third records `e4babda`, which adds only
documentation while the run continued. The seven harness hashes and both test/
companion artifacts are identical in all rows. Raw logs have SHA-256:

- 1.21.11: `04121ec62084149c77e11d169c9a1cd1ca02d94a6efb508515dbceb23b6b09b9`
- 26.1.2: `4bfc34321558bc511de9301d427fcbc4ef9e4f439ac52f823a3dbbee8ea71039`
- 26.2: `6a6528870bb4ec0f232f8f5e255a051acf90837f0dc9c871aef28b57a8f22c6f`

At **2026-10-17T06:31:43.053Z**, the separate local
`build/bridge-selection-properties/revalidate.mjs` re-decoded every raw capture/
ack/replay, reconciled all **864 canonical snapshots** directly against the
console logs, checked **12 complete counter snapshots**, all recorded artifact/
harness hashes, owning-server quit barriers, audit completion and orderly
shutdown fields. It passed all three proofs. Raw fixtures and failed attempts
remain local; only source, research and this bounded report are committed.

## Post-Matrix Regression

The complete `tools/test-server-bridge.ps1 -Java21 <jdk21> -Java25 <jdk25> -Offline`
finished successfully after the three host JVMs had stopped. Fresh evidence is
under `build/bridge-validation`; the previous directory is preserved at
`build/bridge-validation-before-selection-properties-20261017`. The combined
console transcript is `build/bridge-selection-properties/full-regression-console.log`.

- **114 pure/host-ABI Java tests on each of Java 21 and Java 25** pass. This includes
  42 tests in the shared protocol/client modules, not just the 72 in server modules.
- **172 Node harness groups** pass, including all 20 new property/oracle and raw
  evidence rejection groups.
- Both test-only diagnostic agents, **13 JFR-reader groups and three YAML/config
  groups per JVM** pass. These are reader/agent self-tests, not a fresh load study.
- All twelve deterministic admission-order/offset conditions per JVM retain
  **60/60 ordinary replies**, including the 31-attacker cases.
- All **six Fabric/NeoForge native payload-codec adapters** pass their owning
  Java 21/25 runtime checks. No graphical Minecraft client is launched by this step.
- The companion stays Java 21 bytecode without client/test/provided classes.
  Its hash remains `2fc45532e48eb1208dab02cf1134835888f58525c7efb94db75f624ea877a03a`.
  Published 1.8.0 Fabric/NeoForge JAR hashes remain respectively
  `912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0` and
  `ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c`.

No production transaction, scheduler or client fix was required by this matrix.
The first attempt's oracle correction is not advertised as a gameplay fix. The
existing mixed-cost admission limitation, strict cost acceptance, native
concurrent gameplay, ungraceful/backend-switch cases and unimplemented remote
features remain open; these functional results do not substitute for them.

## Preserved First Attempt

The first 1.21.11 run on `f24a05f` failed before the first reversed Extending
operation: the fixture copy constructor normalized X while the initial oracle
expected reversed X. **81 cases** had completed; this attempt is not accepted.
The server stopped normally and no success proof was written. Its complete
world/log/failure rows are preserved under
`build/bridge-selection-properties-first-attempt-20261017/1.21.11`.

This is a reproduced **fixture/oracle assumption error**, not a production
transaction defect. Official [Extending 7.4.2](https://github.com/EngineHub/WorldEdit/blob/7.4.2/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/ExtendingCuboidRegionSelector.java)
and [7.4.5](https://github.com/EngineHub/WorldEdit/blob/7.4.5/worldedit-core/src/main/java/com/sk89q/worldedit/regions/selector/ExtendingCuboidRegionSelector.java)
both normalize the public copy constructor. The corrected oracle explicitly
distinguishes input orientation from effective point orientation; it does not
force an artificial selector state or relax any mutation/authority check.

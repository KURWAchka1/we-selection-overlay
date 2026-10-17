# Real WorldEdit Selection Properties

Status: **1.21.11 passed; 26.x live rows pending**. This is a correctness
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
| Paper 26.1.2-74 / WorldEdit 7.4.3 / Java 25 | - | - | - | - | Pending |
| Paper 26.2-133 / WorldEdit 7.4.5 / Java 25 | - | - | - | - | Pending |

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

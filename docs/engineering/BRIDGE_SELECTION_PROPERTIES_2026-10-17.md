# Real WorldEdit Selection Properties

Status: **harness prepared; live rows not yet accepted**. This is a correctness
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

# Complete loaded-chunk command previews

## State and chunk refresh (2026-10-03)

The large grass-to-dirt regression was reproduced on Fabric 1.21.11: one cell
changing after capture invalidated every captured cell before publication.
The `set`/`replace` provider has no neighbor-dependent source reads under the
supported literal/global masks. Repair that cell with the same parsed WorldEdit
BlockReplace and masks; clear its former change before replacing its original
state. Keep all other source data and random choices. Validate before assembling
output, then retain the displayed snapshot while assembling a refresh.
This is incremental live capture, not a globally atomic world snapshot.

Only block-state changes matter for the supported syntax: changing inventory,
furnace timers or other NBT does not invalidate a state-only result. WorldEdit's
[BaseBlock equality](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/world/block/BaseBlock.java)
also compares NBT; using it as a whole-job invalidation predicate can restart
unrelated previews. Full blocks are still captured when an actual state changes.

Chunk gains are coalesced after the current result. The expanded source is read
under the same work budget, and a ready snapshot remains visible until replaced.
This does not yet implement selective reuse of source tiles for chunk expansion.
Chunk losses, changed selection membership, permissions, masks and session mode
still invalidate; dependent operations such as hollowing do not use point repair.
No world writes, worker-thread Minecraft access or larger tick budget are added.

The regression fixtures keep a captured source divergent at every server tick, assert
an unchanged cancellation epoch, and stage nine chunk notifications backed by
actually loaded native chunks. They also compare point repairs to fresh results
and check that unchanged weighted cells retain their samples.

## Scope

Preview the selection intersected with actual client-loaded chunks. Do not cap
the number of scanned or displayed blocks, force chunks to load, or use a fixed
preview distance. Keep WorldEdit permissions, supported-pattern validation and
the user's WorldEdit edit limit. Work deadlines schedule computation, not truncate
the result. Arbitrarily large cold inputs cannot be instantaneous.

## Primary references inspected

- [Litematica section dispatcher](https://github.com/maruohon/litematica/blob/pre-rewrite/fabric/1.21.1-masa/src/main/java/fi/dy/masa/litematica/render/schematic/ChunkRenderDispatcherLitematica.java):
  queued rebuilds and separately budgeted uploads. This branch's threaded path
  is commented out; it is not proof of safe parallel Minecraft access.
- [Litematica section buffers](https://github.com/maruohon/litematica/blob/pre-rewrite/fabric/1.21.1-masa/src/main/java/fi/dy/masa/litematica/render/schematic/ChunkRendererSchematicVbo.java):
  persistent geometry, transparent-index sorting and explicit resource disposal.
- [Sodium section manager](https://github.com/CaffeineMC/sodium/blob/dev/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java):
  section ownership, immutable build inputs, cancellation, visibility and upload
  scheduling. Do not copy its OpenGL renderer into the mod or require Sodium.
- [Fabric rendering](https://docs.fabricmc.net/develop/rendering/world): separate
  immutable render inputs from drawing and use Blaze3D, not raw OpenGL. Verify
  signatures against actual Minecraft jars for every adapter; 26.2 differs.
- [WorldEdit extent API](https://worldedit.enginehub.org/en/latest/api/concepts/extents/)
  and [official source](https://github.com/EngineHub/WorldEdit/tree/version/7.4.x):
  retain memory-only writes and the original selection for boundary predicates.

These are architecture references, not copied implementations or new bundled
dependencies. The runtime remains independent of Litematica and Sodium.

## Decisions

1. Enumerate loaded chunk coordinates before scheduling server work. Intersect
   their columns with selection bounds before block iteration. A huge unloaded
   bounding box must not produce a huge loop or allocation.
2. Store states in section palettes and local indices. Do not retain a map entry,
   vector and change object per cell. Missing data is unknown, never fabricated
   air. Uniform sections hold one index instead of an array. Varied index arrays
   start at one byte and promote without truncation to two or four bytes for
   larger palettes. Overlay reads the selected vertical column
   plus its upper context.
3. Run official patterns/masks against loaded candidates, but keep the original
   WorldEdit region for boundary predicates. Chunk edges are not selection edges.
4. Cull hidden internal faces; keep every remaining cell. Upload section geometry
   once. Camera movement changes transforms and transparent indices, not vertices.
5. Discard stale generations. Native world reads stay on their owning thread.
   Never spin up a CPU-sized pool or block a GUI callback on a whole selection.
6. Dispose GPU resources on invalidation, world change and shutdown. Resource
   reload rebuilds model-dependent buffers. Re-entering loaded territory must
   regenerate the complete result, not show a permanently capped prefix.

## Verification gates

All six native runtimes and both universal packagers; official comparisons for
every supported shape; selections beyond 65,536 input and 2,048 visible cells;
partial/hugely unloaded bounds; cancellation; source changes; resource reload;
clipboard restoration; unchanged world/history; frame and memory measurements.
Do not infer unlimited hardware capacity or zero FPS cost from capped-FPS tests.

## Additional Region Providers and Source Rendering

The inspected [official region declarations](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/command/RegionCommands.java)
and [EditSession algorithms](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/EditSession.java)
do not expose a single side-effect-free, resumable preview dispatcher. Invoking
ordinary commands would risk session/clipboard/history mutation and native
generation. The implemented providers keep memory-only writes and incremental
steps: center rounding, natural-material layers, exterior flood/Manhattan shells,
line rasterization and spline dilation. Curves use EngineHub's official
Kochanek-Bartels interpolator. Flora reuses EngineHub's public plant patterns
and NoiseFilter2D/RandomNoise instead of a private distribution table.

Do not call Operations.complete on a whole region from a GUI or server tick.
Flood processing, shell expansion/merging and geometry dilation must yield too,
not just block writes. An unknown connected exterior invalidates hollowing;
unloaded blocks are not substituted with invented air. Convex vertex order is
revalidated separately from bounds/membership, since identical hulls can produce
different splines. Complete coverage and remaining providers are tracked in
`COMMAND_PREVIEW_COVERAGE.md`.

Legacy input uses ParserContext.setTryLegacy and the official LegacyMapper;
numeric IDs are not independently mapped. Compare partial models against direct
WorldEdit writes with native side effects disabled, then explicitly document
that normal execution can drop unsupported blocks or propagate fluids.

A ghost button over an unchanged stone cube does not communicate replacement.
Publish an immutable section-local BitSet mask of *all replaced non-air source
cells*, separately from culled visible ghost faces. Existing native, Indigo,
block-entity and optional Sodium visibility hooks consult this render-only mask.
Never change client block storage or collisions. Diff masks by section, invalidate
only changed sections plus their immediate neighbors, and coalesce invalidations
under a per-tick target. Cancellation restores the same source sections without
a global renderer reload. No new rendering dependency is introduced.

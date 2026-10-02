# Complete loaded-chunk command previews

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

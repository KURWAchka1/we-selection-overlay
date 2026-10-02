# Live Command Preview (1.8.0)

## User workflow

Type a normal WorldEdit command in chat. Each edit requests a textured ghost
without a typing-pause timer. Enter still submits the original command
to WorldEdit; Escape clears the preview without executing anything.

Supported commands: `//set`, `//replace` / `//re` / `//rep`, `//walls`,
`//faces` / `//outline`, `//overlay`, `//center` / `//middle`, `//naturalize`,
`//hollow`, `//line`, `//curve`, and `//flora`. Literal block IDs, block states, comma mixtures,
and percentage weights use WorldEdit's parser. Replace supports literal block
masks and `#existing`; omitted masks mean existing non-air blocks.
Legacy numeric IDs and `id:data` use WorldEdit's own LegacyMapper, including
replace masks and percentage mixtures. See [command coverage](COMMAND_PREVIEW_COVERAGE.md)
for arguments, shape constraints and intentionally unsupported operations.

Added/replaced cells use lightly accented, full-bright textures. Removed cells
use red volume markers, not fake air blocks. Unchanged cells have no ghost.
Fluids and non-model content use volume markers rather than claiming accurate
fluid or block-entity rendering. Changed original non-air blocks are suppressed
in native terrain rendering while the result is displayed, including the interiors
of volumes culled out of the ghost mesh. This makes buttons, slabs, stairs, fences,
panes and trapdoors visibly replace their source rather than sit on top of it.
Client/server block data and collisions remain intact. Unchanged neighbors stay
visible, with exposed faces restored. Cancelling or changing the draft restores
the source. This predicts direct writes, not subsequent physics or random ticks:
an unsupported button can drop when the actual command runs.

The compact chat HUD shows exact added/replaced/removed counts and up to three
of the largest tracked block transitions (the histogram tracks at most 512
distinct pairs; this does not limit the total change counts).
Random mixtures display an explicit sample warning.
Flora uses official temperate/desert plant patterns and the official density mask;
like weighted block mixtures, it is an illustrative stable random sample.
The sample stays stable for the same draft and source state. WorldEdit rolls
again on execution: individual random placements are not guaranteed to match.

Preview is enabled by default. Toggle it in `O` settings or with configurable
`F8`, including while chat is open. Clipboard preview remains off by default.
When a supported draft is present, clipboard rendering and background scanning
pause; its enabled preference, clipboard, transforms, and cache are preserved.
Closing chat/removing that draft restores normal clipboard behavior.

## Safety and Scheduling

Only integrated singleplayer WorldEdit sessions are currently supported. Remote
CUI still provides the existing selection overlay, but cannot expose the server's
WorldEdit session, clipboard, permissions, or command calculation. No custom
server plugin/protocol or Paper workaround is introduced.

The preview does not create an EditSession, invoke commands, modify world blocks,
touch undo history, or force-load chunks. It reads on the integrated server
thread and writes only an isolated memory extent. Normal WorldEdit submission
is deliberately untouched.

Unsupported flags (apart from line/curve `-h`), NBT, dynamic/script/expression patterns, complex masks,
unknown extension masks, inventory mode, and world overrides fail visibly
instead of displaying a misleading approximation. Permissions, build height,
and the WorldEdit change limit are checked. Only the intersection of the
selection and actual client-loaded chunks is calculated for region filling.
Overlay/flora include the cell above the surface; line/curve include their
thickness and spline overshoot, still clipped to client-loaded chunks.
Hollow requires its complete source bounds plus a one-cell read halo to be
loaded: a missing connected exterior cannot be safely guessed as air or solid.
An entirely unloaded
selection has no result; a partly loaded selection previews its loaded part.
The HUD identifies this scope. Arriving chunks do not cancel an in-flight scan:
its first result completes, then the expanded footprint is captured in the
background while that result stays displayed. Chunk removal invalidates the
source immediately to stop reading or displaying unloaded input.
Chunks outside the command's source footprint do not restart its
calculation; that footprint includes required surface, thickness and curve context.
There is no scanned-cell, displayed-block or fixed-distance result cap.
An enormous unloaded bounding box is intersected with loaded chunk columns
before iteration, rather than traversed to discover that most cells are absent.

At most one calculation slice runs per integrated-server tick, with a shared
2 ms target checked between cells in batches of up to 1,024 steps. Capture,
simulation, output conversion/statistics, and source validation proceed in the
same slice whenever time remains; larger jobs resume over multiple ticks.
Rapid edits replace the single queued request rather than adding tasks per
character. Repeated CUI packets request source verification without discarding
the result or re-rolling a random sample. A slow external API call cannot be preempted,
so this is a work budget, not a hard latency guarantee. Obsolete drafts cancel
by generation token; stale results cannot be published into another world.
Selection membership and captured block states are incrementally revalidated.
For `//set` and `//replace`, a changed source cell is recaptured and reapplied
through the existing WorldEdit masks/pattern without discarding the whole scan
or re-rolling unchanged random cells. Verification precedes output assembly;
refreshing a displayed result keeps its snapshot until replacement output is ready.
Neighbor-dependent providers still invalidate on relevant state changes rather
than applying an incorrect single-cell repair. Supported syntax does not depend
on changing block-entity NBT, so NBT-only activity is not a reason to restart.
The captured input is read and verified incrementally, not an atomic freeze of
the entire world; updates during output assembly are picked up by the next pass.
Captured states and results use section palettes/local indices instead of
per-cell retained hash-map entries and change objects. Uniform sections need no
per-cell index array; varied palettes use one, two or four bytes per index as
needed without discarding states. Statistics reuse block
state names. The original region defines walls/faces; chunk clipping does not
introduce artificial selection edges. Overlay retains the complete selected
vertical column plus the block above it in each loaded chunk.
Native model templates survive draft changes, with a 256-state cache cap and
baked-model identity checks after resource reloads. Geometry preparation has a
separate 2 ms per-render-call target. Textured vertices are built once per section
and kept in persistent Blaze3D GPU buffers. Completed sections are uploaded
incrementally; every section is included, with native frustum culling and no
fixed preview radius. One render pass draws visible sections back-to-front.
Camera movement may update transparent indices with a separate 1 ms target,
but does not rebuild textured vertices. Model/resource changes and obsolete
drafts dispose GPU geometry. Source suppression dirties only changed sections
and their immediate neighbors through native section invalidation. A coalesced
1 ms per-tick queue avoids whole-world reloads and never alters chunk block data.
Removal/fluid markers are merged incrementally inside the same preparation
budget, not globally sorted in one frame; offscreen/unloaded markers are skipped.

There is no universal instantaneous-result guarantee for cold/large selections:
reading and verifying their blocks still costs real work. The implementation
removes artificial waiting without turning large jobs into an unbounded task.

Advanced options are saved separately beside the existing mod settings as
`command-preview.properties`; existing settings are not migrated or discarded.

| Key | Default | Accepted range |
| --- | --- | --- |
| `enabled` | `true` | boolean |
| `opacity` | `0.65` | 0.15-0.9 |
| `statistics` | `true` | boolean (transition details) |

Manual property changes are loaded on the next game start. The on/off setting
is saved immediately. Obsolete `debounceMillis`, `maxCells`, `maxRenderBlocks`
and `renderDistance` values are ignored even in existing configurations;
enabled, opacity and statistics settings are retained. Work budgets are not
hardware-specific FPS promises. See [performance research](COMMAND_PREVIEW_PERFORMANCE_RESEARCH.md)
for the inspected primary implementations and architecture decisions.

## Implementation references

- [WorldEdit extent model](https://worldedit.enginehub.org/en/latest/api/concepts/extents/)
- [Fabric world rendering and immutable extraction/drawing state](https://docs.fabricmc.net/develop/rendering/world)
- [WorldEdit patterns and relative weights](https://worldedit.enginehub.org/en/latest/usage/general/patterns/)
- [WorldEdit clipboard/session behavior](https://worldedit.enginehub.org/en/latest/usage/clipboard/)
- [WorldEdit 7.4 source](https://github.com/EngineHub/WorldEdit/tree/version/7.4.x)
- [EngineHub's shared CUI protocol](https://github.com/EngineHub/WorldEditCUI-Protocol)

The command argument tokenizer is WorldEdit's public-but-internal
`CommandArgParser`; compatibility is verified against the bundled target
WorldEdit versions, not promised for every future parser version.
`BlockReplace`, official pattern/mask factories, flora patterns/density mask,
Kochanek-Bartels interpolation, and memory-bound masks provide
the domain behavior. Resumable iterators mirror the verified RegionShape and
LayerVisitor/GroundFunction rules because RegionVisitor.resume in these versions
does not yield to a per-tick RunContext budget.

Shared domain code lives in `shared/command-preview`. Version/loader adapters
provide lifecycle, native block-model submission, chat input, and WorldEdit player
adaptation. The universal mixin plugins explicitly register the chat hook for
every supported runtime. Verification and reproduction are in `TESTING.md`.
`tools/sync-command-preview.mjs` ports the canonical Fabric 26.2 native preview
files to the other five adapters; it does not replace shared domain code or
unrelated screens. `tools/prepare-command-tests.mjs` generates ignored,
version-specific test sources. Neither script is needed by end users.

NeoForge's universal artifact never exports EngineHub packages. It uses the
installed official CUI handler when present; otherwise each runtime has a
privately relocated copy of the corresponding official packet codec, registered
through NeoForge's optional payload API. Public-API reflection is resolved once
at startup. This avoids JPMS split packages without replacing WorldEdit's codec
or registering its channel twice.

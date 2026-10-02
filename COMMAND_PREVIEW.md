# Live Command Preview (1.8.0)

## User workflow

Type a normal WorldEdit command in chat. Each edit requests a textured ghost
without a typing-pause timer. Enter still submits the original command
to WorldEdit; Escape clears the preview without executing anything.

Supported initial commands: `//set`, `//replace`, `//walls`, `//faces`,
`//outline`, and `//overlay`. Literal block IDs, block states, comma mixtures,
and percentage weights use WorldEdit's parser. Replace supports literal block
masks and `#existing`; omitted masks mean existing non-air blocks.

Added/replaced cells use lightly accented, full-bright textures. Removed cells
use red volume markers, not fake air blocks. Unchanged cells have no ghost.
Fluids and non-model content use volume markers rather than claiming accurate
fluid or block-entity rendering. Original blocks stay intact and visible;
this is a ghost overlay, not a client-side simulation of all world effects.

The compact chat HUD shows exact added/replaced/removed counts and up to three
of the largest tracked block transitions (the histogram tracks at most 512
distinct pairs; this does not limit the total change counts).
Random mixtures display an explicit sample warning.
The sample stays stable for the same draft and source state. WorldEdit rolls
again on execution: individual random placements are not guaranteed to match.

Preview is enabled by default. Toggle it in `O` settings or with configurable
`F8`, including while chat is open. Clipboard preview remains off by default.
When a supported draft is present, clipboard rendering and background scanning
pause; its enabled preference, clipboard, transforms, and cache are preserved.
Closing chat/removing that draft restores normal clipboard behavior.

## Safety and limits

Only integrated singleplayer WorldEdit sessions are currently supported. Remote
CUI still provides the existing selection overlay, but cannot expose the server's
WorldEdit session, clipboard, permissions, or command calculation. No custom
server plugin/protocol or Paper workaround is introduced.

The preview does not create an EditSession, invoke commands, modify world blocks,
touch undo history, or force-load chunks. It reads on the integrated server
thread and writes only an isolated memory extent. Normal WorldEdit submission
is deliberately untouched.

Unsupported flags, NBT, dynamic/script/expression patterns, complex masks,
unknown extension masks, inventory mode, and world overrides fail visibly
instead of displaying a misleading approximation. Permissions, build height,
unloaded chunks, and the WorldEdit change limit are checked. The initial
bounding-box cap is conservative even for sparse/non-cuboid selections.

At most one calculation slice runs per integrated-server tick, with a shared
2 ms target checked between cells in batches of up to 1,024 steps. Capture,
simulation, output conversion/statistics, and source validation proceed in the
same slice whenever time remains; larger jobs resume over multiple ticks.
Rapid edits replace the single queued request rather than adding tasks per
character. Repeated CUI packets request source verification without discarding
the result or re-rolling a random sample. A slow external API call cannot be preempted,
so this is a work budget, not a hard latency guarantee. Obsolete drafts cancel
by generation token; stale results cannot be published into another world.
Selection membership and captured blocks are incrementally revalidated.
Native model templates survive draft changes, with a 256-state cache cap and
baked-model identity checks after resource reloads. Geometry preparation has a
separate 2 ms per-render-call target and publishes a complete immutable batch
once, not partial batches that appear to rebuild. Render limits remain separate
from statistics. The HUD explicitly reports truncated visual output.

There is no universal instantaneous-result guarantee for cold/large selections:
reading and verifying their blocks still costs real work. The implementation
removes artificial waiting without turning large jobs into an unbounded task.

Advanced options are saved separately beside the existing mod settings as
`command-preview.properties`; existing settings are not migrated or discarded.

| Key | Default | Accepted range |
| --- | --- | --- |
| `enabled` | `true` | boolean |
| `opacity` | `0.65` | 0.15-0.9 |
| `maxCells` | `65536` | 256-262144 |
| `maxRenderBlocks` | `2048` | 64-8192 |
| `renderDistance` | `96` | 16-192 blocks |
| `statistics` | `true` | boolean (transition details) |

Manual property changes are loaded on the next game start. The on/off setting
is saved immediately. The obsolete `debounceMillis` property is ignored even in
existing configurations; other settings are retained. These limits are safeguards, not hardware-specific FPS
promises.

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
`BlockReplace`, official pattern/mask factories, and memory-bound masks provide
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

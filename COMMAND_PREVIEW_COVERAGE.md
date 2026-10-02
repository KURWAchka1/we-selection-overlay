# Command Preview Coverage (1.8.0)

This is an explicit support matrix, not a promise that every WorldEdit command
can run in a client preview. Normal commands are never invoked to generate a
preview. They can change session state, history, entities or world generation.

## Implemented Region Operations

| Command | Previewed arguments | Notes |
| --- | --- | --- |
| `//set` | pattern | Literal states, mixtures, relative percentages, numeric IDs/data |
| `//replace`, `//re`, `//rep` | optional mask, pattern | Literal block masks and `#existing` |
| `//walls` | pattern | Original shape, no artificial chunk-clipping edges |
| `//faces`, `//outline` | pattern | Original shape, including ceiling/floor |
| `//overlay` | pattern | First exposed ground in each selected column |
| `//center`, `//middle` | pattern | Official center rounding, including even-sized bounds |
| `//naturalize` | no arguments | Grass surface, three dirt layers, stone deeper; only natural materials |
| `//hollow` | thickness, optional fill pattern | Exterior flood and Manhattan shell; defaults match WorldEdit |
| `//line` | pattern, optional thickness, `-h` | Cuboid corners or convex vertices, in selection order |
| `//curve` | pattern, optional thickness, `-h` | Convex vertices; official Kochanek-Bartels interpolation |
| `//flora` | optional density (0-100) | Grass/sand plant patterns, default 5%; stable random sample |

All providers operate on isolated captured memory, respect a supported global
mask and WorldEdit permissions/change limits, and yield between incremental
steps. Numeric IDs are resolved by EngineHub, not a private mapping table.
Random execution draws a new sample. Preview describes direct block writes;
neighbor physics, fluid spread, dropped unsupported blocks and random ticks
are not simulated. Fluids/block entities use markers where native model preview
cannot faithfully represent them.

Hollow needs all source bounds and a one-cell halo loaded, since missing chunks
could contain a connection to outside air. It refuses unknown topology rather
than turning unloaded cells into artificial air. Region fills and surface
operations otherwise preview the loaded intersection. Lines/curves include
their generated footprint, never force-loading chunks.

`set`/`replace` refresh changed source cells independently; unchanged weighted
choices are retained. Other providers conservatively rebuild on state changes.
Newly loaded chunks are coalesced after the in-flight result instead of resetting
its progress; a displayed snapshot remains until the expanded result is ready.
Unloading required chunks and meaningful selection/session edits still cancel.

## Not Implemented Yet

| Group | Commands / modes | Required work before honest support |
| --- | --- | --- |
| Movement/repetition | `//move`, `//stack` | Snapshot source and loaded destinations; overlap-safe writes, direction and flags; never shift the real selection |
| Clipboard edits | `//paste`, `//cut`, transforms | Coordinate the existing clipboard preview and command ownership; never mutate clipboard/history during typing |
| Terrain smoothing | `//smooth`, `//snowsmooth` | Incremental official height-map/convolution adaptation; masks, iterations and snow layers |
| Generators | `//sphere`, `//hsphere`, `//cyl`, `//hcyl`, pyramids, forests | Placement, dimensions, generated footprint and native generation isolation |
| Expressions | `//generate`, `//deform` | Bounded/cancellable expression evaluation and validated read dependencies |
| World regeneration | `//regen` | Native world-generator access cannot be assumed safe, cheap or available remotely |
| Utility/flood operations | drain, fill, fix liquids, snow, thaw, green | Placement/radius, connected-component capture and command-specific masks |
| Biomes/entities/history/tools | set biome, remove entities, undo/redo, brushes | These are not static block replacement previews; need separate visual contracts |

Selection commands and clipboard transforms already update their existing
selection/clipboard displays after execution. They must not be executed on a
live LocalSession merely because chat contains a draft.

## Verification Contract

Compare each adapter to official WorldEdit writes in disposable test worlds,
with multiple selection types and clipped snapshots where the algorithm permits
them. Test global masks, defaults, invalid input, cancellation, random stability,
history/clipboard preservation, native source hiding/restoration and unchanged
Enter submission. Unsupported syntax must clear old output and report its state.
See `TESTING.md` for actual completed runs, not inferred compatibility.

## Primary References

- [EngineHub command catalogue](https://worldedit.enginehub.org/en/latest/commands/)
- [Official region command declarations](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/command/RegionCommands.java)
- [EditSession algorithms](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/EditSession.java)
- [Naturalizer layer semantics](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/function/block/Naturalizer.java)
- [Flora patterns](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/function/generator/FloraGenerator.java)

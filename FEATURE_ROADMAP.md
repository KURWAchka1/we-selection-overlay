# WorldEdit companion roadmap

Confirmed with the user: 2026-10-01. UI redesign is a separate prerequisite,
not a replacement for the features below. Preserve existing settings and features.

## Ordered milestones

1. **Command Result Preview** - initial scope implemented and verified in 1.8.0
   (2026-10-02; see COMMAND_PREVIEW.md and TESTING.md). Safely preview `//set`, `//replace`,
   `//walls`, `//faces` (including `//outline`), and `//overlay` before changing
   the world. Distinguish added, replaced, removed, and unchanged cells. Show
   statistics, support explicit on/off and a configurable keybind, invalidate
   stale results, and enforce computation/render limits. Investigate `//move`
   and `//stack` only after this foundation is verified.
   **Live chat workflow:** update the world-space ghost while the user types the
   normal WorldEdit command, before Enter. Do not require a separate command
   editor, intercept submission, or change what Enter executes. Incomplete or
   invalid drafts immediately discard the old result. Coalesce rapid typing into
   the latest queued draft without a deliberate typing-pause delay.
   **Clipboard coexistence:** retain the copied-object preview (off by default).
   A recognized command draft owns the preview channel, including calculation,
   incomplete/error states. Temporarily suppress clipboard rendering, without
   modifying its enable setting, contents, transforms, or cache. Restore it when
   the draft is removed or chat closes. Future paste preview reuses that renderer.
   **Random patterns:** support percentage mixtures through WorldEdit's parser;
   keep the displayed sample stable for an unchanged draft, and label it as a
   sample. Vanilla WorldEdit re-rolls on execution, so per-cell equality must
   not be promised. Never silently seed or alter WorldEdit's actual command.
2. **Interactive Selection Editing** - pending. Six cuboid face handles,
   configurable normal/fast steps, dimensions/volume HUD, and actual WorldEdit
   selection synchronization. Leave an extension point for polygon/convex vertices.
   Proposed interaction, visual and transaction contract (not implemented):
   [SELECTION_EDITING_DESIGN.md](SELECTION_EDITING_DESIGN.md).
3. **Advanced Clipboard Placement** - pending. Position, rotate, flip, reset,
   axis lock, grid snap, numeric offsets, origin/pivot, compact HUD, and explicit
   protected paste confirmation. Keep WorldEdit transforms as the source of truth.
4. **Block Inspector Actions** - pending. Isolate, Hide, Copy ID, Use as Mask,
   and Replace. Replace must reuse milestone 1's engine, with separate preview
   and execution actions and correct WorldEdit parsing.
5. **Slice / Cross-section Mode** - pending. X/Y/Z, layer coordinate, thickness,
   scrolling, and a compact indicator. Compose with isolation without modifying
   world blocks or WorldEdit selections.

## Non-negotiable requirements

- Preview does not execute commands, write world blocks, alter history, or use undo.
- Separate integrated/local WorldEdit capabilities from remote CUI-only data.
  Missing capabilities disable the relevant feature without breaking the overlay.
- Use official WorldEdit parsers and operations where supported. Do not silently
  approximate unsupported patterns, flags, masks, or server-only state.
- Compact, optional HUD; safe preview and destructive execution are distinct.
- Work in bounded increments, cache unchanged geometry, reject excessive work,
  cancel obsolete jobs, and keep Minecraft world access on its owning thread.
- Preserve selection shapes, grid/ruler, palette, inspector/isolation, clipboard,
  old configs, keybinds, and the new switchable interface styles.
- Continue the existing universal packaging: one multi-version Fabric JAR and
  one multi-version NeoForge JAR for 1.21.11, 26.1.x, and 26.2. Do not claim
  support for other 1.21.x versions or one cross-loader JAR without verification.
- Keep shared domain logic independent of loader/version adapters. No speculative
  compatibility claims or unfinished placeholder actions.

## Verification gates

For each milestone: focused domain tests, all six runtime builds, universal
artifact tests, then real-game checks of version-sensitive rendering and input.
For command preview additionally compare against WorldEdit's real result in a
disposable test world; verify no preview writes/history, cancellation, stale
selection/world changes, masks, no-op edits, failures, and work limits.

Do not mark a milestone complete merely because it compiles. Record tested
capabilities and remaining limitations in its implementation notes.

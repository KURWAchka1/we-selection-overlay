# Interactive selection editing

Status: implemented and six-runtime tested for the 1.8.0 development line,
2026-10-08. Milestone 2 of
[FEATURE_ROADMAP.md](FEATURE_ROADMAP.md). See TESTING.md for verified coverage
and remaining integration checks.

## Goal and scope

Resize a real WorldEdit cuboid directly in the world without changing blocks,
opening a large editor. Eight corner markers are visible only while the feature
is enabled and a supported selection is ready. Normal building, selection
rendering and mouse behavior remain unchanged away from these markers.

First scope: complete standard cuboid and extending-cuboid selections in an
integrated world with compatible local WorldEdit. Preserve the selector type
and Pos1/Pos2 identities. Do not convert another shape to a cuboid implicitly.
Polygon and convex vertex editing are future work, not placeholder controls.
Remote CUI-only sessions remain visualization-only: this phase adds no server
plugin, custom editing protocol or automatic chat-command fallback.

## Interaction

Use direct corner dragging plus a temporary hold-to-edit key for face controls.
The feature is opt-in. Expose its availability/settings in the compact O menu.
The native hold key defaults to G; Minecraft retains existing custom assignments.
Left Shift is the configurable
fast modifier; default steps are 1 and 8 blocks.

| State/action | Behavior |
| --- | --- |
| Feature disabled | No markers, HUD, selection reads or mouse interception. |
| Enabled, key not held | Show eight corner markers for a ready cuboid. Coalesce authoritative reads; intercept LMB only on a marker. No extra HUD or hotbar-wheel capture. |
| Left-drag a corner without the edit key | Aim at a marker and hold LMB. Keep normal camera/flight controls; the corner follows the live view ray at its original grab distance, with the initial off-center offset retained. Snap its coordinates and keep the opposite corner fixed. Release LMB to commit once. |
| Hold edit key | Show face handles and a small dimensions HUD. Start with the previously clicked face (UP initially). Movement/look remain available until a drag begins. |
| Click a face | Select that side at any visible distance before resizing. Accent its perimeter/handle. Camera movement alone never changes the remembered choice. Intersection works even when the center handle is outside the viewport. |
| Mouse wheel | Outward/inward resize of the targeted face by the normal step. Lock that face for the gesture. Update only the local draft. Release the edit key to commit once. |
| Fast modifier | Use the configurable fast step only during editing. Shift does not sneak or lower a flying player while serving as the active modifier; vanilla behavior returns on exit. |
| Left-drag a handle/corner while holding the edit key | Capture one face axis or the corner's view plane, temporarily release the cursor and freeze camera rotation/movement. Release LMB to commit once; restore mouse state without a camera jump. |
| Escape | Cancel an uncommitted draft immediately, hide the gesture HUD/face handles, and require key release before hold-mode re-entry. Enabled corner markers remain available. |
| Release edit key during a hold-mode drag | Cancel the incomplete drag. Direct corner drags do not require that key. Do not forward a still-held mouse button into an attack. |
| Chat/menu, lost focus, disconnect, changed world or changed authoritative selection | Cancel the draft; never treat these interruptions as a commit. |
| Commit pending | Permit only one outstanding request. Do not queue further gestures against an unconfirmed selection. |

While the explicit edit mode is active, construction/attack/use/pick clicks are
not forwarded to the world. The wheel cannot change hotbar slots during an edit.
Outside that mode, only LMB hits on visible corner markers start a gesture.
All other mouse input preserves vanilla behavior.
Use native input APIs/events where available, with narrowly scoped hooks only
where the supported runtime requires them. Verify capture and cursor restoration
on each runtime before calling this interaction implemented.

Face dragging changes only the selected face, never the opposite face or other
axes. Corner dragging changes its three adjoining sides together; the opposite
corner stays fixed. Every coordinate snaps to the configured block step.
The face cannot cross the opposite face: the minimum dimension is one block.
Wheel direction is always outward/inward, independent of camera orientation.
Precise hold-mode dragging uses the captured world axis and camera projection,
not camera yaw heuristics. Free corner dragging instead uses the current camera
ray at a fixed captured depth, avoiding view-plane singularities during turns.
It updates only the draft during world rendering, with a final update on release;
movement input, camera capture and velocity are not overridden in this mode.
For a nearly end-on face axis, do not allow an unstable jump: use the
wheel as the reliable fallback. Integer snapping must behave consistently at
negative coordinates, from inside the selection and on all six faces.

## Appearance

The world remains the main view. There is no full-screen background, blur,
sidebar, persistent toolbar, luminous wall or filled selection volume.

- Eight high-contrast diamond corner markers when enabled, with dark interiors,
  screen-stable sizes and slightly larger hit targets. Hover/capture accents
  only that marker. Six face-center handles are additionally visible while held.
- Face-center handles also have stable apparent size and larger interaction
  targets. Inactive handles are quiet neutral tones.
- Only the targeted/captured handle and face perimeter receive a clear accent.
  Reuse the current UI theme and overlay line-width/color settings where relevant.
  Do not brighten the complete cuboid or add a second full grid.
- During a draft, show its thin outer contour and a subdued original contour
  for comparison. Temporarily suppress the original grid only in this edit view;
  never change the saved overlay/grid/ruler settings.
- One compact, noninteractive HUD above the hotbar: dimensions, volume and the
  active face's signed offset, or X/Y/Z offsets for a corner. Example content: `24 x 12 x 18`, `5,184 blocks`,
  `+Y: +3`. No permanent shortcut legend or tutorial text.
- Reuse UiChrome, existing font assets and style tokens. Keep safe margins from
  the crosshair, hotbar, subtitles and screen edges. Stack the two short rows at
  small logical resolutions; do not shrink everything into unreadable text.
- Short opacity-only appearance/hover transitions, respecting Reduced Motion.
  Never animate the selected block coordinates, lag behind the cursor, pulse
  handles or move hitboxes for a decorative transition.
- If the normal overlay is off, show the minimum edit contour only while editing
  and restore the unchanged preference afterwards.

These are visual requirements, not approved pixel measurements. Validate against
actual screenshots at GUI scales 2/3/4, bright terrain and dark interiors.

## Authoritative WorldEdit transaction

The displayed draft must not be installed into SelectionManager or a native
WorldEdit session while dragging. Keep one immutable origin snapshot plus six
integer draft bounds. WorldEdit remains the source of truth.

1. Acquire the actor/session/selector on the integrated server thread through
   the existing loader adapter pattern. Reject incomplete or unsupported
   selections and unsupported session world overrides.
2. Read the selector identity, its exact ordered corners, world and player
   identity, and relevant permissions. Publish only immutable client data.
3. Compute proposed bounds locally. Use inclusive WorldEdit coordinates for
   dimensions (`max - min + 1`) and `max + 1` for rendered outer surfaces.
   Validate world height, coordinate arithmetic and dimensions. Volume overflow
   must not become a negative counter or an accidental resize limit.
4. On commit, reacquire and compare the authoritative origin and permissions.
   Reject stale requests rather than overwriting an axe/command selection change.
   Identical CUI resends alone are not evidence of a changed selection.
5. Apply the one-axis face delta or three-axis corner deltas with the public
   CuboidRegion expansion/contraction route, after prevalidating the entire
   change on a clone. Check every delta's appropriate
   `worldedit.selection.expand` / `worldedit.selection.contract` permission.
   Do not recreate an extending selector or shrink it using selectSecondary.
6. Follow WorldEdit's selection-adjustment lifecycle: learnChanges(), then
   explainRegionAdjust() for authoritative CUI synchronization. Reconcile the
   actual resulting bounds, including any native world-height clamp.
7. Publish success/failure to the client and clear the transaction exactly once.
   No EditSession, block iteration, history entry, undo operation or temporary
   world write is needed. Cancellation applies before submission; a submitted
   commit is not presented as an operation that Escape can silently roll back.

The signatures were checked against the installed WorldEdit 7.4.2/7.4.3/7.4.5
binaries. Native Minecraft mouse, keyboard, movement, projection and HUD APIs
were checked for each version; real packaged-runtime tests exercise these hooks.

## Coexistence with current features

- Command preview: opening chat cancels selection editing. Its existing preview
  priority and command submission remain unchanged. No simultaneous editing HUD.
- Clipboard preview: temporarily suppress it during edit mode for a clear view,
  without changing its preference, clipboard, transform or cached mesh. Restore
  it on exit. Never recapture the clipboard because a handle moved.
- Isolation: keep its current confirmed-region mask throughout the local draft;
  do not rebuild chunk geometry for every step. On successful resize of the same
  selector, preserve chosen block types and update the mask once for the new
  authoritative bounds. Cancelling does not affect isolation.
- Existing isolation reset behavior for unrelated axe/command selection changes
  stays intact. Current clearIsolatedBlocksIfSelectionChanged resets IDs on any
  region revision, so preservation needs a scoped transaction acknowledgement,
  not a global removal of that check. CUI's shape/point event sequence must not
  clear the chosen IDs halfway through an acknowledged edit.
- The inspector can rescan after a committed change, not during each local step.
  The palette editor is unrelated and retains its current projection controls.

## Performance contract

The resize interaction itself is independent of selected volume: eight corner
markers and at most six face handles,
twelve box edges, a few coordinate values and at most one short server commit.
Do not build SelectionRegion grid geometry, scan blocks, request unloaded chunks,
recompile terrain or allocate framebuffer targets for a draft.

Draft updates touch only immutable coordinate values. Selection reads
and stale-state checks run only while enabled with a valid local context, at most
once per four client ticks and with one outstanding read. Unavailable selections
obey that same cadence. Never submit a server task per rendered frame. A disabled feature has no extra render
submission or selection-session polling.

Existing isolation/chunk rebuilds after a committed resize still have a real
cost. They must use the established section-scoped invalidation, not a world
reload. Do not promise zero FPS cost or instantaneous downstream work without
measurements. Measure idle, targeting, rapid wheel edits and dragging separately.

## Implementation and acceptance gates

1. Shared pure cuboid draft/snap/hit-testing state machine and its domain tests.
2. Local WorldEdit read/commit adapters, permissions, stale-request rejection and
   exact-once CUI/selection synchronization, without new UI first.
3. Minimal in-world handles/HUD and narrowly scoped input capture.
4. Isolation acknowledgement and preview coexistence tests.
5. Six native runtime builds and both universal JAR packaging tests.
6. Real-game tests on exact launcher profiles with compatible local WorldEdit;
   early NeoForge 26.1/26.1.1 remain unavailable for local editing.

Cover all faces, reversed Pos1/Pos2, negative coordinates, single-block and large
selections, world-height limits, inside/outside views, end-on axes, perspective
and third-person camera, GUI scaling and English/Russian labels. Compare actual
WorldEdit corners/type before and after each gesture; verify unchanged world
blocks/history/clipboard, no incidental attacks/hotbar changes, and immediate
cancel on focus loss, menus, disconnect or external selection changes.

Capture real screenshots and frame times. Shader/modpack integrations require
their own checks; vanilla passes alone are not a claim about Sodium/Iris/LabyMod.
Do not add release notes or mark the milestone complete based only on compilation.

## Primary references

- [Axiom gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html): axis-specific
  manipulation and wheel nudging. Adapt the interaction principle, not its code.
- [Axiom Box Select](https://axiomdocs.moulberry.com/tools/selection/boxselect.html):
  refinement of a cuboid through in-world controls.
- [WorldEdit selection adjustment](https://worldedit.enginehub.org/en/latest/usage/regions/selections/):
  official expanding/contracting behavior and supported selector modes.
- [WorldEdit RegionSelector API](https://docs.enginehub.org/javadoc/com.sk89q.worldedit/worldedit-core/release/com/sk89q/worldedit/regions/RegionSelector.html):
  learnChanges and explainRegionAdjust lifecycle.
- [Official SelectionCommands source](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/command/SelectionCommands.java):
  permission annotations and native region-adjustment sequence.
- [Official CuboidRegion source](https://github.com/EngineHub/WorldEdit/blob/version/7.4.x/worldedit-core/src/main/java/com/sk89q/worldedit/regions/CuboidRegion.java):
  ordered corners, one-axis operations and height clamping.

## Implementation notes

- Shared drafting, inclusive coordinates, hit testing and transaction code live
  in `shared/selection-editing`; the canonical native adapter is Fabric 26.2.
  `tools/sync-selection-editing.mjs` maps it to the other five native runtimes.
- Settings use a separate `selection-editing.properties` file. Existing overlay,
  palette, grid, ruler and clipboard preferences are not migrated or overwritten.
- Selection reads are coalesced at most once per four eligible client ticks. Draft
  updates touch only immutable bounds. A commit is server-thread owned and
  revalidates session, selector identity, ordered corners, world and permissions.
  The source's weak snapshot cache cannot keep a closed world alive; changed
  player/world identity clears pending draft references and invalidates callbacks.
- Outside hold mode, only a marker hit captures LMB for a live-camera corner
  gesture. Hold-mode dragging captures the camera projection, stops movement
  and releases the cursor. The gesture type is fixed at capture, so pressing G
  halfway through a free drag does not unexpectedly lock the camera. Resize/camera-mode changes
  also cancel the draft. Minecraft's early-return first mouse event is handled.
- Both universal packagers register the hooks explicitly. Artifact tests check
  those registration lists, not merely the presence of mixin class files.
- The native gizmo renderer submits fixed-size geometry without a framebuffer
  or block scan. It uses short opacity appearance transitions and Reduced Motion.
  This is a constant-work design, not a claim of zero FPS cost in every modpack.

References establish API behavior and interaction precedents, not performance
results or visual acceptance of this particular UI.

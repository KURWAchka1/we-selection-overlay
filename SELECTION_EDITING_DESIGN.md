# Interactive selection editing

Status: implemented and six-runtime tested for the 1.8.0 development line,
2026-10-09. Milestone 2 of
[FEATURE_ROADMAP.md](FEATURE_ROADMAP.md). See TESTING.md for verified coverage
and remaining integration checks.

## Goal and scope

Resize or translate a real WorldEdit cuboid directly in the world without changing blocks,
opening a large editor. Eight corner markers are visible only while the feature
is enabled and a supported selection is ready. Normal building, selection
rendering and mouse behavior remain unchanged away from these markers.

First scope: complete standard cuboid and extending-cuboid selections in an
integrated world with compatible local WorldEdit. Preserve the selector type
and Pos1/Pos2 identities. Do not convert another shape to a cuboid implicitly.
Polygon and convex vertex editing are future work, not placeholder controls.
Remote CUI-only sessions remain visualization-only: this phase adds no server
plugin, custom editing protocol or automatic chat-command fallback.
This applies equally to Bukkit/Paper and dedicated Fabric/NeoForge servers.
Installing WorldEdit on a remote client/server is not an integrated-server
capability. Palette edits/history, block inspection/isolation and CUI selection
display remain local visual features and do not require edit permissions.
Any future remote editing adapter must validate WorldEdit permissions and
authoritative session state on the server; CUI is not an authorization protocol.

## Interaction

Use direct free-camera corner dragging plus a tap-to-toggle explicit editor.
The feature is opt-in. Expose its availability/settings in the compact O menu.
The native edit/menu key defaults to G; Minecraft retains existing custom assignments.
Left Shift is the configurable
fast modifier; default steps are 1 and 8 blocks.

| State/action | Behavior |
| --- | --- |
| Feature disabled | No markers, HUD, selection reads or mouse interception. |
| Enabled, editor inactive | Show eight corner markers for a ready cuboid. Coalesce authoritative reads; intercept LMB only on a marker. No extra HUD or hotbar-wheel capture. |
| Left-drag a corner without the edit key | Aim at a marker and hold LMB. Keep normal camera/flight controls; the corner follows the live view ray at its original grab distance, with the initial off-center offset retained. Always snap by one block, ignoring configured normal/fast steps and modifier keys; keep the opposite corner fixed. Release LMB to commit once. |
| Tap edit key | Toggle the explicit editor. Entering captures movement/look and releases the cursor; the next tap commits and exits. The remembered tool/face is retained (Resize/UP initially). |
| Hold edit key for 400 ms | Open a centered two-sector Resize/Move ring, not a new Screen. Hide the cursor and constrain its replacement dot within the ring. Release over a sector to select that tool; the empty center and seams cancel the menu without changing the tool/draft. A long hold never applies a draft. |
| Click a face | Select that side at any visible distance before resizing. Accent its perimeter/handle. Camera movement alone never changes the remembered choice. Intersection works even when the center handle is outside the viewport. |
| Mouse wheel | Resize the targeted face outward/inward in Resize; shift the whole box along the captured camera's dominant world axis in Move. Update only the local draft. |
| Fast modifier | Use the configurable fast step only in the explicit editor, without lowering a flying player. Free corner grabs retain one-block snapping and normal Shift descent, even if G is pressed midway. |
| Left-drag in Resize | Grab a face handle, corner or any face surface. Surface grabs require four physical pixels of motion, so ordinary selection clicks do not resize. Release LMB to finish only that local drag. Compose further drags against the current draft. |
| Left-drag in Move | Grab any face/corner and translate the whole box on the captured view plane with block snapping. All dimensions and both ordered corners stay unchanged relative to each other. Wheel supplies the remaining depth axis. |
| Escape | Cancel the complete draft immediately, including in Move and while the radial menu is open. A still-held key must be released before re-entry. Enabled free corner markers remain available. |
| Second short tap | Finalize a current cursor drag and commit the complete composed draft once, including when LMB is still held. Restore cursor/camera without forwarding an attack. Direct/free corner drags still apply on LMB release. |
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
corner stays fixed. Explicit-editor coordinates snap to the configured block step;
free corner grabs always use one block, even if G or Shift is pressed afterwards.
The face cannot cross the opposite face: the minimum dimension is one block.
Each new drag starts from the current draft, while the original authoritative
snapshot remains unchanged for stale-state checks and cancellation.
Wheel direction is always outward/inward, independent of camera orientation.
Precise face dragging uses a fixed screen projection of the captured world axis,
not camera yaw heuristics. Free corner dragging instead uses the current camera
ray at a fixed captured depth, avoiding view-plane singularities during turns.
It updates only the draft during world rendering, with a final update on release;
movement input, camera capture and velocity are not overridden in this mode.
For a nearly end-on face axis, vertical motion provides a stable fallback:
up expands and down contracts. Its choice is fixed for the gesture, not switched
as the cursor moves. Captured sensitivity uses camera depth with a one-block
working-depth floor and at least one block per 48 physical pixels for close grabs.
Perspective-correct marker picking uses fixed pixel targets and rejects markers
behind the camera. Face-plane intersection accepts exact zero-depth hits.
Integer snapping must behave consistently at
negative coordinates, from inside the selection and on all six faces.

## Appearance

The world remains the main view. There is no full-screen background, blur,
sidebar, persistent toolbar, luminous wall or filled selection volume.

- Eight high-contrast diamond corner markers when enabled, with dark interiors,
  screen-stable sizes and slightly larger hit targets. Hover/capture accents
  only that marker. Six face-center handles are additionally visible in Resize.
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
5. For an unchanged-size translation, validate a clone and apply Region.shift
   with `worldedit.selection.shift` permission. Otherwise validate every changed
   face in the final composed bounds with the public
   CuboidRegion expansion/contraction route, before mutating the real region.
   Apply expansions before contractions on both a draft and a region clone, then
   on the real region. This permits a valid translation across an old opposite
   face without an invalid intermediate shrink. Check every delta's appropriate
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
  Ctrl+Z / Ctrl+Y (also Ctrl+Shift+Z) restore color/alpha, individual/global
  widths, resets and confirmed presets, not camera movement. One slider drag or
  numeric-field edit is one undo step. A changed edit clears redo; no-ops do not.
  Preserve history across resize within the same editor instance, save completed
  changes, and finalize pending edits on close/removal. At most 128 immutable
  palette snapshots are retained; no history snapshots are created per frame.

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

- [WorldEdit permissions](https://worldedit.enginehub.org/en/latest/permissions/):
  selection expansion/contraction permissions, independently of operator status.
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
- [Blender axis constraints](https://github.com/blender/blender/blob/main/source/blender/editors/transform/transform_constraints.cc):
  close-view working-depth correction and vertical-motion fallback for view-aligned axes.
  These are interaction precedents; the mod's linear snapped mapping is independently implemented.
- [Three.js TransformControls](https://github.com/mrdoob/three.js/blob/dev/examples/jsm/controls/TransformControls.js):
  captured drag origins and camera-aware axis/plane constraints.
- [Official WorldEdit shift command](https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/command/SelectionCommands.java):
  worldedit.selection.shift permission, Region.shift, learnChanges and explainRegionAdjust.
- [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode):
  disabled-cursor capture, relative virtual coordinates and native cursor restoration.

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
- Outside the explicit editor, only a marker hit captures LMB for a live-camera corner
  gesture. Entering the editor captures the camera projection, stops movement
  and releases the cursor immediately; LMB release leaves that mode active.
  The gesture type is fixed at capture, so pressing G
  halfway through a free drag does not unexpectedly lock the camera. Resize/camera-mode changes
  also cancel the draft. Minecraft's early-return first mouse event is handled.
- Key timing is a shared monotonic state machine. Repeats do not reopen menus;
  a stalled frame cannot turn a long hold into an accidental short-tap commit.
  Native press/release callbacks catch taps shorter than a client tick; ticks
  only advance the hold threshold. Existing configurable bindings remain native.
  Radial rendering uses two cached anti-aliased ring masks, existing GUI batching
  and licensed Lucide icons. There is no blur, framebuffer or world/block scan.
  GLFW's disabled-cursor mode provides captured relative movement. Only the
  replacement dot is clamped; the mod never warps/recenters the system cursor
  per event. Reversing at the ring edge responds immediately without backtracking.
  Closing restores the native cursor for other menus. The editing HUD is hidden
  while the ring is open so its cancel center stays empty.
  Short hover fades respect Reduced Motion. The HUD is registered above chat
  without replacing chat or changing its position.
- Pure translations are clone-validated and applied with Region.shift on the
  server thread using the shift permission. Both ordered corners and selector
  identity are preserved. The whole box clamps at world borders/height without
  shrinking. Mixed resize drafts retain the existing all-face validation path.
- Both universal packagers register the hooks explicitly. Artifact tests check
  those registration lists, not merely the presence of mixin class files.
- The native gizmo renderer submits fixed-size geometry without a framebuffer
  or block scan. It uses short opacity appearance transitions and Reduced Motion.
  This is a constant-work design, not a claim of zero FPS cost in every modpack.

References establish API behavior and interaction precedents, not performance
results or visual acceptance of this particular UI.

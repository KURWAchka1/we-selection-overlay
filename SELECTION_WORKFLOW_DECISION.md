# Selection editing: task-first implementation decision

Reviewed 2026-10-10 for 1.8.0. This is the implementation contract following the
[comparative study](SELECTION_INTERACTION_COMPARISON.md), not a claim of measured
99% usability or an empirically perfect interaction. No external code is copied.

## Purpose And Success Criterion

Correct an existing WorldEdit region faster and more confidently than replacing
its two points. Do not become a general building editor or move actual blocks.
Do not claim every task beats a wand: clicking two reachable blocks with an axe
already in hand is an excellent workflow. Target the tasks where it is weaker:
an airborne/far corner, one-face correction, and relocation without losing size.

| Task | Recommended interaction | Why it can help |
| --- | --- | --- |
| Correct one nearby corner | Existing visible corner grip | No tool swap, coordinates, or mandatory editor entry |
| Change only height/width/depth | Resize, choose the face, precise drag/nudge | Opposite face and other axes remain fixed |
| Relocate the same box | Move, grip, X/Y/Z + wheel, release, G | Independent of player collision; preserves dimensions and ordered points |
| Known exact distant displacement | Planned compact numeric entry | Flight is not an efficient substitute for known coordinates |
| Unknown distant target | Navigate freely, nudge axes; evaluate source-to-target snapping later | Navigation no longer transforms the box |

## Current Move Contract

- G activates the remembered tool; hold G retains the existing two-sector menu.
- Move idle and gripping keep native look, walking, flight and Shift descent.
  They do not release the cursor, suppress movement or zero player velocity.
- LMB on the indicated selection boundary or center grip starts a wheel grip.
  A center outside the viewport is not required. A miss never grabs the box.
- Capture an immutable box. Only wheel input translates it with one-block
  snapping. Walking, flight, gravity, knockback, looking around, eye height,
  FOV and view bob are not transform inputs. The original root-carry proposal
  was superseded at the user's request on 2026-10-10.
- LMB release ends the grip and retains the local draft. Re-grabbing has no
  initial jump. G applies once; Esc cancels before submission. Long G during
  a grip never applies or opens the radial menu.
- Wheel is owned only while gripping. Each notch nudges by the configured
  regular step along a signed cardinal axis captured from the view at pickup.
  X/Y/Z selects a world-positive wheel axis immediately without re-grabbing.
  Fractional input stays on the axis that received it; a switch never rotates
  accumulated movement. Show the selected axis. Shift remains descent, not
  an implicit Move speed switch.
- Clamp the whole box at coordinate/height limits, never shrink it. Discard
  rejected overshoot separately per axis so reversing responds immediately.
- Native gravity/knockback move only the player. Release to stop the wheel grip;
  Esc cancels the draft. No invented flight or frozen physics.
- Context/world/player/focus loss still cancels; camera entity/type changes
  invalidate a grip. Do not enter an editing session without a ready origin.
- Keep integrated-local WorldEdit cuboid/extend scope and its existing public
  region operations, origin validation, permissions and CUI acknowledgement.

The shared math must do constant coordinate work, independent of box volume,
distance, loaded chunks, clipboard size and world content. No server task,
chunk load, terrain rebuild, mesh regeneration or block scan per pointer event.
This is a complexity guarantee, not an unmeasured promise of zero FPS cost.

## Revisions To Earlier Proposals

The continuous view-vector wheel proposal is superseded for the first slice:
cardinal nudges have an exact, visible result and do not leak into other axes.
The initial axis comes from pickup view; X/Y/Z changes it inside the same grip.

The proposal to require G after every passive corner release is deferred, not
silently implemented. Keep the existing quick-corner apply behavior and Esc
while held. Explicit editing sessions retain staged release/G confirmation.
This semantic distinction must remain documented and tested.

Do not render three independent axis arrows for a single unconstrained grip.
Use one neutral/accent center marker and an indicated boundary anchor; the
wheel-axis indicator is a constraint, not three draggable axis controls.

## Independent Input And Follow-Ups

### Independent Height And Position (2026-10-10)

Player collision is a real limitation of root carry: a solid floor stops the
player, so moving down by flying is not a complete placement workflow. The
selection itself has no terrain collision. Remove root carry and provide an
independent precision channel without opening settings or changing game mode.

Implemented independently of the palette change:

- While an LMB Move grip is held, tap X/Y/Z to choose a world-positive wheel
  axis immediately. Y + wheel raises/lowers the box even through a solid floor;
  X/Z do the same horizontally. No release, re-grab, radial menu or settings.
- The axis change must not translate the box, recapture the player baseline or
  rotate accumulated fractional motion. Only later wheel input uses the axis.
  Player movement never translates the box; it remains available for navigation.
- Keep the selected-axis indicator and XYZ displacement in the compact HUD.
  Explicit Y means positive world-up, regardless of the camera pitch. Native
  Space/Shift, movement and look retain their meanings. No world/block raycast,
  no forced spectator/no-clip and no teleporting the player.
- Only the active Move grip owns these contextual shortcuts. Screens, chat,
  Resize, passive corner grips, idle hotbar and pending applies do not. Respect
  a rebound edit/menu key before an axis shortcut, and track key ownership so
  held/repeated keys and their releases do not trigger other mod bindings.
- A small, unframed axis triad appears only while LMB grips Move. The grip fixes
  its movement directions at pickup; turning the camera changes the projected
  triad, not those directions or the chosen wheel axis. Merely translating the
  player, grip or selection must not rotate it. Project the actual native camera
  right/up/forward basis onto the three fixed world axes, including vertical
  views. A new grip captures a new initial signed wheel direction; explicit
  X/Y/Z remain world-positive. Three thin depth-ordered arms and palette-colored
  labels reuse GUI/font resources. Only the active letter becomes bold; no
  circles, endpoint dots, rings, backplate or invented camera-to-grip look-at.
- G applies the entire draft; Escape cancels. Keep world-height/coordinate
  bounds, dimensions and immediate reversal at saturation. Numeric XYZ entry
  remains the later route for large exact offsets, not mandatory UI to nudge.

Acceptance checks cover stationary-player vertical travel below a solid
floor, all three axis switches within one grip, fractional input, camera turns,
custom key conflicts, LMB/G/Esc release order, and both loaders. This is our
adaptation of explicit-axis transformation, not an assertion that Axiom or
Blender use this exact interaction.

The orientation contract follows the viewport-triad principle in
[Unreal Engine's coordinate-space documentation](https://dev.epicgames.com/documentation/unreal-engine/coordinate-system-and-spaces-in-unreal-engine)
and [VTK's orientation-marker camera synchronization](https://vtk.org/doc/nightly/html/classvtkOrientationMarkerWidget.html).
This is an adaptation, not imported implementation code. Minecraft remains
Y-up, regardless of other editors' conventions. The indicator is informational;
it neither rotates the world axes nor turns letters into clickable controls.

### Other Follow-Ups

1. Resize: capture only during precise gestures, bound screen-to-world gain,
   rebase step changes and remove rejected overshoot. Preserve close/end-on
   behavior and all six face signs with tests before changing input ownership.
   The existing passive corner's fixed-depth live-ray math is unchanged in
   this slice; its distant angular amplification still needs a precision route.
2. Compact numeric input and local gesture undo/redo. Native text focus owns
   its shortcuts. G still submits the complete draft, not each typed number.
3. Mixed Move/Resize semantic operation plan: validate actual operations and
   permissions, rather than infer intent solely from final dimensions.
4. Evaluate target disambiguation, reference-pixel sizing and source-to-target
   snapping with builders. Do not add them merely to resemble another editor.

## Validation Gates

Shared tests: no-jump pickup, independent axis displacement, reversibility, dimensions,
negative coordinates, fractional wheel, all signed axes, grip re-entry, height
and coordinate saturation/reversal, nonfinite input, large boxes and long travel.

Packaged native tests on Fabric/NeoForge 1.21.11, 26.1.x and 26.2: native mouse
look while gripping, Shift descent, wheel ownership, staged release, G/Esc,
radial reopening/cancel, selection identity/permissions and unchanged blocks,
WorldEdit history, isolation choices and clipboard cache. Inspect screenshots.

Automated pass is not human ergonomic acceptance. Before a release, compare
the five tasks above against the wand/pos workflows in actual building. Record
missed grips, accidental edits, final coordinate errors and frame times. The
full redesign remains incomplete until its later slices and usability checks.

## Primary References Rechecked

- [WorldEdit selection workflows](https://worldedit.enginehub.org/en/latest/usage/regions/selections/):
  two-point, crosshair, exact-coordinate and face-adjustment alternatives.
- [Axiom gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html): distinct
  center, axis and plane interactions, cardinal nudges and explicit tool context.
  The earlier root-carry proposal was our design decision and is now superseded,
  not a claimed identical API.
- [Blender axis locking](https://docs.blender.org/manual/en/5.2/scene_layout/object/editing/transform/control/axis_locking.html):
  choosing X/Y/Z during an active transformation, without entering settings.
- [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode):
  retain native captured mouse look, not per-event cursor warping.

Implementation and actual verification results belong in
[SELECTION_EDITING_DESIGN.md](SELECTION_EDITING_DESIGN.md) and [TESTING.md](TESTING.md).

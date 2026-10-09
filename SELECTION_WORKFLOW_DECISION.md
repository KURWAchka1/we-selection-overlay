# Selection editing: task-first implementation decision

Reviewed 2026-10-09 for 1.8.0. This is the implementation contract following the
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
| Relocate the same box | Move, grip, walk/fly, release, G | Preserves dimensions and both ordered WorldEdit points |
| Known exact distant displacement | Planned compact numeric entry | Flight is not an efficient substitute for known coordinates |
| Unknown distant target | Travel/carry initially; evaluate explicit source-to-target snapping later | Do not invent an ambiguous automatic anchor |

## Accepted First Slice: Move Carry

- G activates the remembered tool; hold G retains the existing two-sector menu.
- Move idle and carrying keep native look, walking, flight and Shift descent.
  They do not release the cursor, suppress movement or zero player velocity.
- LMB on the indicated selection boundary or center grip starts carrying.
  A center outside the viewport is not required. A miss never grabs the box.
- Capture an immutable box and the player's root position. Subsequent actual
  root displacement translates the box one-to-one with one-block snapping.
  Looking around, eye height, FOV and view bob are not transform inputs.
- LMB release ends the grip and retains the local draft. Re-grabbing has no
  initial jump. G applies once; Esc cancels before submission. Long G during
  a grip never applies or opens the radial menu.
- Wheel is owned only while carrying. Each notch nudges by the configured
  regular step along a signed cardinal axis captured from the view at pickup.
  Fractional wheel input is retained as displacement; changing view cannot
  rotate an accumulated nudge or reassign its fractional remainder. Show the
  locked axis. Shift remains descent, not an implicit carry speed switch.
- Clamp the whole box at coordinate/height limits, never shrink it. Discard
  rejected overshoot separately per axis so reversing responds immediately.
- Native gravity/knockback are actual displacement while gripping. Release to
  stop carrying; Esc cancels the draft. No invented flight or frozen physics.
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
The axis is fixed for one grip; release and re-grab to choose another direction.

The proposal to require G after every passive corner release is deferred, not
silently implemented. Keep the existing quick-corner apply behavior and Esc
while held. Explicit editing sessions retain staged release/G confirmation.
This semantic distinction must remain documented and tested.

Do not render three independent axis arrows for a single unconstrained grip.
Use one neutral/accent center marker and an indicated boundary anchor; the
wheel-axis indicator is a constraint, not three draggable axis controls.

## Subsequent Slices, Not Included In The First

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

Shared tests: no-jump pickup, root-only displacement, reversibility, dimensions,
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
  Root-displacement carry is our design decision, not a claimed identical API.
- [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode):
  retain native captured mouse look, not per-event cursor warping.

Implementation and actual verification results belong in
[SELECTION_EDITING_DESIGN.md](SELECTION_EDITING_DESIGN.md) and [TESTING.md](TESTING.md).

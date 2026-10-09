# Selection interaction audit and revised proposal

Status: DESIGN ONLY, not implemented or game-tested. Reviewed 2026-10-09 against
commit `4504021a785f44376dea506d66e37c61810c341a` on the 1.8.0 development line.
No production code, test code, settings, release notes or JARs are changed by
this proposal. [SELECTION_EDITING_DESIGN.md](SELECTION_EDITING_DESIGN.md) records
the implemented baseline; [SELECTION_MOVEMENT_REDESIGN.md](SELECTION_MOVEMENT_REDESIGN.md)
is the shorter, revised Move specification.

Follow-up ready-tool research: [reference shortlist and adoption decisions](SELECTION_INTERACTION_REFERENCES.md).
Its additional wheel/precision candidates need prototype comparison before
replacing the corresponding rules below.

Further research: [task comparison and community evidence](SELECTION_INTERACTION_COMPARISON.md).
Its cardinal-wheel refinement remains a comparison candidate; the continuous
wheel rules here and the current runtime behavior are not silently replaced.

## Conclusion

Keep two tools: Move and Resize. Do not turn the addon into a general 3D editor.
One gesture cannot simultaneously offer unconstrained first-person navigation,
exact screen-to-world attachment, distance-independent precision and no input
conflicts. Separate transportation from precision instead of concealing that
tradeoff with acceleration, automatic axis changes or camera smoothing.

Recommended design:

- Move: grab one volumetric cross or an indicated boundary; transport the box
  by the player's actual world displacement. Looking around alone does not
  move it. Wheel nudges and an optional numeric popover provide fine placement.
- Resize: aim freely between gestures; capture mouse look only during a precise
  face/corner drag. Use a fixed constraint with bounded sensitivity. Keep a
  free-camera corner grip as a separate entry gesture using transportation
  rather than the distance-amplified live-ray constraint.
- Both tools: one local draft, release to inspect, G to apply once, Esc to cancel
  before submission. Retain the two-sector hold-G menu, not a third tool menu.
- No block movement, world writes, volume scans, chunk loads, automatic flight,
  camera teleports, block rotation or implicit conversion of other selectors.

This is a reasoned recommendation, not proof that every usability issue has
been eliminated. Remaining tradeoffs and prototype acceptance gates are below.

## Evidence From The Existing Code

Code was discovered through the repository knowledge graph. Fabric 26.2 is the
canonical adapter. The camera-capture predicate and corresponding input policy
were also checked across all six native runtime controllers/mixins.

| Confirmed code behavior | Consequence or inferred interaction risk |
| --- | --- |
| `SelectionEditController.capturesCamera()` captures every explicit active session, not only a drag. | Inspecting and approaching another face requires leaving the explicit editor. Changing the arrow artwork alone cannot fix travel. |
| The keyboard mixin clears movement input; the player mixin cancels `LocalPlayer.move`; the controller also zeros velocity while captured. | Input ownership reaches beyond deliberate navigation into native motion. Gravity, collision and other motion interactions need testing, not blanket suppression. |
| Explicit Move uses a captured view plane; its wheel uses the captured view's dominant world axis. | Short planar adjustments are predictable, but long travel and depth placement are awkward. A diagonal view can make the wheel's axis non-obvious. |
| Direct corner grips use the live view ray at the initial distance. | Camera rotation changes the corner even without translation; distant grips amplify tiny turns. This is a mathematical property, not a measured FPS bug. |
| Direct corners commit on LMB release; explicit drags do not. | Similar-looking gestures have different safety semantics. Esc cannot cancel a direct grip after its release has already committed. |
| Explicit fast-step snapping is recomputed from the same original offset. | Changing from step 1 to 8 can change an existing displacement without further mouse movement; for example, snapping 3 with step 8 produces 0. |
| `FaceDrag.capture()` has a close-depth floor and a minimum sensitivity, but no maximum. | The existing end-on fallback avoids a singularity, yet remote faces can remain far too sensitive. |
| Resize wheel input acts on the remembered face even without an active grip. | Looking at another location while scrolling can resize an old face instead of changing the hotbar. |
| Handles use physical-pixel tolerances and forward-depth picking; multiple handles are drawn on top. | Tiny/distant boxes can have overlapping front/back targets. Raw fixed pixel sizes need DPI and viewport review. |
| `CuboidDraft` clamps bounds and prevents inverted dimensions. It is deliberately stateless. | Bounds safety is good, but an interaction accumulator can retain rejected overshoot and require substantial reverse travel. This is not a defect in pure bounds validation. |
| `commitBounds()` chooses shift versus expansion/contraction from final dimensions. | Mixed operation intent and permissions can differ from the final net boundary differences. |
| WorldEdit transactions validate origin identity and a clone, then use public region operations and CUI synchronization. | Preserve this safety foundation. Do not replace it with chat commands or installing a client draft as authoritative selection data. |

Source locations in the audited tree:

- [Controller](WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/selection/SelectionEditController.java): camera/input ownership, begin/choose/move/scroll/release/submit.
- [Renderer](WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/client/selection/SelectionEditRenderer.java): projection, handle picking, face highlighting and HUD.
- [Movement mixin](WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/mixin/SelectionEditMovementMixin.java) and [player mixin](WEaddon_fabric_26.2/src/main/java/org/marshal/weaddon_lightposition/mixin/SelectionEditPlayerMixin.java): native motion suppression.
- [FaceDrag](shared/selection-editing/src/main/java/org/marshal/weaddon_lightposition/selection/FaceDrag.java) and [CuboidDraft](shared/selection-editing/src/main/java/org/marshal/weaddon_lightposition/selection/CuboidDraft.java): captured projection, snapping, picking and coordinate bounds.
- [Transaction](shared/selection-editing/src/main/java/org/marshal/weaddon_lightposition/selection/worldedit/SelectionEditTransaction.java): permissions, stale-state validation and final application.

Existing pure-math and packaged-runtime tests cover the implemented baseline.
They do not establish the ergonomics or correctness of this proposed redesign.
Not every inferred risk above has been reproduced in a new game session.

## Comparing The Alternatives

| Approach | Advantages | Disadvantages | Decision |
| --- | --- | --- | --- |
| Captured view plane and released cursor, as now | Stable local plane, familiar desktop manipulation | Stops exploration; cursor edges and depth-axis changes hinder long travel | Retain the captured-constraint principle for precision, not the session-wide freeze |
| Fixed-distance live-ray carrying, previously proposed | Grab point remains attached to the view; native flight; simple geometry | Head turns, bob and distant anchors can throw the draft around; still weak at exact depth | Reject as the default for Move and free corners |
| Player-world-displacement carrying | 1:1 travel, free look, no distant angular amplification, constant coordinate work | Looking alone cannot reposition; gravity and knockback are still actual displacement; long travel requires travel | Recommended transportation gesture, with explicit precision alternatives |
| Independent XYZ arrows and plane squares | Standard axis isolation, good precision | Small/overlapping handles; end-on axes; extra visual and interaction complexity; not the requested single grab cross | Do not introduce as the default or silently imply them with RGB arrows |
| Numeric displacement and click steppers | Exact, fast for long distances, no continuous holding required | Less direct, briefly needs input focus, must handle validation | Compact secondary interaction, not a permanent window |
| Automatic distance-dependent rotation gain or hidden mode switching | Can make a demo appear responsive | Unpredictable control/display relationship and transitions | Reject |

### Why The Previous Ray Proposal Is Insufficient

For a stationary eye and a fixed grab radius `d`, a turn through angle `a`
moves the tracked point by `2 * d * sin(a / 2)`. These independently calculated
examples concern the tracking point before block snapping:

| Radius | Turn | Displacement |
| --- | --- | --- |
| 10 blocks | 1 degree | 0.175 blocks |
| 100 blocks | 1 degree | 1.745 blocks |
| 1000 blocks | 1 degree | 17.453 blocks |

Following a rendered camera also imports view-bob/eye-height effects unless they
are explicitly removed. Smoothing would delay an inaccurate transform rather
than remove the ambiguity. A fixed-distance center grab is a valid interaction
in other editors, but that alone does not make it the best default here.

## Common Interaction Contract

### Session And Input Ownership

Represent tool choice, draft lifetime and input ownership separately. Suggested
states are `inactive`, `draft-idle`, `carry`, `face-drag`, `corner-drag`,
`radial`, `target-list`, `numeric` and `applying`. Authoritative read availability is separate;
do not activate a blocking editor while waiting for a missing origin snapshot.

| State | Mouse look | Locomotion and physics | Mouse buttons / wheel |
| --- | --- | --- | --- |
| Inactive | Native | Native | Native, except a visible enabled corner can start an implicit local draft |
| Draft idle | Native | Native | Suppress world construction; LMB selects/grabs; wheel remains native hotbar input |
| Move carry / free corner carry | Native | Native, including Shift descent | Owned LMB grip; wheel nudges the draft, never hotbar |
| Precise Resize drag | Relative mouse delta edits the captured constraint; do not also turn the camera | Native WASD/flight/physics; suppress the configured fast modifier's overlapping action only during this precise grip | LMB release ends grip; wheel edits the locked target/depth |
| Radial | Captured by the bounded menu dot | Native physics/navigation; no transform | Menu owns pointer input; wheel does not resize; no click-through |
| Target list / numeric popover | Owned menu/widget focus | Native Screen input policy, never cancel player physics or zero velocity | Widgets own their events; no click-through, hotbar change or G shortcut while typing |
| Applying | Native | Native | No new edit gesture; swallow buttons already captured until their physical release |

Native motion during a precision grip can move the face's image relative to the
reticle. The transform remains governed by the captured axis/plane, not by that
motion. Release, reposition and re-grab for a different view. This is an explicit
tradeoff, not a claim that the grabbed point always stays under the reticle.
Never solve it by cancelling every call to player movement.

### Apply, Cancel And History

- A short G tap activates a draft, or submits the current draft once. When used
  during a grip, sample its last input and end the grip before submission.
- LMB release ends a gesture only. It never writes to WorldEdit in either tool.
- Esc cancels the entire unsubmitted draft, including while a grip, radial menu
  or owned numeric popover is open. Require held inputs to be released before
  re-entry; do not forward a trailing release as an attack/use action.
- Once a commit has been submitted, Esc is not an undo promise. The accepted
  server mutation cannot be silently unsent. Keep a clear pending state and
  reconcile its result; never queue another commit against an unconfirmed origin.
- Keep the existing 400 ms monotonic hold threshold and cancel center. Open the
  radial menu only outside a grip and outside a focused text field. A long hold
  during a grip is ignored, not converted to an apply on release. Selecting a
  different tool preserves the draft and history; it does not apply or reset it.
- A menu opened while inactive only chooses the remembered tool, as currently.
  A subsequent short tap activates it. Do not unexpectedly start carrying.
- Add local gesture undo/redo as a proposed safety measure: one completed grip,
  numeric edit or wheel sequence is one history step. Ctrl+Z/Y is not `//undo`.
  During a grip, finalize its local state, undo it and require release before a
  new grip. Text fields own their normal text-history shortcuts instead.
- No-op gestures add no history. Retain the session origin independently of a
  bounded gesture-history buffer so whole-session Esc remains valid. Do not
  allocate an undo snapshot per mouse event or frame.
- Applying an unchanged draft simply exits; do not enqueue a mutation or rebuild
  downstream previews. Entering with a mouse button already held requires a
  fresh press before grabbing. Never reinterpret a pre-existing attack hold as
  an editor gesture.

**Intentional behavior change requiring acceptance:** the existing free corner
entry gesture stays available without G, with free camera, native flight and
one-block steps. Its LMB release would now leave an implicit draft requiring G
to apply. This adds one confirmation but removes the dangerous release-semantic
split. Do not describe it as unchanged behavior or add an obscure second apply
setting merely to hide the tradeoff.

### Target Selection

1. Pick only targets that are drawn and in front of the camera. Resolve the
   indicated target once on press, then lock it for the gesture. Do not switch
   sides or corners halfway through a drag.
2. Prefer an actual nearby handle over a broad face hit. Among overlapping
   handles, use screen proximity and front depth with deterministic ties.
   Hover hysteresis may retain a target only within a small tie band; it must
   not preserve a distant back handle against a clearly nearer front handle.
3. Context actions are available only inside an active draft; RMB outside it
   remains native building/use input. Move has only one action, so RMB on its
   indicated target opens numeric displacement directly. In Resize, ambiguous
   overlaps open a small candidate list with live face/corner highlight; an
   unambiguous target can open precise entry directly. The list is an owned
   compact UI with the same focus/cancellation policy as the numeric popover.
4. Front/back here describes selection geometry. The overlay can intentionally
   be visible through world blocks; do not introduce a hidden chunk scan to
   determine terrain occlusion. A hidden rear handle cannot become a surprise
   default hit just because its on-top renderer happens to overlap another one.
5. Treat near-plane drawing and face picking separately. Near/inside views need
   the outgoing face, or an explicitly selected near face, not arbitrary enum
   order for multiple zero-distance intersections. Do not remove mathematically
   valid zero-depth intersections from the shared helper without a policy test.
6. Express visible sizes and hit tolerances in the same reference viewport units
   after converting native pointer/window/framebuffer coordinates. Do not scale
   hitboxes directly with Minecraft GUI scale or assume raw physical pixels are
   equally usable at 1080p, 4K and mixed-DPI window sizes.

## Move: Transportation And Placement

### Primary Grip

The volumetric cross is one neutral/accent grab target, not three colored axis
controls. Draw it at the geometric center. Also indicate a reticle-hit boundary
as a valid grip, so a center off-screen or outside a huge box never blocks use.
Capture that local anchor without recentering the selection on press. During
the grip, show one small marker at the captured boundary point rather than two
competing bright crosses. Keep the original thin outline subdued for comparison.

Use the player's root position, not the rendered eye/camera transform:

```text
rawDelta = (playerRootNow - playerRootAtGrip) + explicitWheelDelta
draft = gripOrigin.translate(snapAndConstrain(rawDelta), worldLimits)
```

This is conceptual math, not a new API call signature. Normal carrying uses
one-block displacement snapping. The selection's dimensions and ordered-corner
relationship do not change. Maintain double-precision unsnapped input; derive
integer bounds from the grip origin, not repeated rounded per-frame changes.
Update from a coherent native player-position sample without view-bob, crouch
eye-height, FOV animation or independent camera transforms. Looking 180 degrees
while stationary must leave all six bounds unchanged.

Wheel input adds an explicit small world-space displacement along the view
direction captured at that wheel event. Accumulated wheel vectors never rotate
retroactively when the player looks elsewhere. Retain fractional scroll input;
positive pushes away and negative pulls nearer. Accumulate fractional vectors
at each event, not a delayed whole notch reassigned to a later view direction.
Normalize diagonal directions,
snap the resulting coordinate displacement and expose the actual X/Y/Z change;
one wheel notch is not promised to change exactly one axis by one block.

### Exact Placement

RMB on the indicated target opens an owned compact numeric popover, styled with
existing UI components, not a full-screen editor or permanent axis toolbar.
Move offers `Delta X`, `Delta Y`, `Delta Z` integer fields with click steppers.
Values are relative to the draft when the popover opened, initially zero. Do not
reapply a typed delta on every render. A valid value updates only its local
numeric draft; Enter or the close control stages one gesture and returns to
navigation. G then applies the overall session. Esc cancels the overall session.

Use actual native text widgets/focus behavior. Reject overflow/invalid input
without changing the last valid preview. Show a constraint/permission failure
without silently replacing the requested number. This provides exact far
relocation without requiring a long flight, and a no-hold alternative.

The owned popover needs a narrow exception to the existing generic Screen
cancellation policy, not removal of that policy. Chat, inventory and unrelated
screens still cancel. All remapped tool keys remain text input while a field is
focused. Opening precise entry ends any grip before taking focus.

### Honest Limitations

- The grabbed point does not remain under the reticle when looking around.
  This is deliberate: transportation follows the player, not head rotation.
- Ordinary gravity/knockback are real player displacement and therefore affect
  a held carry. Releasing ends it; local history can undo it. Do not freeze
  physics or infer movement solely from WASD, which would ignore collisions.
- A recognized teleport, respawn, camera-entity replacement or server correction
  ends the grip at the last valid draft. A dimension/world change cancels the
  whole session. Do not use an arbitrary distance cutoff that breaks fast flight.
  Where correction signals cannot be distinguished reliably, validate the
  fallback per runtime before claiming protection.
- Survival remains survival. No free-camera or flight bypass is added. Numeric
  placement is the alternative to inaccessible terrain or a long manual trip.
- Continuous holding can fatigue some users. Numeric fields/steppers supply a
  non-drag path; an explicit hold/toggle accessibility preference can be assessed
  later. Do not add an accidental double-click latch to the default gesture.

## Resize: Precision Without A Session-Wide Freeze

### Faces

Keep the successful current invariant: a chosen face changes only its world
axis, with the opposite face fixed and a one-block minimum dimension.
Capture face identity, origin, view basis, sensitivity and relative mouse
position when the grip starts. Between grips, looking and moving remain native.

Reuse the fixed projected-axis direction and end-on vertical fallback principle
from `FaceDrag`, but use unbounded relative captured mouse coordinates rather
than a released cursor that eventually reaches the desktop edge. Up expands and
down contracts in the end-on fallback; latch that choice for the whole gesture.
Ordinary camera movement or projection noise must not invert it halfway through.

Normalize input to reference viewport units and bound the control/display gain
both below and above. The current far-depth multiplication must not cause
uncontrolled growth with target distance. A candidate prototype range is roughly
1/48 to 1/8 snap step per reference pixel; these are calibration candidates, not
approved universal settings. Distance can inform the initial gain only within
those bounds. Explicit fast steps or numeric entry cover large changes.

Multiply this bounded step-unit gain by the active configured step. Switching
1 to 8 should make the same new mouse travel produce eight times the displacement,
not require eight times more travel merely to reach the next snap. Rebase first
so the switch itself changes no coordinates. This deliberate fast scale is
different from unintended sensitivity growth with target distance.

The configured fast modifier affects precise Resize grips only. Shift retains
native descent in Move, draft idle and free-camera corner grips. If a custom
fast binding overlaps another movement binding, suppress only that overlapping
action while the precision grip owns it. Do not disable unrelated movement.

### Corners

Precise corner grips use a captured view-plane basis for two components and
wheel input for depth, with the same bounded gain. A linear captured-basis
mapping avoids the far-offscreen ray/plane singularity; it does not depend on
subsequent player movement. Apply its world components only to the captured
corner's three faces. Keep the opposite corner and axis identities fixed.

Free-camera grips outside the explicit editor instead use the same root-world
displacement plus explicit wheel nudges as Move, applied to one corner only.
This retains flying while holding a corner but removes head-turn amplification.
They always use one-block steps and never consume Shift for faster resizing.

Precision entry remains available for each target: one signed outward amount
for a face, or three signed coordinate deltas for a corner. Values start at zero
relative to the draft at popover opening. Never confuse inclusive block maxima
with the rendered positive face at `max + 1` in displayed coordinates or size.

### Snapping, Saturation And Re-Grabbing

- Snap displacement, not absolute world coordinates. A box starting at an
  arbitrary coordinate must not jump onto a different global grid on pickup.
- At a step/gain change, keep the current integer draft and rebase the input
  reference. Discard the old fractional residual at this explicit transition;
  do not re-snap the previous total displacement against the new step.
- Preserve fractional motion during ordinary constant-step input. Use a
  deterministic signed rounding convention, including half-step ties; the
  existing ties-to-even rule is not by itself a defect. No coordinate easing.
- On reaching a height/coordinate limit or opposite face, discard only the
  rejected overflow of the saturated axis. Keep other axes and valid fractional
  input. Reversing must respond after the normal snap threshold, not after
  retracing the entire rejected movement. Do not repeatedly derive from the
  last rounded result and thereby introduce frame-rate-dependent drift.
- Limits never swap corners, shrink a translated box or silently cross a face.
  Highlight the constrained handle once; do not generate repeated chat messages.
- Re-grabbing starts from the current draft. Viewport/camera-mode changes end a
  grip safely and preserve its last valid draft if the world/origin is still
  valid. Focus loss/unrelated screens cancel the session. Ordinary animated FOV
  does not change a latched precision sensitivity.

## Authoritative Safety And Coexistence

Keep the current integrated-local-WorldEdit and cuboid/extend scope. Remote
CUI-only visualization does not authorize editing, including on Fabric/NeoForge
servers. Public WorldEdit region operations, not renderer state, remain the
only application path. Revalidate identity, ordered corners, world and current
permissions on the integrated server thread immediately before applying.

Mixed gestures require semantic operation intent rather than inferring all
permissions from the final dimensions. Example: shift X by +20, then expand
the east face by 5 requires shift plus expand, not a contraction merely because
the final west boundary is inside the original box. Conversely, expanding one
side and contracting the opposite side can have unchanged dimensions without
being an authorized shift gesture. Undo/redo must restore the operation plan
alongside coordinates; coordinates alone cannot encode permission intent.

Validate the ordered semantic operations on a region clone, check each required
permission and the expected final bounds, then apply on the same server-thread
turn. Learn changes and emit authoritative CUI synchronization once. Do not
advertise a database-style atomic rollback guarantee from WorldEdit's API. If
an unexpected exception occurs after mutation begins, read the actual selection,
resynchronize and report failure; never retry blindly or display a false origin.

Keep client drafts out of SelectionManager and native WorldEdit sessions.
Command chat cancels editing; clipboard preview is temporarily hidden without
discarding its mesh/preferences. Isolation stays on confirmed bounds during the
draft and changes once after acknowledgement. No isolation/chunk rebuild or
inspector scan per pointer movement. Preserve scoped acknowledgement so a CUI
event sequence does not clear the user's isolated block IDs halfway through.

## Risk Closure Register

The entries below are proposed mitigations, not claims of verified fixes.

| ID | Risk | Design response | Residual check |
| --- | --- | --- | --- |
| R01 | Long-range angular amplification | Root-displacement carry, not live ray | Stationary 180-degree turn leaves bounds identical |
| R02 | Bob/crouch/FOV causes drift | Exclude rendered camera from transportation math | Walk bob, crouch and sprint FOV in all runtimes |
| R03 | Initial grab recenters/jumps | Capture draft plus actual boundary anchor; initial delta zero | Off-center and close/inside pickups |
| R04 | Center inaccessible on large box | Boundary grip and numeric placement | Center outside viewport, player inside volume |
| R05 | Long travel is tiring/slow | Numeric deltas/steppers, repeated release/re-grab | Test both exact distant placement and native flight |
| R06 | Distant Resize too sensitive | Upper/lower reference-unit gain bounds | Calibrate at 1, 10, 100 and 1000 blocks |
| R07 | End-on/parallel-axis inversion | Captured signed axis and latched vertical fallback | All six faces, oblique and end-on views |
| R08 | Screen edge stops a drag | Native disabled-cursor relative input | Multiple screen-width gestures without warping |
| R09 | Step changes jump the draft | Rebase from displayed bounds and new input reference | Toggle normal/fast while pointer is stationary |
| R10 | Bounds create dead reverse travel | Axis-local saturation/overshoot discard | Move/resize far past each limit, reverse slightly |
| R11 | Front/back handles conflict | Deterministic proximity/depth, hover tie band, explicit candidate list | Single block, thin box, narrow FOV and far box |
| R12 | Very close/inside face unpickable | Separate near-plane drawing from outgoing/selected face policy | Exact face plane, epsilon inside/outside, camera inside |
| R13 | Accidental attacks/use/pick | Explicit draft owns construction; captured release latch | Hold LMB through G/Esc and re-entry |
| R14 | Hotbar and old-face scroll conflict | Capture wheel only during a grip or focused widget | Idle scroll changes slot but never selection |
| R15 | Shift descent broken outside precision | Separate modifier ownership from active-session state | Move, idle, free corners, custom fast binding |
| R16 | Release unexpectedly commits | Same staged apply semantics for every grip | Server corners unchanged until short G apply |
| R17 | G hold/tap or stalled frame applies unexpectedly | Existing monotonic gesture state machine; no menu during grip | 399/400/401 ms, key repeats, missed frames |
| R18 | Esc cannot cancel accepted server work | Explicit submission boundary; one pending request | Esc before submission versus after acceptance |
| R19 | Dirty draft lost after an individual mistake | Local gesture undo/redo; immutable cancel origin | Mixed gestures, undo during grip, redo after new edit |
| R20 | Mixed permission intent misclassified | Semantic operation plan, authoritative per-operation checks | Shift+expand without contract, resize without shift |
| R21 | External selection/world changes overwritten | Original token validation; cancel stale local draft | Axe/commands, selector replacement, disconnect |
| R22 | Focus, screen or popup steals input | Owned popover exception only; release barriers | Alt-Tab, chat, inventory, remapped G in text field |
| R23 | Runtime/mapping/camera divergence | Shared tested math; six native adapter fixtures | Third-person, camera mods, DPI, resize, zoom |
| R24 | Expensive collateral preview updates | Coordinate-only draft; downstream work after acknowledgement | Isolation on, clipboard visible, huge loaded selection |
| R25 | Physics introduces unintended carry | Do not hide native movement; easy release/history/numeric path | Falling, knockback, correction and teleport signals |
| R26 | Gesture appears smooth but trails actual bounds | Animate opacity only, never coordinates or hitboxes | HUD, contour and committed corners agree frame by frame |

## Performance And Validation Gates

Keep the hot path independent of selected volume: six faces, eight corners,
twelve edges, a cached small cross mesh and a few coordinate calculations.
No picking loop over blocks, loaded sections or clipboard content. No new
framebuffer/blur, GPU readback, lighting, entities or terrain recompile for the
gizmo. Keep draft math in integers/doubles and subtract the camera origin before
converting render vertices to floats; far coordinates must not turn a one-block
handle into unstable geometry. Cache text/layout until displayed values or the
viewport actually change.
Accumulate relative pointer/wheel input without allocating a bounds snapshot
for every high-frequency mouse event. Publish at most one local draft update
per frame, with a final coherent sample on release/apply. Player-root sampling
can use native position interpolation without rendered eye effects; never add
cosmetic easing to integer bounds. Verify low-FPS and high-polling-rate devices
do not lose events or change the resulting displacement.
Do not create server tasks on render or mouse events; retain coalesced reads
at most once per four eligible ticks, at most one outstanding request and one
commit per apply. A disabled feature adds no session polling or gizmo rendering.

Semantic history is per completed gesture, not per input sample. Even a huge
selection needs only coordinates and compact operation data. WorldEdit commit
cost depends on the operation plan, not block volume. Existing post-commit
isolation invalidation and inspector scans still have a real cost; measure them
separately and retain section-scoped invalidation rather than world reloads.

Implementation must not start with decorative arrow changes. Proposed order:

1. Shared input-ownership/session model and deterministic geometry tests.
2. No-jump transportation, precision gain, step rebasing and anti-windup tests.
3. Semantic operation plan, permissions, stale origin and exact-once apply tests.
4. Native input/projection adapters, minimal handles, numeric focus and history.
5. Six packaged runtime tests and real-game screenshots/profiling.

Acceptance matrix:

| Area | Required cases and pass condition |
| --- | --- |
| Math | Positive/negative/far coordinates; all faces/corners; reversed Pos1/Pos2; minimum dimensions; overflow. No drift from splitting the same unconstrained path into different frame counts. |
| Move | Zero-delta pickup, look-only motion, bob/crouch/FOV, diagonal flight, Shift descent, release/re-grab, wheel turns, numeric long travel. Dimensions and ordered-corner relationship remain unchanged. |
| Resize | All end-on/oblique axes, near/inside targets, far sensitivity, stationary step changes, corner depth, limit reversal. Only captured boundaries change. |
| Input | Hold LMB through apply/cancel; rapid G taps; long holds; focus loss; menus; hotbar; key remaps; fast modifier; custom mouse sensitivity/raw-motion preference. No stuck input or incidental construction. |
| Authority | Each permission combination, mixed modes, external selection changes, world switch, pending/failing commit. No writes before apply, no world-block/history/clipboard changes. |
| Coexistence | Command preview, clipboard preview, isolation, inspector, overlay disabled, ruler/grid settings. No draft-triggered terrain rebuilds or preference changes. |
| Presentation | GUI scales 2/3/4; 720p/1080p/1440p/4K; narrow/wide windows; mixed DPI; Russian/English; bright/dark worlds; Reduce Motion. Readable non-overlapping HUD and target indicators. |
| Runtime | Fabric and NeoForge, each native 1.21.11/26.1/26.2 adapter with an actually compatible WorldEdit installation. Early unavailable WorldEdit combinations remain gated, not relabeled as supported. |
| Performance | Compare disabled, enabled idle, target hover, active grip, radial and numeric states at ordinary and extreme selection sizes. Record CPU/GPU frame-time distributions and allocations, not only average FPS. |

Numeric sensitivity, hover tolerance and whether users understand transportation
versus look-following require actual comparative trials. Do not claim zero FPS
cost, full shader/modpack compatibility or elimination of all ergonomic defects
from code inspection or similarity to another editor.

## Primary Research And Interpretation

- [Axiom gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html): distinguishes
  independent axes, planes and a camera-distance center grab; documents push/pull
  scrolling. Useful precedent, but its complete control scheme is different.
- [Unreal transforming actors](https://dev.epicgames.com/documentation/en-us/unreal-engine/transforming-actors-in-unreal-engine):
  separates axis, plane and free-center translation and provides numeric entry.
- [Unity positioning](https://docs.unity3d.com/6000.0/Documentation/Manual/PositioningGameObjects.html)
  and [increment snapping](https://docs.unity3d.com/6000.0/Documentation/Manual/SnapIncrements.html):
  conventional transform constraints and configurable increments, not proof of
  the addon's proposed rebase behavior.
- [Godot 3D workspace](https://docs.godotengine.org/en/stable/tutorials/3d/introduction_to_3d.html):
  provides an overlapping-target list, numeric transforms and separate navigation
  controls. Supports explicit disambiguation rather than hidden target guessing.
- [Three.js TransformControls source](https://github.com/mrdoob/three.js/blob/dev/examples/jsm/controls/TransformControls.js):
  captures transform origins and a constraint at pointer-down; separates picking
  geometry from visible handles. Reviewed as a reference, not a Java dependency
  or a claim that it implements this proposal's anti-windup rule.
- [GLFW input guide](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode):
  disabled-cursor mode supplies captured unbounded coordinates without repeated
  manual warps. Preserve Minecraft's raw-motion preference and native lifecycle.
- [W3C dragging alternatives](https://www.w3.org/WAI/WCAG22/Understanding/dragging-movements.html):
  a web accessibility precedent for click/numeric alternatives to sustained
  dragging, not a claim of Minecraft WCAG conformance.
- [WorldEdit SelectionCommands](https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/command/SelectionCommands.java):
  public shift/expand/contract operations, permission checks and selector/CUI
  adjustment lifecycle. Version-specific signatures must still be checked against
  the installed binaries before implementation.

The player-root transportation choice, exact draft semantics and residual-risk
assessment are this project's design conclusions. External references establish
available patterns and API behavior, not empirical validation of those choices.
No third-party code, shaders, assets or libraries were copied into the project.

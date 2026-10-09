# Selection movement: free-camera transportation

Historical proposal, revised 2026-10-09 for the 1.8.0 development
line after auditing both Move and Resize. Only the subsequent first Move slice
is implemented, not this complete proposal. Current behavior is recorded in
[SELECTION_EDITING_DESIGN.md](SELECTION_EDITING_DESIGN.md). The complete evidence,
alternatives, risk register and acceptance matrix are in
[SELECTION_INTERACTION_AUDIT.md](SELECTION_INTERACTION_AUDIT.md).

Implementation follow-up: [task-first decision](SELECTION_WORKFLOW_DECISION.md).
It supersedes this proposal's continuous-vector wheel rule and defers the
passive-corner confirmation change. Only its Move carry slice is being
implemented; the numeric and Resize proposals below are not runtime features.

The [comparative study](SELECTION_INTERACTION_COMPARISON.md) evaluates this carry
candidate against constrained dragging and exact nudges using documented tools
and public user reports. Carry is not a proven default; the study proposes
cardinal wheel input for comparison with the continuous-vector rule below.

## Revised Decision

Make Move a first-person grab-and-transport tool. A volumetric arrow cross is
one grab handle, not three independently draggable XYZ axes. Holding LMB carries
the whole selection while native walking, flight and mouse look remain usable.

Revise the earlier fixed-distance live-ray proposal: carry follows the player's
actual world displacement, not camera rotation. At a 100-block grab radius, a
stationary 1-degree turn moves a ray-tracked point about 1.745 blocks; at 1000
blocks it moves about 17.453. Following the view is therefore not a robust
distance-independent default. Smoothing would not remove that ambiguity.

Looking around alone must leave the box unchanged. Exact placement is provided
by explicit wheel nudges and a compact numeric popover. This is a reasoned
recommendation requiring an in-game prototype, not a claim that root-displacement
carrying is an established identical Axiom workflow.

The prior recommendation to leave Resize unchanged is superseded by the
two-tool audit. Its proposed narrower input ownership, precision gain limits,
step rebasing and common apply/cancel semantics are specified there.

## Interaction Contract

| Action/state | Proposed behavior |
| --- | --- |
| Tap G with Move chosen | Start a local draft; show one grab cross and compact offset readout. Keep native look, movement and physics. Do not enter a blocking mode without an authoritative origin. |
| Aim at the cross or indicated boundary | Highlight a valid grip before intercepting it. A distant/off-screen center must not be the only entry point. |
| Hold LMB on a target | Capture the current draft, player-root position and actual local anchor. Do not recenter the box or change dimensions. |
| Walk/fly while holding | Translate by actual player-world displacement with one-block snapping; normal Shift descent remains available. |
| Look around while holding | Change only the view. Previously accumulated displacement does not rotate with the camera. |
| Wheel while carrying | Add small explicit pushes/pulls along the view direction at that wheel event. Capture wheel only during a grip and retain fractional scroll input. |
| Release LMB | End the grip, keep the local draft for inspection/re-grabbing. Never commit on mouse release. |
| RMB on an indicated Move target inside the draft | End a grip if necessary; open compact Delta X/Y/Z fields and click steppers. Enter stages a local adjustment and closes the popover, not the WorldEdit transaction. |
| Tap G again | Finish the current grip and apply the composed draft once. Suppress any trailing captured mouse release. |
| Esc before submission | Cancel the complete draft, including an owned popover or radial menu. A submitted server commit is not silently reversible. |
| Hold G for the radial menu | Keep the 400 ms threshold and bounded dot/cancel center. Do not open it during a grip or focused text input. A long-key release never applies. |

Idle wheel input stays native; a remembered target alone does not authorize
editing it. Construction clicks are blocked throughout an active draft, including
missed grips, to prevent accidental world edits. Outside editing, ordinary
building remains native except a visible enabled free-corner entry gesture.

## Handle And Large Selections

- Use one small cached solid cross mesh in the existing neutral/accent treatment.
  No RGB axis implication, spinning, pulsing, dynamic light, blur or entities.
- Scale and pick in coherent reference viewport units so distance, FOV, mixed
  DPI and GUI scale do not make the grip microscopic or screen-filling.
- A slightly larger invisible hit tolerance is acceptable; hover fades must
  not animate bounds or hitboxes. Respect Reduce Motion.
- Allow grabbing a reticle-indicated nearby boundary rather than flying to the
  box center. During that grip, show one small anchor marker, not two competing
  bright crosses. The anchor never changes Pos1/Pos2 identities or orientation.
- A center behind the camera cannot be grabbed through the camera. Inside a
  box, the outgoing visible boundary remains eligible. Picking is coordinate
  geometry, not a block/terrain scan.

## Movement Calculation

Use the existing CuboidDraft.translate safety foundation, not the live-camera
rayPoint tracking constraint for transportation:

```text
rawDelta = playerRootNow - playerRootAtGrip + explicitWheelDelta
draft = gripOrigin translated by snapped, constrained rawDelta
```

This is conceptual pseudocode, not a proposed native API signature. Maintain
double-precision unsnapped input against an immutable grip origin. Do not derive
the next frame from the previous rounded box. Exclude eye-height changes,
rendered view bob and FOV effects; preserve ordinary actual player displacement.

Each wheel event adds a normalized view-direction vector at that instant.
Turning later cannot rotate already accumulated nudges. Diagonal direction plus
coordinate snapping can change several axes; show the actual integer result
rather than promise that every notch always moves exactly one axis one block.

At a coordinate/height limit, discard only rejected overshoot on that axis so
reversing does not require undoing hidden dead travel. Do not shrink the box or
discard valid movement on other axes. Coordinate limits are not an arbitrary
travel/volume/chunk-loading cap, nor a claim about a custom Minecraft WorldBorder.

## Exact Placement And Remaining Tradeoffs

The numeric popover uses existing native text widgets, focus and UI styling.
Delta fields start at zero relative to its opening draft; stage one adjustment,
not one per render. Reject invalid/overflowing or unauthorized values without
silently changing the request. It is an owned-screen exception, not removal of
chat/inventory cancellation. Remapped tool keys remain text while typing.

Transportation intentionally does not keep the anchor under the crosshair when
turning. Falling/knockback also count as real displacement during a held grip;
release, local gesture undo and numeric placement are the safety alternatives.
Do not freeze player physics or invent flight to avoid those tradeoffs.

Recognized correction/teleport/camera-entity transitions end the grip safely;
world/dimension changes cancel the session. Avoid arbitrary distance thresholds
that break fast legitimate flight. Third-person and camera-mod behavior require
native validation; unsupported projections must fail safely, not invert input.

## Safety And Validation Gates

Keep integrated-local WorldEdit and cuboid/extend scope. Use official region
operations, original-session validation, permissions, clone checking,
learnChanges and authoritative CUI synchronization. Moving a selection moves
coordinates, not blocks. Mixed Move/Resize sessions need the semantic operation
plan described in the audit; final dimensions alone cannot determine intent.

Keep command/clipboard-preview arbitration and isolation acknowledgement.
No selection-volume scan, terrain rebuild, clipboard recapture, framebuffer or
server task per pointer event. Update downstream systems after confirmation,
not during the local draft. Do not promise zero FPS cost without measurements.

1. Test no-jump pickup, stationary turns, bob/crouch/FOV, Shift descent, diagonal
   flight, release/re-grab, wheel direction changes and exact numeric travel.
2. Test boundaries/inside targets, an off-screen center, overlaps, mixed DPI,
   world limits, correction events and local gesture undo/redo.
3. Test G/Esc/long-hold conflicts, text focus, pending commits, stale selections,
   permissions and preservation of block/clipboard state.
4. Validate all six native adapters, both final universal JARs and actual
   screenshots/frame times. The two-tool audit includes Resize-specific gates.

## Research Basis

- [Axiom gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html): center/axis/
  plane distinctions and push/pull. Its retained-distance center behavior is a
  useful alternative, not the chosen transportation constraint here.
- [Unreal transforms](https://dev.epicgames.com/documentation/en-us/unreal-engine/transforming-actors-in-unreal-engine)
  and [Godot 3D editing](https://docs.godotengine.org/en/stable/tutorials/3d/introduction_to_3d.html):
  conventional manipulation, exact input and target disambiguation patterns.
- [Three.js TransformControls](https://github.com/mrdoob/three.js/blob/dev/examples/jsm/controls/TransformControls.js):
  immutable gesture origins and separate picking/visible geometry.
- [GLFW cursor modes](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode):
  native captured input without per-event manual cursor warps.
- [WorldEdit SelectionCommands](https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/command/SelectionCommands.java):
  authoritative public region-adjustment lifecycle.

References establish precedents and API behavior. They do not validate this
addon's proposed interaction, performance or compatibility in every modpack.

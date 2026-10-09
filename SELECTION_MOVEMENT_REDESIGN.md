# Selection movement: free-camera carrying

Status: proposed, not implemented. Reviewed 2026-10-09 for the 1.8.0 development
line. This document does not replace the implemented behavior recorded in
SELECTION_EDITING_DESIGN.md until the new interaction passes validation.

## Decision

Make Move a first-person grab-and-carry tool. A volumetric arrow cross is one
grab handle, not three independently draggable axes. Holding LMB carries the
selection while the player looks, walks, or flies normally. This is the primary
interaction, not a secondary mode hidden behind a modifier.

The user clarified that the arrows describe the desired appearance, not an
axis-constrained interaction. Do not introduce colored XYZ handles, plane
squares, or another camera-locking editor as the default solution.

## Why the current interaction limits travel

The current SelectionEditController captures editView when entering either
tool. capturesCamera then suppresses keyboard movement and player movement for
the entire explicit editing session. Move uses the released screen cursor and
a plane captured from that view. Camera-relative wheel movement is a separate
operation. This suits short adjustments but makes long relocation awkward.

Replacing the visual handle alone would preserve these limitations. Separate
Move's active session from ownership of camera/player input. Keep Resize's
tested behavior unchanged in this revision.

## Interaction contract

| Action/state | Proposed behavior |
| --- | --- |
| Tap G with Move chosen | Start a local draft; show the grab handle and compact offset readout. Keep native first-person mouse look and movement. |
| Aim at the handle | Highlight the entire cross as one target. A visible boundary can also be grabbed; the center must not be the only usable entry point. |
| Hold LMB on a valid target | Capture a point and its distance from the camera. Carry the entire draft with the live view ray. No initial jump or rotation. |
| Walk/fly/look while holding | Move the grabbed point with the player. Normal flight controls, including Shift descent, remain available. |
| Wheel while carrying | Push/pull the grabbed point along the view ray in one-block increments. Capture scrolling only during the grab; retain fractional wheel input. |
| Release LMB | End only this grab. Keep the draft at its last position, so the player can inspect it and grab it again. Do not commit to WorldEdit. |
| Tap G again | Finish any current grab, apply the complete draft once, and exit. Suppress the trailing mouse release so it cannot break a block. |
| Esc at any point | Cancel all uncommitted movement, restore the original displayed selection, and exit. |
| Hold G for the radial menu | Keep its existing threshold, bounded dot, and empty cancel center. Never apply a draft on long-key release. Do not open it in the middle of a grab. |

Move keeps Minecraft's captured mouse for navigation. There is no desktop
cursor to reach a window edge, and no automatic camera pan, teleportation,
increased player speed, or new free-camera capability. Creative flight stays
creative flight; survival movement retains its native restrictions.

Shift is not Move's fast-step modifier: suppressing descent again would recreate
an existing usability problem. Carrying snaps to one block, as free corner
grabs already do. Normal/fast Resize settings remain intact.

## Handle and large selections

- Use a small cached 3D mesh with solid shafts and arrowheads. One neutral/accent
  treatment indicates one action; do not imply independent axes through RGB
  colors or XYZ labels. No spinning, pulsing, blur, dynamic light, or entities.
- Position the main cross at the cuboid's geometric center. Scale its world
  geometry to keep a bounded, readable screen footprint; do not let distance,
  FOV, GUI scale, or a huge selection make it microscopic or screen-filling.
- Use a slightly larger invisible picking tolerance than the visible handle.
  Apply a short hover fade without animating selection coordinates or hitboxes.
- Also allow grabbing a visible part of the boundary. Highlight that target
  before intercepting the grab. For a large box, grab the nearby point being
  aimed at, not its distant center. Show a small temporary cross at that point
  while carrying; the center marker can be subdued to avoid duplicate targets.
- A center behind the camera is not interactive through the camera. If the
  camera is inside the box, a visible exit boundary remains a valid target.
  Define boundary picking in screen pixels, without scanning block contents.

Do not require flying to the middle of a large selection, and do not invent a
second persistent pivot editor. A boundary grab only changes the local grab
anchor, never Pos1, Pos2, dimensions, or the world-aligned selection orientation.

## Movement calculation

Reuse the existing free-corner rayPoint path for tracking and CuboidDraft.translate
for translation. Keep one immutable draft origin per grab and an unsnapped
double-precision displacement accumulator. A simplified constraint is:

```text
initialTarget = initialEye + normalized(initialRay) * initialDistance
target        = liveEye    + normalized(liveRay)    * currentDistance
displacement  = target - initialTarget
draft         = grabOrigin.translate(displacement, oneBlockStep, worldBounds)
```

Preserve the initial grab offset; do not align the selection's center to the
crosshair when LMB goes down. Normalize the view ray and reject non-finite
values. Measure initial distance at the actual boundary/handle grab. At very
small distances, use a safe positive depth while preserving the zero initial
displacement. Keep enough distance for a usable push/pull gesture.

Derive each result from grabOrigin, not the previous rounded draft. This avoids
frame-rate-dependent drift and accumulated rounding errors. Keep unrounded
input during sub-block motion. No world-space easing: after snapping, the
outline and coordinates must describe the same selection immediately.

Long travel comes from native player/camera movement, not from scaling screen
pixels into very large distances. No arbitrary travel cap, chunk-loading
requirement, or selection-volume cap belongs in this coordinate-only operation.
Retain actual world coordinate/height bounds and whole-box clamping without
shrinking the selection. Test reversal at a boundary for dead travel before
choosing the final accumulator rebasing rule.

## Safety and integration

Reuse SelectionEditTransaction's official Region.shift commit, permission
checks, original-session identity validation, selector learning, and CUI update.
Moving the selection does not move, copy, delete, or generate blocks.

Keep integrated local WorldEdit and cuboid/extend scope. Do not add a remote
chat-command fallback or silently convert other shapes. Preserve existing
clipboard/command-preview arbitration and isolation-mask acknowledgement.
Do not recalculate these subsystems on each mouse event.

Construction actions are blocked in the explicitly activated tool, including
missed grabs, to avoid breaking blocks while trying to pick a handle. Wheel
ownership is narrower: only carrying uses it. Leaving the mode restores native
input immediately. Focus loss, another Screen, world changes, and stale
selections retain safe cancellation; no stuck LMB, lost mouse grab, or delayed
application on return. Third-person and viewport/FOV changes need explicit
validation; unsupported transitions must cancel safely, not invert movement.

## Implementation and validation gates

1. Separate camera-look, movement, construction, scrolling, and radial-menu
   ownership. A single capturesCamera flag must not govern all Move input.
2. Add shared carry math and boundary/handle picking tests before adapting the
   six native controllers/renderers. Reuse the existing translation transaction.
3. Test no-jump pickup, release/regrab, one-block snapping, wheel reversal,
   close/inside/distant targets, center off-screen, diagonal flight, Shift descent,
   rapid camera turns, and 1000-plus-block carries in an existing test world.
4. Test G/Esc during a grab, held-key menu conflicts, focus/screens, permissions,
   world limits, stale sessions, no world-block writes, and unchanged Resize.
5. Run all six native fixtures using both final universal JARs. Inspect real
   screenshots at 2x/3x/4x, narrow/wide viewports, and relevant FOV settings.
6. Profile the active tool separately. Coordinate and picking work must be
   constant-size; cache arrow geometry. Do not claim zero FPS loss without data.

## Research basis and limits

- [Axiom gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html) documents a
  center node that moves freely at a retained camera distance, plus push/pull
  scrolling. This supports the grab constraint, not a claim that our proposed
  complete G/flight workflow is identical to Axiom's. No Axiom code/assets copied.
- [Axiom selections](https://axiomdocs.moulberry.com/editor/selections.html)
  distinguishes moving selection coordinates from moving blocks.
- [Unreal translation gizmos](https://dev.epicgames.com/documentation/en-us/unreal-engine/transforming-actors-in-unreal-engine)
  separates axis, plane, and free-center gestures. The recommended default here
  uses free carrying, not the more complex axis/plane control set.
- [Unity positioning](https://docs.unity3d.com/6000.0/Documentation/Manual/PositioningGameObjects.html)
  and [Three.js TransformControls](https://threejs.org/docs/pages/TransformControls.html)
  were reviewed as conventional precision-editor alternatives, not dependencies
  to embed in Minecraft. They do not by themselves solve first-person long travel.
- [GLFW cursor modes](https://www.glfw.org/docs/latest/input_guide.html#cursor_mode)
  supports unbounded captured input; use native Minecraft navigation for carrying
  rather than desktop-cursor clamping or repeated cursor warps.
- [WorldEdit SelectionCommands](https://github.com/EngineHub/WorldEdit/blob/master/worldedit-core/src/main/java/com/sk89q/worldedit/command/SelectionCommands.java)
  provides the public selection-shift lifecycle already used by this project.

The free-flight workflow and single-cross appearance are design recommendations
for this addon. Their usability still requires an in-game prototype and user
testing; documentation of a similar grab constraint is not proof of usability
or compatibility in every modpack.

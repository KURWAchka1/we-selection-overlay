# Ready-made interaction references

Reviewed 2026-10-09. Research only: no mod implementation, dependency additions,
game installation or new runtime/performance tests. This supplements the
[two-tool audit](SELECTION_INTERACTION_AUDIT.md) and
[Move proposal](SELECTION_MOVEMENT_REDESIGN.md); it does not mark either as
implemented or prove the usability of our player-root carrying candidate.

Follow-up: [task comparison and community evidence](SELECTION_INTERACTION_COMPARISON.md)
adds a source ledger, contrary preferences, Axiom Pilot / Scroll for WorldEdit
references and a prototype evaluation plan. It is research, not user-test data.

## Shortlist

| Reference | Why it fits | Adopt or evaluate | Do not copy |
| --- | --- | --- | --- |
| Axiom Box Select / selection gizmos | Actual Minecraft selection refinement | Indicated handles, selected-face feedback, separate selection movement and block movement | The entire editor, combined rotation/scale controls or proprietary code/assets |
| Litematica area selection nudging | Actual Minecraft box/corner manipulation | Explicit target identity and exact cardinal one-block nudges | Its whole creative Move tool or an expanding collection of overlapping modifiers |
| Blockbench | Cuboid editing and precise small adjustments | Move/Resize distinction; a number supporting drag, steppers and direct entry; explicit source/target anchoring | Persistent sidebars, free mesh scaling, model rotations or unrelated editor infrastructure |
| Roblox Terrain Editor | Direct manipulation of a voxel selection volume | Boundary scale handles, position/size entry and wireframe-before-apply workflow | Terrain mutation or proportional scaling of blocks |
| Unreal Modeling Mode | Structured preview/accept/cancel sessions | Local changes before confirmation, in-tool undo and delta entry | Combined transform gizmo or automatic apply when switching tools |
| ImGuizmo | Reusable open-source gizmo implementation | Hover versus active-drag state, snapping, screen-sized handles and explicit axis-flip policy | A new native Dear ImGui/JNI rendering stack merely to draw our small tool |
| Effortless Building | In-world tool selection rather than a desktop editor | Hold-to-open radial menu and contextual options | Its many build modes, server block operations or a crowded command ring |

## 1. Axiom: Closest Overall Minecraft Reference

Documented behavior: Box Select refines a cuboid through three gizmos and offers
numeric coordinates. The selection editor explicitly distinguishes moving the
selection from moving its blocks and exposes scale nodes for selection bounds.
Its general gizmos distinguish center, axis and plane controls; a center drag
retains camera distance. [Box Select](https://axiomdocs.moulberry.com/tools/selection/boxselect.html),
[selections](https://axiomdocs.moulberry.com/editor/selections.html),
[gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html).

Design conclusion: use clear world-space handles and an obvious selected target.
Our volumetric cross can remain a single grab target; its appearance must not
promise independent axis dragging. Axiom's full editor is not a drop-in control
scheme for first-person Minecraft navigation.

The Builder Move page describes moving actual blocks/entities, a hologram,
one-block wheel nudges and explicit axis constraints. This is a different tool
from moving selection coordinates. Its controls must not be cited as proof that
our draft changes blocks, or that it implements player-root transportation.
[Builder Move](https://axiomdocs.moulberry.com/builder/buildertools/move.html).

## 2. Litematica: Strongest Exact-Nudge Reference

The author documents selecting a whole box versus its individual corners and
nudging the selected element with a modifier plus wheel. The same interaction
can target an area box, corner, origin or placement.
[Author explanation](https://github.com/maruohon/litematica/discussions/791),
[area selection guide](https://github.com/maruohon/litematica/wiki/Multi%E2%80%90Region-Area-Selections-%28pre%E2%80%90mod%E2%80%90rewrite%29).

Source inspection: `MouseScrollHandlerImpl` at commit
`479a4f0cbefa09666af4cd49cbb19d727090fc2d` uses the closest looking world direction
and a signed one-block amount. It gates handling on the tool/context and routes
nudging to the selected element, not a general continuous view vector.
[Pinned source](https://github.com/maruohon/litematica/blob/479a4f0cbefa09666af4cd49cbb19d727090fc2d/src/main/java/litematica/input/MouseScrollHandlerImpl.java).
This is the repository's `ornithe/1.12.2` branch, not evidence that every recent
Fabric/NeoForge release uses identical internal methods.

Important counterexample: in the same author discussion, the 2026-09-15 reply
warns that the separate creative Move tool is awkward and distinguishes an
accidentally selected corner from the whole box. Do not copy that tool wholesale.

Design conclusion: a wheel operation must clearly indicate WHAT is selected
and WHICH signed axis will move. Cardinal nudging is worth testing against our
current proposed continuous-vector wheel: a single notch can then mean exactly
one block on one axis, without diagonal rounding surprises. Our defaults should
not inherit old-mod limitations such as sign-only handling of fractional scroll.

## 3. Blockbench: Compact Precision Controls

Its official guide distinguishes Move and Resize and describes numeric controls
with three entry methods: drag the value, click arrows, or type directly.
Vertex Snap explicitly chooses a source vertex and a destination vertex to align
objects; it can move or scale. [Official guide](https://blockbench.net/wiki/guides/blockbench-overview-tips/).

Design conclusion: use one compact displacement/size control with multiple
input methods rather than another large settings window. World-space dragging
is the main action; exact entry is available when needed. Keep size semantics
as integer selection boundaries, not percentage scaling of a model.

Source-to-target anchoring is an interesting later solution for placing a chosen
corner at a destination without flying the full distance. It is not yet part of
the approved interaction. A Minecraft version would need clear face/cell anchor
semantics and loaded-world picking without requesting chunks. Do not silently
add another mode or a competing meaning for RMB in the current proposal.

## 4. Roblox Terrain Editor: A Voxel Box, Not Just A Mesh

The official Select tool uses move draggers, scale handles, numeric position/size
and voxel snapping. Transform can disable Live Edit, display a wireframe and
wait for an explicit apply action.
[Terrain Editor](https://create.roblox.com/docs/studio/terrain-editor#select).

Design conclusion: resizing through a box boundary and confirming a wireframe
draft are established patterns for voxel volumes. Our tool still only changes
WorldEdit selection coordinates. Do not import Roblox's terrain edits, rotation,
resampling or proportional scaling. Its editor viewport is not proof that its
mouse/camera ownership works unchanged inside a first-person game.

Official visual reference: [selection handles](https://prod.docsiteassets.roblox.com/assets/studio/terrain-editor/Select-Region-Labeled.jpg).
This is an external reference, not a mockup or screenshot of our mod.

## 5. Unreal Modeling Mode: Local Session Before Apply

The official documentation describes tools that preview changes before Accept,
Cancel discarding them and undo/redo within the active tool. A transform panel
supports world-space and local-delta numeric entry. Its actual workflow can
apply changes when switching tools, which is not our desired rule.
[Modeling Mode](https://dev.epicgames.com/documentation/unreal-engine/modeling-mode-in-unreal-engine?lang=en-US).

Design conclusion: keep one local draft with gesture history, then one explicit
WorldEdit commit. Switching Move/Resize must preserve the local draft rather
than apply it. Do not copy the whole workflow just because it contains a useful
confirmation pattern.

## 6. ImGuizmo: Ready Code, But Not A Drop-In Minecraft Library

The upstream project provides C++ Dear ImGui controls for translation, rotation
and scale. Its public header exposes bounds manipulation, snapping, handle size,
hover/active target queries, and an explicit axis-flipping policy.
[Repository](https://github.com/CedricGuillemet/ImGuizmo),
[public header](https://github.com/CedricGuillemet/ImGuizmo/blob/master/src/ImGuizmo.h),
[documentation](https://github.com/CedricGuillemet/ImGuizmo/blob/master/docs/documentation.md).

Design conclusion: target hover, captured target and tool enabled are different
states. Keep handle size readable and make orientation policy deliberate rather
than guessing from yaw. Do not introduce native bindings and a separate ImGui
backend just for this feature. Existing Minecraft render/input adapters are a
better fit for the project's six native runtimes and small artifact requirement.
This is an integration judgment, not an ImGuizmo performance benchmark.

## 7. Effortless Building: Radial Menu Precedent

The project's README documents holding Alt to choose a build mode through a
radial menu, with contextual options and undo/redo controls nearby.
[Project and demonstrations](https://github.com/Requios/effortless-building-multi).

Design conclusion: a transient in-world ring fits a building tool. Our two-sector
ring can stay small and focused. This reference does not establish our hidden
bounded-dot behavior, hold threshold, frame-time cost or conflict-free keybinds.

## Proposed Refinements To Validate

These are research candidates, not silent changes to the prior specification:

1. Compare exact cardinal wheel nudges against the previous continuous-vector
   wheel. For the cardinal version, show the current signed direction BEFORE
   scrolling, add a small axis-choice hysteresis near diagonal views, and execute
   only the indicated axis. Keep fractional input without reassigning a previous
   axis's remainder to an unrelated axis. No extra persistent XYZ gizmo required.
2. Prototype Blockbench-style numeric controls inside the already proposed
   compact popover: drag, click-step and direct integer entry. Group the whole
   adjustment into one local history gesture and preserve normal text focus.
3. Keep face/corner handles for resizing and a single grab cross for free
   transportation. Do not bundle rotation, pivot editing or automatic scale.
4. Test source-to-target anchoring separately as a possible later precision
   shortcut. It needs an input contract before inclusion, not an undocumented
   modifier competing with existing actions.
5. Treat player-root carrying as our addon-specific candidate. The reviewed
   ready editors substantiate gizmos, snapping, nudges and numeric entry; they
   do not demonstrate that root-follow carrying is the universally best default.

No reviewed package supplies our exact G/radial, free-flight, cuboid boundary,
WorldEdit permission and six-runtime requirements as a ready Java component.
Adopt the proven interaction parts while keeping application authority in the
existing official WorldEdit adapter. No third-party source/assets were copied.

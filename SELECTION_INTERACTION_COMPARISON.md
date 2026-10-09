# Selection manipulation: comparative research

Historical desk study, reviewed 2026-10-09 for the 1.8.0 development line.
The study itself made no production/dependency changes, game installation,
user experiment or runtime/performance tests. It is not a claim of consensus.

Read alongside the [code audit](SELECTION_INTERACTION_AUDIT.md),
[ready-tool references](SELECTION_INTERACTION_REFERENCES.md) and
[Move candidate](SELECTION_MOVEMENT_REDESIGN.md). The
[implementation record](SELECTION_EDITING_DESIGN.md) tracks subsequent changes.

Implementation follow-up: the [task-first decision](SELECTION_WORKFLOW_DECISION.md)
selects a first Move carry slice and records which earlier rules are superseded
or deferred. The study itself is historical research, not runtime validation.

## Executive Decision

Use complementary controls rather than asking one drag to solve every task:

- Coarse transportation: prototype the single-cross, player-root carry candidate
  against conventional constrained dragging. Do not call carry a proven winner.
- Fine placement: prefer exact, indicated cardinal wheel nudges for the next
  comparison, rather than unlabelled continuous view-vector nudges.
- Long, known offsets: provide compact integer entry and click steppers. Flying
  hundreds of blocks while holding LMB must not be the only practical route.
- Resize: indicated faces/corners, a constraint captured at pickup, bounded
  sensitivity and a numeric alternative. No hidden target changes during a drag.
- Safety: one local draft, release to inspect, G to apply, Esc to cancel before
  submission. Selection coordinates, world blocks and clipboard placement are
  different targets, not interchangeable meanings of "Move".

The evidence supports clear targets, precision alternatives and reversible
previews. It does not establish that most Minecraft players prefer our carry
math, a particular hold threshold or a particular modifier.

## 1. Method And Evidence Limits

The community case ledger contains 12 directly relevant Minecraft building
discussions and one adjacent Create input-conflict report. Publication years
range from 2021 to 2026; the two pre-2023 cases are explicitly legacy context.
Twelve discussions are English-language and one is Russian-language. Maintainer
answers and product documentation are identified separately, not counted as
additional independent users.

Search families included placement/movement questions, accidental activation,
selection-mode confusion, positive placement experiences, Axiom versus WorldEdit
preferences, and Russian-language familiarity/preferences. Examples:

```text
site.reddit.com/r/litematica moving schematic placement controls
site.reddit.com/r/litematica "keep accidentally" hotkeys
site.reddit.com Axiom WorldEdit selection move controls intuitive building
site.reddit.com/r/WorldEdit selection shift move
Russian-language search: Axiom / WorldEdit control convenience and preferences
"Scroll for WorldEdit" github
3D positioning mouse constraints user study
```

This is purposive sampling of useful cases, not an exhaustive systematic review.
Complaint-oriented searches were supplemented with positive and mixed reports;
that reduces one obvious bias but does not make the sample representative.

Inclusion: first-person workflow reports, concrete control questions, relevant
replies and official descriptions of actual controls. Exclusion: generic mod
rankings, SEO comparisons, unrelated crashes, easy-place networking problems,
AI image-editing projects named WorldEdit, and our own mod's promotional posts.
Repeated search hits and cross-posts are not separate cases. A second article
by the same identifiable author is contextual evidence, not another vote.

Limits: public support threads overrepresent difficulties; successful users may
never post. Search indexing and visible comment sorting are selective; private
Discord communities were not sampled. User versions/configurations are not
controlled, and legacy behavior may differ from current releases. Upvotes,
downloads and stars are not participant counts or an estimate of preferences.

Evidence labels used below:

| Label | Meaning | What it cannot establish |
| --- | --- | --- |
| D | Officially documented behavior | Usability superiority or measured FPS |
| S | Source inspected in the preceding reference study, with a pinned revision | Identical internals in other releases |
| U | Public user report or stated preference | A reproduced defect, prevalence or causation |
| I | Our analytical comparison | Measured task performance |
| P | Proposed behavior to test | A validated default |

## 2. Community Case Ledger

Dates below are publication dates where available; C11 is year-only. Summaries
are paraphrases. Each row is one discussion, not one person or one vote.

| ID | Date / context | Observation and boundary |
| --- | --- | --- |
| C01 | 2024-12-06, technical builders | The author values Axiom's preview before committing. Replies disagree about learning difficulty and distinguish Builder from Editor mode. This supports feedback and differentiated workflows, not universal Axiom superiority. [Discussion](https://www.reddit.com/r/technicalminecraft/comments/1h7puav/) |
| C02 | 2023-11-03, broad Minecraft thread | Comments include both difficult and intuitive Axiom-control experiences, plus a preference for familiar WorldEdit commands in precise work. A popular meme is not a usability poll; the votes on the post do not endorse each reply. [Discussion](https://www.reddit.com/r/MinecraftMemes/comments/17n3b2v/) |
| C03 | 2026-02-23, first-time Axiom user | The user expects blocks to move when only the selection highlight moves. The report directly illustrates selection-versus-content ambiguity; it does not demonstrate a broken selection transform. [Discussion](https://www.reddit.com/r/feedthebeast/comments/1rcvnbz/axiom_help/) |
| C04 | 2025-02-19, Litematica placement | The user reports unexpected placement moves and offset placement. Replies explain origin identity, nudging, coordinates and tool prerequisites. Several distinct causes are possible; do not label all of them a bad drag algorithm. [Discussion](https://www.reddit.com/r/litematica/comments/1it2uma/) |
| C05 | 2026-08-03, Litematica origin/placement | The user cannot move the expected target. Replies discuss tool mode, whole-placement versus subregion selection and origin handling. Advice is not fully consistent; the exact cause remains unresolved in the visible discussion. [Discussion](https://www.reddit.com/r/litematica/comments/1veojd6/) |
| C06 | 2025-11-21, Litematica wheel input | Alt-scroll does not work; the user confirms the nudge binding was actually Z and changing it solves the problem. This is a verified explanation within the thread, not evidence that wheel translation itself fails. [Discussion](https://www.reddit.com/r/litematica/comments/1p2x34o/) |
| C07 | 2025-02-28, Litematica selection | The expected two-corner behavior is actually Expand mode. The user confirms the explanation helps; the author also explains the mode-switch binding. This is a mode-visibility case, not a reproduced coordinate bug. [Discussion](https://www.reddit.com/r/litematica/comments/1j0fwhr/) |
| C08 | 2022-07-26, legacy placement help | A vertical-position question receives both look-direction wheel nudging and coordinate-field/stepper alternatives. The maintainer describes numeric adjustment as perhaps more common, but supplies no usage measurement. [Discussion](https://www.reddit.com/r/litematica/comments/w8ys29/) |
| C09 | 2025-03-24, ordinary building | The user accidentally invokes Litematica controls while trying to build normally and ultimately disables it. Replies suggest rebinding, a different tool item and placement locking. This is one concrete interference case, not an abandonment rate. [Discussion](https://www.reddit.com/r/litematica/comments/1jim6t9/) |
| C10 | 2024-11-02, WorldEdit/redstone macro | Repeated G-triggered movement reportedly loses part of a build. The visible discussion does not identify the macro or diagnose its cause. Relevant warning: do not conflate selection movement with repeated world-block operations. [Discussion](https://www.reddit.com/r/redstone/comments/1gi8vfc/) |
| C11 | 2026, adjacent Create input case | A player remaps sprint/sneak and reports selection shortcuts still conflicting after modifier changes. No reproduced diagnosis is provided. Use this only as a cross-mod remapping test case, not evidence of our own defect. [Discussion](https://www.reddit.com/r/CreateMod/comments/1unge1g/) |
| C12 | 2026-09-14, Russian-language preferences | One commenter favors familiar WorldEdit, another strongly values Axiom. This is a small, casual discussion, useful as counterevidence to a universal preference claim. [Discussion](https://www.reddit.com/r/Mine_Craft_/comments/1wg5gx8/) |
| C13 | 2021-04-21, legacy technical building | A builder praises Litematica's ray/wheel placement, reusable components and iterative previews; another participant questions the need for such tools. Positive placement evidence must not disappear from a complaint-led sample. [Discussion](https://www.reddit.com/r/technicalminecraft/comments/mv73k9/) |

### Maintainer And Context Checks

M1: Litematica discussion #791 starts with a successful modifier/nudge answer
in 2023. A 2026-09-15 exchange explains why a selected corner moves instead of
the whole box and warns against the separate creative world-block Move tool.
The warning is not a condemnation of every area-selection nudge.
[Maintainer explanation](https://github.com/maruohon/litematica/discussions/791).

M2: A 2025 builder comparison explicitly favors tools according to task and
experience, including command familiarity for city builds. The identifiable
author also appears as C02's post author, so this is not another independent
preference vote. Product/version/pricing statements in the old post are not
used as current technical facts.
[Builder comparison](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/mods-discussion/3212582-builders-axiom-vs-world-edit-2025).

### Recurring Themes, Not Population Frequencies

| Theme | Supporting cases | Design implication (I/P) |
| --- | --- | --- |
| Target/mode/origin identity | C03, C04, C05, C07; M1 | Indicate the exact target before interception; keep it captured during a gesture. Distinguish selection from contents and clipboard. |
| Hidden prerequisites or a rebound shortcut | C04, C06, C08 | Display actual configured bindings and valid/invalid target state, not assumed physical keys or silent failure. |
| Accidental interference with normal building | C04, C09 | Editing needs a visible context, release barriers, cancel and a way to disable incidental corner manipulation. |
| Value of inspecting before applying | C01, C13 | Keep a local reversible draft. Do not commit on grip release. |
| Multiple precision routes | C04, C08 | Pair in-world control with numeric/stepper adjustment; these maintainer replies are not independent usage measurements. |
| Preference depends on experience/context | C01, C02, C12, C13; M2 | Preserve WorldEdit's command workflow instead of replacing it with a compulsory full editor. |

Conclusion from U evidence: target clarity and interference are recurring
failure modes worth prioritizing. The sample cannot rank carry versus axis
dragging by majority preference, or justify a numerical satisfaction claim.

## 3. Ready Solutions Compared

These are documented mechanisms, not measured comparisons of their users.
The preceding reference study contains the more detailed adoption boundaries.

| Reference | Documented mechanism | Useful part / integration limit |
| --- | --- | --- |
| Axiom (D) | Box refinement gizmos and numeric coordinates; separate selection and block movement; center/axis/plane controls | Most relevant visual interaction reference, but an editor viewport is not the same input context as ordinary first-person play. [Box](https://axiomdocs.moulberry.com/tools/selection/boxselect.html), [gizmos](https://axiomdocs.moulberry.com/editor/gizmos.html), [selections](https://axiomdocs.moulberry.com/editor/selections.html) |
| Litematica (D/S) | Select box/corner/origin, then modifier-wheel nudge; inspected legacy source chooses a cardinal looking direction | Strong exact-nudge precedent; do not inherit hidden modes, sign-only scroll handling or world-block Move behavior. [Maintainer](https://github.com/maruohon/litematica/discussions/791), [pinned source](https://github.com/maruohon/litematica/blob/479a4f0cbefa09666af4cd49cbb19d727090fc2d/src/main/java/litematica/input/MouseScrollHandlerImpl.java) |
| Axiom Pilot (D, author description only) | Captured first-person mouse look, WASD flight, crosshair tool input and LMB gizmo grabs | Close navigation precedent. No source inspection or runtime test here; the description does not establish player-root carry math or broad player preference. [Project](https://modrinth.com/mod/axiom-pilot) |
| Scroll for WorldEdit (D) | Wheel-driven move/shift/expand/contract/stack modes, configurable activation and scroll threshold | Ready precedent for less typing, not measured demand; its documented command modes are not our staged coordinate transaction. No dependency/source copied. [Repository](https://github.com/hf02/scroll-for-worldedit) |
| Effortless Building (D) | Hold-to-open radial mode selection with contextual options | Relevant temporary tool-choice pattern, not proof of our exact G threshold or hidden bounded-dot behavior. [Repository](https://github.com/Requios/effortless-building-multi) |
| Blockbench (D) | Move/Resize tools; number dragging, steppers and typing; source-to-target Vertex Snap | Best compact precision reference. Anchor placement is a separate possible enhancement, not another undocumented RMB meaning. [Guide](https://blockbench.net/wiki/guides/blockbench-overview-tips/) |
| Roblox Terrain Editor (D) | Voxel selection handles, numeric position/size, optional wireframe-before-apply | Useful volume/confirmation precedent. Its terrain resampling, rotation and world mutation are outside our selection-only scope. [Docs](https://create.roblox.com/docs/studio/terrain-editor) |
| Unreal Modeling Mode (D) | Preview, Accept/Cancel, in-tool undo and numeric transform controls | Useful draft lifecycle, but do not copy tool switching that applies edits. [Docs](https://dev.epicgames.com/documentation/unreal-engine/modeling-mode-in-unreal-engine?lang=en-US) |
| ImGuizmo (D) | Ready C++ transform/bounds gizmos, snap, hover/active distinction and axis-flip policy | A behavioral/code reference, not a drop-in Java control. Adding a native UI/backend merely for these handles is not justified by this study. [Repository](https://github.com/CedricGuillemet/ImGuizmo), [header](https://github.com/CedricGuillemet/ImGuizmo/blob/master/src/ImGuizmo.h) |

WorldEdit itself distinguishes shifting selection coordinates from moving region
contents, and cuboid selections are world-axis aligned. Retain these semantics;
visual references do not authorize rotating a cuboid or changing block contents.
[Selection docs](https://worldedit.enginehub.org/en/latest/usage/regions/selections/),
[command definitions](https://worldedit.enginehub.org/en/latest/commands/).

### What Academic Evidence Can Add

Sun, Stuerzlinger and Riecke (SAP 2018) compared three input methods for sliding
objects in an HTC Vive. Their within-subject experiment used 12 university
participants and counterbalanced conditions; mouse input performed well in that
setup. All targets contacted surfaces, head turns were deliberately unnecessary,
and the experiment was not Minecraft, root carrying or a desktop free-flight
comparison. Consequently, do not import its performance outcome as our winner.
[Author-hosted paper](https://junweis.github.io/files/3225153.3225167.pdf).

Useful methodological lesson (I): compare identical tasks, record error as well
as time, counterbalance learning and distinguish user preference from accuracy.
VR hand/gaze papers found during searching do not validate ordinary mouse/key
defaults here. A plausible paper title is not a substitute for matching its task.

## 4. Mechanism Comparison By Task

The following is an analytical evaluation (I), not a scored user experiment.
"Good" means the mechanism directly supplies the required constraint; it does
not mean observed faster completion or a known satisfaction percentage.

| Mechanism | Precise 1-block adjustment | Long relocation | Looking/flying while gripping | Principal drawback |
| --- | --- | --- | --- | --- |
| Live view ray at fixed distance | Poor as distance grows | Can sweep far quickly | Yes, but looking also moves the target | Angular error scales with distance; depth is ambiguous. |
| Independent XYZ gizmo | Good with snapping, except end-on axes | Possible, requires viewport management | Needs separate look ownership | Small/overlapping handles and repeated re-grabs. |
| Captured screen plane + wheel depth | Good after capture | Awkward at viewport edges | Mouse delta cannot both drag and freely turn the view | Depth direction and frozen plane can become unintuitive after navigation. |
| Player-root carry | Needs nudge/numeric finish | Unbounded by mouse travel, but costs physical travel time | Yes; looking alone changes nothing | Anchor does not follow reticle; falling/corrections also translate it. |
| Cardinal wheel nudge | Exact on one announced axis | Repetitive without a coarse/number alternative | Yes, if axis policy is explicit | Diagonal-view ambiguity; wheel/hotbar conflicts without context ownership. |
| Integer fields + steppers | Exact, includes a no-drag path | Good when destination/offset is known | Field focus temporarily owns keyboard | Requires knowing a number or choosing a meaningful anchor. |
| Source-to-target anchor | Exact once source/destination semantics are clear | Good if destination is visible/pickable | Can inspect between picks | Occlusion, unloaded targets and cell-versus-face ambiguity. |

### Analytical Checks

1. Fixed-radius view tracking has displacement `2*d*sin(a/2)` for a stationary
   eye and angular change `a` in radians. At 1 degree, `d=100` gives about 1.745
   blocks and `d=1000` about 17.453. Smoothing delays this movement; it does not remove the
   dependence on distance. This is geometry, not measured hand jitter.
2. Root carry can use `delta = rootNow - rootAtPickup`, independent of look
   rotation. For a required straight displacement D and bounded travel speed v,
   travel time is at least `D/v`; detours and acceleration can increase it.
   Therefore "can carry arbitrarily far" does not mean "fast to place far away".
3. A cardinal wheel operation changes one coordinate by one configured step.
   Snapping a continuous diagonal displacement may instead change multiple
   coordinates together or defer a change until accumulated thresholds are met.
   Neither is intrinsically broken, but only the former promises one-axis nudges.
4. A world axis viewed end-on has a vanishing screen projection. Picking/dragging
   cannot recover a well-conditioned 1D constraint merely by enlarging the icon.
   Use a captured fallback direction, exact nudges or numeric entry, with feedback.
5. A defined source vertex A and destination vertex B give exact translation
   `B-A`. A block cell and its positive exterior face differ by one coordinate
   unit; anchor semantics must be explicit before adding a point-to-point tool.

## 5. Task Decisions And Remaining Risks

| Task | Recommended candidate (P) | Risk and mitigation to verify |
| --- | --- | --- |
| Move a box a few blocks beside the player | Root carry plus exact nudge, compared against conventional plane/axis drag | Release to inspect; local gesture undo; no recentering on pickup. Do not assume carry is easier. |
| Move a box 128 or 1000 blocks | Numeric delta now; optional carry; investigate anchor placement later | Carry alone is slow/fatiguing. Numeric movement of bounds must not require target chunks to be loaded. |
| Raise only the top face by 1 | Captured face + outward step/numeric amount | The indicated face, not a remembered hidden face, owns input. Inactive wheel remains hotbar input. |
| Resize inside/very close to the box | Outgoing face/visible handle picking with an explicit overlap choice | Near-plane drawing must not make the actual boundary unreachable; never silently choose the rear target. |
| Resize a distant/end-on face | Bounded captured screen constraint plus numeric/step fallback | Large distance must not amplify gain without bound; do not silently flip drag direction mid-gesture. |
| Look around/fly with a held box or free corner | Root carry candidate, native view and locomotion | A look-only test must leave coordinates unchanged; release/numeric entry handles falls and long travel. No hidden physics suppression. |
| Build normally with the addon installed | No active editor interception; optional incidental-corner feature can be disabled | Avoid accidental attack-hold conversion, old-face wheel actions and stale targets. |
| Change Move to Resize after several adjustments | Preserve the same local draft | Switching tool must not commit; one final apply after authoritative revalidation. |
| Correct a mistake or leave the tool | Gesture undo/redo, whole-draft Esc, explicit apply | Undo before apply concerns selection coordinates only. Do not promise Esc can unsend an accepted server mutation. |

### Recommended Wheel Candidate

This is a deliberate refinement to compare against the continuous-vector wheel
in the earlier audit, not an implemented replacement:

- In Move, show one signed cardinal direction before accepting a wheel gesture.
  Use a small choice hysteresis; latch direction for the scroll burst so slight
  view changes do not redistribute consecutive events among axes. The indicated
  direction must remain the actual one, even if the camera turns during the burst.
- Positive wheel input moves along that indicated direction; negative reverses
  it. In a face grip, use the face's captured outward direction instead of camera
  direction. Corner precision depth needs its own visibly captured direction.
- Keep fractional input and actual event magnitude. On direction/step transitions
  rebase explicitly or keep separate residuals; never give an old axis's fraction
  to a different axis. One step must have the same meaning at every frame rate.
- Own wheel only during the relevant grip or focused widget. A remembered target
  is not permission to intercept idle hotbar scrolling. Burst timing and hysteresis
  size are prototype calibration questions, not evidence-backed fixed constants.

### Keep The Interface Small

Keep the existing two-tool ring; do not add separate coarse/fine/axis/anchor
sectors. A grip shows its target and actual signed change, not an instruction
paragraph. Color is reinforced by target geometry and short coordinate identity;
selected state must remain recognizable without distinguishing red from green.

The Move cross is one pickup handle, not three arrows that misleadingly promise
independent axis dragging. Boundaries can supply another indicated pickup when
the center is off-screen. After pickup, the active anchor is stable; do not
recenter the draft under the reticle when the player turns. Hide irrelevant
handles, but never hide the active target or a constraint failure.

Keep numeric entry in the proposed owned popover, not a persistent full editor.
It supports click steppers and direct integer typing; value dragging is optional
pending comparison. Read actual configured tool bindings. Native movement keys
and a precision modifier are distinct roles; do not hard-code physical Shift as
"fast" across every state or globally suppress descent.

Esc continues to cancel the entire unsubmitted draft, including inside the
owned popover, as specified in the audit. Enter stages only the local numeric
adjustment. Text focus must not reinterpret a typed remapped G as Apply.

### Performance And Authority

The proposed math changes bounds, not world blocks. Keep picking restricted to
a bounded set of selection handles/faces; do not search the selection's cells.
Root delta, captured constraints and nudges require no volume scan or new chunk
loads. Numeric coordinate relocation also does not need destination terrain.

Reuse the audited shared draft/transaction boundary and native render/input
adapters. Coalesce input and publish at most one draft per frame, with a final
sample before release/apply; cache layout/text until relevant values change.
Rendering actual block previews is separate work and must not be restarted for
each pointer event. A cheap arithmetic path is not a measured FPS guarantee.

Retain integrated-world authority checks, semantic permission checks, stale
origin rejection and one pending apply. Public control examples do not establish
that remote CUI supplies mutation authority. No server bypass or chunk request
is justified by a selection-editing UI.

An explicit native teleport/dimension/world transition must end the transport
interaction rather than count as ordinary carry motion. Do not substitute a
guessed distance cap for a real transition signal. Ordinary falls/knockback are
still a tradeoff of following actual motion; release, undo and numeric entry
reduce the risk without pretending to infer the player's intent or disabling
physics. Camera-mod/root mismatches need native-adapter verification.

## 6. What Needs An Actual Prototype Comparison

Use a small formative pilot of 8-12 builders, with WorldEdit-command users,
Litematica/Axiom users and less experienced builders represented. This is a
problem-discovery study, not enough to estimate a population majority. Do not
recruit only supporters of the new control idea or silently treat friends as a
representative sample. No participants have been recruited or tested here.

Compare baseline constrained dragging, root carry with precision alternatives,
and the same precision controls without carry. Separately compare cardinal and
continuous-vector wheel input. Keep feedback, snap size and confirmation behavior
matched when testing a mapping, otherwise the result cannot isolate that mapping.

Give each participant the same brief training and counterbalance control order.
Use different equivalent coordinates to avoid memorizing the answer. Record
prior tool experience and report individual results, not only a pooled average.

| Task set | What to record |
| --- | --- |
| Move 8 blocks; place exactly +1 up; return | Time, final coordinate error, corrections, wrong-axis events |
| Move 128 and 1000 blocks, known and unknown destination | Travel time separately from manipulation, numeric use, re-grabs, fatigue comments |
| Resize each face and a corner; camera inside/near/end-on/far | Wrong target rate, direction reversals, unwanted boundaries changed |
| Fly diagonally, descend, look 180 degrees, release/re-grab | Look-induced movement, unintended translation, pickup jumps, navigation interference |
| Normal building, idle scrolling, rebind sneak/sprint/tool key | Accidental edits, hotbar conflicts, binding comprehension |
| Undo a gesture, cancel draft, apply, switch tool; focus loss | Surprise commits, lost work, stuck input, ability to explain current state |

Hard correctness gates, distinct from usability preferences: zero writes before
apply; one pending mutation; no dimension change during Move; no coordinate
change on look-only carry; deterministic captured targets; no input leak after
cancel; no block/clipboard mutation; permission and stale-selection failures
leave authority intact. Test all six runtime adapters before shipping.

Measure frame time, input latency and allocations in actual Minecraft with
isolation/clipboard/command-preview combinations. Do not turn a design goal into
an invented benchmark. If an approach is fast but causes more placement mistakes,
do not select it solely on median time. A split preference may justify keeping
one optional mapping, but not an ever-growing set of hidden modes.

## 7. Decision Status

Adopt as design constraints: visible target identity, local preview/apply/cancel,
exact coordinate semantics, native navigation outside owned precision input,
numeric alternatives, no idle wheel takeover and no volume-proportional editing
work. These combine official precedents with concrete failure cases.

Prefer for the next prototype: root carry plus exact cardinal nudges and compact
numeric precision; bounded face/corner Resize. Compare carry rather than declare
it the default based on popularity. Source-to-target placement remains a later
candidate until its anchor and input contract are designed and tested.

Still open: carry versus constrained drag task performance, scroll-burst timing,
axis hysteresis, gain calibration, close-target tolerances, grip fatigue and
discoverability. No credible source in this study establishes a majority choice
for those details. The current mod behavior and release status are unchanged.

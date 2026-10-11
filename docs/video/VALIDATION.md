# Corrected Review: 2026-10-11

This records technical checks and targeted visual review, not full owner approval
or a public upload. Production mod code and both release JARs are unchanged.

## Native Evidence

Logs are local ignored artifacts under `build/desktop-tests/`:

- Core retake: `WesoTest-27ae83cad30443e68e02c21df962e654.log`.
  Successful guarded run, 50 native takes, 120 frames each. Native rounded
  tracking area: 637 chunks at distance 12, then mesh/light completion.
- Beauty opening: `WesoTest-8a1008f30efb46f2b3a4add198cae006.log`.
  Successful separate shader run, four 120-frame takes at distance 12.
- Palette pickups: `WesoTest-81edcd94f35b4aae9c417548069987d6.log`.
  Successful vanilla run, seven 90-frame takes, including preset confirmation.
- Continuous closing: `WesoTest-776975536f414955b790bd3c38f85af9.log`.
  Successful separate guarded shader run, one 660-frame take, 637 chunks at
  distance 12 downloaded and rendered. Every source house block remains unchanged.
- Editing pickups: `WesoTest-4060fc36222a4ae9a6702768b0799309.log`.
  Successful guarded vanilla run, ten 120-frame takes at distance 12. Assertions
  check a real corner grip, Move radial choice, camera-only draft stability,
  independent X/Y wheel movement, mouse release, G apply and Escape cancellation.
  The selected house's block-state fingerprint remains unchanged. Cursor-isolation
  preflight: `WesoTest-9081339faf2b4bcba2e9c25332025c1c.log`.
- All runs require the private desktop and cursor-isolation agent. No normal
  desktop activation, system input, user-save edits or cursor confinement.
- The core assertion compares every selected house block state after all drafts;
  true clipboard transform identity is checked after rotation/reflection drafts.
- Offline native frame sampling and last-frame holds are not an FPS/latency test.

The first 8-chunk distant core shoot and the first thumbnail are superseded.
Only the exact successful retake logs above may be used for the corrected edit.

## Export Checks

The complete English master and Russian-captioned review each decode to exactly
8,231 frames: 274.3667 seconds, 1920x1080, 30 fps. There are 83 frame-aligned
cut decisions, eight chapters, 64 voice/caption cues, 61 retained vanilla source
clips and five separate beauty sources. Replacement is held for six seconds;
Escape restoration is separate. Source cadence is not artificially accelerated.

English MP4 SHA-256:
`a952779ca011d2a992c3a058955875e0d21bdd3d330234eb6d5d2956076ec2e9`.

Russian review MP4 SHA-256:
`fd5ac27413561cd40bdf73f24b4b3c10333fb58b411e1f8a6e396b6073599da1`.

AAC audio measured from the actual English master: integrated -16.12 LUFS,
true peak -3.86 dBTP, loudness range 2.30 LU. English narration is synthetic;
the owner approved the opening voice/pace, not yet the whole finished review.
Russian captions are limited to two 42-character lines and 17 characters/second.
The extracted Russian review excerpts fully decode as well: the editing chapter
contains 1,723 frames and the continuous closing contains 646 frames. Their
inherited full-video chapter metadata is removed.

Targeted actual-output review covers the orbit's beginning/end, the complete
selection silhouette, inspector rows/header, palette preview/controls, and the
full contact sheet. The centered opening title remains in clear sky. Duplicate
English feature callouts are removed. Inspector RU captions use the free header,
editing captions use the free top header, and palette captions sit below its preview.
The revised editing chapter lasts 57.4333 seconds: quick corner/face resizing is
separate from Move, camera orientation, X/Y selection, release, apply and cancel.
Nine short key hints accompany the relevant actions, not the entire chapter.
A crop of the actual bottom-right compass is enlarged during four consecutive
cues through the X/Y choices; its picture is not replaced by fabricated axes.
Only the first/last compass cues fade, avoiding a blink at each internal cut.
Targeted full-size and 640x360 checks cover key hints, selected Y and closing type.
This does not certify a full audiovisual watchthrough or every possible frame.

The earlier 5:28 review is superseded and retained locally under
`build/video/1.8.0/revisions/20261011-before-voice-revision/`. It is not the current
publication candidate. The preceding 4:12 review is also preserved under
`build/video/1.8.0/revisions/20261011-before-interaction-edit/`.
The current 739-word English script and Russian translation
follow `VOICE_STYLE.md`; development fixes and the cut/paste exclusion checklist
are removed. Exact installation requirements remain in the description.

The closing is 21.5333 seconds, consuming 646 consecutive native frames with
offsets 0, 79, 253 and 395 across its four cues. Every closing decision has zero
freeze padding; a short source is rejected. The final voice ends at 269.511s,
the last caption at 269.941s, and the closing product/download lockup starts at
270.167s. After its 0.35-second arrival it is fully visible for over three seconds.
One 0.6-second fade affects picture and graphics together and reaches black on
the exact last frame; actual MP4 and Blender frame RGB extrema are all zero.
Neither speech nor the last caption is cut off. The approved voice and base speed remain;
the owner has not yet auditioned the complete revised narration.

## Tooling

Fourteen pipeline tests pass, including actual FFmpeg frame-padding, continuous
closing offsets/overrun rejection, narration-scope, graphic bounds, native compass
composition and final-caption/fade checks.
Python compilation and guarded PowerShell runner parsing pass. Blender 4.5.9
builds the three-scene full and opening projects in background mode with two
threads and Python failures mapped to a nonzero exit code. Native editable title
fades, caption size/header position, key hints, compass enlargement, closing title
and actual VSE frame output are checked. The editable compass strip retains its
last frame when a narration slot is slightly longer than the native take.
The project builder rejects changed script/edit plans and changed native take
hashes. Both current Blender projects were rebuilt after their verified MP4 edits.

Fabric runtime SHA-256 remains:
`912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0`.

NeoForge runtime SHA-256 remains:
`ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c`.

Generated video, map/world data, fonts, models, editor binaries and Blender
projects remain in ignored local build storage, not Git or either mod JAR.
The publication draft contains the map credit, preview-build notice and remaining
checks. Public 1.8.0 availability and authenticated-channel upload are unverified.

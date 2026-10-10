# WorldEdit Selection Overlay 1.8.0: production brief

## Deliverables

1. A concise English overview, with original native Minecraft demonstrations.
2. English and Russian SRT tracks, plus a Russian-captioned review version.
3. Separate narration master, editable script/timeline, source clips and provenance.
4. A truthful thumbnail and publication text, after picture lock.

Public destination authorized by the owner:
https://www.youtube.com/@NgocChauBui-q6c . Do not publish an unfinished review
cut. Verify the authenticated channel, release availability, subtitle tracks,
thumbnail and video processing before the final public action. No browser-cookie
extraction or reuse of private launcher credentials.

## Editorial treatment

Working title: **See WorldEdit Before You Press Enter | Selection Overlay 1.8**.
Single promise, useful reveal in the first seconds, restrained technical polish.
Most of the frame is the actual mod, not animated captions listing features.
Use straight cuts between actions and a few matched before/after cuts.
Chapter navigation belongs in metadata, not repeated labels over gameplay.
Avoid transition packs, repeated zoom punches, cinematic bars,
or lens effects. Hold each changed state before moving on.

The opening and command sections must preserve the order:
real original -> typed command -> visual draft -> Escape restoration.
A separate shot may demonstrate Enter; label that action unambiguously.
Do not cut away a loading interval and claim real-time benchmark performance.

## Demonstration world

Only a disposable client-game-test world, never the owner's normal save.
The initial gate-only set was rejected by the owner as visually inadequate.
Do not use it in the final edit. The original atelier also lacked convincing
surroundings. The replacement is the completed riverside townhouse in **Celestia
Town**, filmed with explicit creator permission and mandatory credit.
See `SCENE.md` for the reviewed source, exact bounds, cameras and safeguards.

| Zone | Purpose | Shot acceptance |
| --- | --- | --- |
| Townhouse, within 15x18x15 cells | Replacement preview, ruler, cuboid edits | Three-quarter shot shows roof, upper room and facade; full selection in frame. |
| Sandstone walls and brick roof | Block inspection and isolation | Before/after at identical camera; nearby garden remains visible. Reset restores the house. |
| Separate riverside placement | Clipboard rotation/mirroring | Rooftop room makes orientation changes obvious; check background overlap. No blocks pasted. |
| Same city framing | Other CUI shapes | Five different native selections, consistent scale/camera; not fake diagrams. |
| Stationary townhouse view | Settings, palette, themes | 1920x1080 at GUI 2, native UI, readable on phone. |

Lighting: fixed clear daytime. Vanilla textures. Core feature evidence is filmed
without shaders. An optional second beauty profile uses Iris and Complementary
Reimagined at low quality, with motion blur/DOF disabled and restrained bloom.
Do not use shader lighting to claim the mod supplies that lighting. No fog trick
or resource pack masking problems. Creative or spectator only in this disposable world.
Hide debug/chat history when not needed; keep real command entry visible during
the command demonstration. Do not change production render behavior for filming.

## Shot map

| Chapter | Action | Coverage |
| --- | --- | --- |
| Hook | Change townhouse sandstone in a native chat draft, hold, cancel separately | Result before Enter; visual-only safety |
| Selection | Show cuboid, ruler, grid toggle; briefly cycle all shapes | CUI geometry, blocky/smooth, rulers, diagonals |
| Inspect | Open B, search, filter, show loaded counts | Real material list; air is counted, not invented as a visible block |
| Isolation | Select two materials, return to the world, reset | Multi-select, full-bright textures, collision unchanged, selected rows pinned |
| Commands | Replace -> mixed pattern -> air -> walls/flora examples | State/legacy support, supported scope, stable illustrative randomness, F8/Escape |
| Edit | Enable editing, G radial, resize face, apply; Move Y wheel | Draft/apply/cancel, axis switch, no player-follow movement, bounds only |
| Clipboard | Enable, copy the townhouse, type rotate/flip, cancel | Default off, oriented states, unchanged true buffer before Enter, no live cut/paste |
| Customize | O pencil; preset confirmation, RGBA, thickness, undo; themes | Native 3D editor, per-element reset, global rules, all shipped UI styles |
| Install | Show the townhouse and concise compatibility text | One JAR per loader; exact targets, local/server distinction, preview-release status |

`edit-plan.json` maps every narration cue to named native takes. `edit.py`
rejects absent/incomplete takes and verifies viewport, cadence and runtime hash.
Short takes hold their final frame rather than inventing motion. This is not
a latency benchmark. The owner requested a longer opening replacement reveal:
hold it for six seconds, show Escape separately and use a closer house camera.
Replace the rejected small floating title with one readable animated lockup.
The owner also rejected text touching the selected object's outline, and a
low-contrast edition label over trees. Remove the edition label entirely;
preview-build status remains in narration, subtitles and the description.
Center the single opening title in clear sky, above the entire contour.
Remove secondary English callouts duplicated by narration/subtitles. Protect
the selection outline and the object, including through camera movement.
The first generated thumbnail is a rejected draft, not a verified screenshot.
Reshoot both beauty and core footage at 12 chunks and wait for the full requested chunk
download AND mesh render using Fabric's official gametest API before recording.
Subtitles are separate tracks in the English master; the RU-burned copy is a
review version. Publication remains disabled until review/channel checks pass.

Local tooling: verified official portable Blender LTS 4.5.9 and FFmpeg. Blender
provides an editable VSE project, with no visible app or system changes required.
User removed the 30 GB cap, but avoid unnecessary downloads and retain free space.
The portable ZIP is verified against the official publisher SHA-256 list.

`script.json` is the editable narration/caption source. Generated `SCRIPT.md`
and `timeline.json` carry real voice-derived timings, not assumed speaking speed.
No hard-coded chapter timecodes until synthesis and shot lengths are finalized.

## Sound

Neutral English synthetic narrator, conversational pace, not a sales announcer.
Local Kokoro ONNX with a small CPU thread budget; audition voices before final
lock. Speak WorldEdit as World Edit, IDs as normal block names when practical.
No copyrighted music or voice cloning. First master is voice-only; music is an
optional finishing choice once a licensed track is selected.

Use an actual measured two-pass loudness process, target about -16 LUFS and
at most -1 dBTP. Keep the dry master; do not repeatedly compress a compressed MP3.
For subtitles, preserve product names/commands, use concise original Russian
translation and at most two 42-character lines. Aim at <=17 Russian chars/sec.

## Recording and edit safeguards

- Existing private-desktop launcher plus cursor agent, mandatory preflight,
  BelowNormal Job and watchdog. No global input, visible window or desktop switch.
- Native client renders the release JAR; fixture scripts live only under tools.
- Test-only filming configuration must never ship inside either production JAR.
- 1080p30 target, bounded capture queue, no unlimited PNG flood or frame backlog.
- Wait for loaded chunks and stable meshes before an aesthetic shot. Keep a
  separate continuous interaction take if discussing calculation responsiveness.
- Native offline frame sampling may be used for controlled camera movement;
  document it and never present it as an FPS benchmark.
- Keep chapter graphics outside the live UI's important controls. Command shots
  keep captions above the chat input; editing captions use the top-center area.
  Inspector captions use the free top-center header, not the material rows.
- Preserve source clips and edit parameters; a scene can be replaced without
  rebuilding every voice segment or re-recording every other scene.

## Release Gates

- [x] Existing six-runtime production verification and newest clipboard checks reviewed.
- [x] Scope/claims reconciled with current README, release notes and feature docs.
- [x] Research decisions recorded in `RESEARCH.md`.
- [x] Full-mod coverage included, not only new 1.8.0 features.
- [x] Full native coverage captured: 50 core takes, four beauty takes, seven palette pickups.
- [x] Output contact sheet and targeted title/caption/layout frames visually checked.
- [ ] Complete source and finished-edit audiovisual watchthrough approved.
- [x] Full narration generated; owner approved opening voice and pace.
- [ ] Full narration pronunciation and finished audio reviewed.
- [ ] Russian caption timing/translation checked at full and phone size.
- [ ] Complete edit watched start to finish; no source/ghost flicker, blank frames or UI clipping.
- [x] Review exports fully decoded: exact frame count, cadence, dimensions and chapters.
- [x] Final AAC measured: -16.15 LUFS integrated, -3.84 dBTP; no clipping.
- [ ] Final publication rights/account/processing checks passed.
- [ ] Public 1.8.0 download exists, or video explicitly says preview build.
- [ ] Authenticated channel verified; processed upload reviewed and made public.

The production packet can be ready while the final video is not. Keep these
statuses honest; a successful automated render is not human audition or approval.

## Review Revisions

The first complete 5:28 review passed full frame decoding, but is not picture
lock. Its distant core-shot framing and incomplete far background are rejected.
The closer 12-chunk core retake must finish before rebuilding the edit. Keep the
seven successful native palette pickups and the separate loaded beauty opening.
The second thumbnail is a designed candidate based on the loaded native frame,
not an unedited screenshot; its type is confined above the cyan outline.
Check title and caption placement on actual output frames at full and phone size.
The corrected review uses the real beauty orbit for its single product-title
cue. `VALIDATION.md` records the exact source logs and export hashes. Picture
lock and public upload remain pending; do not treat technical passes as approval.

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
- All runs require the private desktop and cursor-isolation agent. No normal
  desktop activation, system input, user-save edits or cursor confinement.
- The core assertion compares every selected house block state after all drafts;
  true clipboard transform identity is checked after rotation/reflection drafts.
- Offline native frame sampling and last-frame holds are not an FPS/latency test.

The first 8-chunk distant core shoot and the first thumbnail are superseded.
Only the exact successful retake logs above may be used for the corrected edit.

## Export Checks

The complete English master and Russian-captioned review each decode to exactly
9,851 frames: 328.3667 seconds, 1920x1080, 30 fps. There are 93 frame-aligned
cut decisions, eight chapters, 70 voice/caption cues, 57 retained vanilla source
clips and four separate beauty sources. Replacement is held for six seconds;
Escape restoration is separate. Source cadence is not artificially accelerated.

English MP4 SHA-256:
`1cd6d35deef64dfe5606d17ee4e511cb447928e29256247942ec1a11d1c08f9e`.

Russian review MP4 SHA-256:
`17a4a6485aae78e8c016b819ee97799b17db788377e24b8feb6d857bc0b1d4e7`.

AAC audio measured from the actual English master: integrated -16.15 LUFS,
true peak -3.84 dBTP, loudness range 2.30 LU. English narration is synthetic;
the owner approved the opening voice/pace, not yet the whole finished review.
Russian captions are limited to two 42-character lines and 17 characters/second.

Targeted actual-output review covers the orbit's beginning/end, the complete
selection silhouette, inspector rows/header, palette preview/controls, and the
full contact sheet. The centered opening title remains in clear sky. Duplicate
English feature callouts are removed. Inspector RU captions use the free header,
editing captions stay above the HUD, and palette captions sit below its preview.
This does not certify a full audiovisual watchthrough or every possible frame.

## Tooling

Nine pipeline tests pass, including an actual FFmpeg frame-padding regression.
Python compilation and guarded PowerShell runner parsing pass. Blender 4.5.9
builds the three-scene full and opening projects in background mode with two
threads and Python failures mapped to a nonzero exit code. Native editable title
fades, caption size/header position and actual VSE frame output are checked.
The project builder rejects changed script/edit plans and changed native take
hashes. The corrected full edit must be rebuilt before its Blender project.

Fabric runtime SHA-256 remains:
`912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0`.

NeoForge runtime SHA-256 remains:
`ea6a8e2e3d58cc1ffcb1473ddddc9036d850ae5f773ef108dab1e570eb379b1c`.

Generated video, map/world data, fonts, models, editor binaries and Blender
projects remain in ignored local build storage, not Git or either mod JAR.
The publication draft contains the map credit, preview-build notice and remaining
checks. Public 1.8.0 availability and authenticated-channel upload are unverified.

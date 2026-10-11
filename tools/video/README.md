# Reproducible video production

This is production tooling, not a Minecraft runtime feature. It never ships
inside the mod JAR. Read `docs/video/PRODUCTION.md` and repository `AGENTS.md`
before recording. Never run the fixture on the owner's normal desktop.
Read `docs/video/VOICE_STYLE.md` before changing spoken copy or translated captions.
Read `docs/video/INTERACTION_EDIT.md` for the focused movement walkthrough,
timed key hints, native compass detail and unobscured closing lockup.
Read `docs/video/COVERAGE_AUDIT.md` for the full-mod outcome/footage audit.

## Local voice and captions

Install `requirements-voice.txt` into `build/video-tools/kokoro` and
`imageio-ffmpeg==0.6.0` into `build/video-tools/python`, using a compatible Python.
The models are downloaded separately and verified against the publisher's
SHA-256 digests. Preserve their license files and provenance.

```powershell
python tools/video/fetch-voice.py
python tools/video/prepare.py --plan-only
python tools/video/prepare.py --limit 3
python tools/video/prepare.py
python tools/video/test_pipeline.py
```

The full pass emits a dry master, 48 kHz narration master, synchronized EN/RU
SRT, positioned RU ASS review captions, chapters and `timeline.json` under
`build/video/1.8.0/`. Changed lines are synthesized separately; unchanged audio
is reused. CPU inference has two threads. Audition and pronunciation review
are separate from the automated technical checks.

## Native footage

Build the current Fabric universal JAR first. Set the actual verified FFmpeg
executable and launch only through the guarded runner:

```powershell
$env:WESO_VIDEO_FFMPEG = 'C:\path\to\ffmpeg.exe'
$env:WESO_VIDEO_FRAMES = '90'
.\tools\test-ui-layout.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Showcase
```

This creates an original disposable demonstration world, invokes native UI
and real WorldEdit APIs, and reads only Minecraft's render target. Source clips
and encoder logs go to `build/video/1.8.0/capture/`. Still evidence is in the
fixture instance's screenshots directory. Frames are sampled offline at a
controlled cadence; they are not a frame-rate or responsiveness benchmark.
Only one frame buffer is retained, software encoding uses two threads, and
the private-desktop Job/watchdog owns all children.

Optional lighting is a separate run, with no change to production JARs:

```powershell
python tools/video/prepare-shaders.py
.\tools\test-ui-layout.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Showcase -Beauty
```

The pinned Iris/Sodium/Complementary files stay in ignored local build storage.
Publisher SHA-512 hashes are checked again before launch. The separate
`run-video-beauty-test` instance uses upstream LOW settings, no depth of field
or motion blur, and restrained bloom. Never use these shots as proof of the
addon's own fullbright rendering. Never redistribute dependency binaries with
the video packet or mod source.

No script publishes to YouTube automatically. Release availability, full edit
review, authenticated channel, caption attachment and processing are explicit
publication gates.

## Credited location scout

The owner rejected the sparse original courtyard. A separate native scout
inspects Celestia Town, whose creator explicitly permits videos with a link:

```powershell
python tools/video/fetch-location.py
.\tools\test-ui-layout.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Showcase -Beauty -LocationPreview
```

The source ZIP stays in ignored build storage. Its reviewed SHA-256 is checked
before import. Only overworld regions are copied into a freshly closed disposable
test save; no original level/player data, mods or packs are installed. Native
Minecraft handles chunk upgrading. Source clips go to `capture-location/`.
These are location scouts, not final feature demonstrations or a finished video.
Credit **Celestia Town by Fireless007 / leolerenard** and link its CurseForge page
when any of its footage is used. Never ship the map in the mod or Git repository.

## Full Credited-Location Shoot and Edit

```powershell
$env:WESO_VIDEO_FRAMES = '120'
.\tools\test-ui-layout.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Showcase -CreditedLocation
python tools/video/fetch-editor.py
```

Use `-Beauty -CreditedLocation` for the separate four-take opening profile.
Core takes go to `capture-celestia/`, beauty takes to `capture-celestia-beauty/`.
The exact capture log is required; a failed or stale take never gets a substitute.
`--opening` renders only the hook chapter, explicitly a short review excerpt.

For the separate vanilla palette pickup pass, set `$env:WESO_VIDEO_PICKUPS = '1'`
before `-Showcase -CreditedLocation`, then remove that environment variable.
It captures rulers, editing axes, the mixed-width rule, preset confirmation and
the reset menu under `capture-celestia-pickups/`. Pass its successful log as
`--pickups-log`, and the separate beauty log as `--beauty-log` to `edit.py`.

Record the continuous closing separately, with enough frames for its complete
voice-derived chapter, including the silent closing-title tail. This pass records 22 seconds:

```powershell
$env:WESO_VIDEO_FRAMES = '660'
.\tools\test-ui-layout.ps1 -Java21 'C:\path\to\jdk-21' -Java25 'C:\path\to\jdk-25' -Showcase -Beauty -CreditedLocation -Closing
python tools/video/edit.py --capture-log build/desktop-tests/WesoTest-CORE.log --beauty-log build/desktop-tests/WesoTest-BEAUTY.log --pickups-log build/desktop-tests/WesoTest-PICKUPS.log --closing-log build/desktop-tests/WesoTest-CLOSING.log --editing-log build/desktop-tests/WesoTest-EDITING.log --overview-log build/desktop-tests/WesoTest-OVERVIEW.log
```

Closing footage goes to `capture-celestia-closing/`, leaving all earlier sources
untouched. Continuous chapter offsets advance across cue boundaries. A short
source is rejected instead of looping or holding it. `--opening` does not need
the closing log. Full output fades only after the final voice/caption tail.

For the separate native interaction pass, use `$env:WESO_VIDEO_FRAMES = '120'`
and `-Showcase -CreditedLocation -EditingPickups`. This records corner adjustment,
Move grip, native camera-relative compass, X/Y scroll, release, apply and cancel
under `capture-celestia-editing/`. Add its successful `--editing-log` to the full
edit command. WorldEdit bounds and unchanged world blocks are asserted. The
full cut rejects unverified sources. `graphics.py` shares timing/layout with the
editable project; key hints are short editorial overlays, not changes to the mod.

For the separate coverage pass, use the same 120-frame setting and
`-Showcase -CreditedLocation -OverviewPickups`. Pass its successful log as
`--overview-log` to the full edit. Sources are kept separately under
`capture-celestia-overview/`: normal point commands, roof-material search,
visible command typing, states, flora, F8 and explicit WorldEdit execution/undo.
Only this disposable pass executes the wall replacement; the fixture verifies
that WorldEdit's normal undo restores every source-house block. The editor
marks these execution sources separately, never presenting them as a draft.

The edit produces an English AAC master with EN/RU subtitle streams, an RU-burned
review, chapter metadata, a source-hashed manifest and an actual contact sheet.
It does not publish or claim human watchthrough. Windows Segoe UI is used locally,
not redistributed. Blender is installed portably in ignored build storage and
verified against its official publisher checksum; no visible GUI is launched.

Run the portable Blender in background mode with `--threads 2 --python-exit-code 1 --python
tools/video/build-project.py` after the full edit, or append `-- --opening` for
the opening project. Three scenes preserve the verified conform, editable RU
caption strips, and a native-source assembly with independent title layers,
explicit last-frame holds and voice. All generated projects/media stay ignored.
The editor's sequencer cache is limited to 256 MB; the user's desktop stays free.
Both MP4 variants are decoded completely and checked against exact frame counts.
The Blender assembly checks script/edit-plan hashes and every native take hash;
re-recording or changing the graphics requires rebuilding the verified edit first.
See `docs/video/PUBLICATION.md` for the preview notice, mandatory map credit,
caption packet and remaining publication checks. `docs/video/THUMBNAIL.md`
records the rejected draft and the separate designed candidate.

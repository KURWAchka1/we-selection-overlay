# Four Shorts From the Full Review

## Editorial Direction

One true, useful promise per video. No invented feature, performance claim,
fake result, reaction face or "guaranteed viral" label. The provocative titles
are explained by the actual native footage, not an unrelated thumbnail.

- **Before Enter:** original house, live replacement draft, Escape restoration.
- **See Inside:** selected materials, hidden unselected materials, reset.
- **Move the Box:** choose Move, grip, camera-oriented axes, X/Y movement,
  apply and cancel. The house does not move.
- **Check the Copy:** placement preview, typed rotation/reflection, restoration.

The approved English narration and native picture are cut from the finished
review. No new game launch, voice generation or desktop input is needed.
Russian captions are burned in; both English and Russian SRT tracks are also
provided. Cuts retain full spoken phrases and their explanatory pauses.

## Research Applied

### Actual Minecraft Shorts

Observed on YouTube on 2026-10-11. Counts below are rounded counts displayed
by YouTube search, not estimates from a marketing article. The player was also
opened to inspect actual footage. Reference screenshots remain local under
`build/release/1.8.0/shorts/references/`; they are not reused in our videos.

| Creator / Short | Displayed views | Visual observations |
| --- | ---: | --- |
| [AchZach: Building Mods](https://www.youtube.com/shorts/AsaOAD63Gq0) | 25 million | Vertical gameplay fills the picture; a short outlined tool name replaces the opening title; the building operation remains central. A large Subscribe graphic also appears, but we reject that obstruction. |
| [HendyVideos: Cool Minecraft Mods Pt. 4](https://www.youtube.com/shorts/yedgkY23aJ8) | 45 million | Native Axiom interaction occupies the vertical frame; compact mod identification near the sky, no permanent title/footer layout. |
| [MCBlakeyy: Copy and Paste Your House](https://www.youtube.com/shorts/voJDvCPF_B8) | 667 thousand | Large building in a full-height frame; short phrase captions near the upper part of the image; clear building-tool subject. |
| [Snifferish: Three Great Building Mods](https://www.youtube.com/shorts/1hTaLKT3jx4) | 154 thousand | Close subject framing and brief bold, outlined caption fragments; warm native footage rather than a presentation template. |

The search shelf was inspected separately for real thumbnail composition.
Successful examples vary considerably; high views do not establish that any
one font, color or layout caused their success. We borrow the readable visual
hierarchy, not their footage, logos, music, exaggerated claims or giant CTA.

### Platform Guidance

- [YouTube: Shorts deep dive](https://blog.youtube/creator-and-artist-stories/youtube-shorts-deep-dive/):
  make the opening meaningful immediately and tell a small self-contained story.
- [YouTube: converting long-form video to Shorts](https://blog.youtube/creator-and-artist-stories/transitioning-your-long-form-content-to-youtube-shorts/):
  extract a coherent moment and reframe it for vertical viewing; captions help
  communicate without sound.
- [YouTube: misleading metadata policy](https://support.google.com/youtube/answer/2801973?hl=en):
  the title and description must represent the actual contents.
- [YouTube: related-video traffic](https://blog.youtube/creator-and-artist-stories/youtube-related-videos-traffic-guide/):
  each Short must stand alone; link the relevant full walkthrough as its related
  video rather than stretching the ending to fill time.

These are editorial guidelines, not a promise of recommendation placement.

## Picture and Sound

1080 x 1920, 30 fps, H.264 / AAC. Native gameplay fills the vertical frame,
without poster bands or a duplicated blurred background. A short opening hook
lasts 1.1 seconds. Bold white Russian caption phrases use a restrained dark
stroke for readability on the actual footage. Each phrase lasts at least
0.75 seconds; no word-by-word flashing or permanent marketing headline.
Text avoids the central building and the right-hand Shorts action rail.
The mod name appears briefly at the end, over the ongoing scene.
Command shots exclude the old debug/status corner and chat strip by cropping,
not by fabricating or repainting gameplay. Captions prefer one measured line;
longer phrases use at most two. The command captions occupy the sky above the
selection, with a separately spaced opening hook.

Relevant command moments additionally show an enlarged crop of the **actual
native chat input** from the same frame and clock as the scene. The full
`//replace smooth_sandstone cyan_terracotta` line is kept intact; shorter
`//rotate 90` and `//flip east` lines use a larger enlargement. Nearest-neighbor
scaling retains the pixel font. The native background, argument coloring and
caret remain part of the recording. No command is retyped, reconstructed,
substituted or composited from a different take. The crop fades in/out over
0.16 seconds, stays away from the subtitles and subject, and disappears after
its demonstration. It is not added to unrelated isolation or movement shots.

Each topic has explicit crop decisions. The block inspector focuses on native
checkboxes and material names. Isolation opens with a 1.1-second glimpse of
the actual isolated result, then shows the native checkbox interaction, under
the unchanged introductory narration. Movement retains the full native picture height;
the original long-form key hints are outside the crop. The live axis inset is
reframed from the existing native recording and stays bottom-right, matching the
narration. Clipboard shots focus on the copied preview. No aspect stretching,
simulated game action, accelerated voice or artificial gameplay motion.

Covers are separate assets: actual native result footage with two to four
large words, no fake arrow, reaction face, UI drawing or permanent banner.
They are not burned in throughout the videos. A separate JPEG does not mean
YouTube has accepted it as a Shorts thumbnail; selection must be verified in
the platform's supported interface.

Intermediate MOVs use PCM audio so cue joins are assembled before a single AAC
encode. Frame-aligned cuts, exact decoded frame counts, output dimensions,
audio loudness and measured text bounds are checked. Source SHA-256 is pinned.
Encoding uses two threads at below-normal process priority. Native footage is
illustrative sampled footage, not a responsiveness or FPS benchmark.

## Build and Deliverables

Run `tools/video/shorts.py` with the existing video-tooling Python environment.
`--only` can render one named Short. Plan, titles, alternative titles,
descriptions and suggested pinned comments are in `shorts-plan.json`.

Generated outputs are under `build/release/1.8.0/shorts/`:

- Four MP4s and four native-scene cover JPEGs with concise cover text.
- Retimed English/Russian SRT files.
- Upload text and machine-readable metadata.
- Native-output mobile proof sheets and technical manifests.

Videos are prepared for review, not automatically published. Publishing should
use the user's channel, the chosen title and the full review as related video.
Do not claim that a cover JPEG has been selected inside the Shorts player until
YouTube's actual UI confirms it.

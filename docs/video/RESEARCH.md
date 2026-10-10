# Video production knowledge base

Reviewed: 2026-10-10. Product baseline: commit e4b5376, development version 1.8.0.
This is a decision record, not a collection of copied articles. External guidance
does not establish mod compatibility or performance; repository tests do that.

## Direction

Make a builder's product demonstration, not a trailer of unrelated cinematic
Minecraft shots. Show a useful result immediately, then show the real action
that produces it. English narration, professionally readable Russian captions,
native Minecraft footage, one original demonstration build, no talking avatar.
Target roughly five minutes; actual duration follows the delivered narration.

The opening promise is **see the result before Enter**. The second promise is
**understand and adjust the selection without repeatedly placing two points**.
There is no guarantee of downloads, audience retention, or zero performance cost.

## Audience and structure

| Primary/professional source | Finding | Production decision |
| --- | --- | --- |
| [YouTube: key moments for audience retention](https://support.google.com/youtube/answer/9314415) | The opening should match the title/thumbnail; compelling later moments can be introduced earlier. | Start with a real replacement preview, not a logo animation. Judge the eventual retention curve, not invented conversion statistics. |
| [Frame.io: preparing video for social media](https://blog.frame.io/2017/10/09/prepping-social-media-video/) | Strong early imagery and continuous narration across picture cuts support concise edits. | Lead with the useful change. Let the voice continue across related detail shots. Main delivery remains horizontal. |
| [Frame.io: precision editing and split edits](https://blog.frame.io/2023/04/05/insider-tips-final-cut-pro-precision-editor/) | Picture and sound edits need not occur at the same instant. | Use restrained L-cuts between explanation and the resulting state. No transition sound on every click. |
| [Blackmagic Design: official Resolve training](https://www.blackmagicdesign.com/products/davinciresolve/training) | Editing, color, and Fairlight audio are separate finishing disciplines. | Picture lock, then sound/captions, then technical export. Keep editable stems and a shot manifest. |
| [YouTube: chapters](https://support.google.com/youtube/answer/9884579) | Chapters start at 00:00, need at least three entries and ten seconds per chapter. | Generate chapters from the actual final timeline, never placeholder timecodes. |

These sources inform our editorial judgment. They do not prescribe a magic
cut frequency. Hold interactions long enough to understand; do not speed up a
calculation and present the result as an instantaneous performance claim.

## Capture, color, and machine load

| Source | Decision |
| --- | --- |
| [OBS: standard recording output](https://obsproject.com/kb/standard-recording-output-guide) | Preserve recoverable source clips separately; final MP4 is a delivery artifact, not the only master. |
| [OBS: encoder presets](https://obsproject.com/kb/recording-encoder-presets-guide) | Do not select lossless recording indiscriminately: storage and CPU costs are real. Use a high-quality compressed intermediate. |
| [OBS: hardware encoding](https://obsproject.com/kb/hardware-encoding) | Prefer a tested hardware encoder when recording; software fallback has an explicit small thread budget. |
| [YouTube: upload encoding](https://support.google.com/youtube/answer/1722171) | Deliver progressive H.264 MP4, faststart, 4:2:0, BT.709, AAC at 48 kHz; preserve the actual source cadence. 1080p30 is the first delivery target. |
| [FFmpeg: input/demuxer documentation](https://ffmpeg.org/ffmpeg-formats.html) | Use explicit raw frame format, dimensions and cadence, and validate stream metadata. Never infer these from the file extension. |
| [FFmpeg: filtering documentation](https://ffmpeg.org/ffmpeg-filters.html) | Use deterministic trims, concatenation, subtitle rendering and measured loudness processing; avoid a stack of needless filters. |
| [Microsoft: Win32 desktops](https://learn.microsoft.com/en-us/windows/win32/winstation/desktops) | Capture only the disposable Minecraft instance on our existing private desktop. Do not switch the user's desktop or capture their personal screen. |
| [Fabric: automatic testing](https://docs.fabricmc.net/develop/automatic-testing) | Reuse the native client-game-test environment for repeatable scenes, not external global mouse/keyboard automation. |
| [Iris: official configuration implementation](https://github.com/IrisShaders/Iris/blob/1.21.11/common/src/main/java/net/irisshaders/iris/config/IrisConfig.java) | Use the real shader-pack configuration keys, confined to a separate filming instance. Verify the pinned 26.2 Iris/Sodium dependency pair. |
| [Complementary: official shader site](https://www.complementary.dev/shaders/) | Subtle beauty shots only; retain vanilla textures and actual mod geometry. The downloaded pack's own LOW profile supplies the settings, with blur disabled and restrained bloom. |

Repository launch guards take precedence over generic OBS advice. A private
desktop alone does not stop global cursor confinement: retain the cursor agent,
preflight and Job cleanup. No visible-window fallback is permitted.

Filming settings: 1920x1080, GUI scale 2, fixed field of view, daylight, clear
weather, no external resource pack, stable time, no camera bobbing,
no mobs, muted game music. The mod's shipped UI and models remain unmodified.
Core feature evidence has no shaders. The owner subsequently allowed a separate
Iris/Complementary beauty profile for establishing and closing shots. It must
not stand in for vanilla isolation/fullbright evidence. Record shader versions,
publisher hashes and the exact options; do not bundle those assets in the mod.
Keep FPS capped and avoid running synthesis, encoding and Minecraft together
when that would unnecessarily load the laptop. Offline camera samples are not
a benchmark or evidence that the game always maintains the delivery frame rate.

## Narration and intelligibility

| Source | Decision |
| --- | --- |
| [Adobe: improve dialogue clarity](https://helpx.adobe.com/uk/premiere/desktop/add-audio-effects/adjust-volume-and-levels/improve-dialogue-clarity.html) | Natural, clearly articulated voice; avoid excessive compression/EQ. Background audio must not compete with instructions. |
| [Adobe: loudness meter](https://helpx.adobe.com/premiere/desktop/add-audio-effects/apply-audio-effects/about-loudness-meter.html) | Measure integrated loudness and true peak, not only sample peaks. |
| [Adobe: measure track loudness](https://helpx.adobe.com/ca/premiere/desktop/add-audio-effects/apply-audio-effects/measure-the-loudness-of-your-track.html) | Check the entire assembled track, including section-to-section consistency. |
| [Microsoft: audio content creation](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/how-to-audio-content-creation) | Pronunciation, pace and prosody need review; generating speech is not the final quality check. Azure is an optional production alternative, not an assumed connected account. |
| [Kokoro: original model and license](https://huggingface.co/hexgrad/Kokoro-82M) | A local, licensed neutral synthetic narrator is preferable to imitating a real creator or depending on an unaudited web-TTS endpoint. Keep model/license provenance. |
| [Kokoro ONNX: implementation](https://github.com/thewh1teagle/kokoro-onnx) | Local CPU inference with a small thread budget is reproducible and keeps text off third-party voice APIs. |

Our delivery target is about -16 LUFS integrated, true peak at most -1 dBTP.
This is our editorial choice for clear narration, **not** a mandatory YouTube
loudness specification. No purchased service, voice cloning or copyrighted
music is implicitly authorized. First voice master is dry and music-free.
Pronounce WorldEdit as "World Edit", NeoForge as "Neo Forge"; speak command
names naturally instead of reading each slash and underscore.

## Captions and mobile readability

| Source | Decision |
| --- | --- |
| [Netflix: general timed-text requirements](https://partnerhelp.netflixstudios.com/hc/en-us/articles/215758617-Timed-Text-Style-Guide-General-Requirements) | At most two lines, sensible phrase breaks, no overlap with important onscreen text. Use roughly 1-7 second cues. |
| [Netflix: Russian timed text](https://partnerhelp.netflixstudios.com/hc/en-us/articles/215346638-Russian-Timed-Text-Style-Guide) | Use 42 characters per line and target at most 17 characters/second for translated Russian. These are useful borrowed constraints, not a Netflix delivery claim. |
| [W3C: captions](https://www.w3.org/WAI/media/av/captions/) | Include an English transcript/caption track as well as the Russian translation. Do not rely only on automatic captions. |
| [YouTube: supported caption files](https://support.google.com/youtube/answer/2734698) | Deliver plain UTF-8 SRT; an ASS review track can reposition captions away from Minecraft's bottom chat. |
| [YouTube: custom thumbnails](https://support.google.com/youtube/answer/72431) | Use a truthful 16:9 real demonstration image; verify current upload limits before publishing. The current help page recommends 3840x2160. |

Command demonstrations need upper-right captions in the review render; otherwise the
translation would cover chat, the very interaction being explained. YouTube's
plain SRT track cannot guarantee this placement, so review at normal and phone
size, and offer a Russian-captioned review render separately. Command strings
stay intact; names and numbers must never be translated into different values.
The upper-left preview status panel stays unobscured; translated captions use
a fixed right margin, rather than being centered on top of that panel.

## Rights and honest publication

| Source | Decision |
| --- | --- |
| [Minecraft: usage guidelines](https://www.minecraft.net/en-us/usage-guidelines) | Use our own original footage/build plus original commentary. Do not imply Mojang approval. |
| [Minecraft: content creation guidance](https://help.minecraft.net/hc/en-us/articles/4408934440589-Content-Creation-and-Broadcasting-Terms-of-Use-and-Guidelines) | Keep Minecraft-related promotional use within the game's current rules. |
| [YouTube: Audio Library](https://support.google.com/youtube/answer/3376882) | Only add music once a specific track's license/attribution has been retained; otherwise leave it out. "Royalty-free" in a random video title is insufficient. |
| [YouTube: generated-content disclosure](https://support.google.com/youtube/answer/14328491) | Describe the synthetic narrator transparently. Do not impersonate a person or label captured gameplay as generated gameplay. Check the current Studio disclosure wording at upload. |

## Product fact check

- Local `TESTING.md`, `COMMAND_PREVIEW.md`, `changelog-1.8.0.md` and the shipped
  settings are the authority for feature claims, not third-party blog posts.
- The published [CurseForge page](https://www.curseforge.com/minecraft/mc-mods/worldedit-selection-overlay)
  currently lists 1.7.0. It is not evidence that 1.8.0 is downloadable yet.
- No "all WorldEdit commands", "all 1.21 versions", "instant at any size", or
  "no FPS cost" claims. Region preview uses loaded selection chunks; large cold
  jobs still take work. Clipboard has separate scan/render safeguards.
- Commands/clipboard/editing need local integrated WorldEdit. Server CUI is not
  a clipboard protocol. NeoForge 26.1 local features require 26.1.2 or later.
- Isolation is visibility-only, not 70% transparency; do not advertise abandoned
  white-air-block/air-contour experiments.
- Enter executes WorldEdit normally; previews do not themselves change blocks.
  Bounds editing changes the selection, not the blocks contained in it.
- Random mixtures are illustrative and re-roll on execution. Unsupported
  command syntax is not silently presented as a valid result.

## Rejected directions

No generic generated Minecraft trailer instead of real mod footage. No borrowed
creator footage or unlicensed builds. A filming map needs explicit video permission,
a retained source revision and a visible author credit. No shader-dependent glow to exaggerate vanilla results.
No bombastic voice, long intro, fake download counters, invented endorsements,
rapid flashing isolation montage, or moving camera underneath an unreadable UI.
No huge link list without an explicit production consequence.

## Location revision after owner review

The owner rejected the sparse gate set. The generated atelier improved the
silhouette but its empty surroundings still read as a test scene. Investigate
[Celestia Town by Fireless007 / leolerenard](https://www.curseforge.com/minecraft/worlds/celestia-town):
the creator explicitly permits use in videos with a download-page link.
Use this specific permission, not an assumed open-source license: the overall
project license remains All Rights Reserved. Do not redistribute the map.

File 5307205 is pinned by a locally measured SHA-256, with a provenance manifest.
The native scout imports only overworld region files into a disposable test
save. Minecraft upgrades those chunks; author/player saves are never modified.
Choose the actual filming site from native captures before changing the feature
storyboard. No source trailer, screenshot or generated scene substitutes for
footage of our running mod.

## Editing Tools and Owner Review

- [Blender LTS](https://www.blender.org/releases/4-5/) and the
  [official download archive](https://download.blender.org/release/Blender4.5/)
  provide a portable, publisher-checksummed editor. The directly available package
  selected here is 4.5.9, not an unverified claim that it is the latest patch.
- [VSE Python strip API](https://docs.blender.org/api/4.5/bpy.types.StripsTopLevel.html)
  supplies real movie/sound/text strips and frame-aligned native project assembly.
  Version-specific alignment properties are verified against the installed RNA.
- The owner approved narrator voice/pace, but rejected the tiny floating title
  and too-fast replacement. Consequence: one readable short title animation,
  normal bottom-centered subtitles, closer hero framing and six seconds on the
  changed result before a distinct Escape restoration. No fake real-time claims.
- An editable project and automated checks are necessary, not sufficient, for
  professional output. Picture lock still needs actual visual/audio review.

## Native Background Readiness

[Fabric's documented render wait](https://maven.fabricmc.net/docs/fabric-api-0.155.2%2B26.2/net/fabricmc/fabric/api/client/gametest/v1/context/TestClientLevelContext.html)
distinguishes downloading chunks from rendering them. Its
[current connection implementation](https://github.com/FabricMC/fabric-api/blob/26.2/fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/impl/client/gametest/context/TestServerConnectionImpl.java)
checks a square for download readiness. Native Minecraft `ChunkTrackingView`
defines the actual rounded tracking area, verified against the bundled 26.2
class signatures. The filming fixture waits for that native area to reach FULL,
then uses Fabric's mesh/light render wait. No guessed sleep or custom distance
formula; the 12-chunk beauty pass verified 637 chunks and completed four takes.
This is a filming-fixture decision, not a change to either release JAR.

After the owner's layout feedback, the opening title is one line in open sky
above the complete selection contour. Remove the small edition label instead
of putting it over foliage. Preview-build status remains in the installation
chapter and publication description. The first AI-edited thumbnail is rejected:
preserve the loaded horizon and reserve the entire selection silhouette.

Editable title fades use the VSE strip's `blend_alpha`, matching Blender 4.5.9's
[official fade implementation](https://raw.githubusercontent.com/blender/blender/v4.5.9/scripts/startup/bl_operators/sequencer.py),
not animation of the Text effect's color alpha. A local native probe verified
opacity 0 / 0.5 / 1 across the fade and 0 at the final frame. Use
`--python-exit-code 1` for background project builds so a Python exception cannot
be mistaken for a successful Blender exit.
Read animated values from Blender's evaluated dependency graph, not the original
RNA defaults. Native VSE QA renders a one-frame animation range: a plain still
can retain original strip values. Actual beginning/middle fade frames are checked.
Blender caption size 30 is visually calibrated against the 40-unit ASS captions;
using the same numeric font size in both engines made the native project captions
too large and overlapped the inspector header. The MP4 review is the reference.

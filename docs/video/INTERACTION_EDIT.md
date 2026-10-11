# Interaction walkthrough and closing treatment

Owner review, 2026-10-11. This changes video production only, not the mod UI.

## Editorial decision

Give resizing and moving separate, readable actions. The move demonstration
must show: choose Move, tap G, grip with LMB, identify the native XYZ compass,
turn the camera, select X/Y, scroll, release, apply, and cancel a separate draft.
Keep the enlarged native compass through X/Y selection so its highlighted
letter can be matched to the actual movement, without a fade between those steps.
Do not describe a corner drag over footage of dragging a face.

The compass belongs to the real bottom-right HUD. Briefly magnify that actual
region alongside the house; never substitute a hand-drawn axes diagram. Keep
the selected house, complete bounds, status HUD and native compass unobscured.
Use vanilla footage so shader lighting is not confused with addon behavior.

Key hints are editorial overlays, not new controls in the mod. One compact
keycap/action at a time, at the actual demonstrated step, with a short fade.
Use only important actions: G hold/tap, LMB grip, X/Y + scroll, G apply, Esc cancel.
Do not put a permanent keyboard, shortcut legend or extra feature labels over
the footage. Editing captions move into the free top header.

After the last spoken words and translated caption, reveal a two-line closing
lockup in the clear upper sky:

WorldEdit Selection Overlay
Downloads in the description

Hold it for about four seconds on the existing continuous orbit, then fade
picture and lockup together. No repeated take, artificial clickable download
button, feature recap, or overlapping text on the cyan selection outline.

## Research

- [Brame: Effective Educational Videos](https://pmc.ncbi.nlm.nih.gov/articles/PMC5132380/):
  signaling, segmenting and removing extraneous information support a sequence
  of focused actions with relevant short cues. This is an editorial adaptation,
  not evidence that a specific timing is universally optimal.
- [Adobe: lower-third graphics](https://www.adobe.com/uk/creativecloud/video/discover/lower-third-graphics.html):
  provide context without drawing attention away from the main picture.
  Our lower-left hint must avoid the central status panel and right-hand compass.
- [YouTube: end screens](https://support.google.com/youtube/answer/6388789):
  native interactive end screens are separate publication elements. This edit
  uses a plain closing title and links in the description, not fake buttons.

## Checks

- Read shipped input/controller behavior before scripting the sequence.
- Record only through the private-desktop and cursor-isolation guarded launcher.
- Assert axis choice and changed draft coordinates; verify apply updates real
  WorldEdit bounds, cancellation restores them, and world blocks never change.
- Reject absent, stale or short motion sources. Preserve previous review exports.
- Rebuild voice, captions, chapters, both reviews and editable Blender projects.
- Inspect real frames with hints, enlarged compass, captions and closing lockup
  at full and phone size. Decode all output frames; keep publication pending.

# Project Page Art Direction: 1.8.0

Status: local proof, not published. Source privacy and the release license gate
in `RELEASE_1.8.0.md` are unchanged. This is presentation work, not a mod UI change.

## Intent

Show WorldEdit builders what they can check before committing an edit. The page
should feel like a carefully made building tool, not a collection of promotional
screenshots. Keep the existing project name and icon.

The new full YouTube review leads the description. Essential information stays
in editable text; image captions and alt text explain the same mechanism. The
gallery supplies genuine in-game evidence. Schematics are illustrations, not
screenshots or a promise of a new rendering mode.

## References and Decisions

Checked 2026-10-11. These principles inform our own composition; no artwork,
logos, page layouts or code were copied.

| Primary reference | Useful principle | What we do not borrow |
| --- | --- | --- |
| [CurseForge project-page guide](https://blog.curseforge.com/how-to-create-the-best-project-page/) | Explain concrete features clearly; keep relevant gameplay examples in the gallery. | A long procession of screenshots or decorative section headers. |
| [Carbon pictogram usage](https://v10.carbondesignsystem.com/guidelines/pictograms/usage/) | Consistent alignment, clearance, contrast and restrained use of expressive illustrations. | IBM pictograms, logos, proprietary brand elements or a container around every object. |
| [Carbon typography](https://www.carbondesignsystem.com/building-blocks/foundations/typography/overview) | Use size and weight to separate title, label and body; neutral text remains readable. | Its exact typeface, blue brand palette or product UI component styling. |
| [Axiom selections](https://axiomdocs.moulberry.com/editor/selections.html) | Distinguish moving bounds from moving blocks. Our description must make this equally clear. | Axiom's controls or claims that this addon offers the same editing system. |

This is a focused reference review, not evidence of higher conversion or a user
study. Typography and contrast checks validate legibility, not download rates.

## Chosen Style

- Neutral graphite `#202528`, off-white `#F2F5F5`, secondary text `#B9C4C7`.
- Cyan `#55D3ED` represents selection bounds and preview geometry.
- Mint `#A0E1C1` represents the chosen material in the isolation diagram.
- Cool stone faces establish the unchanged build. Depth comes from three planar
  face shades, not blur, glow, gradients or a busy Minecraft landscape.
- One type family, semibold headlines, regular labels. No hairline text,
  stretched tracking, tiny IDs, badges, fake controls or watermark slogans.
- A shared isometric projection and model connect the two illustrations.
  Large objects, clear before/after labels and equal margins carry the meaning.
- No framed cards within a banner. No ornamental voxel background. No more than
  two banners in the description; the video remains its primary demonstration.

## Page Order

1. New YouTube review at the top, with a plain watch link if embedding is unavailable.
2. One-sentence purpose and a release-version notice while 1.8.0 is not public.
3. Command preview: optional comparison diagram, short explanation and limits.
4. Material isolation: optional comparison diagram and collision clarification.
5. Selection editing, clipboard orientation, ruler and palette in short text.
6. Correct loader/version requirements, basic keys and showcase credit.

Do not add download buttons drawn into an image. Platform file controls remain
the real download path. Do not advertise unavailable 1.8.0 binaries as released.

## Asset and Placement Rules

Run `tools/video/style-banners.py` with the same Pillow runtime as the gallery
renderer. Generated media lives in ignored `build/release/1.8.0/banners/`.
Assets are original schematic geometry; no map, shaders, proprietary mod assets,
fonts or source archives are redistributed.

Both images are 1360 by 660 pixels. A single 82px headline scales to about 19px
at a 320px image width. All other necessary facts are also supplied in real text.
Desktop and 320px/360px mobile proof sheets show the actual raster downsampling.
Export PNG; lossless WebP is a local size comparison, not a required platform format.

Keep the top video responsive using the platform's supported YouTube embedding
feature. Do not paste arbitrary page CSS or assume an iframe works on both
platforms. Validate the real editor preview before saving a public description.
Upload approved banners before inserting their verified hosted URLs. Never
publish local paths, temporary placeholders or invented CDN links.

The live Modrinth editor was inspected without editing: it exposes an
"Insert YouTube video" dialog, and the saved old description includes an iframe.
The current CurseForge WYSIWYG description also displays the old YouTube iframe.
The new local draft therefore includes the replacement embed plus a watch link.
Its final platform preview still needs checking after insertion; no public
description was changed during these checks.

## Acceptance Checks

- Actual strings fit their allotted bounds; all comparison objects fit the canvas.
- The same material occupies the same coordinates before and after isolation.
- Unselected objects vanish in the isolation schematic; no adjacent outside
  blocks are shown as removed, and no air-visualization feature is implied.
- Equal comparison spacing and identical camera angle; no hidden object cropping.
- Main title and labels contrast at least 4.5:1 against the background.
- Mobile proofs retain title readability and the cyan boundary is discernible.
- Description stays useful with images disabled or an unsupported video embed.
- Binary release and private-source preferences remain unchanged by this work.

## Local Proof Result

Both full banners and the 720px/320px proof sheets were inspected. Text is
outside the illustrations, comparison labels fit, and the selected structural
material remains at the same coordinates after isolation. The two PNGs total
197,223 bytes; they add no game assets, runtime work or JAR size.

Measured text contrast is 14.12:1 for the main text and 8.69:1 for the secondary
text. Eleven new checks cover material preservation, text bounds, mobile proof
sizes and the video-first description. All 26 video-tooling tests pass.
This validates the local proofs only, not final hosted image URLs or a public
platform rendering. Nothing was uploaded to the public project pages.

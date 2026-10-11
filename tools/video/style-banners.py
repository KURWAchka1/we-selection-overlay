"""Render original, schematic project-page illustrations and mobile proofs."""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "build/release/1.8.0/banners"
SIZE = (1360, 660)
SCALE = 2
BACKGROUND = "#202528"
TEXT = "#F2F5F5"
SECONDARY = "#B9C4C7"
CYAN = "#55D3ED"
MINT = "#A0E1C1"
STONE = "#BEC7CA"
PREVIEW = "#49ACBF"
Point = tuple[int, int, int]


def rgb(color: str) -> tuple[int, int, int]:
    return tuple(int(color[index:index + 2], 16) for index in (1, 3, 5))


def shade(color: str, multiplier: float) -> tuple[int, int, int]:
    return tuple(min(255, round(channel * multiplier)) for channel in rgb(color))


def contrast(first: str, second: str) -> float:
    def luminance(color: str) -> float:
        linear = [value / 255 for value in rgb(color)]
        linear = [value / 12.92 if value <= 0.04045 else
                  ((value + 0.055) / 1.055) ** 2.4 for value in linear]
        return sum(value * weight for value, weight in
                   zip(linear, (0.2126, 0.7152, 0.0722)))

    light, dark = sorted((luminance(first), luminance(second)), reverse=True)
    return (light + 0.05) / (dark + 0.05)


def build_model() -> dict[Point, str]:
    blocks: dict[Point, str] = {}
    for x in range(6):
        for z in range(4):
            blocks[(x, 0, z)] = STONE
            if x in (0, 5) or z in (0, 3):
                for y in range(1, 4):
                    if (x, z) not in ((2, 3), (3, 3)):
                        blocks[(x, y, z)] = STONE
            blocks[(x, 4, z)] = STONE
    for x in (0, 5):
        for z in (0, 3):
            for y in range(1, 5):
                blocks[(x, y, z)] = MINT
    for x in range(1, 5):
        blocks[(x, 4, 3)] = MINT
    return blocks


def isolate(blocks: dict[Point, str], material: str) -> dict[Point, str]:
    return {point: color for point, color in blocks.items() if color == material}


@dataclass
class Illustration:
    image: Image.Image
    regular: Path
    semibold: Path

    def __post_init__(self) -> None:
        self.draw = ImageDraw.Draw(self.image)
        self.bounds: list[dict] = []

    def text(self, text: str, x: int, y: int, size: int, *, bold: bool = False,
             fill: str = TEXT, center: bool = False, max_width: int = 1232) -> None:
        font = ImageFont.truetype(str(self.semibold if bold else self.regular), size * SCALE)
        width = self.draw.textlength(text, font=font)
        if width > max_width * SCALE:
            raise ValueError(f"Text exceeds its allotted width: {text!r}")
        left = x * SCALE - width / 2 if center else x * SCALE
        bbox = self.draw.textbbox((left, y * SCALE), text, font=font, anchor="lt")
        if bbox[0] < 48 * SCALE or bbox[2] > (SIZE[0] - 48) * SCALE:
            raise ValueError(f"Text exceeds safe margins: {text!r}")
        if bbox[1] < 0 or bbox[3] > SIZE[1] * SCALE:
            raise ValueError(f"Text exceeds canvas height: {text!r}")
        self.draw.text((left, y * SCALE), text, font=font, fill=fill, anchor="lt")
        self.bounds.append({"text": text, "font_px": size,
                            "bounds": [round(value / SCALE, 2) for value in bbox]})

    def line(self, points: list[tuple[float, float]], color: str,
             width: int = 3) -> None:
        self.draw.line([(round(x * SCALE), round(y * SCALE)) for x, y in points],
                       fill=color, width=width * SCALE, joint="curve")

    def model(self, blocks: dict[Point, str], center: int, *, wire: bool = True) -> None:
        def project(point: tuple[float, float, float]) -> tuple[float, float]:
            x, y, z = point
            return (center + (x - z - 1) * 33,
                    376 + (x + z) * 14.5 - y * 32)

        def polygon(points: list[Point], color: tuple[int, int, int]) -> None:
            projected = [project(point) for point in points]
            if not all(200 <= y <= 550 and abs(x - center) <= 240
                       for x, y in projected):
                raise ValueError("Model exceeds its illustration area")
            self.draw.polygon([(round(x * SCALE), round(y * SCALE)) for x, y in projected],
                              fill=color)

        # Faces are emitted only when no neighboring voxel occludes them.
        for x, y, z in sorted(blocks, key=lambda p: (p[0] + p[2], p[1])):
            color = blocks[(x, y, z)]
            if (x + 1, y, z) not in blocks:
                polygon([(x + 1, y, z), (x + 1, y, z + 1),
                         (x + 1, y + 1, z + 1), (x + 1, y + 1, z)], shade(color, 0.72))
            if (x, y, z + 1) not in blocks:
                polygon([(x, y, z + 1), (x + 1, y, z + 1),
                         (x + 1, y + 1, z + 1), (x, y + 1, z + 1)], shade(color, 0.90))
            if (x, y + 1, z) not in blocks:
                polygon([(x, y + 1, z), (x + 1, y + 1, z),
                         (x + 1, y + 1, z + 1), (x, y + 1, z + 1)], shade(color, 1.08))

        if wire:
            corners = [(x, y, z) for x in (-0.25, 6.25)
                       for y in (-0.25, 5.25) for z in (-0.25, 4.25)]
            # Outline the front and top planes without drawing through solid faces.
            for a in corners:
                for b in corners:
                    if a >= b or sum(i != j for i, j in zip(a, b)) != 1:
                        continue
                    if ((a[1] == b[1] == 5.25) or
                        (a[0] == b[0] == 6.25) or (a[2] == b[2] == 4.25)):
                        self.line([project(a), project(b)], CYAN, 3)

    def arrow(self) -> None:
        self.line([(631, 397), (716, 397)], SECONDARY, 5)
        self.line([(698, 379), (716, 397), (698, 415)], SECONDARY, 5)


def render(kind: str, regular: Path, semibold: Path) -> tuple[Image.Image, list[dict]]:
    artwork = Illustration(Image.new("RGB", (SIZE[0] * SCALE, SIZE[1] * SCALE), BACKGROUND),
                           regular, semibold)
    model = build_model()
    if kind == "command":
        artwork.text("See it before Enter.", 64, 58, 82, bold=True)
        artwork.text("Live command preview", 64, 157, 46, fill=SECONDARY)
        original = {point: STONE for point in model}
        preview = {point: PREVIEW for point in model}
        labels = ("Original", "Preview")
        artwork.model(original, 360)
        artwork.model(preview, 1000)
    elif kind == "isolation":
        artwork.text("Look past the outer walls.", 64, 58, 82, bold=True)
        artwork.text("Choose the materials you want to see", 64, 157, 46, fill=SECONDARY)
        labels = ("All materials", "Selected material")
        artwork.model(model, 360)
        artwork.model(isolate(model, MINT), 1000)
    else:
        raise ValueError(f"Unknown illustration: {kind}")
    artwork.arrow()
    artwork.text(labels[0], 360, 562, 60, center=True, max_width=525)
    artwork.text(labels[1], 1000, 562, 60, center=True, max_width=525)
    return artwork.image.resize(SIZE, Image.Resampling.LANCZOS), artwork.bounds


def render_proof(images: list[Image.Image], width: int) -> Image.Image:
    margin, gap = 16, 16
    height = round(SIZE[1] * width / SIZE[0])
    proof = Image.new("RGB", (width + margin * 2, height * len(images) +
                            gap * (len(images) - 1) + margin * 2), "#F1F2F3")
    for index, artwork in enumerate(images):
        proof.paste(artwork.resize((width, height), Image.Resampling.LANCZOS),
                    (margin, margin + index * (height + gap)))
    return proof


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", type=Path, default=Path("C:/Windows/Fonts/segoeui.ttf"))
    parser.add_argument("--font-semibold", type=Path,
                        default=Path("C:/Windows/Fonts/seguisb.ttf"))
    args = parser.parse_args()
    if not args.font.is_file() or not args.font_semibold.is_file():
        parser.error("Supply installed regular and semibold fonts; no font binaries are bundled.")
    for color in (TEXT, SECONDARY):
        if contrast(color, BACKGROUND) < 4.5:
            raise ValueError("Insufficient text contrast")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    images, manifest = [], []
    for name, kind in (("01-command-preview", "command"), ("02-material-isolation", "isolation")):
        artwork, bounds = render(kind, args.font, args.font_semibold)
        images.append(artwork)
        png = OUTPUT / f"{name}.png"
        webp = OUTPUT / f"{name}.webp"
        artwork.save(png, optimize=True)
        artwork.save(webp, lossless=True, method=6)
        manifest.append({"file": png.name, "bytes": png.stat().st_size,
                         "sha256": hashlib.sha256(png.read_bytes()).hexdigest(),
                         "size": list(SIZE), "schematic_not_screenshot": True,
                         "text": bounds})
    for width in (320, 360, 720):
        render_proof(images, width).save(OUTPUT / f"proof-{width}.png", optimize=True)
    (OUTPUT / "manifest.json").write_text(json.dumps({
        "assets": manifest, "text_contrast": {color: round(contrast(color, BACKGROUND), 2)
                                               for color in (TEXT, SECONDARY)},
        "fonts_embedded": False,
    }, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"output": str(OUTPUT), "assets": len(manifest),
                      "total_png_bytes": sum(item["bytes"] for item in manifest)}))


if __name__ == "__main__":
    main()

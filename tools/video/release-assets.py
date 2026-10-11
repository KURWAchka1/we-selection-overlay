"""Create presentation cards from real uncaptioned Minecraft capture frames."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'build/video-tools/python'))
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

VIDEO = ROOT / 'build/video/1.8.0'
OUT = ROOT / 'build/release/1.8.0/gallery'
SIZE = (1600, 1100)
FONT = Path('C:/Windows/Fonts/seguisb.ttf')
REGULAR = Path('C:/Windows/Fonts/segoeui.ttf')
BG = '#172321'
ACCENT = '#A0E1C1'
CARDS = [
    {'id': '01-command-preview', 'title': 'See the result before Enter',
     'subtitle': 'Try a material change. Escape returns to the original.',
     'takes': ['capture-celestia/01-house-original.mp4',
               'capture-celestia/02-replace-preview.mp4'],
     'labels': ['ORIGINAL', 'COMMAND PREVIEW'], 'crop': [520, 120, 880, 940],
     'note': 'Live command preview requires singleplayer WorldEdit.'},
    {'id': '02-material-isolation', 'title': 'Look inside the build',
     'subtitle': 'Keep the materials you choose. Hide the rest visually.',
     'takes': ['capture-celestia/01-house-original.mp4',
               'capture-celestia/14-isolated-materials.mp4'],
     'labels': ['ALL MATERIALS', 'SELECTED MATERIALS'], 'crop': [520, 120, 880, 940],
     'note': 'World blocks and collisions remain unchanged.'},
    {'id': '03-selection-and-ruler', 'title': 'Know the shape. Read the dimensions.',
     'subtitle': 'Clear boundaries, an optional grid, and an in-world ruler.',
     'takes': ['capture-celestia/05-grid-ruler.mp4'],
     'note': 'Cuboid / Extend, Polygon, Ellipsoid, Cylinder and Convex.'},
    {'id': '04-move-selection', 'title': 'Move the selection, not the house',
     'subtitle': 'Choose X, Y or Z. Scroll to move. G applies; Escape cancels.',
     'takes': ['capture-celestia-editing/64-move-y.mp4'],
     'note': 'Optional cuboid editing requires singleplayer WorldEdit.'},
    {'id': '05-clipboard-preview', 'title': 'Check a copied build in place',
     'subtitle': 'Preview rotation and reflection while typing.',
     'takes': ['capture-celestia/35-clipboard-flip.mp4'],
     'note': 'Clipboard Preview starts off. Requires singleplayer WorldEdit.'},
    {'id': '06-palette-editor', 'title': 'Make it readable your way',
     'subtitle': 'Live 3D palette, colors, opacity, line thickness and undo / redo.',
     'takes': ['capture-celestia/40-palette-editor.mp4'],
     'note': 'Presets, per-element resets, and Glass / Studio / Classic menus.'},
]


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def fit_rect(size, box):
    x, y, w, h = box
    scale = min(w / size[0], h / size[1])
    nw, nh = round(size[0] * scale), round(size[1] * scale)
    return x + (w - nw) // 2, y + (h - nh) // 2, nw, nh


def bounded_text(draw, xy, value, size, color, max_width, bold=False):
    face = FONT if bold else REGULAR
    font = ImageFont.truetype(str(face), size)
    if draw.textlength(value, font=font) > max_width:
        raise ValueError(f'Presentation text exceeds its bounds: {value}')
    draw.text(xy, value, font=font, fill=color, anchor='lt')


def frame(take):
    source = VIDEO / take
    if not source.is_file():
        raise FileNotFoundError(source)
    still = OUT / 'native' / (source.stem + '.png')
    still.parent.mkdir(parents=True, exist_ok=True)
    command = [imageio_ffmpeg.get_ffmpeg_exe(), '-hide_banner', '-loglevel', 'error',
               '-y', '-threads', '2', '-sseof', '-0.1', '-i', str(source),
               '-frames:v', '1', str(still)]
    subprocess.run(command, check=True, capture_output=True,
                   creationflags=getattr(subprocess, 'BELOW_NORMAL_PRIORITY_CLASS', 0))
    return Image.open(still).convert('RGB'), {'take': take, 'sha256': digest(source),
                                            'frame': str(still.relative_to(ROOT))}


def build_card(spec):
    canvas = Image.new('RGB', SIZE, BG)
    draw = ImageDraw.Draw(canvas)
    bounded_text(draw, (40, 20), 'WORLDEDIT SELECTION OVERLAY / 1.8', 20, ACCENT, 1200, True)
    bounded_text(draw, (40, 54), spec['title'], 42, 'white', 1510, True)
    bounded_text(draw, (40, 108), spec['subtitle'], 24, '#CED9D5', 1510)
    sources = []
    for index, take in enumerate(spec['takes']):
        native, provenance = frame(take)
        sources.append(provenance)
        if len(spec['takes']) == 1:
            rect = (0, 140, 1600, 900)
        else:
            x, y, w, h = spec['crop']
            if x < 0 or y < 0 or x + w > native.width or y + h > native.height:
                raise ValueError('Native image crop escapes the captured frame')
            native = native.crop((x, y, x + w, y + h))
            rect = (index * 804, 186, 796, 854)
            bounded_text(draw, (40 + index * 804, 154), spec['labels'][index],
                         22, ACCENT, 715, True)
        px, py, pw, ph = fit_rect(native.size, rect)
        canvas.paste(native.resize((pw, ph), Image.Resampling.LANCZOS), (px, py))
    bounded_text(draw, (40, 1060), spec['note'], 21, '#CED9D5', 1510)
    path = OUT / (spec['id'] + '.jpg')
    canvas.save(path, quality=94, subsampling=0, optimize=True)
    return {**spec, 'file': str(path.relative_to(ROOT)), 'sha256': digest(path),
            'size': SIZE, 'sources': sources,
            'credit': 'Celestia Town by Fireless007 / leolerenard',
            'credit_url': 'https://www.curseforge.com/minecraft/worlds/celestia-town'}


def main():
    argparse.ArgumentParser(description=__doc__).parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    cards = [build_card(spec) for spec in CARDS]
    (OUT / 'manifest.json').write_text(json.dumps(cards, indent=2) + '\n', encoding='utf-8')
    sheet = Image.new('RGB', (1200, 1238), '#F0F4F2')
    for index, spec in enumerate(cards):
        image = Image.open(ROOT / spec['file'])
        image.thumbnail((588, 404), Image.Resampling.LANCZOS)
        sheet.paste(image, (8 + index % 2 * 600, 8 + index // 2 * 412))
    sheet.save(OUT / 'contact-sheet.jpg', quality=93)
    print(json.dumps({'cards': len(cards), 'directory': str(OUT)}, indent=2))


if __name__ == '__main__':
    main()

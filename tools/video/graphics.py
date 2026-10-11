"""Restrained editorial graphics shared by the review and editable assembly."""

import hashlib
import json
import math
from pathlib import Path

from prepare import ROOT, digest

SIZE = (1920, 1080)
COMPASS_CROP = (1780, 882, 112, 112)
COMPASS_RECT = (1536, 260, 288, 288)


def graphic_spec(plan, cue):
    hint = plan.get('key_hints', {}).get(cue['id'])
    closing = plan.get('closing_title', {})
    length = cue['slot_end'] - cue['start']
    if hint:
        start = hint['start']
        end = min(start + hint['duration'], length - .1)
        if start < 0 or end - start < 1.2:
            raise ValueError('Key hint has insufficient readable time')
        if not hint['keys'] or any(key not in ('G','LMB','X','Y','Scroll','Esc','B','N','F8','Enter') for key in hint['keys']):
            raise ValueError('Unreviewed key hint')
        return {'kind':'key', 'keys':hint['keys'], 'label':hint['label'],
                'start':start, 'end':end, 'fade_in':.18, 'fade_out':.18}
    if cue['id'] == closing.get('cue'):
        start = math.ceil((cue['end'] - cue['start'] + closing['after_caption']) * 30) / 30
        fade = plan['continuous_chapters'][cue['chapter']]['fade_out']
        if length - fade - start - closing['fade_in'] < 3:
            raise ValueError('Closing title needs at least three fully readable seconds after captions')
        return {'kind':'closing', 'lines':closing['lines'], 'start':start,
                'end':length, 'fade_in':closing['fade_in'], 'fade_out':fade}
    return None


def asset(spec, directory):
    from PIL import Image, ImageDraw, ImageFont

    font_path = Path('C:/Windows/Fonts/seguisb.ttf')
    key = hashlib.sha256(json.dumps([spec,digest(font_path),digest(Path(__file__))],sort_keys=True).encode()).hexdigest()[:16]
    path = directory / 'graphics' / f'{key}.png'
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.is_file():
        return path
    scale = 3
    canvas = Image.new('RGBA',(SIZE[0]*scale,SIZE[1]*scale))
    draw = ImageDraw.Draw(canvas)

    def font(size):
        return ImageFont.truetype(str(font_path),size*scale)

    def text(x,y,value,size,color,shadow=False,anchor='lt'):
        if shadow:
            draw.text(((x+1)*scale,(y+2)*scale),value,font=font(size),fill=(0,0,0,220),anchor=anchor,
                      stroke_width=scale,stroke_fill=(0,0,0,120))
        draw.text((x*scale,y*scale),value,font=font(size),fill=color,anchor=anchor)

    if spec['kind'] == 'key':
        x,y=96,808
        for keycap in spec['keys']:
            width=max(48,math.ceil(draw.textlength(keycap,font=font(28))/scale)+26)
            draw.rounded_rectangle((x*scale,y*scale,(x+width)*scale,(y+48)*scale),radius=8*scale,
                                   fill=(20,29,28,225),outline=(220,232,229,200),width=scale)
            text(x+width/2,y+24,keycap,28,(255,255,255,255),anchor='mm')
            x += width+12
        text(x+6,y+24,spec['label'],30,(255,255,255,255),shadow=True,anchor='lm')
        if x+6+draw.textlength(spec['label'],font=font(30))/scale > 640:
            raise ValueError('Key hint intrudes into the selected house')
    elif spec['kind'] == 'closing':
        if len(spec['lines']) != 2:
            raise ValueError('Closing lockup must have exactly two lines')
        for line,y,size in zip(spec['lines'],(74,148),(54,30)):
            text(960,y,line,size,(24,46,42,255),anchor='mt')
    else:
        raise ValueError('Unknown editorial graphic')
    canvas.resize(SIZE,Image.Resampling.LANCZOS).save(path)
    return path


def filter_graph(filters, spec=None, inset=False, duration=0, inset_fades=(True,True)):
    """Return one explicit output; overlays share the exact native segment clock."""
    graph = ['[0:v]'+','.join(filters)+'[base]']
    current='base'
    if inset:
        x,y,w,h=COMPASS_CROP
        px,py,pw,ph=COMPASS_RECT
        fades=[]
        if inset_fades[0]: fades.append('fade=t=in:st=0:d=0.2:alpha=1')
        if inset_fades[1]: fades.append(f'fade=t=out:st={duration-.2:.9f}:d=0.2:alpha=1')
        detail=[f'crop={w}:{h}:{x}:{y}',f'scale={pw}:{ph}:flags=lanczos','format=rgba',
                'drawbox=x=0:y=0:w=iw:h=ih:color=white@0.45:t=1',*fades]
        graph += ['[base]split[world][detail]',
                  '[detail]'+','.join(detail)+'[compass]',
                  f'[world][compass]overlay={px}:{py}:format=auto[focused]']
        current='focused'
    if spec:
        fades=[f"fade=t=in:st={spec['start']:.9f}:d={spec['fade_in']}:alpha=1"]
        if spec['kind'] != 'closing':
            fades.append(f"fade=t=out:st={spec['end']-spec['fade_out']:.9f}:d={spec['fade_out']}:alpha=1")
        graph += ['[1:v]format=rgba,'+','.join(fades)+'[graphic]',
                  f"[{current}][graphic]overlay=0:0:format=auto:enable='between(t,{spec['start']:.9f},{spec['end']:.9f})'[composed]"]
        current='composed'
    graph.append(f'[{current}]format=yuv420p[out]')
    return ';'.join(graph)

"""Create an editable Blender 4.5 VSE project from verified native edit decisions."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

import bpy

sys.path.insert(0,str(Path(__file__).resolve().parent))
from graphics import COMPASS_CROP, COMPASS_RECT

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/video/1.8.0"
FPS = 30


def frames(seconds):
    return round(seconds * FPS)


def native_path(path):
    return str(path.resolve())


def setup_scene(name, duration, chapters):
    scene = bpy.data.scenes.new(name)
    scene.render.resolution_x = 1920
    scene.render.resolution_y = 1080
    scene.render.resolution_percentage = 100
    scene.render.fps = FPS
    scene.render.fps_base = 1.0
    scene.render.threads_mode = 'FIXED'
    scene.render.threads = 2
    scene.render.use_sequencer = True
    scene.render.image_settings.file_format = 'PNG'
    scene.view_settings.view_transform = 'Standard'
    scene.view_settings.look = 'None'
    scene.frame_start = 1
    scene.frame_end = frames(duration)
    scene.sequence_editor_create()
    for chapter in chapters:
        scene.timeline_markers.new(chapter["title"], frame=frames(chapter["start"])+1)
    scene["map_credit"] = "Celestia Town by Fireless007 / leolerenard"
    scene["map_source"] = "https://www.curseforge.com/minecraft/worlds/celestia-town"
    scene["status"] = "Review; human watchthrough and publication pending"
    return scene


def fade_strip(scene,strip,start,end,attack,release):
    if not scene.animation_data:
        scene.animation_data_create()
    if not scene.animation_data.action:
        scene.animation_data.action=bpy.data.actions.new(scene.name+'Action')
    curve=scene.animation_data.action.fcurve_ensure_for_datablock(scene,strip.path_from_id('blend_alpha'))
    points=[(start,0),(start+attack,1)] if attack else [(start,1)]
    points += [(end-release,1),(end-1,0)] if release else [(end-1,1)]
    for frame,value in points:
        point=curve.keyframe_points.insert(frame,value,options={'FAST'})
        point.interpolation='LINEAR'
    curve.update()
    strip.invalidate_cache('COMPOSITE')


def text_strip(scene, font, name, text, start, end, channel, size, x, y, color, anchor='TOP', fade=False):
    strip = scene.sequence_editor.strips.new_effect(name,'TEXT',channel,start,frame_end=end)
    strip.text = text
    strip.font = font
    strip.font_size = size
    strip.location = (x/1920,1-y/1080)
    strip.anchor_x = 'LEFT' if x != 960 else 'CENTER'
    strip.anchor_y = anchor
    strip.alignment_x = 'LEFT' if x != 960 else 'CENTER'
    strip.color = color
    strip.use_shadow = False
    strip.use_outline = False
    strip.wrap_width = 1
    if fade:
        bpy.context.window.scene = scene
        fade_strip(scene,strip,start,end,10,8)
    return strip


def captions(scene, timeline, font, muted=False):
    for cue in timeline:
        start,end = frames(cue['start'])+1,frames(cue['end'])+1
        top = cue['position'] in ('top', 'header')
        strip = text_strip(scene,font,'RU '+cue['id'],cue['ru'],start,end,5,30,960,
                           {'top':140, 'header':12, 'bottom':1000}[cue['position']],
                           (1,1,1,1),'TOP' if top else 'BOTTOM')
        strip.use_outline = True
        strip.outline_color = (0,0,0,1)
        strip.outline_width = 2/30
        strip.use_shadow = True
        strip.shadow_color = (0,0,0,.8)
        strip.shadow_offset = 1/30
        strip.mute = muted


def voice(scene):
    scene.sequence_editor.strips.new_sound('English narration - approved voice',
        native_path(OUT/'narration-en.wav'),2,1)


def render_check(scene, frame, path):
    bpy.context.window.scene = scene
    first,last = scene.frame_start,scene.frame_end
    scene.frame_start = scene.frame_end = frame
    scene.render.filepath = native_path(path.with_suffix(''))+'-'
    try:
        # Animation rendering evaluates VSE keyframes; a plain still can use original values.
        bpy.ops.render.render(animation=True,scene=scene.name)
        path.with_name(path.stem+f'-{frame:04}.png').replace(path)
    finally:
        scene.frame_start,scene.frame_end = first,last
        scene.render.filepath = native_path(path)


def check_title_animation(scene):
    bpy.context.window.scene = scene
    for strip in scene.sequence_editor.strips:
        if not strip.name.startswith('Editable product title'):
            continue
        points = ((strip.frame_final_start,0), (strip.frame_final_start+10,1),
                  (strip.frame_final_end-1,0))
        for frame,expected in points:
            scene.frame_set(frame)
            evaluated = scene.evaluated_get(bpy.context.evaluated_depsgraph_get())
            alpha = next(s.blend_alpha for s in evaluated.sequence_editor.strips if s.name==strip.name)
            if abs(alpha-expected) > .001:
                raise ValueError('Editable title animation did not evaluate in the saved project')


def conform(scene, directory, decisions):
    cursor = 1
    for decision in decisions:
        strip = scene.sequence_editor.strips.new_movie(
            decision['cue']+' / '+decision['source'],native_path(directory/decision['segment']),1,cursor)
        if strip.frame_duration != decision['frames']:
            raise ValueError(f"Blender decoded a different segment duration: {decision['segment']}")
        strip.frame_final_duration = decision['frames']
        cursor += decision['frames']
    if cursor != scene.frame_end+1:
        raise ValueError('Blender picture length does not match the narration timeline')
    voice(scene)


def source_assembly(scene, manifest, decisions, plan, timeline, font):
    cursor = 1
    stills = OUT/'project-stills'
    stills.mkdir(exist_ok=True)
    ffmpeg = ROOT/'build/video-tools/python/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
    for decision in decisions:
        sources = manifest['beauty_sources'] if decision['profile'] == 'beauty' else manifest['native_sources']
        source = sources[decision['source']]
        path = ROOT/source['path']
        moving = min(source['frames']-decision['trim_frames'],decision['frames'])
        strip = scene.sequence_editor.strips.new_movie(
            decision['cue']+' / native '+decision['source'],native_path(path),1,cursor)
        strip.frame_offset_start = decision['trim_frames']
        strip.frame_final_start = cursor
        strip.frame_final_duration = moving
        if decision.get('compass_inset'):
            inset = scene.sequence_editor.strips.new_movie(
                'Editable native compass detail '+decision['cue'],native_path(path),3,cursor)
            inset.frame_offset_start = decision['trim_frames']
            inset.frame_final_start = cursor
            inset.frame_final_duration = decision['frames']
            x,y,w,h=COMPASS_CROP
            px,py,pw,ph=COMPASS_RECT
            inset.crop.min_x=x
            inset.crop.max_x=1920-x-w
            inset.crop.min_y=1080-y-h
            inset.crop.max_y=y
            inset.transform.scale_x=pw/w
            inset.transform.scale_y=ph/h
            # VSE cropping preserves the original full-frame coordinate origin.
            inset.transform.offset_x=px+pw/2-960-(x+w/2-960)*pw/w
            inset.transform.offset_y=540-py-ph/2-(540-y-h/2)*ph/h
            inset.blend_type='ALPHA_OVER'
            attack,release=decision.get('compass_fades',(True,True))
            fade_strip(scene,inset,cursor,cursor+decision['frames'],6 if attack else 0,6 if release else 0)
        graphic = decision.get('graphic')
        if graphic:
            path_graphic=ROOT/graphic['path']
            if hashlib.sha256(path_graphic.read_bytes()).hexdigest()!=graphic['sha256']:
                raise ValueError('Editorial graphic changed after the verified render')
            start=cursor+frames(graphic['start'])
            end=cursor+frames(graphic['end'])
            overlay=scene.sequence_editor.strips.new_image(
                'Editable '+graphic['kind']+' graphic '+decision['cue'],native_path(path_graphic),4,start)
            overlay.frame_final_duration=end-start
            overlay.blend_type='ALPHA_OVER'
            release=0 if graphic['kind']=='closing' else frames(graphic['fade_out'])
            fade_strip(scene,overlay,start,end,frames(graphic['fade_in']),release)
        if decision['last_frame_hold']:
            still = stills/(source['sha256'][:16]+'.png')
            if not still.is_file():
                subprocess.run([str(ffmpeg),'-v','error','-nostdin','-y','-threads','2','-i',str(path),
                                '-vf',f"select=eq(n\\,{source['frames']-1})",'-frames:v','1',str(still)],
                               check=True,capture_output=True,
                               creationflags=getattr(subprocess,'BELOW_NORMAL_PRIORITY_CLASS',0))
            freeze = scene.sequence_editor.strips.new_image(
                decision['cue']+' / last-frame hold',native_path(still),1,cursor+moving)
            freeze.frame_final_duration = decision['last_frame_hold']
        cursor += decision['frames']
    if decisions[-1].get('continuous'):
        spec = plan['continuous_chapters'][timeline[-1]['chapter']]
        duration = frames(spec['fade_out'])
        fade = scene.sequence_editor.strips.new_effect(
            'Editable closing fade','COLOR',6,cursor-duration,frame_end=cursor)
        fade.color = (0,0,0)
        fade.blend_type = 'ALPHA_OVER'
        if not scene.animation_data:
            scene.animation_data_create()
        if not scene.animation_data.action:
            scene.animation_data.action = bpy.data.actions.new(scene.name+'Action')
        curve = scene.animation_data.action.fcurve_ensure_for_datablock(
            scene,fade.path_from_id('blend_alpha'))
        for frame,value in ((cursor-duration,0),(cursor-1,1)):
            point = curve.keyframe_points.insert(frame,value,options={'FAST'})
            point.interpolation = 'LINEAR'
        curve.update()
        fade.invalidate_cache('COMPOSITE')
    for cue in timeline:
        text = plan['callouts'].get(cue['id'])
        if not text:
            continue
        start,end = frames(cue['start'])+1,frames(cue['slot_end'])+1
        if cue['id'] == 'hook-04':
            for i,line in enumerate(text.splitlines()):
                text_strip(scene,font,'Editable product title '+str(i),line,start,end,3+i,54,960,
                           74+i*72,(.094,.18,.165,1),fade=True)
        else:
            text_strip(scene,font,'Editable callout '+cue['id'],text,start,end,3,32,76,175,
                       (.094,.18,.165,1),fade=True)
    voice(scene)
    captions(scene,timeline,font,muted=True)
    scene['captions'] = 'Editable RU strips are muted on channel 5; enable them to review translations'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--opening',action='store_true')
    args = parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
    directory = OUT/('opening-edit' if args.opening else 'full-edit')
    manifest = json.loads((directory/'edit-manifest.json').read_text(encoding='utf-8'))
    timeline = json.loads((OUT/'timeline.json').read_text(encoding='utf-8'))['cues']
    if args.opening:
        timeline = [cue for cue in timeline if cue['chapter'] == 'hook']
    plan = json.loads((ROOT/'docs/video/edit-plan.json').read_text(encoding='utf-8'))
    script = json.loads((ROOT/'docs/video/script.json').read_text(encoding='utf-8'))
    titles = {item['id']:item['title'] for item in script['chapters']}
    chapters = []
    for cue in timeline:
        if not chapters or chapters[-1]['id'] != cue['chapter']:
            chapters.append({'id':cue['chapter'],'title':titles[cue['chapter']],'start':cue['start']})
    if manifest['timeline_sha256'] != hashlib.sha256((OUT/'timeline.json').read_bytes()).hexdigest():
        raise ValueError('Narration timeline has changed since the reviewed edit')
    for key, path in (('script_sha256', ROOT/'docs/video/script.json'),
                      ('edit_plan_sha256', ROOT/'docs/video/edit-plan.json')):
        if manifest.get(key) != hashlib.sha256(path.read_bytes()).hexdigest():
            raise ValueError(f'Edit inputs changed; rebuild the MP4 review before the project: {path.name}')
    for sources in (manifest['native_sources'], manifest.get('beauty_sources', {})):
        for source in sources.values():
            path = ROOT/source['path']
            if hashlib.sha256(path.read_bytes()).hexdigest() != source['sha256']:
                raise ValueError(f'Native take changed after the verified edit: {path.name}')
    bpy.context.preferences.system.memory_cache_limit = 256
    font = bpy.data.fonts.load(native_path(ROOT/'build/video-tools/fonts/seguisb.ttf'))
    english = setup_scene('01 English Conform',manifest['duration'],chapters)
    conform(english,directory,manifest['decisions'])
    russian = setup_scene('02 Russian Review',manifest['duration'],chapters)
    conform(russian,directory,manifest['decisions'])
    captions(russian,timeline,font)
    original = setup_scene('03 Editable Native Assembly',manifest['duration'],chapters)
    source_assembly(original,manifest,manifest['decisions'],plan,timeline,font)
    for scene in list(bpy.data.scenes):
        if scene not in (english,russian,original):
            bpy.data.scenes.remove(scene)
    bpy.context.window.scene = russian
    for area in bpy.context.screen.areas:
        if area.type == 'VIEW_3D':
            area.type = 'SEQUENCE_EDITOR'
            area.spaces.active.view_type = 'SEQUENCER_PREVIEW'
    output = OUT/('Opening-Review.blend' if args.opening else 'WorldEdit-Selection-Overlay-1.8.blend')
    bpy.ops.wm.save_as_mainfile(filepath=str(output),relative_remap=True)
    # Render one frame in the actual VSE to verify the project is not just a flat video wrapper.
    check_title_animation(original)
    render_check(russian,min(frames(17)+1,russian.frame_end),directory/'blender-review-check.png')
    render_check(original,min(frames(17)+1,original.frame_end),directory/'blender-editable-check.png')
    title = next(s for s in original.sequence_editor.strips if s.name.startswith('Editable product title'))
    render_check(original,title.frame_final_start,directory/'blender-title-fade-start.png')
    if not args.opening:
        inspector=next(c for c in timeline if c['id']=='inspect-01')
        render_check(russian,frames(inspector['start']+2)+1,directory/'blender-inspector-caption-check.png')
        for identifier,label in (('edit-07','key-hint'),('edit-09','compass'),('install-04','closing-title')):
            cue=next(c for c in timeline if c['id']==identifier)
            moment=cue['slot_end']-2 if identifier=='install-04' else cue['start']+2
            render_check(original,frames(moment)+1,directory/f'blender-{label}-check.png')
        render_check(original,original.frame_end,directory/'blender-closing-fade-check.png')
        if 'overview_capture_log_sha256' in manifest:
            for identifier,label in (('commands-02','typing'),('commands-11','flora'),
                                     ('commands-14','executed'),('clipboard-04','reflection')):
                cue=next(c for c in timeline if c['id']==identifier)
                render_check(original,frames(cue['slot_end']-0.6)+1,directory/f'blender-{label}-check.png')
    bpy.context.window.scene = russian
    bpy.ops.file.make_paths_relative()
    bpy.ops.wm.save_as_mainfile(filepath=str(output),relative_remap=True)
    print(f'BLENDER PROJECT READY: {output}; three editable scenes, native sources, separate voice and RU captions')


if __name__ == '__main__':
    main()

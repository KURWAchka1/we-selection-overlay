"""Bounded pipeline checks; no Minecraft, network, voice model, or desktop interaction."""

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from prepare import ROOT, spoken, timestamp, wrap_caption
from edit import continuous_trim, decoded_frames, frame_count, imageio_ffmpeg, segment_filters, split_frames, weighted_frames


class PipelineChecks(unittest.TestCase):
    def test_closing_is_one_continuous_shot(self):
        plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
        source = plan['continuous_chapters']['install']['source']
        self.assertEqual(source, '53-closing-orbit')
        for cue, shots in plan['cues'].items():
            if cue.startswith('install-'):
                self.assertEqual(shots, [source])
        offset = 0
        for length in (100,170,140,130):
            self.assertEqual(continuous_trim(offset / 30, length, 900), offset)
            offset += length
        for seconds, length in ((29,31),(-1,30),(.01,30)):
            with self.assertRaises(ValueError): continuous_trim(seconds,length,900)

    def test_narration_has_no_development_or_absent_feature_checklist(self):
        script = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
        text = ' '.join(pair[0] for chapter in script['chapters'] for pair in chapter['cues']).lower()
        for phrase in ('cut and paste', 'no live command preview', 'cold previews', 'not every',
                       '26.1.2', 'fixed a bug', 'walking never drags', 'stairs and partial'):
            self.assertNotIn(phrase, text)
        self.assertIn('random positions can change', text)
        self.assertIn('local worldedit', text)

    def test_padding_produces_the_requested_decoded_frames(self):
        with tempfile.TemporaryDirectory() as folder:
            movie = Path(folder) / "timing.mp4"
            result = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-nostdin",
                                     "-f", "lavfi", "-i", "color=c=black:s=320x180:r=30:d=1",
                                     "-vf", ",".join(segment_filters(0,15)), "-frames:v", "45",
                                     "-r", "30", "-an", "-c:v", "libx264", "-threads", "2", str(movie)],
                                    capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(decoded_frames(movie,45),45)
            with self.assertRaises(ValueError): decoded_frames(movie,46)

    def test_edit_plan_covers_the_whole_mod(self):
        source = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
        plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
        ids = {f"{chapter['id']}-{i+1:02}" for chapter in source["chapters"]
               for i in range(len(chapter["cues"]))}
        self.assertEqual(set(plan["cues"]),ids)
        self.assertTrue(set(plan["callouts"]).issubset(ids))
        self.assertTrue(all(plan["cues"].values()))
        self.assertTrue(set(plan.get('beauty_cues', {})).issubset(ids))
        self.assertEqual(plan['beauty_cues']['hook-04'], ['04-hero-orbit'])

    def test_gameplay_has_no_duplicate_feature_callouts(self):
        plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
        self.assertEqual(plan["callouts"], {"hook-04": "WorldEdit Selection Overlay"})

    def test_interaction_story_and_graphics(self):
        from graphics import graphic_spec, filter_graph
        script=json.loads((ROOT/'docs/video/script.json').read_text(encoding='utf-8'))
        chapter=next(c for c in script['chapters'] if c['id']=='edit')
        text=' '.join(pair[0] for pair in chapter['cues']).lower()
        for phrase in ('bottom-right corner','turn with the camera','press x','choose y','still a draft'):
            self.assertIn(phrase,text)
        self.assertEqual(chapter['position'],'header')
        plan=json.loads((ROOT/'docs/video/edit-plan.json').read_text(encoding='utf-8'))
        self.assertEqual(plan['cues']['edit-02'],['54-quick-corner'])
        self.assertEqual(plan['compass_insets'],['edit-08','edit-09','edit-10','edit-11'])
        self.assertEqual(len([cue for cue in plan['key_hints'] if cue.startswith('edit-')]),9)
        self.assertTrue(set(plan['key_hints']).issubset(plan['cues']))
        for cue in json.loads((ROOT/'build/video/1.8.0/timeline.json').read_text(encoding='utf-8'))['cues']:
            spec=graphic_spec(plan,cue)
            if spec:
                self.assertLess(spec['start'],spec['end'])
                self.assertLessEqual(spec['end'],cue['slot_end']-cue['start']+.00001)
                if spec['kind']=='closing':
                    self.assertGreater(cue['start']+spec['start'],cue['end'])
        with self.assertRaises(ValueError):
            graphic_spec(plan,{'id':'install-04','chapter':'install','start':0,'end':3,'slot_end':4})
        graph=filter_graph(segment_filters(0,0),inset=True,duration=4)
        self.assertIn('crop=112:112:1780:882',graph)
        self.assertIn('[out]',graph)
        graph=filter_graph(segment_filters(0,0),inset=True,duration=4,inset_fades=(False,False))
        self.assertNotIn('fade=',graph)

    def test_native_compass_composition_decodes(self):
        from graphics import filter_graph
        with tempfile.TemporaryDirectory() as folder:
            movie=Path(folder)/'compass.mp4'
            command=[imageio_ffmpeg.get_ffmpeg_exe(),'-v','error','-nostdin','-y','-threads','2',
                     '-f','lavfi','-i','color=c=black:s=1920x1080:r=30:d=1',
                     '-filter_complex_threads','1','-filter_complex',
                     filter_graph(segment_filters(0,0),inset=True,duration=1),
                     '-map','[out]','-frames:v','30','-an','-c:v','libx264','-threads','2',str(movie)]
            result=subprocess.run(command,capture_output=True)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertEqual(decoded_frames(movie,30),30)

    def test_full_overview_has_readable_independent_evidence(self):
        script=json.loads((ROOT/'docs/video/script.json').read_text(encoding='utf-8'))
        plan=json.loads((ROOT/'docs/video/edit-plan.json').read_text(encoding='utf-8'))
        for index,shape in enumerate(('polygon','ellipsoid','cylinder','convex'),6):
            self.assertEqual(plan['cues'][f'selection-{index:02}'],[f'08-shape-{shape}'])
            self.assertGreaterEqual(script['minimum_slots'][f'selection-{index:02}'],3.5)
        for cue,shot in (('selection-01','68-selection-first-point'),('selection-02','69-selection-complete'),
                         ('commands-02','71-command-typing'),('commands-03','72-state-preview'),
                         ('commands-11','73-flora-preview'),('commands-13','75-command-execute'),
                         ('commands-14','76-command-applied'),('commands-15','77-command-undone')):
            self.assertEqual(plan['cues'][cue],[shot])
            self.assertIn(shot,plan['overview_sources'])
        self.assertEqual(plan['cues']['clipboard-03'],['34-clipboard-rotate'])
        self.assertEqual(plan['cues']['clipboard-04'],['35-clipboard-flip'])
        self.assertEqual(plan['cues']['palette-05'],['44-presets'])
        self.assertEqual(plan['cues']['palette-06'],['50-preset-confirm','51-preset-applied'])
        commands=next(c for c in script['chapters'] if c['id']=='commands')
        self.assertIn("WorldEdit's undo",commands['cues'][-1][0])

    def test_graphic_bounds_leave_house_and_native_hud_clear(self):
        from graphics import graphic_spec, asset
        from PIL import Image
        plan=json.loads((ROOT/'docs/video/edit-plan.json').read_text(encoding='utf-8'))
        timeline=json.loads((ROOT/'build/video/1.8.0/timeline.json').read_text(encoding='utf-8'))
        with tempfile.TemporaryDirectory() as folder:
            for cue in timeline['cues']:
                spec=graphic_spec(plan,cue)
                if not spec:
                    continue
                with Image.open(asset(spec,Path(folder))) as image:
                    self.assertEqual(image.size,(1920,1080))
                    left,top,right,bottom=image.getbbox()
                    if spec['kind']=='key':
                        self.assertGreaterEqual(left,90)
                        self.assertLessEqual(right,640)
                        self.assertGreaterEqual(top,800)
                        self.assertLessEqual(bottom,870)
                    else:
                        self.assertLess(bottom,200)

    def test_frame_allocation_preserves_exact_length(self):
        for count in (118,142,141,1,300):
            for parts in range(1,min(count,5)+1):
                slots = split_frames(count,parts)
                self.assertEqual(sum(slots),count)
                self.assertLessEqual(max(slots)-min(slots),1)
        self.assertEqual(frame_count(118/30),118)
        self.assertEqual(weighted_frames(180,[1,5]),[30,150])
        with self.assertRaises(ValueError): frame_count(1.01)
        with self.assertRaises(ValueError): split_frames(2,3)
        with self.assertRaises(ValueError): weighted_frames(10,[1,0])

    def test_source_captions(self):
        script = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
        chapters = [chapter["id"] for chapter in script["chapters"]]
        self.assertEqual(len(chapters), len(set(chapters)))
        self.assertEqual(set(chapters), {"hook", "selection", "inspect", "commands",
                                        "edit", "clipboard", "palette", "install"})
        self.assertEqual(next(c for c in script['chapters'] if c['id'] == 'inspect')['position'], 'header')
        for chapter in script["chapters"]:
            for pair in chapter["cues"]:
                for text in pair:
                    lines = wrap_caption(text).splitlines()
                    self.assertLessEqual(len(lines), 2)
                    self.assertLessEqual(max(map(len, lines)), 42)

    def test_timestamp_carry(self):
        self.assertEqual(timestamp(59.9999), "00:01:00,000")
        self.assertEqual(timestamp(3599.9999), "01:00:00,000")
        self.assertEqual(timestamp(59.9999, ass=True), "0:01:00.00")

    def test_caption_fail_closed(self):
        with self.assertRaises(ValueError):
            wrap_caption("x" * 100)

    def test_pronunciation(self):
        text = spoken("WorldEdit on NeoForge 26.1.2 and Minecraft 1.21.11")
        self.assertIn("twenty six point one point two", text)
        self.assertIn("one point twenty one point eleven", text)
        self.assertNotIn("26.1", text)
        self.assertEqual(spoken('F8 and CUI'), 'F eight and see you eye')

    def test_generated_timeline_if_present(self):
        path = ROOT / "build/video/1.8.0/timeline.json"
        if not path.is_file():
            self.skipTest("Voice-derived timeline has not been generated")
        timeline = json.loads(path.read_text(encoding="utf-8"))
        last = 0.0
        for cue in timeline["cues"]:
            self.assertAlmostEqual(cue["start"], last, places=6)
            self.assertLess(cue["start"], cue["voice_end"])
            self.assertLessEqual(cue["voice_end"], cue["end"])
            self.assertLess(cue["end"], cue["slot_end"])
            self.assertLessEqual(cue["russian_cps"], 17.00001)
            self.assertLessEqual(cue["end"] - cue["start"], 7)
            last = cue["slot_end"]
        self.assertAlmostEqual(last, timeline["duration"], places=6)
        script = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
        chapters = []
        for chapter in script['chapters']:
            cue = next(c for c in timeline['cues'] if c['chapter'] == chapter['id'])
            seconds = round(cue['start'] * timeline['fps']) // timeline['fps']
            chapters.append(f"{seconds // 60:02}:{seconds % 60:02} {chapter['title']}")
        self.assertEqual((path.parent / 'chapters.txt').read_text(encoding='utf-8').splitlines(), chapters)
        plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
        fade = plan['continuous_chapters']['install']['fade_out']
        self.assertGreaterEqual(last - fade, timeline['cues'][-1]['end'])


if __name__ == "__main__":
    unittest.main()

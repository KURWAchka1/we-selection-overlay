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
        plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
        fade = plan['continuous_chapters']['install']['fade_out']
        self.assertGreaterEqual(last - fade, timeline['cues'][-1]['end'])


if __name__ == "__main__":
    unittest.main()

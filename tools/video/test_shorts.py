import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).with_name("shorts.py")
SPEC = importlib.util.spec_from_file_location("shorts", MODULE)
shorts = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(shorts)


class ShortsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = json.loads(shorts.PLAN.read_text(encoding="utf-8"))
        cls.timeline = json.loads((shorts.ROOT/cls.plan["timeline"]).read_text(encoding="utf-8"))

    def test_four_independent_topics(self):
        self.assertEqual(len(self.plan["shorts"]), 4)
        self.assertEqual(len({spec["id"] for spec in self.plan["shorts"]}), 4)

    def test_real_review_hash_is_pinned(self):
        self.assertEqual(len(self.plan["master_sha256"]), 64)
        self.assertEqual(shorts.sha256(shorts.ROOT/self.plan["master"]), self.plan["master_sha256"])

    def test_frame_exact_cuts_keep_complete_voice_cues(self):
        for spec in self.plan["shorts"]:
            cuts = shorts.build_cuts(spec, self.timeline)
            cursor = 0
            for cut in cuts:
                self.assertEqual(cut["output_start_frame"], cursor)
                self.assertGreater(cut["frames"], 0)
                self.assertLessEqual(cut["caption_end"], cut["frames"]/shorts.FPS)
                cursor += cut["frames"]
            self.assertLess(cursor/shorts.FPS, 60)
            self.assertGreater(cursor/shorts.FPS, 10)

    def test_no_cue_repeats_or_fake_loop(self):
        for spec in self.plan["shorts"]:
            cuts = shorts.build_cuts(spec, self.timeline)
            self.assertEqual(len(spec["cues"]), len(set(spec["cues"])))
            for previous, current in zip(cuts, cuts[1:]):
                self.assertGreaterEqual(current["source_start_frame"],
                                        previous["source_start_frame"]+previous["frames"])

    def test_all_crops_are_inside_the_native_picture(self):
        for x, y, width, height in shorts.VIEWS.values():
            self.assertGreaterEqual(x, 0)
            self.assertGreaterEqual(y, 0)
            self.assertLessEqual(x+width, 1920)
            self.assertLessEqual(y+height, 1080)
            self.assertTrue(all(value % 2 == 0 for value in (x, y, width, height)))

    def test_movement_excludes_the_old_editorial_graphics(self):
        x, y, width, height = shorts.VIEWS["move"]
        self.assertGreaterEqual(x, 640)
        self.assertLessEqual(x+width, 1536)
        self.assertEqual((y, height), (0, 1080))

    def test_axes_are_actual_native_footage_in_the_bottom_right(self):
        spec = next(spec for spec in self.plan["shorts"] if spec["id"] == "03-move-the-box")
        cuts = shorts.build_cuts(spec, self.timeline)
        self.assertEqual([cut["cue"] for cut in cuts if cut["compass"]], spec["compass_cues"])
        graph = shorts.filter_graph(next(cut for cut in cuts if cut["compass"]))
        self.assertIn("[0:v]split=2[scene][detail]", graph)
        self.assertIn("crop=288:288:1536:260", graph)
        x, y, width, height = shorts.COMPASS_TARGET
        self.assertGreaterEqual(x, 700)
        self.assertGreater(y, 1200)
        self.assertLessEqual(x+width, shorts.SIZE[0])
        self.assertLessEqual(y+height, 1500)

    def test_no_aspect_distortion_or_speed_changes(self):
        for spec in self.plan["shorts"]:
            for cut in shorts.build_cuts(spec, self.timeline):
                graph = shorts.filter_graph(cut)
                self.assertIn("force_original_aspect_ratio=increase", graph)
                self.assertIn("crop=1080:1920", graph)
                self.assertNotIn("pad=", graph)
                self.assertNotIn("zoompan", graph)
                self.assertNotIn("setpts", graph)
                self.assertNotIn("atempo", graph)

    def test_measured_titles_captions_and_hints_fit(self):
        with tempfile.TemporaryDirectory() as temporary:
            for spec in self.plan["shorts"]:
                for cut in shorts.build_cuts(spec, self.timeline):
                    for event in shorts.overlay_events(spec, cut):
                        bounds = shorts.graphic(spec, event["text"], Path(temporary)/"graphic.png",
                                                y=event["y"], size=event["size"], color=event["color"])
                        for text in bounds:
                            left, top, right, bottom = text["box"]
                            self.assertGreaterEqual(left, 52)
                            self.assertLessEqual(right, 936)
                            self.assertGreaterEqual(top, 80)
                            self.assertLessEqual(bottom, 1640)

    def test_graphics_leave_the_picture_uncovered(self):
        from PIL import Image
        spec = self.plan["shorts"][0]
        cut = shorts.build_cuts(spec, self.timeline)[0]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)/"graphic.png"
            shorts.graphic(spec, spec["hook"], path, y=140, size=84)
            with Image.open(path) as art:
                self.assertEqual(art.getpixel((540, 800))[3], 0)
                self.assertEqual(art.getpixel((540, 1400))[3], 0)
                self.assertEqual(art.getpixel((0, 0))[3], 0)

    def test_hooks_are_transient_and_captions_do_not_stack(self):
        for spec in self.plan["shorts"]:
            for cut in shorts.build_cuts(spec, self.timeline):
                events = shorts.overlay_events(spec, cut)
                if cut["first"]:
                    self.assertEqual(events[0]["end"], 1.1)
                captions = [event for event in events if event["text"] in spec["captions"][cut["cue"]]]
                self.assertEqual(len(captions), len(spec["captions"][cut["cue"]]))
                for previous, current in zip(captions, captions[1:]):
                    self.assertLessEqual(previous["end"], current["start"])
                for event in captions:
                    self.assertGreaterEqual(event["end"]-event["start"], .65)
                    self.assertLessEqual(event["end"], cut["frames"]/30)

    def test_covers_are_separate_native_scene_assets(self):
        with tempfile.TemporaryDirectory() as temporary:
            for spec in self.plan["shorts"]:
                self.assertLessEqual(len(spec["cover_text"].split()), 4)
                self.assertIn(spec["cover_view"], shorts.VIEWS)
                shorts.graphic(spec, spec["cover_text"], Path(temporary)/"cover.png", y=140, size=92)

    def test_command_text_stays_above_the_selection(self):
        spec = self.plan["shorts"][0]
        with tempfile.TemporaryDirectory() as temporary:
            for cut in shorts.build_cuts(spec, self.timeline):
                for event in shorts.overlay_events(spec, cut):
                    if event["y"] > 1000:
                        continue
                    bounds = shorts.graphic(spec, event["text"], Path(temporary)/"caption.png",
                                            y=event["y"], size=event["size"])
                    self.assertLess(max(item["box"][3] for item in bounds), 250)

    def test_chat_insets_are_synchronized_native_pixel_crops(self):
        found = []
        for spec in self.plan["shorts"]:
            for cut in shorts.build_cuts(spec, self.timeline):
                inset = cut["chat_inset"]
                if inset is None:
                    continue
                found.append(inset["observed_command"])
                graph = shorts.filter_graph(cut, shorts.overlay_events(spec, cut))
                self.assertIn("[0:v]split=2[scene][chat]", graph)
                self.assertIn("[chat]crop=", graph)
                self.assertIn("flags=neighbor", graph)
                self.assertIn("fade=t=in", graph)
                self.assertIn("fade=t=out", graph)
                self.assertNotIn("drawtext", graph)
                self.assertNotIn(inset["observed_command"], graph)
                self.assertEqual(inset["crop"][1]+inset["crop"][3], 1080)
        self.assertEqual(found, ["//replace smooth_sandstone cyan_terracotta", "//rotate 90", "//flip east"])

    def test_chat_insets_do_not_cover_captions_or_the_subject(self):
        with tempfile.TemporaryDirectory() as temporary:
            for spec in self.plan["shorts"]:
                for cut in shorts.build_cuts(spec, self.timeline):
                    inset = cut["chat_inset"]
                    if inset is None:
                        continue
                    px, py = inset["position"]
                    width, height = shorts.chat_size(inset)
                    self.assertLessEqual(py+height, 250 if cut["view"] == "command" else 420)
                    for event in shorts.overlay_events(spec, cut):
                        if event["end"] <= inset["start"] or event["start"] >= inset["end"]:
                            continue
                        bounds = shorts.graphic(spec, event["text"], Path(temporary)/"art.png",
                                                y=event["y"], size=event["size"])
                        for item in bounds:
                            left, top, right, bottom = item["box"]
                            self.assertTrue(right <= px or left >= px+width or
                                            bottom+12 <= py or top >= py+height+12)

    def test_invalid_chat_crop_or_clock_is_rejected(self):
        inset = self.plan["shorts"][0]["chat_insets"]["hook-02"]
        shorts.validate_chat_inset(inset, 6)
        for changes in ({"crop": [4, 1060, 492, 30]}, {"position": [600, 184]},
                        {"crop": [4, 1050, 0, 30]}, {"crop": [4, 1050, 492, 0]},
                        {"end": 7}, {"fade": 3}, {"width": 0}):
            with self.assertRaises(ValueError):
                shorts.validate_chat_inset(dict(inset, **changes), 6)

    def test_subtitles_retime_after_the_isolation_jump(self):
        spec = self.plan["shorts"][1]
        cuts = shorts.build_cuts(spec, self.timeline)
        self.assertEqual(cuts[-1]["cue"], "inspect-11")
        self.assertAlmostEqual(cuts[-1]["output_start_frame"]/30, 16.4333333333)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)/"subtitles.srt"
            shorts.write_subtitles(cuts, "ru", path)
            text = path.read_text(encoding="utf-8")
            self.assertIn("00:00:16,433", text)
            self.assertIn("Сами блоки на месте", text)
            self.assertNotIn("00:01:46", text)

    def test_honest_titles_and_required_scope(self):
        for spec in self.plan["shorts"]:
            self.assertLessEqual(len(spec["title"]), 100)
            self.assertNotIn("zero lag", spec["description"].lower())
            self.assertNotIn("guaranteed", spec["description"].lower())
            self.assertLessEqual(spec["description"].count("#"), 3)
        for index in (0, 2, 3):
            self.assertIn("singleplayer", self.plan["shorts"][index]["description"])
        self.assertIn("off by default", self.plan["shorts"][3]["description"])
        self.assertIn("not the building", self.plan["shorts"][2]["description"])


if __name__ == "__main__":
    unittest.main()

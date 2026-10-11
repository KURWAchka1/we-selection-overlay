"""Focused checks for schematic fidelity, readability and export bounds."""

import importlib.util
import sys
import unittest
from pathlib import Path

PATH = Path(__file__).with_name("style-banners.py")
SPEC = importlib.util.spec_from_file_location("weso_style_banners", PATH)
STYLE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = STYLE
SPEC.loader.exec_module(STYLE)
REGULAR = Path("C:/Windows/Fonts/segoeui.ttf")
SEMIBOLD = Path("C:/Windows/Fonts/seguisb.ttf")


class ModelTests(unittest.TestCase):
    def test_isolation_preserves_selected_coordinates_and_colors(self):
        original = STYLE.build_model()
        selected = STYLE.isolate(original, STYLE.MINT)
        self.assertTrue(selected)
        self.assertLess(len(selected), len(original))
        self.assertEqual(selected, {point: color for point, color in original.items()
                                    if color == STYLE.MINT})

    def test_absent_material_does_not_fabricate_blocks(self):
        self.assertEqual(STYLE.isolate(STYLE.build_model(), "#FFFFFF"), {})

    def test_selection_contains_entire_model(self):
        self.assertTrue(all(0 <= x < 6 and 0 <= y < 5 and 0 <= z < 4
                            for x, y, z in STYLE.build_model()))

    def test_text_contrast(self):
        for color in (STYLE.TEXT, STYLE.SECONDARY):
            self.assertGreaterEqual(STYLE.contrast(color, STYLE.BACKGROUND), 4.5)

    def test_contrast_is_symmetric_and_identity_is_one(self):
        self.assertEqual(STYLE.contrast(STYLE.TEXT, STYLE.TEXT), 1)
        self.assertEqual(STYLE.contrast(STYLE.TEXT, STYLE.BACKGROUND),
                         STYLE.contrast(STYLE.BACKGROUND, STYLE.TEXT))


@unittest.skipUnless(REGULAR.is_file() and SEMIBOLD.is_file(), "Installed proof fonts required")
class RenderTests(unittest.TestCase):
    def test_both_banners_fit_and_have_large_mobile_headlines(self):
        for kind in ("command", "isolation"):
            image, text = STYLE.render(kind, REGULAR, SEMIBOLD)
            self.assertEqual(image.size, STYLE.SIZE)
            self.assertEqual(len(text), 4)
            self.assertGreaterEqual(text[0]["font_px"] * 320 / STYLE.SIZE[0], 19)
            for item in text:
                left, top, right, bottom = item["bounds"]
                self.assertGreaterEqual(left, 48)
                self.assertLessEqual(right, STYLE.SIZE[0] - 48)
                self.assertLess(bottom, STYLE.SIZE[1])
                self.assertGreater(bottom, top)

    def test_unknown_illustration_fails(self):
        with self.assertRaises(ValueError):
            STYLE.render("fake-feature", REGULAR, SEMIBOLD)

    def test_text_overflow_fails_instead_of_silently_cropping(self):
        surface = STYLE.Illustration(
            STYLE.Image.new("RGB", tuple(n * STYLE.SCALE for n in STYLE.SIZE)), REGULAR, SEMIBOLD)
        with self.assertRaises(ValueError):
            surface.text("This should never fit", 64, 40, 82, max_width=10)

    def test_mobile_proofs_retain_actual_display_width(self):
        banner, _ = STYLE.render("command", REGULAR, SEMIBOLD)
        for width in (320, 360, 720):
            proof = STYLE.render_proof([banner, banner], width)
            height = round(STYLE.SIZE[1] * width / STYLE.SIZE[0])
            self.assertEqual(proof.size, (width + 32, height * 2 + 48))


class DescriptionTests(unittest.TestCase):
    def test_video_leads_without_old_screenshot_collection(self):
        text = (STYLE.ROOT / "docs/release/description-1.8.0.md").read_text(encoding="utf-8")
        self.assertTrue(text.startswith('<p align="center">\n<iframe'))
        self.assertIn("https://www.youtube.com/embed/nZlb22H432E", text)
        self.assertNotIn("media.forgecdn.net", text)
        self.assertNotIn("build/release/", text)

    def test_draft_keeps_important_compatibility_and_visual_only_limits(self):
        text = (STYLE.ROOT / "docs/release/description-1.8.0.md").read_text(encoding="utf-8")
        for statement in ("Hidden blocks still exist and collide", "Only bounds move",
                          "26.1.2 or newer", "one JAR for your loader", "2x"):
            self.assertIn(statement, text)


if __name__ == "__main__":
    unittest.main()

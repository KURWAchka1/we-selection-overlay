"""Pin a subtle 450 weight; Minecraft's native TTF provider is not variable-font aware."""
import argparse
from pathlib import Path
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

root = Path(__file__).resolve().parent.parent
modules = ["WEaddon_fabric_1.21.11", "WEaddon_lightposition", "WEaddon_fabric_26.2",
           "WEaddon_neoforge_1.21.11", "WEaddon_neoforge_26.1", "WEaddon_neoforge_26.2"]
relative = "src/main/resources/assets/worldedit_selection_overlay/font/manrope.ttf"
parser = argparse.ArgumentParser()
parser.add_argument("--source", type=Path, help="Upstream variable TTF (after assets are generated, this is optional)")
args = parser.parse_args()
font = TTFont(args.source or root / modules[0] / relative)
if "fvar" in font:
    font = instantiateVariableFont(font, {"wght": 450}, inplace=True)
elif font["OS/2"].usWeightClass != 450:
    raise ValueError("Use --source with the upstream variable font to change a static font's weight")
for module in modules:
    font.save(root / module / relative)
print("450-weight Manrope written to all six runtimes.")

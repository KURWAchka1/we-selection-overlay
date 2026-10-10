"""Read-only roof inventory for choosing exact, native filming selections.

Uses nbtlib for NBT; source is the pinned modern Anvil map, not a general importer.
"""

import io
from pathlib import Path
import struct
import sys
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "build/video-tools/python"))
import nbtlib
import numpy as np


def roof_cells():
    cells = set()
    heights = {}
    chosen_heights = []
    with zipfile.ZipFile(ROOT / "build/video-tools/celestia-town.zip") as archive:
        for name in ("r.0.-1.mca", "r.1.-1.mca"):
            region = archive.read("Celestia Town by leolerenard/region/" + name)
            for index in range(1024):
                offset = int.from_bytes(region[index * 4:index * 4 + 3], "big") * 4096
                if not offset:
                    continue
                size = struct.unpack_from(">I", region, offset)[0]
                if region[offset + 4] != 2:
                    raise ValueError("Unreviewed source compression")
                chunk = nbtlib.File.parse(io.BytesIO(zlib.decompress(region[offset + 5:offset + 4 + size])))
                cx, cz = int(chunk["xPos"]), int(chunk["zPos"])
                if not (18 <= cx <= 37 and -19 <= cz <= -5):
                    continue
                for section in chunk["sections"]:
                    sy = int(section["Y"])
                    if not (4 <= sy <= 7) or "block_states" not in section:
                        continue
                    states = section["block_states"]
                    palette = states["palette"]
                    roof = [i for i, state in enumerate(palette)
                            if str(state["Name"]) in ("minecraft:bricks", "minecraft:brick_stairs", "minecraft:brick_slab")]
                    if not roof:
                        continue
                    if len(palette) == 1:
                        values = np.zeros(4096, dtype=np.uint64)
                    else:
                        bits = max(4, (len(palette) - 1).bit_length())
                        per_word = 64 // bits
                        packed = np.asarray(states["data"], dtype=np.int64).view(np.uint64)
                        indices = np.arange(4096, dtype=np.uint64)
                        values = (packed[(indices // per_word).astype(int)] >>
                                  ((indices % per_word) * bits)) & ((1 << bits) - 1)
                    for i in np.flatnonzero(np.isin(values, roof)):
                        x, z = cx * 16 + int(i) % 16, cz * 16 + (int(i) // 16) % 16
                        cells.add((x, z))
                        heights[(x, z)] = max(heights.get((x, z), 0), sy * 16 + int(i) // 256)
                        if 411 <= x <= 430 and -146 <= z <= -131:
                            chosen_heights.append(sy * 16 + int(i) // 256)
    if chosen_heights:
        print(f"Chosen house brick roof/chimney Y: {min(chosen_heights)}..{max(chosen_heights)}")
    return cells, heights


def main():
    cells, heights = roof_cells()
    components = []
    while cells:
        stack = [cells.pop()]
        group = []
        while stack:
            x, z = stack.pop()
            group.append((x, z))
            for neighbor in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                if neighbor in cells:
                    cells.remove(neighbor)
                    stack.append(neighbor)
        if len(group) >= 60:
            xs, zs = zip(*group)
            components.append((len(group), min(xs), max(xs), min(zs), max(zs), max(heights[p] for p in group)))
    print("Roof cells / X min-max / Z min-max (source coordinates, before native upgrade):")
    for area, x1, x2, z1, z2, y in sorted(components, reverse=True):
        print(f"{area:4d}: X {x1}..{x2}, Z {z1}..{z2}, roof/chimney top Y {y}")


if __name__ == "__main__":
    main()

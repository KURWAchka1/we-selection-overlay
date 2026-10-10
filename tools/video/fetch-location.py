"""Acquire the credited filming map; never package it with the mod or sources."""

import hashlib
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "build/video-tools/celestia-town.zip"
SHA256 = "1f6c55d0a3691fe4b8aae5d0551766cbf33298bcf64eb3a763d411ea52ee45cb"
SOURCE = "https://www.curseforge.com/minecraft/worlds/celestia-town"
URL = "https://mediafilez.forgecdn.net/files/5307/205/Celestia%20Town%20by%20leolerenard.zip"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    TARGET.parent.mkdir(parents=True, exist_ok=True)
    if not TARGET.is_file() or digest(TARGET) != SHA256:
        partial = TARGET.with_suffix(".zip.part")
        subprocess.run(["curl.exe", "-fsSL", "--retry", "2", "--max-time", "240",
                        "--output", str(partial), URL], check=True, timeout=750)
        if digest(partial) != SHA256:
            raise ValueError("Map revision differs from the reviewed source; do not import it")
        partial.replace(TARGET)
    manifest = {
        "title": "Celestia Town", "creator": "Fireless007 / leolerenard",
        "source": SOURCE, "file_id": 5307205, "download": URL, "sha256": SHA256,
        "hash_basis": "Local pin of the reviewed CurseForge download, not a publisher signature",
        "permission": "Author's description explicitly permits videos with a download-page credit",
        "credit": f"Filming location: Celestia Town by Fireless007 / leolerenard. {SOURCE}",
        "redistribute": False, "use": "Disposable local recording instance only",
    }
    (TARGET.parent / "celestia-provenance.json").write_text(json.dumps(manifest, indent=2) + "\n",
                                                          encoding="utf-8")
    print("FILMING MAP PASS: reviewed archive; author credit required")


if __name__ == "__main__":
    main()

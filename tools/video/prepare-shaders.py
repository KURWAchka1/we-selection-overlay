"""Acquire pinned filming-only shader dependencies, checking publisher hashes."""

import hashlib
import json
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "build/video-tools/shaders"
VERSIONS = {"iris": "gxZWWnKH", "sodium": "xJZxADzI", "complementary": "Bqen1mJX"}


def main():
    TARGET.mkdir(parents=True, exist_ok=True)
    manifest = []
    for project, version in VERSIONS.items():
        with urllib.request.urlopen(f"https://api.modrinth.com/v2/version/{version}", timeout=30) as response:
            info = json.load(response)
        asset = next(f for f in info["files"] if f["primary"])
        if Path(asset["filename"]).name != asset["filename"]:
            raise ValueError("Unexpected asset path")
        path = TARGET / asset["filename"]
        if not path.is_file() or path.stat().st_size != asset["size"]:
            # Windows curl uses the system trust store; do not disable TLS verification.
            subprocess.run(["curl.exe", "-fsSL", "--retry", "2", "--max-time", "240",
                            "--output", str(path), asset["url"]], check=True, timeout=750)
        if hashlib.sha512(path.read_bytes()).hexdigest() != asset["hashes"]["sha512"]:
            raise ValueError(f"Shader dependency hash mismatch: {project}")
        manifest.append({"project": project, "version": info["version_number"], "filename": asset["filename"],
                         "url": asset["url"], "sha512": asset["hashes"]["sha512"], "use": "filming only"})
        print(f"SHADER ASSET PASS: {project} {info['version_number']}", flush=True)
        if project == "complementary":
            with zipfile.ZipFile(path) as archive:
                properties = archive.read("shaders/shaders.properties").decode("utf-8")
                (TARGET / "upstream-shader-profiles.txt").write_text("\n".join(line for line in properties.splitlines()
                    if line.startswith("profile.") or "screen." in line), encoding="utf-8")
    (TARGET / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()

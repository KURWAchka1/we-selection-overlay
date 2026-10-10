"""Fetch publisher assets in validated resumable ranges, retaining upstream hashes."""

from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/video-tools/models"
API = "https://api.github.com/repos/thewh1teagle/kokoro-onnx/releases/tags/model-files-v1.1"


def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "WesoVideo/1.0"}), timeout=60)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with fetch(API) as response:
        release = json.load(response)
    manifest = []
    for name in ("kokoro-v1.0.onnx", "voices-v1.0.bin"):
        asset = next(a for a in release["assets"] if a["name"] == name)
        destination = OUT / name
        expected = asset["size"]
        publisher_hash = asset["digest"].removeprefix("sha256:")
        if not destination.is_file() or destination.stat().st_size != expected:
            prefix = destination.stat().st_size if destination.is_file() else 0
            if prefix > expected:
                raise ValueError("Oversized partial asset")
            pieces = OUT / (name + ".parts")
            pieces.mkdir(exist_ok=True)
            chunk = 16 * 1024 * 1024
            intervals = [(start, min(start + chunk, expected) - 1) for start in range(prefix, expected, chunk)]

            def part(interval):
                start, end = interval
                path = pieces / f"{start}-{end}.part"
                length = end - start + 1
                if path.is_file() and path.stat().st_size == length:
                    return path
                for attempt in range(3):
                    try:
                        request = urllib.request.Request(asset["browser_download_url"],
                            headers={"User-Agent": "WesoVideo/1.0", "Range": f"bytes={start}-{end}"})
                        with urllib.request.urlopen(request, timeout=60) as response:
                            if response.status != 206 or response.headers.get("Content-Range") != f"bytes {start}-{end}/{expected}":
                                raise ValueError("Server did not honor the exact requested range")
                            with path.open("wb") as stream:
                                while data := response.read(256 * 1024):
                                    stream.write(data)
                        if path.stat().st_size != length:
                            raise ValueError("Truncated range")
                        print(f"Downloaded {name} {start}-{end}", flush=True)
                        return path
                    except (OSError, ValueError):
                        if attempt == 2:
                            raise

            with ThreadPoolExecutor(max_workers=4) as pool:
                paths = list(pool.map(part, intervals))
            with destination.open("ab") as stream:
                for path in paths:
                    with path.open("rb") as source:
                        while data := source.read(1024 * 1024):
                            stream.write(data)
        h = hashlib.sha256()
        with destination.open("rb") as source:
            while data := source.read(1024 * 1024):
                h.update(data)
        if destination.stat().st_size != expected or h.hexdigest() != publisher_hash:
            raise ValueError(f"Publisher hash mismatch: {name}")
        manifest.append({"name": name, "bytes": expected, "url": asset["browser_download_url"],
                         "sha256": h.hexdigest(), "publisher_digest_verified": True})
        print(f"ASSET PASS: {name}", flush=True)
    (OUT / "upstream-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    for filename, url in {
        "kokoro-onnx-MIT.txt": "https://raw.githubusercontent.com/thewh1teagle/kokoro-onnx/main/LICENSE",
        "kokoro-model-card.md": "https://huggingface.co/hexgrad/Kokoro-82M/raw/main/README.md",
        "Apache-2.0.txt": "https://www.apache.org/licenses/LICENSE-2.0.txt",
    }.items():
        with fetch(url) as response:
            (OUT / filename).write_bytes(response.read())


if __name__ == "__main__":
    main()

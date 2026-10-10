"""Install a verified official portable editor locally, without changing Windows."""

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
VERSION = "4.5.9"
NAME = f"blender-{VERSION}-windows-x64.zip"
BASE = "https://download.blender.org/release/Blender4.5/"
PIN = "41da973b9bf95bb312cbeff4d1982feb13259b43c821686b9bafea4dfe5477cf"


def main():
    root = ROOT / "build/video-tools/blender"
    root.mkdir(parents=True, exist_ok=True)
    if shutil.disk_usage(root).free < 8 * 1024**3:
        raise RuntimeError("Preserve free disk space; fewer than 8 GiB remain")
    publisher = subprocess.run(["curl.exe","-fLsS",BASE + f"blender-{VERSION}.sha256"],
                               capture_output=True, check=True).stdout.decode()
    found = dict(line.split(None,1)[::-1] for line in publisher.splitlines())
    if found.get(NAME) != PIN:
        raise RuntimeError("Official editor checksum changed; do not install")
    archive = root / NAME
    partial = archive.with_suffix(".zip.partial")
    if not archive.is_file():
        subprocess.run(["curl.exe","-fLsS","--retry","2",BASE + NAME,"-o",str(partial)],check=True)
        partial.replace(archive)
    h = hashlib.sha256()
    with archive.open("rb") as stream:
        for data in iter(lambda: stream.read(1024*1024),b""): h.update(data)
    if h.hexdigest() != PIN:
        raise RuntimeError("Editor archive does not match its publisher digest")
    executable = root / f"blender-{VERSION}-windows-x64/blender.exe"
    if not executable.is_file():
        with zipfile.ZipFile(archive) as package:
            total = sum(info.file_size for info in package.infolist())
            if total > 4 * 1024**3: raise RuntimeError("Unexpected editor footprint")
            for info in package.infolist():
                target = (root / info.filename).resolve()
                if not target.is_relative_to(root.resolve()): raise RuntimeError("Unsafe archive path")
                if info.is_dir(): target.mkdir(parents=True,exist_ok=True)
                else:
                    target.parent.mkdir(parents=True,exist_ok=True)
                    with package.open(info) as source, target.open("wb") as output:
                        shutil.copyfileobj(source,output)
    manifest = {"tool":"Blender LTS","version":VERSION,"source":BASE+NAME,"sha256":PIN,
                "publisher_checksums":BASE+f"blender-{VERSION}.sha256",
                "portable":True,"executable":str(executable),"background_only":True}
    (root / "manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    print(f"EDITOR VERIFIED: {executable}",flush=True)


if __name__ == "__main__": main()

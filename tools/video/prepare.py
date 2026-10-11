"""Produce editable script, local narration, and voice-derived subtitle timings."""

import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path[:0] = [str(ROOT / "build/video-tools/kokoro"),
                str(ROOT / "build/video-tools/python")]


def wrap_caption(text, width=42):
    lines = text.splitlines()
    if len(lines) <= 2 and all(len(line) <= width for line in lines):
        return text
    words = text.split()
    candidates = [(" ".join(words[:i]), " ".join(words[i:]))
                  for i in range(1, len(words))]
    candidates = [pair for pair in candidates if max(map(len, pair)) <= width]
    if not candidates:
        raise ValueError(f"Caption needs editorial shortening: {text}")
    return "\n".join(min(candidates, key=lambda pair: abs(len(pair[0]) - len(pair[1]))))


def timestamp(seconds, ass=False):
    if ass:
        value = round(seconds * 100)
        return f"{value // 360000}:{value // 6000 % 60:02}:{value // 100 % 60:02}.{value % 100:02}"
    value = round(seconds * 1000)
    return f"{value // 3600000:02}:{value // 60000 % 60:02}:{value // 1000 % 60:02},{value % 1000:03}"


def spoken(text):
    return (text.replace("WorldEdit", "World Edit").replace("NeoForge", "Neo Forge")
            .replace("F8", "F eight").replace("CUI", "see you eye")
            .replace("1.21.11", "one point twenty one point eleven")
            .replace("26.1.2", "twenty six point one point two")
            .replace("26.1.x", "twenty six point one")
            .replace("26.1", "twenty six point one").replace("26.2", "twenty six point two"))


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan-only", action="store_true")
    parser.add_argument("--voice", default=None)
    parser.add_argument("--limit", type=int, default=None, help="Audition only the first N cues")
    args = parser.parse_args()
    if args.limit is not None and args.limit < 1:
        parser.error('--limit must be positive')
    script = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
    voice = args.voice or script["voice"]
    out = ROOT / "build/video/1.8.0"
    out.mkdir(parents=True, exist_ok=True)
    source_lines = [f"# {script['title']}", "", "English narration / Russian subtitles.",
                    "Timings are supplied by the generated audio timeline, not estimated here.", ""]
    cues = []
    for chapter in script["chapters"]:
        source_lines += [f"## {chapter['title']}", "", f"Shot: {chapter['shot']}", ""]
        for index, (en, ru) in enumerate(chapter["cues"]):
            item = {"id": f"{chapter['id']}-{index + 1:02}", "chapter": chapter["id"],
                    "en": en, "ru": wrap_caption(ru), "position": chapter["position"]}
            # Long English sentences split into separate synchronous cues before synthesis.
            wrap_caption(en)
            cues.append(item)
            source_lines += [f"**{item['id']}**", "", en, "", item["ru"].replace("\n", "\\\n"), ""]
    (ROOT / "docs/video/SCRIPT.md").write_text("\n".join(source_lines), encoding="utf-8", newline="\n")
    print(f"Script validated: {len(cues)} cues, {sum(len(c['en'].split()) for c in cues)} English words", flush=True)
    if args.plan_only:
        return

    import numpy as np
    import onnxruntime as ort
    import soundfile as sf
    from kokoro_onnx import Kokoro
    import imageio_ffmpeg

    model_dir = ROOT / "build/video-tools/models"
    model = model_dir / "kokoro-v1.0.onnx"
    voices = model_dir / "voices-v1.0.bin"
    if model.stat().st_size != 325505369 or voices.stat().st_size != 28214398:
        raise ValueError("Incomplete voice model assets; do not synthesize a partial download")
    manifest = json.loads((model_dir / "upstream-manifest.json").read_text(encoding="utf-8"))
    for path in (model, voices):
        expected = next(asset for asset in manifest if asset["name"] == path.name)["sha256"]
        if digest(path) != expected:
            raise ValueError(f"Publisher digest mismatch: {path.name}")
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
    engine = Kokoro.from_session(ort.InferenceSession(str(model), options,
                                providers=["CPUExecutionProvider"]), str(voices))
    audio_dir = out / "voice" / voice
    audio_dir.mkdir(parents=True, exist_ok=True)
    timeline, track = [], []
    offset = 0.0
    selected = cues[:args.limit]
    for index, cue in enumerate(selected):
        key = hashlib.sha256(json.dumps([spoken(cue["en"]), voice, script["speed"],
                            "kokoro-onnx-0.6.1"], ensure_ascii=False).encode()).hexdigest()[:16]
        path = audio_dir / f"{cue['id']}-{key}.wav"
        if path.is_file():
            samples, rate = sf.read(path, dtype="float32")
        else:
            samples, rate = engine.create(spoken(cue["en"]), voice=voice,
                                          speed=script["speed"], lang="en-us")
            samples = np.asarray(samples, dtype=np.float32)
            sf.write(path, samples, rate, subtype="PCM_24")
        if rate != 24000 or samples.ndim != 1 or len(samples) == 0 or not np.isfinite(samples).all():
            raise ValueError(f"Invalid voice output: {cue['id']}")
        duration = len(samples) / rate
        ru_length = len(cue["ru"].replace("\n", " "))
        caption_duration = max(duration + 0.12, ru_length / 17, 1.2)
        pause = 0.12
        if index + 1 == len(selected):
            pause = script.get("final_pause", 0.9)
        elif selected[index + 1]["chapter"] != cue["chapter"]:
            pause = script.get("chapter_pause", 0.4)
        caption_duration = max(caption_duration, script.get("minimum_slots", {}).get(cue["id"], 0) - pause)
        if caption_duration > 7:
            raise ValueError(f"Split long cue before production: {cue['id']} ({caption_duration:.2f}s)")
        total = math.ceil((caption_duration + pause) * script["fps"]) / script["fps"]
        item = {**cue, "start": offset, "voice_end": offset + duration,
                "end": offset + caption_duration, "slot_end": offset + total,
                "wav": str(path.relative_to(out)), "russian_cps": ru_length / caption_duration}
        timeline.append(item)
        track += [samples, np.zeros(round((total - duration) * rate), dtype=np.float32)]
        offset += total
        print(f"{cue['id']}: voice {duration:.2f}s, slot {total:.2f}s", flush=True)

    if args.limit:
        sf.write(out / f"voice-audition-{voice}.wav", np.concatenate(track), 24000, subtype="PCM_24")
        return
    sf.write(out / "narration-dry.wav", np.concatenate(track), 24000, subtype="PCM_24")
    timeline_file = out / "timeline.json"
    timeline_file.write_text(json.dumps({"fps": script["fps"], "duration": offset,
                               "voice": voice, "cues": timeline}, ensure_ascii=False, indent=2), encoding="utf-8")
    for language in ("en", "ru"):
        content = []
        for i, cue in enumerate(timeline, 1):
            content.append(f"{i}\n{timestamp(cue['start'])} --> {timestamp(cue['end'])}\n{wrap_caption(cue[language])}\n")
        (out / f"subtitles-{language}.srt").write_text("\n".join(content), encoding="utf-8")
    ass = ["[Script Info]", "ScriptType: v4.00+", "PlayResX: 1920", "PlayResY: 1080",
           "WrapStyle: 2", "", "[V4+ Styles]",
           "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding",
           "Style: RU,Segoe UI,40,&H00FFFFFF,&H00FFFFFF,&H90000000,&H90000000,0,0,0,0,100,100,0,0,1,2,1,2,140,140,80,1",
           "", "[Events]", "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"]
    for cue in timeline:
        position = {"top": r"{\an8\pos(960,140)}", "header": r"{\an8\pos(960,12)}",
                    "bottom": r"{\an2}"}[cue["position"]]
        text = cue["ru"].replace("\n", r"\N")
        ass.append(f"Dialogue: 0,{timestamp(cue['start'], True)},{timestamp(cue['end'], True)},RU,,0,0,0,,{position}{text}")
    (out / "subtitles-ru-review.ass").write_text("\n".join(ass), encoding="utf-8")
    chapter_lines = []
    for chapter in script["chapters"]:
        cue = next(c for c in timeline if c["chapter"] == chapter["id"])
        sec = round(cue["start"] * script["fps"]) // script["fps"]
        chapter_lines.append(f"{sec // 60:02}:{sec % 60:02} {chapter['title']}")
    (out / "chapters.txt").write_text("\n".join(chapter_lines), encoding="utf-8")
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    measure = subprocess.run([ffmpeg, "-hide_banner", "-nostdin", "-i", str(out / "narration-dry.wav"),
                              "-af", "loudnorm=I=-16:TP=-1:LRA=7:print_format=json", "-f", "null", "-"],
                             capture_output=True, text=True, check=True)
    (out / "loudness-pass1.txt").write_text(measure.stderr, encoding="utf-8")
    measured = json.loads(re.findall(r"\{[^{}]+\}", measure.stderr)[-1])
    normalize = ("loudnorm=I=-16:TP=-1:LRA=7:linear=true:"
                 f"measured_I={measured['input_i']}:measured_TP={measured['input_tp']}:"
                 f"measured_LRA={measured['input_lra']}:measured_thresh={measured['input_thresh']}:"
                 f"offset={measured['target_offset']}:print_format=json")
    done = subprocess.run([ffmpeg, "-hide_banner", "-nostdin", "-y", "-i", str(out / "narration-dry.wav"),
                           "-af", normalize, "-ar", "48000", "-ac", "2", "-c:a", "pcm_s24le",
                           str(out / "narration-en.wav")], capture_output=True, text=True, check=True)
    (out / "loudness-pass2.txt").write_text(done.stderr, encoding="utf-8")
    provenance = {"model": "Kokoro-82M v1.0, Apache-2.0", "inference": "kokoro-onnx 0.6.1, MIT",
                  "voice": voice, "threads": 2, "source_commit": "e4b5376",
                  "model_url": "https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.1/kokoro-v1.0.onnx",
                  "model_sha256": digest(model), "voices_sha256": digest(voices),
                  "synthetic_narration": True, "music": None, "duration": offset,
                  "human_audio_audition": "owner approved af_heart voice and pace on the opening excerpt; full narration review pending", "picture_lock": "pending"}
    (out / "provenance.json").write_text(json.dumps(provenance, indent=2), encoding="utf-8")
    print(f"PRODUCTION AUDIO PASS: {len(timeline)} cues, {offset:.2f}s, captions <=17 RU cps", flush=True)


if __name__ == "__main__":
    main()

"""Reframe the approved review into four independent, captioned vertical edits."""

import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "build/video-tools/python"))
import imageio_ffmpeg

from edit import decoded_frames, run

OUT = ROOT / "build/release/1.8.0/shorts"
PLAN = ROOT / "docs/video/shorts-plan.json"
SIZE = (1080, 1920)
FPS = 30
BG = "#191F22"
TEXT = "#F2F5F5"
ACCENTS = {"cyan": "#55D3ED", "mint": "#A0E1C1"}
BOLD = Path("C:/Windows/Fonts/arialbd.ttf")
# Crops are editorial focus changes, never a change to native game behavior.
VIEWS = {
    "command": (700, 132, 484, 860),
    "isolation": (656, 0, 608, 1080),
    "inspector": (380, 0, 608, 1080),
    "move": (640, 0, 608, 1080),
    "clipboard-wide": (726, 220, 456, 810),
    "clipboard": (726, 220, 456, 810),
}
COMPASS_SOURCE = (1536, 260, 288, 288)
COMPASS_TARGET = (710, 1250, 220, 220)


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def frames(seconds):
    value = round(seconds * FPS)
    if abs(value / FPS - seconds) > 1e-6:
        raise ValueError(f"Source cut is not on a frame boundary: {seconds}")
    return value


def build_cuts(spec, timeline):
    by_id = {cue["id"]: cue for cue in timeline["cues"]}
    cuts, cursor = [], 0
    for cue_id in spec["cues"]:
        cue = by_id[cue_id]
        start = frames(cue["start"])
        end = frames(cue["slot_end"])
        if end <= start or cue["voice_end"] > cue["slot_end"]:
            raise ValueError(f"Invalid or truncated spoken cue: {cue_id}")
        view = spec.get("views", {}).get(cue_id, spec["default_view"])
        if view not in VIEWS:
            raise ValueError(f"Unknown crop: {view}")
        chat = spec.get("chat_insets", {}).get(cue_id)
        if chat is not None:
            validate_chat_inset(chat, (end-start)/FPS)
        cuts.append({"cue": cue_id, "source_start_frame": start,
                     "frames": end - start, "output_start_frame": cursor,
                     "view": view, "compass": cue_id in spec.get("compass_cues", []),
                     "phase": spec["phases"][cue_id], "en": cue["en"], "ru": cue["ru"],
                     "voice_duration": cue["voice_end"]-cue["start"],
                     "first": cue_id == spec["cues"][0], "last": cue_id == spec["cues"][-1],
                     "reveal_time": spec.get("reveal_times", {}).get(cue_id),
                     "chat_inset": chat,
                     "caption_end": min(cue["end"] - cue["start"], (end-start)/FPS)})
        cursor += end - start
    if cursor / FPS >= 60:
        raise ValueError("This series is intentionally shorter than one minute per Short")
    return cuts


def chat_size(inset):
    _, _, width, height = inset["crop"]
    target_width = inset["width"]
    if min(width, height, target_width) <= 0:
        raise ValueError("Invalid native chat size")
    return target_width, round(height*target_width/width/2)*2


def validate_chat_inset(inset, duration):
    x, y, width, height = inset["crop"]
    px, py = inset["position"]
    tw, th = chat_size(inset)
    if (min(x, y) < 0 or min(width, height, tw, th) <= 0 or
            x+width > 1920 or y+height > 1080 or
            any(value % 2 for value in (x, y, width, height, tw, px, py))):
        raise ValueError("Invalid native chat crop")
    if px < 48 or py < 80 or px+tw > 936 or py+th > 1640:
        raise ValueError("Chat inset crosses the Shorts safe area")
    start, end, fade = inset["start"], inset["end"], inset["fade"]
    if not 0 <= start < end <= duration or not 0 < fade <= (end-start)/2:
        raise ValueError("Invalid chat inset clock")


def wrap_measured(text, face, width, max_lines=2):
    words = text.replace("\n", " ").split()
    if not words:
        raise ValueError("Empty text")
    whole = " ".join(words)
    if face.getlength(whole) <= width:
        return [whole]
    if max_lines != 2:
        raise ValueError("Only one- or two-line lockups are supported")
    choices = []
    for index in range(1, len(words)):
        lines = [" ".join(words[:index]), " ".join(words[index:])]
        lengths = [face.getlength(line) for line in lines]
        if max(lengths) <= width:
            choices.append((abs(lengths[0]-lengths[1]), lines))
    if not choices:
        raise ValueError("Text does not fit two measured lines")
    return min(choices, key=lambda item: item[0])[1]


def fit_caption(text):
    for size in range(68, 53, -2):
        face = ImageFont.truetype(str(BOLD), size)
        if face.getlength(text) <= 820:
            return face, size, [text]
    for size in range(68, 53, -2):
        face = ImageFont.truetype(str(BOLD), size)
        try:
            return face, size, wrap_measured(text, face, 820)
        except ValueError:
            pass
    raise ValueError(f"Caption needs editorial shortening: {text}")


def graphic(spec, value, destination, *, y=200, size=68, color=TEXT):
    image = Image.new("RGBA", SIZE, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    bounds = []
    face = ImageFont.truetype(str(BOLD), size)
    lines = wrap_measured(value, face, 820)
    for index, line in enumerate(lines):
        position = (490, y+index*(size+10))
        box = draw.textbbox(position, line, font=face, anchor="mt", stroke_width=4)
        if box[0] < 52 or box[2] > 936 or box[1] < 80 or box[3] > 1640:
            raise ValueError(f"Unsafe text bounds {box}: {value}")
        draw.text(position, line, font=face, fill=color, anchor="mt",
                  stroke_width=4, stroke_fill="#121719")
        bounds.append({"text": line, "box": box, "font_size": size})
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, optimize=True)
    return bounds


def overlay_events(spec, cut):
    duration = cut["frames"]/FPS
    events = []
    command = cut["view"] == "command"
    if cut["first"]:
        events.append({"text": spec["hook"], "start": 0, "end": 1.1,
                       "y": 90 if command else 140, "size": 76 if command else 84,
                       "color": ACCENTS[spec["accent"]]})
    phrases = spec["captions"][cut["cue"]]
    total = sum(len(phrase) for phrase in phrases)
    remainder = cut["voice_duration"]-.75*len(phrases)
    if remainder < 0:
        raise ValueError("Too many caption phrases for comfortable reading")
    cursor = 0
    for phrase in phrases:
        end = cursor+.75+remainder*len(phrase)/total
        _, size, _ = fit_caption(phrase)
        events.append({"text": phrase, "start": round(cursor*FPS)/FPS,
                       "end": round(end*FPS)/FPS, "y": 1430 if cut["view"] == "inspector" else
                       ((180 if cut["first"] else 100) if command else
                        (280 if cut["first"] else 200)), "size": size, "color": TEXT})
        cursor = end
    if cut["last"]:
        events.append({"text": "WorldEdit Selection Overlay", "start": max(0, duration-1.6),
                       "end": duration, "y": 1520, "size": 42, "color": TEXT})
    return events


def filter_graph(cut, events=()):
    x, y, width, height = VIEWS[cut["view"]]
    graph = []
    split_outputs = ["scene"]
    if cut["compass"]:
        split_outputs.append("detail")
    if cut.get("chat_inset"):
        split_outputs.append("chat")
    if len(split_outputs) > 1:
        graph.append(f"[0:v]split={len(split_outputs)}"+"".join(f"[{name}]" for name in split_outputs))
        source = "scene"
    else:
        source = "0:v"
    graph.append(f"[{source}]crop={width}:{height}:{x}:{y},"
                 "scale=1080:1920:force_original_aspect_ratio=increase:force_divisible_by=2:flags=lanczos,"
                 "crop=1080:1920,setsar=1[base]")
    current = "base"
    if cut.get("reveal_time") is not None:
        x, y, width, height = VIEWS["isolation"]
        graph.append(f"[{len(events)+1}:v]crop={width}:{height}:{x}:{y},"
                     "scale=1080:1920:force_original_aspect_ratio=increase:force_divisible_by=2:flags=lanczos,"
                     "crop=1080:1920,setsar=1[reveal]")
        graph.append("[base][reveal]overlay=0:0:enable='lt(t,1.1)':shortest=1[revealed]")
        current = "revealed"
    if cut["compass"]:
        x, y, width, height = COMPASS_SOURCE
        px, py, pw, ph = COMPASS_TARGET
        graph.append(f"[detail]crop={width}:{height}:{x}:{y},scale={pw}:{ph}:flags=lanczos[axes]")
        graph.append(f"[{current}][axes]overlay={px}:{py}:shortest=1[focused]")
        current = "focused"
    if cut.get("chat_inset"):
        inset = cut["chat_inset"]
        x, y, width, height = inset["crop"]
        px, py = inset["position"]
        start, end, fade = inset["start"], inset["end"], inset["fade"]
        graph.append(f"[chat]crop={width}:{height}:{x}:{y},"
                     f"scale={inset['width']}:-2:flags=neighbor,setsar=1,format=rgba,"
                     f"fade=t=in:st={start:.6f}:d={fade:.6f}:alpha=1,"
                     f"fade=t=out:st={end-fade:.6f}:d={fade:.6f}:alpha=1[chat_zoom]")
        graph.append(f"[{current}][chat_zoom]overlay={px}:{py}:shortest=1:"
                     f"enable='gte(t,{start:.6f})*lt(t,{end:.6f})'[with_chat]")
        current = "with_chat"
    for index, event in enumerate(events, 1):
        graph.append(f"[{current}][{index}:v]overlay=0:0:shortest=1:"
                     f"enable='gte(t,{event['start']:.6f})*lt(t,{event['end']:.6f})'[art{index}]")
        current = f"art{index}"
    graph.append(f"[{current}]format=yuv420p[out]")
    return ";".join(graph)


def timestamp(seconds, ass=False):
    milliseconds = round(seconds * 1000)
    hours, remainder = divmod(milliseconds, 3600000)
    minutes, remainder = divmod(remainder, 60000)
    seconds, millis = divmod(remainder, 1000)
    return f"{hours:02}:{minutes:02}:{seconds:02},{millis:03}"


def write_subtitles(cuts, lang, path):
    lines = []
    for index, cut in enumerate(cuts, 1):
        start = cut["output_start_frame"]/FPS
        end = start+cut["caption_end"]
        lines += [str(index), f"{timestamp(start)} --> {timestamp(end)}", cut[lang], ""]
    path.write_text("\n".join(lines), encoding="utf-8", newline="\n")


def render_cut(spec, cut, master, directory, ffmpeg):
    events = overlay_events(spec, cut)
    assets, bounds = [], []
    for index, event in enumerate(events):
        art = directory / f"{cut['cue']}-art-{index}.png"
        bounds.extend(graphic(spec, event["text"], art, y=event["y"],
                              size=event["size"], color=event["color"]))
        assets.append(art)
    graph = filter_graph(cut, events)
    cache = hashlib.sha256(json.dumps([[sha256(art) for art in assets], cut, graph, sha256(Path(__file__))],
                                     sort_keys=True).encode()).hexdigest()[:16]
    path = directory / f"{cut['cue']}-{cache}.mov"
    if not path.is_file():
        command = [ffmpeg, "-hide_banner", "-nostdin", "-y", "-threads", "2",
                   "-ss", f"{cut['source_start_frame']/FPS:.9f}", "-i", str(master)]
        for art in assets:
            command += ["-loop", "1", "-framerate", "30", "-i", str(art)]
        if cut.get("reveal_time") is not None:
            command += ["-ss", str(cut["reveal_time"]), "-i", str(master)]
        command += [
                   "-filter_complex_threads", "1", "-filter_complex", graph,
                   "-map", "[out]", "-map", "0:a:0", "-sn", "-map_metadata", "-1",
                   "-map_chapters", "-1", "-frames:v", str(cut["frames"]),
                   "-t", f"{cut['frames']/FPS:.9f}",
                   "-af", f"atrim=end_sample={cut['frames']*1600},asetpts=PTS-STARTPTS,apad=whole_len={cut['frames']*1600}",
                   "-c:v", "libx264", "-threads", "2", "-preset", "fast", "-crf", "18",
                   "-r", "30", "-fps_mode", "cfr", "-pix_fmt", "yuv420p",
                   "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
                   "-video_track_timescale", "90000", "-c:a", "pcm_s16le", "-ar", "48000", str(path)]
        run(command, path.with_suffix(".log"))
    decoded_frames(path, cut["frames"])
    return path, bounds


def cover_image(spec, master, ffmpeg, directory):
    still = directory / "cover-native.jpg"
    cut = {"view": spec["cover_view"], "compass": False}
    run([ffmpeg, "-hide_banner", "-nostdin", "-y", "-threads", "2", "-ss", str(spec["cover_source_time"]),
         "-i", str(master), "-filter_complex", filter_graph(cut), "-map", "[out]",
         "-frames:v", "1", "-q:v", "2", str(still)], directory / "cover.log")
    image = Image.open(still).convert("RGBA")
    title = directory / "cover-title.png"
    graphic(spec, spec["cover_text"], title, y=140, size=92, color=TEXT)
    image.alpha_composite(Image.open(title).convert("RGBA"))
    path = OUT / f"{spec['id']}-cover.jpg"
    image.convert("RGB").save(path, quality=96)
    return path


def qa_video(path, expected_frames, ffmpeg, directory):
    decoded_frames(path, expected_frames)
    info = imageio_ffmpeg.read_frames(str(path))
    metadata = next(info)
    info.close()
    if metadata["size"] != SIZE or abs(metadata["fps"]-FPS) > .001:
        raise ValueError(f"Unexpected output format: {metadata}")
    result = run([ffmpeg, "-hide_banner", "-nostdin", "-threads", "2", "-i", str(path),
                  "-map", "0:a:0", "-af", "loudnorm=I=-16:TP=-2:LRA=7:print_format=json",
                  "-f", "null", "-"], directory / "loudness.log")
    loudness = json.loads(re.findall(r"\{\s*\"input_i\".*?\}", result, re.S)[-1])
    if not -19 <= float(loudness["input_i"]) <= -13 or float(loudness["input_tp"]) > -1:
        raise ValueError(f"Unexpected narration level: {loudness}")
    return {"decoded_frames": expected_frames, "size": SIZE, "fps": FPS,
            "duration": expected_frames/FPS, "loudness": loudness}


def proof_frames(spec, path, ffmpeg, directory):
    samples = []
    for index, seconds in enumerate(spec["proof_times"], 1):
        sample = directory / f"proof-{index:02}.jpg"
        run([ffmpeg, "-hide_banner", "-nostdin", "-y", "-threads", "2", "-ss", str(seconds),
             "-i", str(path), "-frames:v", "1", "-q:v", "2", str(sample)], sample.with_suffix(".log"))
        samples.append(sample)
    return samples


def proof_sheet(samples, destination, columns=3):
    rows = math.ceil(len(samples)/columns)
    image = Image.new("RGB", (360*columns, 640*rows), BG)
    for index, path in enumerate(samples):
        with Image.open(path) as frame:
            frame = frame.convert("RGB").resize((360, 640), Image.Resampling.LANCZOS)
            image.paste(frame, ((index%columns)*360, (index//columns)*640))
    image.save(destination, quality=94)


def render_short(spec, plan, timeline, ffmpeg):
    master = ROOT / plan["master"]
    directory = OUT / spec["id"]
    directory.mkdir(parents=True, exist_ok=True)
    cuts = build_cuts(spec, timeline)
    segments, layouts = [], []
    for cut in cuts:
        path, bounds = render_cut(spec, cut, master, directory, ffmpeg)
        segments.append(path)
        layouts.append({"cue": cut["cue"], "text": bounds})
        print(f"CUT READY: {spec['id']} / {cut['cue']}", flush=True)
    concat = directory / "concat.txt"
    concat.write_text("\n".join(f"file '{path.name}'" for path in segments), encoding="utf-8")
    subtitles = {}
    for lang in ("en", "ru"):
        subtitles[lang] = OUT / f"{spec['id']}-{lang}.srt"
        write_subtitles(cuts, lang, subtitles[lang])
    output = OUT / f"{spec['id']}.mp4"
    count = sum(cut["frames"] for cut in cuts)
    run([ffmpeg, "-hide_banner", "-nostdin", "-y", "-threads", "2", "-f", "concat", "-safe", "0",
         "-i", str(concat), "-i", str(subtitles["en"]), "-i", str(subtitles["ru"]),
         "-map", "0:v:0", "-map", "0:a:0", "-map", "1:0", "-map", "2:0",
         "-map_metadata", "-1", "-map_chapters", "-1", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
         "-ar", "48000", "-c:s", "mov_text", "-t", f"{count/FPS:.9f}",
         "-metadata", f"title={spec['title']}", "-metadata", "artist=Marshal_RF",
         "-metadata", "comment=Reframed native review footage; existing synthetic English narration; Russian captions",
         "-metadata:s:a:0", "language=eng", "-metadata:s:s:0", "language=eng",
         "-metadata:s:s:1", "language=rus", "-movflags", "+faststart", str(output)], directory / "mux.log")
    check = qa_video(output, count, ffmpeg, directory)
    samples = proof_frames(spec, output, ffmpeg, directory)
    proof_sheet(samples, directory / "phone-proof.jpg")
    cover = cover_image(spec, master, ffmpeg, directory)
    credits = "Filmed in Celestia Town; map credit is in the full walkthrough. English narration uses the approved synthetic voice."
    footer = ("\n\nFull walkthrough: " + plan["related_video"] +
              "\nDownload: https://modrinth.com/mod/wg-selection-overlay" +
              "\nCurseForge: https://www.curseforge.com/minecraft/mc-mods/worldedit-selection-overlay" +
              "\n\nCreated by Marshal_RF. " + credits)
    metadata = {"id": spec["id"], "title": spec["title"], "alternate_title": spec["alternate_title"],
                "description": spec["description"]+footer, "pinned_comment": spec["pinned_comment"],
                "related_video": plan["related_video"], "language": "en", "caption_language": "ru",
                "tags": ["Minecraft", "WorldEdit", "WorldEdit Selection Overlay", "Minecraft mods", "building"],
                "file": str(output.relative_to(ROOT)), "sha256": sha256(output), "bytes": output.stat().st_size,
                "cover": str(cover.relative_to(ROOT)), "cover_sha256": sha256(cover),
                "cuts": cuts, "qa": check, "layout": layouts, "published": False}
    (directory / "manifest.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUT / f"{spec['id']}-upload.txt").write_text(spec["title"]+"\n\n"+metadata["description"]+
                                                "\n\nPINNED COMMENT\n"+spec["pinned_comment"], encoding="utf-8")
    print(f"SHORT READY: {output.name}, {count/FPS:.3f}s", flush=True)
    return metadata, cover


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=["01-before-enter", "02-see-inside", "03-move-the-box", "04-check-the-copy"])
    args = parser.parse_args()
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    if sha256(ROOT / plan["master"]) != plan["master_sha256"]:
        raise ValueError("Review source has changed; recheck the edit before rendering")
    timeline = json.loads((ROOT / plan["timeline"]).read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    rendered, samples = [], []
    for spec in plan["shorts"]:
        if args.only and spec["id"] != args.only:
            continue
        metadata, proof = render_short(spec, plan, timeline, ffmpeg)
        rendered.append(metadata)
        samples.append(proof)
    (OUT / ("manifest.json" if not args.only else f"manifest-{args.only}.json")).write_text(
        json.dumps({"master_sha256": plan["master_sha256"], "plan_sha256": sha256(PLAN),
                    "shorts": rendered}, ensure_ascii=False, indent=2), encoding="utf-8")
    proof_sheet(samples, OUT / ("series-phone-proof.jpg" if not args.only else "single-phone-proof.jpg"), columns=4 if not args.only else 1)


if __name__ == "__main__":
    main()

"""Build a reproducible review cut from completed native takes, never substitutes."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

from prepare import ROOT, digest, timestamp

sys.path.insert(0, str(ROOT / "build/video-tools/python"))
import imageio_ffmpeg

OUT = ROOT / "build/video/1.8.0"
EXPECTED_JAR = "912a4c0c455928ec05a24fe2001f009dd68322d8aa437c2c9e82d014e5db46d0"


def frame_count(seconds, fps=30):
    count = round(seconds * fps)
    if count < 1 or abs(count / fps - seconds) > .000001:
        raise ValueError("An edit boundary is not on a native frame")
    return count


def split_frames(total, parts):
    if parts < 1 or total < parts:
        raise ValueError("Invalid shot allocation")
    whole, remainder = divmod(total, parts)
    return [whole + (i < remainder) for i in range(parts)]


def weighted_frames(total, weights):
    if not weights or any(weight <= 0 for weight in weights):
        raise ValueError("Invalid cut weights")
    ends = [round(total * sum(weights[:i+1]) / sum(weights)) for i in range(len(weights))]
    result = [end - (ends[i-1] if i else 0) for i,end in enumerate(ends)]
    if any(value < 1 for value in result):
        raise ValueError("Weighted shot has no frames")
    return result


def run(command, log):
    completed = subprocess.run(command, cwd=ROOT, capture_output=True,
                               creationflags=getattr(subprocess,"BELOW_NORMAL_PRIORITY_CLASS",0))
    log.write_bytes(completed.stderr)
    if completed.returncode:
        raise RuntimeError(f"Render failed; diagnostic: {log}")
    return completed.stderr.decode("utf-8", errors="replace")


def inspect_take(path):
    reader = imageio_ffmpeg.read_frames(str(path), output_params=["-threads", "2"])
    try:
        meta = next(reader)
    finally:
        reader.close()
    if meta["size"] != (1920, 1080) or abs(meta["fps"] - 30) > .001:
        raise ValueError(f"Unexpected source viewport/cadence: {path}")
    return meta


def decoded_frames(path, expected):
    command = [imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-nostdin", "-xerror",
               "-threads", "2", "-i", str(path), "-map", "0:v:0", "-an", "-sn",
               "-fps_mode", "passthrough", "-progress", "pipe:1", "-nostats", "-f", "null", "-"]
    result = subprocess.run(command, capture_output=True,
                            creationflags=getattr(subprocess,"BELOW_NORMAL_PRIORITY_CLASS",0))
    counts = re.findall(rb"(?m)^frame=(\d+)\r?$", result.stdout)
    if result.returncode or not counts or int(counts[-1]) != expected:
        actual = int(counts[-1]) if counts else "unknown"
        raise ValueError(f"Incomplete video: {path.name}; expected {expected} decoded frames, got {actual}")
    return expected


def segment_filters(skip, hold):
    # Reset timestamps after padding: setpts before tpad can inherit an incorrect frame rate.
    return [f"trim=start_frame={skip}", f"tpad=stop_mode=clone:stop={hold}", "setpts=N/(30*TB)"]


def source_manifest(plan, cues, log, opening, names_override=None):
    text = log.read_text(encoding="utf-8-sig", errors="replace")
    if "CURSOR ISOLATION GUARD PASS:" not in text:
        raise ValueError("No cursor-isolated native capture evidence")
    if not opening and "WESO VIDEO CAPTURE PASS:" not in text:
        raise ValueError("Full source capture did not finish successfully")
    if plan['source_directory'] == 'capture-celestia-beauty' and 'VIDEO TERRAIN READY:' not in text:
        raise ValueError('Beauty footage has no full native terrain-readiness evidence')
    directory = OUT / plan["source_directory"]
    if not directory.resolve().is_relative_to(OUT.resolve()):
        raise ValueError("Source directory escaped production storage")
    names = sorted(names_override if names_override is not None else
                   {name for cue in cues for name in plan["cues"][cue["id"]]})
    sources = {}
    for name in names:
        if not re.fullmatch(r"[A-Za-z0-9-]+", name):
            raise ValueError("Unreviewed source name")
        evidence = re.search(rf"VIDEO TAKE: {re.escape(name)}, (\d+) native frames", text)
        if not evidence:
            raise ValueError(f"Incomplete/stale native source: {name}")
        path = directory / f"{name}.mp4"
        meta = inspect_take(path)
        frames = int(evidence[1])
        if abs(meta["duration"] - frames / 30) > .04:
            raise ValueError(f"Incomplete encoder output: {name}")
        decoded_frames(path, frames)
        sources[name] = {"path": str(path.relative_to(ROOT)).replace("\\", "/"),
                         "sha256": digest(path), "frames": frames, "fps": 30}
    jar = ROOT / "WEaddon_fabric_universal_1.21.11-26.2/libs/1.8.0/worldedit_selection_overlay-1.8.0-fabric-1.21.11-26.2.jar"
    if digest(jar) != EXPECTED_JAR:
        raise ValueError("Runtime JAR changed; re-record and verify its new provenance")
    return {"runtime_sha256": EXPECTED_JAR, "capture_log_sha256": digest(log),
            "native_sources": sources, "map_credit": "Celestia Town by Fireless007 / leolerenard",
            "map_source": "https://www.curseforge.com/minecraft/worlds/celestia-town",
            "offline_frame_sampling": True, "not_a_performance_benchmark": True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--capture-log", type=Path, required=True)
    parser.add_argument("--beauty-log", type=Path, help="Completed separate native opening; core evidence remains vanilla")
    parser.add_argument("--pickups-log", type=Path, help="Completed separate vanilla UI pickups")
    parser.add_argument("--opening", action="store_true", help="Opening chapter only; never publish as the full review")
    args = parser.parse_args()
    plan = json.loads((ROOT / "docs/video/edit-plan.json").read_text(encoding="utf-8"))
    timeline = json.loads((OUT / "timeline.json").read_text(encoding="utf-8"))
    all_cues = timeline["cues"]
    if set(plan["cues"]) != {cue["id"] for cue in all_cues}:
        raise ValueError("Every narration cue needs an explicit shot decision")
    cues = [cue for cue in all_cues if cue["chapter"] == "hook"] if args.opening else all_cues
    names = {name for cue in cues for name in plan['cues'][cue['id']]}
    pickups = names.intersection(plan.get('pickup_sources',[]))
    if pickups and not args.pickups_log:
        raise ValueError('This edit requires the completed UI pickup log')
    manifest = source_manifest(plan,cues,args.capture_log,args.opening,names-pickups)
    if pickups:
        pickups_plan = {**plan,'source_directory':'capture-celestia-pickups'}
        pickup_manifest = source_manifest(pickups_plan,cues,args.pickups_log,False,pickups)
        manifest['native_sources'].update(pickup_manifest['native_sources'])
        manifest['pickup_capture_log_sha256'] = pickup_manifest['capture_log_sha256']
    beauty_sources = None
    beauty_cues = {**plan['cues'], **plan.get('beauty_cues', {})}
    if args.beauty_log:
        opening_plan = {**plan,"source_directory":"capture-celestia-beauty", "cues":beauty_cues}
        hook = [cue for cue in all_cues if cue["chapter"] == "hook"]
        beauty_manifest = source_manifest(opening_plan,hook,args.beauty_log,False)
        beauty_sources = beauty_manifest["native_sources"]
        manifest["beauty_sources"] = beauty_sources
        manifest["beauty_capture_log_sha256"] = beauty_manifest["capture_log_sha256"]
    directory = OUT / ("opening-edit" if args.opening else "full-edit")
    directory.mkdir(parents=True, exist_ok=True)
    font = ROOT / "build/video-tools/fonts/seguisb.ttf"
    font.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(Path("C:/Windows/Fonts/seguisb.ttf"), font)
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    base = [ffmpeg, "-hide_banner", "-nostdin", "-y", "-threads", "2", "-filter_threads", "1"]
    segments, decisions = [], []
    for cue in cues:
        profile = "beauty" if beauty_sources and cue["chapter"] == "hook" else "vanilla"
        shot_names = (beauty_cues if profile == 'beauty' else plan['cues'])[cue['id']]
        length = frame_count(cue["slot_end"] - cue["start"])
        allocations = split_frames(length, len(shot_names))
        if cue["id"] in plan.get("cut_weights", {}):
            allocations = weighted_frames(length, plan["cut_weights"][cue["id"]])
            if len(allocations) != len(shot_names):
                raise ValueError("Cut weights do not match the source decisions")
        for index, (name, allocated) in enumerate(zip(shot_names, allocations)):
            source = beauty_sources[name] if profile == "beauty" else manifest["native_sources"][name]
            # Preserve the end of a gesture if a short matched-cut slot trims the take.
            skip = max(0, source["frames"] - allocated) if name.startswith(("26-", "30-")) else 0
            hold = max(0, allocated - source["frames"])
            filters = segment_filters(skip, hold)
            label = plan["callouts"].get(cue["id"])
            if label:
                label_path = directory / f"{cue['id']}.txt"
                label_path.write_text(label, encoding="utf-8", newline="\n")
                if cue["id"] == "hook-04":
                    duration = allocated / 30
                    filters.append("drawtext=fontfile='build/video-tools/fonts/seguisb.ttf':"
                                   f"textfile='{label_path.relative_to(ROOT).as_posix()}':"
                                   "fontcolor=0x182e2a:fontsize=54:line_spacing=4:x=(w-text_w)/2:"
                                   "y='74+10*(1-min(1,t/0.35))':"
                                   f"alpha='min(1,t/0.35)*min(1,({duration:.9f}-t)/0.25)'")
                else:
                    filters.append("drawtext=fontfile='build/video-tools/fonts/seguisb.ttf':"
                                   f"textfile='{label_path.relative_to(ROOT).as_posix()}':"
                                   "fontcolor=0x182e2a:fontsize=32:line_spacing=9:x=76:y=175:"
                                   "alpha='min(1,t/0.2)'")
            key = hashlib.sha256(json.dumps([source["sha256"],allocated,skip,filters,"review-v6"],
                                           sort_keys=True).encode()).hexdigest()[:16]
            path = directory / f"{cue['id']}-{index}-{key}.mp4"
            if not path.is_file():
                run(base + ["-i", source["path"], "-vf", ",".join(filters), "-frames:v", str(allocated),
                            "-an", "-c:v", "libx264", "-threads", "2", "-preset", "fast", "-crf", "18",
                            "-r", "30", "-fps_mode", "cfr",
                            "-pix_fmt", "yuv420p", "-colorspace", "bt709", "-color_primaries", "bt709",
                            "-color_trc", "bt709", str(path)], path.with_suffix(".log"))
            inspect_take(path)
            decoded_frames(path, allocated)
            segments.append(path)
            decisions.append({"cue":cue["id"],"source":name,"profile":profile,"frames":allocated,"trim_frames":skip,
                              "last_frame_hold":hold,"segment":path.name})
        print(f"CUT: {cue['id']}, {length} frames", flush=True)
    total = cues[-1]["slot_end"]
    concat = directory / "concat.txt"
    concat.write_text("\n".join(f"file '{path.name}'" for path in segments), encoding="utf-8")
    metadata = directory / "chapters.ffmetadata"
    lines = [";FFMETADATA1", "title=WorldEdit Selection Overlay 1.8 - Preview Review",
             "comment=Native Minecraft footage; synthetic English narration; credited Celestia Town filming map"]
    chapters = []
    for cue in cues:
        if not chapters or chapters[-1]["id"] != cue["chapter"]:
            chapters.append({"id":cue["chapter"],"start":cue["start"]})
    script = json.loads((ROOT / "docs/video/script.json").read_text(encoding="utf-8"))
    titles = {chapter["id"]:chapter["title"] for chapter in script["chapters"]}
    for index, chapter in enumerate(chapters):
        end = chapters[index+1]["start"] if index+1 < len(chapters) else total
        lines += ["[CHAPTER]", "TIMEBASE=1/1000", f"START={round(chapter['start']*1000)}",
                  f"END={round(end*1000)}", f"title={titles[chapter['id']]}"]
    metadata.write_text("\n".join(lines), encoding="utf-8")
    title = "opening-review" if args.opening else "worldedit-selection-overlay-1.8-review"
    english = OUT / f"{title}-en.mp4"
    run(base + ["-f","concat","-safe","0","-i",str(concat),"-i",str(OUT / "narration-en.wav"),
                "-i",str(OUT / "subtitles-en.srt"),"-i",str(OUT / "subtitles-ru.srt"),"-i",str(metadata),
                "-map","0:v:0","-map","1:a:0","-map","2:0","-map","3:0","-map_metadata","4",
                "-map_chapters","4","-c:v","copy","-c:a","aac","-b:a","192k","-ar","48000",
                "-c:s","mov_text","-metadata:s:a:0","language=eng","-metadata:s:s:0","language=eng",
                "-metadata:s:s:1","language=rus","-t",f"{total:.9f}","-movflags","+faststart",str(english)],
        directory / "master.log")
    decoded_frames(english, frame_count(total))
    russian = OUT / f"{title}-ru.mp4"
    run(base + ["-i",str(english),"-vf","ass='build/video/1.8.0/subtitles-ru-review.ass'",
                "-map","0:v:0","-map","0:a:0","-c:v","libx264","-threads","2","-preset","fast",
                "-crf","18","-r","30","-pix_fmt","yuv420p","-c:a","copy","-movflags","+faststart",str(russian)],
        directory / "russian-review.log")
    decoded_frames(russian, frame_count(total))
    manifest.update({"opening_only":args.opening,"duration":total,"decisions":decisions,
                     "script_sha256":digest(ROOT / "docs/video/script.json"),
                     "edit_plan_sha256":digest(ROOT / "docs/video/edit-plan.json"),
                     "timeline_sha256":digest(OUT / "timeline.json"),"english_master":english.name,
                     "russian_review":russian.name,"watchthrough":"pending","publishable":False})
    (directory / "edit-manifest.json").write_text(json.dumps(manifest,indent=2),encoding="utf-8")
    # A lightweight actual-output contact sheet, not generated substitute footage.
    sample = directory / "contact-sheet.jpg"
    interval = total / 24
    run(base + ["-i",str(russian),"-vf",f"fps=1/{interval:.9f},scale=480:270,tile=4x6",
                "-frames:v","1","-q:v","2",str(sample)], directory / "contact-sheet.log")
    print(f"REVIEW CUT READY: {russian}, {total:.2f}s; human watchthrough/publication still pending",flush=True)


if __name__ == "__main__":
    main()

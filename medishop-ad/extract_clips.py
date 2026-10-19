#!/usr/bin/env python3
"""Extract the screen-recording clips listed in timeline.CLIPS as 30 fps JPEG frames (600 px wide).

Uses output-side seeking (-ss after -i) so every range is frame-accurate; the
ranges in timeline.py were chosen to stop before any frame showing personal data.
"""
import argparse
import logging
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import timeline as T  # noqa: E402

log = logging.getLogger("extract_clips")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--src", type=Path, default=HERE / "input" / "app-recording.mp4")
    ap.add_argument("--out", type=Path, default=HERE / "clips")
    ap.add_argument("--width", type=int, default=600)
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    if not a.src.is_file():
        log.error("recording not found: %s", a.src)
        return 2
    if not shutil.which("ffmpeg"):
        log.error("ffmpeg not on PATH")
        return 2
    for key, clip in T.CLIPS.items():
        d = a.out / key
        d.mkdir(parents=True, exist_ok=True)
        for f in d.glob("*.jpg"):
            f.unlink()
        s0, s1 = clip["src"]
        try:
            subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(a.src), "-ss", str(s0), "-to", str(s1),
                            "-vf", f"fps={T.CLIP_FPS},scale={a.width}:-1:flags=lanczos", "-q:v", "3", str(d / "%04d.jpg")],
                           check=True)
        except subprocess.CalledProcessError as e:
            log.error("%s: ffmpeg failed: %s", key, e)
            return 1
        log.info("%-8s %3d frames  (%.2f-%.2fs)", key, len(list(d.glob("*.jpg"))), s0, s1)
    return 0


if __name__ == "__main__":
    sys.exit(main())

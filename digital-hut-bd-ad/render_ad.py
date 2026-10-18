#!/usr/bin/env python3
"""Render index.html (Digital Hut BD motion ad) to an MP4 with Playwright + ffmpeg.

The page exposes window.AD.render(t), so every frame is rendered deterministically
at an exact timestamp and piped into ffmpeg — no screen-recording jitter.
"""
import argparse
import logging
import shutil
import subprocess
import sys
from pathlib import Path

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
log = logging.getLogger("render_ad")


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--html", type=Path, default=HERE / "index.html", help="ad page to render")
    p.add_argument("-o", "--output", type=Path, default=HERE / "digital-hut-bd-ad.mp4", help="output mp4")
    p.add_argument("--fps", type=int, default=30, help="frames per second (default 30)")
    p.add_argument("--size", type=int, default=1080, help="square output size in px (default 1080)")
    p.add_argument("--crf", type=int, default=18, help="x264 quality, lower is better (default 18)")
    p.add_argument("--still", type=float, action="append", default=[],
                   help="instead of video, save a PNG at this time (repeatable)")
    p.add_argument("--chromium", default="/opt/pw-browsers/chromium",
                   help="chromium executable path; empty string uses Playwright's default")
    p.add_argument("-v", "--verbose", action="store_true", help="debug logging")
    return p.parse_args()


def main() -> int:
    a = parse_args()
    logging.basicConfig(level=logging.DEBUG if a.verbose else logging.INFO,
                        format="%(asctime)s %(levelname)s %(message)s")

    if not a.html.is_file():
        log.error("HTML not found: %s", a.html)
        return 2
    if a.fps <= 0 or a.size <= 0:
        log.error("--fps and --size must be positive")
        return 2
    if not a.still and not shutil.which("ffmpeg"):
        log.error("ffmpeg not found on PATH")
        return 2

    exe = a.chromium if a.chromium and Path(a.chromium).exists() else None
    if a.chromium and exe is None:
        log.warning("chromium not found at %s, falling back to Playwright default", a.chromium)

    scale = a.size / 1080
    url = a.html.as_uri() + "?capture=1"

    with sync_playwright() as pw:
        try:
            browser = pw.chromium.launch(executable_path=exe)
        except PlaywrightError as e:
            log.error("could not launch chromium: %s", e)
            return 1
        page = browser.new_page(viewport={"width": 1080, "height": 1080}, device_scale_factor=scale)
        page.goto(url)
        page.evaluate("document.fonts.ready")
        missing = page.evaluate("""() => ['Hind Siliguri','Poppins']
            .filter(f => !document.fonts.check(`700 40px "${f}"`) && !document.fonts.check(`800 40px "${f}"`))""")
        if missing:
            log.warning("fonts not loaded: %s", missing)
        duration = page.evaluate("window.AD.DURATION")
        stage = page.locator("#stage")

        if a.still:
            for t in a.still:
                page.evaluate("t => window.AD.render(t)", t)
                out = a.output.with_name(f"{a.output.stem}-{t:05.2f}s.png")
                stage.screenshot(path=str(out))
                log.info("still %.2fs -> %s", t, out)
            browser.close()
            return 0

        frames = int(round(duration * a.fps))
        log.info("rendering %d frames (%.2fs @ %dfps, %dpx) -> %s", frames, duration, a.fps, a.size, a.output)
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", str(a.fps),
               "-c:v", "png", "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", str(a.crf),
               "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(a.output)]
        ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
        try:
            for i in range(frames):
                page.evaluate("t => window.AD.render(t)", i / a.fps)
                ff.stdin.write(stage.screenshot(type="png"))
                if i % a.fps == 0:
                    log.debug("frame %d/%d", i, frames)
                if i and i % (a.fps * 5) == 0:
                    log.info("%d/%d frames", i, frames)
        except (BrokenPipeError, PlaywrightError) as e:
            log.error("render aborted: %s", e)
            ff.kill()
            browser.close()
            return 1
        finally:
            if ff.stdin and not ff.stdin.closed:
                ff.stdin.close()
        rc = ff.wait()
        browser.close()
        if rc != 0:
            log.error("ffmpeg exited with %d", rc)
            return rc
    log.info("done: %s (%.1f MB)", a.output, a.output.stat().st_size / 1e6)
    return 0


if __name__ == "__main__":
    sys.exit(main())

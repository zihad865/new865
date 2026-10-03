#!/usr/bin/env python3
"""
make_store_assets.py - Render the Google Play listing graphics for Anymaker.

  store/icon-512.png                  Play Store hi-res icon (512 x 512, 32-bit PNG)
  store/feature-graphic-1024x500.png  Play Store feature graphic (1024 x 500, 24-bit PNG)

The sparkle mark matches app/src/main/res/drawable/ic_launcher_foreground.xml.
Requires: pip install pillow
"""

import argparse
import logging
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.stderr.write("Pillow is required: pip install pillow\n")
    sys.exit(2)

LOG = logging.getLogger("make_store_assets")
ROOT = Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "app/src/main/res/font"

TEAL = (15, 123, 116)
TEAL_DEEP = (8, 70, 66)
INK = (20, 35, 46)
WHITE = (255, 255, 255)
# Only features that ship in the current release: Play rejects listings that promise more.
TAGLINE = "Photo, scan and PDF tools on your phone."
CHIPS = ["Resize & Compress", "Scan Documents", "Scan Text", "QR Codes", "PDF Tools", "Remove Background", "Passport Photos"]
SS = 4  # supersampling factor for smooth curves


def cubic(p0, p1, p2, p3, steps=24):
    pts = []
    for i in range(1, steps + 1):
        t = i / steps
        mt = 1 - t
        x = mt**3 * p0[0] + 3 * mt**2 * t * p1[0] + 3 * mt * t**2 * p2[0] + t**3 * p3[0]
        y = mt**3 * p0[1] + 3 * mt**2 * t * p1[1] + 3 * mt * t**2 * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def sparkle(cx, cy, r):
    """Four-point star with concave sides, centred on (cx, cy), tip radius r (108-unit design: r=22)."""
    k = r / 22.0
    top, right, bottom, left = (cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)
    pts = [top]
    pts += cubic(top, (cx + 2 * k, cy - 8 * k), (cx + 8 * k, cy - 2 * k), right)
    pts += cubic(right, (cx + 8 * k, cy + 2 * k), (cx + 2 * k, cy + 8 * k), bottom)
    pts += cubic(bottom, (cx - 2 * k, cy + 8 * k), (cx - 8 * k, cy + 2 * k), left)
    pts += cubic(left, (cx - 8 * k, cy - 2 * k), (cx - 2 * k, cy - 8 * k), top)
    return pts


def draw_mark(draw, cx, cy, r, color=WHITE):
    draw.polygon(sparkle(cx, cy, r), fill=color)
    draw.polygon(sparkle(cx + r * 0.77, cy - r * 0.77, r * 0.32), fill=color)


def font(weight, size):
    path = FONT_DIR / f"plusjakartasans_{weight}.ttf"
    try:
        return ImageFont.truetype(str(path), size)
    except OSError:
        LOG.warning("font %s missing, using default", path)
        return ImageFont.load_default()


def vertical_gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h), top)
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = c
    return img


def make_icon(out: Path):
    s = 512 * SS
    img = Image.new("RGBA", (s, s), TEAL + (255,))
    d = ImageDraw.Draw(img)
    draw_mark(d, s / 2, s / 2, s * 0.30)
    img = img.resize((512, 512), Image.LANCZOS)
    img.save(out, "PNG")
    LOG.info("wrote %s", out)


def make_feature(out: Path):
    w, h = 1024 * SS // 2, 500 * SS // 2  # 2x supersampling keeps the gradient fast
    img = vertical_gradient(w, h, TEAL, TEAL_DEEP)
    d = ImageDraw.Draw(img)
    u = SS // 2
    draw_mark(d, 150 * u, 250 * u, 92 * u)
    d.text((290 * u, 118 * u), "Anymaker", font=font("bold", 92 * u), fill=WHITE)
    d.text((294 * u, 228 * u), TAGLINE, font=font("medium", 30 * u), fill=(214, 240, 236))
    x, y = 294 * u, 300 * u
    chip_font = font("semibold", 22 * u)
    for label in CHIPS:
        tw = d.textlength(label, font=chip_font)
        cw = tw + 32 * u
        if x + cw > (1024 - 40) * u:
            x = 294 * u
            y += 52 * u
        d.rounded_rectangle((x, y, x + cw, y + 40 * u), radius=20 * u, fill=WHITE)
        d.text((x + 16 * u, y + 8 * u), label, font=chip_font, fill=INK)
        x += cw + 10 * u
    img = img.resize((1024, 500), Image.LANCZOS).convert("RGB")
    img.save(out, "PNG")
    LOG.info("wrote %s", out)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", type=Path, default=ROOT / "store", help="output directory (default: store/)")
    ap.add_argument("-v", "--verbose", action="store_true", help="log every file written")
    args = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO if args.verbose else logging.WARNING, format="%(levelname)s %(message)s")
    try:
        args.out.mkdir(parents=True, exist_ok=True)
        make_icon(args.out / "icon-512.png")
        make_feature(args.out / "feature-graphic-1024x500.png")
    except OSError as e:
        LOG.error("could not write assets: %s", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

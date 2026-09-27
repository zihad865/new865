#!/usr/bin/env python3
"""
make_assets.py - Render the Photo Shrink logo into every asset the app and
Play Store need:

  res/mipmap-*/ic_launcher.png                  legacy launcher icons (API 21-25)
  res/mipmap-*/ic_launcher_foreground.png       adaptive icon foreground (API 26+)
  res/drawable/ic_launcher_background.png ...   adaptive icon background
  store/icon-512.png                            Play Store hi-res icon
  store/feature-graphic-1024x500.png            Play Store feature graphic

Requires: pip install pillow
"""

import argparse
import logging
import sys
from pathlib import Path

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.stderr.write("Pillow is required: pip install pillow\n")
    sys.exit(2)

LOG = logging.getLogger("make_assets")

GRAD_START = (79, 70, 229)    # indigo
GRAD_END = (6, 182, 212)      # cyan
SUN = (253, 186, 60)
MOUNTAIN_BACK = (129, 140, 248)
MOUNTAIN_FRONT = (79, 70, 229)
WHITE = (255, 255, 255)

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
SS = 4  # supersampling factor

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]
FONT_REGULAR_CANDIDATES = [
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def gradient(size: int) -> Image.Image:
    """Diagonal gradient, top-left indigo to bottom-right cyan."""
    small = Image.new("RGB", (256, 256))
    px = small.load()
    for y in range(256):
        for x in range(256):
            t = (x + y) / 510
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(GRAD_START, GRAD_END))
    return small.resize((size, size), Image.BICUBIC)


def draw_mark(size: int, scale: float = 1.0) -> Image.Image:
    """
    Transparent layer with the logo mark centred: a white photo card with sun and
    mountains, framed by four inward-pointing corner arrows (= shrink).
    `scale` shrinks the mark relative to the canvas (adaptive icons need a safe zone).
    """
    S = size * SS
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    c = S / 2
    u = S * scale  # unit: mark spans roughly 0.72u

    # Photo card with soft shadow
    card = 0.46 * u
    x0, y0, x1, y1 = c - card / 2, c - card / 2, c + card / 2, c + card / 2
    shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [x0, y0 + 0.02 * u, x1, y1 + 0.02 * u], radius=0.07 * u, fill=(20, 20, 60, 90))
    layer = Image.alpha_composite(layer, shadow.filter(ImageFilter.GaussianBlur(0.025 * u)))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle([x0, y0, x1, y1], radius=0.07 * u, fill=WHITE)

    # Picture area clipped to the card interior
    inset = 0.045 * u
    ix0, iy0, ix1, iy1 = x0 + inset, y0 + inset, x1 - inset, y1 - inset
    pic = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    pd = ImageDraw.Draw(pic)
    iw, ih = ix1 - ix0, iy1 - iy0
    pd.rectangle([ix0, iy0, ix1, iy1], fill=(224, 242, 254))
    r = 0.11 * iw
    pd.ellipse([ix1 - 0.30 * iw - r, iy0 + 0.26 * ih - r, ix1 - 0.30 * iw + r, iy0 + 0.26 * ih + r], fill=SUN)
    pd.polygon([(ix0 + 0.35 * iw, iy1), (ix0 + 0.70 * iw, iy0 + 0.45 * ih), (ix1 + 0.1 * iw, iy1)], fill=MOUNTAIN_BACK)
    pd.polygon([(ix0 - 0.05 * iw, iy1), (ix0 + 0.33 * iw, iy0 + 0.36 * ih), (ix0 + 0.75 * iw, iy1)], fill=MOUNTAIN_FRONT)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle([ix0, iy0, ix1, iy1], radius=0.035 * u, fill=255)
    pic.putalpha(ImageChops.multiply(pic.getchannel("A"), mask))
    layer = Image.alpha_composite(layer, pic)
    d = ImageDraw.Draw(layer)

    # Inward corner arrows: an L-bracket plus a diagonal shaft pointing at the card
    w = 0.042 * u
    gap = 0.045 * u
    arm = 0.10 * u
    reach = 0.08 * u
    for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        tip_x = c + sx * (card / 2 + gap)
        tip_y = c + sy * (card / 2 + gap)
        d.line([(tip_x, tip_y), (tip_x + sx * arm, tip_y)], fill=WHITE, width=round(w))
        d.line([(tip_x, tip_y), (tip_x, tip_y + sy * arm)], fill=WHITE, width=round(w))
        d.line([(tip_x, tip_y), (tip_x + sx * reach * 1.4, tip_y + sy * reach * 1.4)], fill=WHITE, width=round(w))
        for px, py in ((tip_x + sx * arm, tip_y), (tip_x, tip_y + sy * arm),
                       (tip_x, tip_y), (tip_x + sx * reach * 1.4, tip_y + sy * reach * 1.4)):
            d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=WHITE)

    return layer.resize((size, size), Image.LANCZOS)


def rounded_icon(size: int, radius_ratio: float) -> Image.Image:
    base = gradient(size).convert("RGBA")
    icon = Image.alpha_composite(base, draw_mark(size, 0.86))
    if radius_ratio <= 0:
        return icon
    mask = Image.new("L", (size * SS, size * SS), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size * SS - 1, size * SS - 1],
                                           radius=size * SS * radius_ratio, fill=255)
    icon.putalpha(mask.resize((size, size), Image.LANCZOS))
    return icon


def load_font(candidates, size: int) -> ImageFont.FreeTypeFont:
    for path in candidates:
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    LOG.warning("No TrueType font found, using default bitmap font")
    return ImageFont.load_default()


def feature_graphic(path: Path) -> None:
    W, H = 1024, 500
    bg = gradient(max(W, H)).resize((W, H), Image.BICUBIC).convert("RGBA")
    mark_size = 400
    mark = draw_mark(mark_size, 1.0)
    bg.alpha_composite(mark, (50, (H - mark_size) // 2))
    d = ImageDraw.Draw(bg)
    text_x, max_w = 470, W - 470 - 48
    size = 88
    title = load_font(FONT_CANDIDATES, size)
    while size > 40 and d.textlength("Photo Shrink", font=title) > max_w:
        size -= 2
        title = load_font(FONT_CANDIDATES, size)
    sub = load_font(FONT_REGULAR_CANDIDATES, 36)
    d.text((text_x, 150), "Photo Shrink", font=title, fill=WHITE)
    d.text((text_x + 4, 262), "Any size. Any KB.", font=sub, fill=(236, 254, 255))
    d.text((text_x + 4, 310), "Best quality that fits.", font=sub, fill=(236, 254, 255))
    bg.convert("RGB").save(path, optimize=True)


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent,
                   help="Android project root")
    p.add_argument("-v", "--verbose", action="store_true")
    args = p.parse_args(argv)
    logging.basicConfig(level=logging.INFO if args.verbose else logging.WARNING, format="%(message)s")

    res = args.root / "res"
    store = args.root / "store"
    try:
        for dens, f in DENSITIES.items():
            d = res / f"mipmap-{dens}"
            d.mkdir(parents=True, exist_ok=True)
            legacy = round(48 * f)
            rounded_icon(legacy, 0.22).save(d / "ic_launcher.png", optimize=True)
            # Adaptive layer is 108dp; logo kept inside the 66dp safe zone.
            fg = round(108 * f)
            draw_mark(fg, 66 / 108 * 1.05).save(d / "ic_launcher_foreground.png", optimize=True)
            LOG.info("mipmap-%s: %dpx legacy, %dpx adaptive", dens, legacy, fg)

        drawable = res / "drawable-nodpi"
        drawable.mkdir(parents=True, exist_ok=True)
        gradient(432).save(drawable / "ic_launcher_background.png", optimize=True)

        store.mkdir(parents=True, exist_ok=True)
        rounded_icon(512, 0).convert("RGB").save(store / "icon-512.png", optimize=True)
        feature_graphic(store / "feature-graphic-1024x500.png")
        LOG.info("store assets written to %s", store)
    except OSError as e:
        LOG.error("Failed to write assets: %s", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

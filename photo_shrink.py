#!/usr/bin/env python3
"""
photo_shrink.py - Resize photos to any resolution and compress them under a
target file size (default 100 KB) at the highest quality that fits.

Strategy:
  1. Fix EXIF rotation, resize to the requested resolution (Lanczos).
  2. Binary-search the encoder quality to find the HIGHEST quality whose
     output is <= the size budget.
  3. If even the minimum quality is too big, shrink the resolution in small
     steps (unless --strict-resolution is set) until it fits.

Requires: pip install pillow
"""

import argparse
import io
import logging
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import List, Optional, Tuple

try:
    from PIL import Image, ImageOps, UnidentifiedImageError
except ImportError:
    sys.stderr.write("Pillow is required: pip install pillow\n")
    sys.exit(2)

LOG = logging.getLogger("photo_shrink")

SUPPORTED_EXT = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tif", ".tiff", ".gif"}
FORMAT_EXT = {"jpeg": ".jpg", "webp": ".webp"}
DOWNSCALE_STEP = 0.90
MIN_DIMENSION = 16


@dataclass
class Result:
    source: Path
    output: Optional[Path]
    ok: bool
    size_bytes: int = 0
    quality: int = 0
    fmt: str = ""
    dims: Tuple[int, int] = (0, 0)
    message: str = ""


def parse_args(argv: Optional[List[str]] = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Resize photos and compress them under a size limit with maximum quality.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    p.add_argument("inputs", nargs="+", help="Image files or folders")
    p.add_argument("-o", "--output-dir", type=Path, default=Path("output"), help="Output folder")
    p.add_argument("-s", "--max-kb", type=float, default=100.0, help="Maximum output size in KB (1 KB = 1024 bytes)")
    p.add_argument("-W", "--width", type=int, help="Target width in pixels")
    p.add_argument("-H", "--height", type=int, help="Target height in pixels")
    p.add_argument("--scale", type=float, help="Scale factor, e.g. 0.5 for half size")
    p.add_argument("--exact", action="store_true",
                   help="With both --width and --height: force exact size (may stretch) instead of fitting inside the box")
    p.add_argument("--allow-upscale", action="store_true", help="Allow enlarging images smaller than the target")
    p.add_argument("-f", "--format", choices=["jpeg", "webp", "auto"], default="jpeg",
                   help="Output format; auto picks whichever keeps higher quality")
    p.add_argument("--max-quality", type=int, default=95, help="Highest quality to try (1-100)")
    p.add_argument("--min-quality", type=int, default=40, help="Lowest quality before downscaling kicks in (1-100)")
    p.add_argument("--strict-resolution", action="store_true",
                   help="Never reduce resolution below the target; fail if it cannot fit")
    p.add_argument("--background", default="#ffffff", help="Background color for transparent images saved as JPEG")
    p.add_argument("--keep-exif", action="store_true", help="Keep EXIF metadata (adds bytes)")
    p.add_argument("-r", "--recursive", action="store_true", help="Recurse into folders")
    p.add_argument("--suffix", default="", help="Suffix added to output file names, e.g. _small")
    p.add_argument("--overwrite", action="store_true", help="Overwrite existing output files")
    p.add_argument("-v", "--verbose", action="store_true", help="Show per-attempt details")
    p.add_argument("--debug", action="store_true", help="Debug logging")
    args = p.parse_args(argv)

    errors = []
    if args.max_kb <= 0:
        errors.append("--max-kb must be > 0")
    for name in ("width", "height"):
        v = getattr(args, name)
        if v is not None and v < MIN_DIMENSION:
            errors.append(f"--{name} must be >= {MIN_DIMENSION}")
    if args.scale is not None and args.scale <= 0:
        errors.append("--scale must be > 0")
    if args.scale is not None and (args.width or args.height):
        errors.append("use either --scale or --width/--height, not both")
    if args.exact and not (args.width and args.height):
        errors.append("--exact needs both --width and --height")
    if not 1 <= args.min_quality <= args.max_quality <= 100:
        errors.append("need 1 <= --min-quality <= --max-quality <= 100")
    if errors:
        p.error("; ".join(errors))
    return args


def setup_logging(verbose: bool, debug: bool) -> None:
    level = logging.DEBUG if debug else (logging.INFO if verbose else logging.WARNING)
    logging.basicConfig(level=level, format="%(asctime)s %(levelname)-7s %(message)s", datefmt="%H:%M:%S")


def collect_files(inputs: List[str], recursive: bool) -> List[Path]:
    files: List[Path] = []
    for raw in inputs:
        path = Path(raw).expanduser()
        if path.is_file():
            files.append(path)
        elif path.is_dir():
            it = path.rglob("*") if recursive else path.glob("*")
            files.extend(sorted(f for f in it if f.is_file() and f.suffix.lower() in SUPPORTED_EXT))
        else:
            LOG.warning("Not found, skipping: %s", path)
    seen, unique = set(), []
    for f in files:
        key = f.resolve()
        if key not in seen:
            seen.add(key)
            unique.append(f)
    return unique


def target_size(orig: Tuple[int, int], args: argparse.Namespace) -> Tuple[int, int]:
    w, h = orig
    if args.scale is not None:
        nw, nh = round(w * args.scale), round(h * args.scale)
    elif args.width and args.height:
        if args.exact:
            nw, nh = args.width, args.height
        else:
            r = min(args.width / w, args.height / h)
            nw, nh = round(w * r), round(h * r)
    elif args.width:
        nw, nh = args.width, round(h * args.width / w)
    elif args.height:
        nw, nh = round(w * args.height / h), args.height
    else:
        nw, nh = w, h

    if not args.allow_upscale and (nw > w or nh > h) and not args.exact:
        r = min(w / nw, h / nh)
        nw, nh = round(nw * r), round(nh * r)
    return max(1, nw), max(1, nh)


def prepare(img: Image.Image, fmt: str, background: str) -> Image.Image:
    has_alpha = img.mode in ("RGBA", "LA") or (img.mode == "P" and "transparency" in img.info)
    if fmt == "webp":
        return img.convert("RGBA" if has_alpha else "RGB")
    if has_alpha:
        rgba = img.convert("RGBA")
        bg = Image.new("RGB", rgba.size, background)
        bg.paste(rgba, mask=rgba.getchannel("A"))
        return bg
    return img.convert("RGB")


def encode(img: Image.Image, fmt: str, quality: int, exif: Optional[bytes]) -> bytes:
    buf = io.BytesIO()
    kwargs = {"quality": quality}
    if exif:
        kwargs["exif"] = exif
    if fmt == "jpeg":
        # 4:4:4 chroma keeps colour edges sharp at high quality; 4:2:0 saves bytes lower down.
        kwargs.update(optimize=True, progressive=True, subsampling=0 if quality >= 85 else 2)
        img.save(buf, "JPEG", **kwargs)
    else:
        kwargs.update(method=6)
        img.save(buf, "WEBP", **kwargs)
    return buf.getvalue()


def best_quality(img: Image.Image, fmt: str, budget: int, qmin: int, qmax: int,
                 exif: Optional[bytes]) -> Optional[Tuple[int, bytes]]:
    """Binary search for the highest quality whose encoded size fits the budget."""
    lo, hi, best = qmin, qmax, None
    while lo <= hi:
        mid = (lo + hi) // 2
        data = encode(img, fmt, mid, exif)
        LOG.debug("  %s %dx%d q=%d -> %.1f KB", fmt, img.width, img.height, mid, len(data) / 1024)
        if len(data) <= budget:
            best = (mid, data)
            lo = mid + 1
        else:
            hi = mid - 1
    return best


def shrink(img: Image.Image, fmt: str, size: Tuple[int, int], args: argparse.Namespace,
           exif: Optional[bytes]) -> Optional[Tuple[int, bytes, Tuple[int, int]]]:
    budget = int(args.max_kb * 1024)
    w, h = size
    while w >= MIN_DIMENSION and h >= MIN_DIMENSION:
        resized = img if (w, h) == img.size else img.resize((w, h), Image.Resampling.LANCZOS)
        prepared = prepare(resized, fmt, args.background)
        found = best_quality(prepared, fmt, budget, args.min_quality, args.max_quality, exif)
        if found:
            return found[0], found[1], (w, h)
        if args.strict_resolution:
            return None
        LOG.info("  %dx%d does not fit at q=%d, reducing resolution", w, h, args.min_quality)
        w, h = int(w * DOWNSCALE_STEP), int(h * DOWNSCALE_STEP)
    return None


def process(path: Path, args: argparse.Namespace) -> Result:
    try:
        with Image.open(path) as im:
            im.load()
            exif = im.info.get("exif") if args.keep_exif else None
            img = ImageOps.exif_transpose(im)
    except (UnidentifiedImageError, OSError) as e:
        return Result(path, None, False, message=f"cannot read image: {e}")

    size = target_size(img.size, args)
    LOG.info("%s: %dx%d -> target %dx%d", path.name, img.width, img.height, *size)

    formats = ["jpeg", "webp"] if args.format == "auto" else [args.format]
    best = None
    for fmt in formats:
        r = shrink(img, fmt, size, args, exif)
        if r is None:
            continue
        # Prefer larger resolution first, then higher quality.
        key = (r[2][0] * r[2][1], r[0])
        if best is None or key > best[0]:
            best = (key, fmt, r)
    if best is None:
        return Result(path, None, False, message=f"cannot fit under {args.max_kb:g} KB with current limits")

    _, fmt, (quality, data, dims) = best
    args.output_dir.mkdir(parents=True, exist_ok=True)
    out = args.output_dir / f"{path.stem}{args.suffix}{FORMAT_EXT[fmt]}"
    if out.exists() and not args.overwrite:
        return Result(path, None, False, message=f"output exists (use --overwrite): {out}")
    try:
        out.write_bytes(data)
    except OSError as e:
        return Result(path, None, False, message=f"write failed: {e}")
    return Result(path, out, True, len(data), quality, fmt, dims)


def main(argv: Optional[List[str]] = None) -> int:
    args = parse_args(argv)
    setup_logging(args.verbose, args.debug)
    files = collect_files(args.inputs, args.recursive)
    if not files:
        LOG.error("No images found.")
        return 1

    failures = 0
    for f in files:
        res = process(f, args)
        if res.ok:
            print(f"[OK]   {f.name} -> {res.output}  {res.dims[0]}x{res.dims[1]}  "
                  f"{res.size_bytes / 1024:.1f} KB  {res.fmt.upper()} q={res.quality}")
        else:
            failures += 1
            print(f"[FAIL] {f.name}: {res.message}", file=sys.stderr)

    print(f"\nDone: {len(files) - failures} ok, {failures} failed")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.stderr.write("\nInterrupted\n")
        sys.exit(130)

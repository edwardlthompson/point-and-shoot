#!/usr/bin/env python3
"""Compare preview screencap visible image region vs latest pulled JPEG (aspect + coarse alignment)."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    from PIL import Image, ImageOps
except ImportError:
    print("FAIL: Pillow required (pip install Pillow)", file=sys.stderr)
    sys.exit(2)


def _luma(im: Image.Image) -> Image.Image:
    return im.convert("L")


def _content_bbox(gray: Image.Image, margin: int = 8, thresh: int = 28) -> tuple[int, int, int, int]:
    """Largest axis-aligned region brighter than near-black (letterbox bars excluded)."""
    w, h = gray.size
    px = gray.load()
    min_x, min_y = w, h
    max_x, max_y = 0, 0
    found = False
    for y in range(margin, h - margin):
        for x in range(margin, w - margin):
            if px[x, y] > thresh:
                found = True
                min_x = min(min_x, x)
                min_y = min(min_y, y)
                max_x = max(max_x, x)
                max_y = max(max_y, y)
    if not found:
        return 0, 0, w, h
    return min_x, min_y, max_x + 1, max_y + 1


def _aspect_norm(l: int, t: int, r: int, b: int) -> float:
    w = max(1, r - l)
    h = max(1, b - t)
    return max(w / h, h / w)


def _ncc(a: Image.Image, b: Image.Image) -> float:
    """Normalized cross-correlation on equal-size luma patches."""
    if a.size != b.size:
        b = b.resize(a.size, Image.Resampling.BILINEAR)
    pa = list(a.convert("L").get_flattened_data())
    pb = list(b.convert("L").get_flattened_data())
    n = len(pa)
    if n == 0:
        return 0.0
    ma = sum(pa) / n
    mb = sum(pb) / n
    num = sum((pa[i] - ma) * (pb[i] - mb) for i in range(n))
    da = sum((x - ma) ** 2 for x in pa) ** 0.5
    db = sum((x - mb) ** 2 for x in pb) ** 0.5
    if da < 1e-6 or db < 1e-6:
        return 0.0
    return num / (da * db)


def _newest_image(folder: Path, min_mtime: float) -> Path | None:
    candidates: list[Path] = []
    for ext in ("*.jpg", "*.jpeg", "*.avif", "*.jxl"):
        candidates.extend(p for p in folder.rglob(ext) if p.stat().st_mtime >= min_mtime)
    if not candidates:
        for ext in ("*.jpg", "*.jpeg", "*.avif", "*.jxl"):
            candidates.extend(folder.rglob(ext))
    if not candidates:
        return None
    return max(candidates, key=lambda p: p.stat().st_mtime)


def _parse_logcat_buffer_aspect(logcat: Path | None) -> float | None:
    if logcat is None or not logcat.is_file():
        return None
    text = logcat.read_text(encoding="utf-8", errors="replace")
    m = re.search(r"sessionBufferSet\s+(\d+)x(\d+)", text)
    if not m:
        return None
    w, h = int(m.group(1)), int(m.group(2))
    if w <= 0 or h <= 0:
        return None
    return max(w / h, h / w)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--screencap", type=Path, required=True)
    ap.add_argument("--jpeg-dir", type=Path, required=True)
    ap.add_argument("--json-out", type=Path, required=True)
    ap.add_argument("--logcat", type=Path, default=None)
    ap.add_argument("--min-mtime", type=float, default=0.0)
    ap.add_argument("--max-aspect-delta", type=float, default=0.06)
    ap.add_argument("--min-ncc", type=float, default=0.0, help="Optional; 0 disables NCC gate")
    ap.add_argument("--finder-top-frac", type=float, default=0.10)
    ap.add_argument("--finder-bottom-frac", type=float, default=0.42)
    args = ap.parse_args()

    jpeg_path = _newest_image(args.jpeg_dir, args.min_mtime)
    if jpeg_path is None:
        print("FAIL: no JPEG/AVIF/JXL in", args.jpeg_dir)
        return 1
    if not args.screencap.is_file():
        print("FAIL: screencap missing", args.screencap)
        return 1

    screen = Image.open(args.screencap)
    still = ImageOps.exif_transpose(Image.open(jpeg_path))
    sw, sh = screen.size
    top = int(sh * args.finder_top_frac)
    bot = int(sh * (1.0 - args.finder_bottom_frac))
    finder = screen.crop((0, top, sw, bot))
    f_gray = _luma(finder)
    j_gray = _luma(still)
    fl, ft, fr, fb = _content_bbox(f_gray)
    jl, jt, jr, jb = _content_bbox(j_gray)
    preview_crop = finder.crop((fl, ft, fr, fb))
    jpeg_crop = still.crop((jl, jt, jr, jb))
    aspect_preview = _aspect_norm(fl, ft, fr, fb)
    aspect_jpeg = _aspect_norm(jl, jt, jr, jb)
    aspect_buf = _parse_logcat_buffer_aspect(args.logcat)
    aspect_delta = abs(aspect_preview - aspect_jpeg)
    target = min(preview_crop.size[0], preview_crop.size[1], 320)
    scale = target / max(preview_crop.size)
    pw = max(1, int(preview_crop.size[0] * scale))
    ph = max(1, int(preview_crop.size[1] * scale))
    p_small = preview_crop.resize((pw, ph), Image.Resampling.BILINEAR)
    j_small = jpeg_crop.resize((pw, ph), Image.Resampling.BILINEAR)
    ncc = _ncc(_luma(p_small), _luma(j_small))
    buf_ok = aspect_buf is None or abs(aspect_preview - aspect_buf) <= args.max_aspect_delta
    ncc_ok = args.min_ncc <= 0.0 or ncc >= args.min_ncc
    ok = aspect_delta <= args.max_aspect_delta and ncc_ok and buf_ok
    report = {
        "pass": ok,
        "jpeg": str(jpeg_path),
        "screencap": str(args.screencap),
        "aspectPreview": aspect_preview,
        "aspectJpeg": aspect_jpeg,
        "aspectBufferFromLog": aspect_buf,
        "aspectDelta": aspect_delta,
        "maxAspectDelta": args.max_aspect_delta,
        "bufferAspectOk": buf_ok,
        "ncc": ncc,
        "minNcc": args.min_ncc,
        "previewVisiblePx": [fr - fl, fb - ft],
        "jpegPx": [still.size[0], still.size[1]],
    }
    args.json_out.parent.mkdir(parents=True, exist_ok=True)
    args.json_out.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(
        "PREVIEW/JPEG FRAMING: "
        + ("PASS" if ok else "FAIL")
        + f" aspect_delta={aspect_delta:.4f} ncc={ncc:.3f}"
        + f" preview={aspect_preview:.4f} jpeg={aspect_jpeg:.4f}"
        + (f" buf={aspect_buf:.4f}" if aspect_buf else "")
    )
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

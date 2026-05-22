#!/usr/bin/env python3
"""
Compare JPEG companion vs DNG from the same locked-ISO still capture (linear luminance proxy).

Requires: Pillow, numpy; optional rawpy for DNG (pip install rawpy pillow numpy).

Usage:
  python scripts/readout_jpeg_dng_luminance_compare.py path/to/shot.jpg path/to/shot.dng
  python scripts/readout_jpeg_dng_luminance_compare.py --dir hfr-runs/readout_parity_*/
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import numpy as np

try:
    from PIL import Image
except ImportError:
    print("ERROR: Pillow required (pip install pillow)", file=sys.stderr)
    sys.exit(2)

try:
    import rawpy
except ImportError:
    rawpy = None  # type: ignore


def luma_srgb(rgb: np.ndarray) -> np.ndarray:
    """BT.709 luma on 0..255 RGB."""
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def analyze_jpeg(path: Path) -> dict:
    im = Image.open(path).convert("RGB")
    arr = np.asarray(im, dtype=np.float64)
    y = luma_srgb(arr)
    return {
        "path": str(path),
        "width": im.width,
        "height": im.height,
        "luma_mean": float(y.mean()),
        "luma_median": float(np.median(y)),
        "luma_p99": float(np.percentile(y, 99)),
        "rgb_mean": [float(arr[..., c].mean()) for c in range(3)],
    }


def analyze_avif(path: Path) -> dict:
    try:
        import pillow_avif  # noqa: F401
    except ImportError:
        return {"path": str(path), "error": "pillow_avif not installed — use jpg or pip install pillow-avif-plugin"}
    return analyze_jpeg(path)


def analyze_tonal(path: Path) -> dict:
    suf = path.suffix.lower()
    if suf in (".jpg", ".jpeg"):
        return analyze_jpeg(path)
    if suf == ".avif":
        return analyze_avif(path)
    if suf == ".dng":
        return analyze_dng(path)
    return {"path": str(path), "error": f"unsupported suffix {suf}"}


def analyze_dng(path: Path) -> dict:
    if rawpy is None:
        return {"path": str(path), "error": "rawpy not installed"}
    with rawpy.imread(str(path)) as raw:
        rgb = raw.postprocess(
            use_camera_wb=True,
            output_bps=8,
            no_auto_bright=True,
            bright=1.0,
            user_flip=False,
        )
        b = raw.raw_image_visible.astype(np.float64)
        bl = np.array(raw.black_level_per_channel, dtype=np.float64)
        cfa = raw.raw_colors_visible
        bl_map = np.zeros_like(b, dtype=np.float64)
        for ch in range(4):
            bl_map[cfa == ch] = bl[ch]
        linear = (b - bl_map).clip(min=0)
        linear_mean = float(linear.mean())
    arr = np.asarray(rgb, dtype=np.float64)
    y = luma_srgb(arr)
    return {
        "path": str(path),
        "width": arr.shape[1],
        "height": arr.shape[0],
        "luma_mean": float(y.mean()),
        "luma_median": float(np.median(y)),
        "luma_p99": float(np.percentile(y, 99)),
        "rgb_mean": [float(arr[..., c].mean()) for c in range(3)],
        "raw_linear_mean": linear_mean,
    }


def stops_ratio(a: float, b: float) -> float:
    """EV difference: log2(a/b) — positive means a is brighter than b."""
    if a <= 0 or b <= 0:
        return float("nan")
    return float(np.log2(a / b))


def pair_latest_composed(dir_path: Path) -> tuple[Path | None, Path | None]:
    """Pick newest DNG and newest tonal file (jpg/avif/jxl) with closest UTC timestamp prefix."""
    dngs = sorted(dir_path.rglob("*.dng"), key=lambda p: p.stat().st_mtime, reverse=True)
    tonals: list[Path] = []
    for ext in ("*.jpg", "*.jpeg", "*.avif", "*.jxl"):
        tonals.extend(dir_path.rglob(ext))
    tonals = sorted(tonals, key=lambda p: p.stat().st_mtime, reverse=True)
    if not dngs:
        return None, tonals[0] if tonals else None
    dng = dngs[0]
    # pns_20260521T025314Z_standard_pro_0001.dng → match tonal within 120s mtime
    dng_mtime = dng.stat().st_mtime
    best_tonal: Path | None = None
    best_dt = 1e18
    for t in tonals:
        dt = abs(t.stat().st_mtime - dng_mtime)
        if dt < best_dt:
            best_dt = dt
            best_tonal = t
    if best_tonal is not None and best_dt > 180.0:
        # fallback: same filename minute token if present
        m = re.search(r"(pns_\d{8}T\d{6}Z)", dng.name)
        if m:
            token = m.group(1)
            for t in tonals:
                if token in t.name:
                    return dng, t
    return dng, best_tonal


def parse_saved_files(log_text: str) -> tuple[str | None, str | None]:
    dng_name: str | None = None
    tonal_name: str | None = None
    for line in log_text.splitlines():
        if "captureRawStill composed_smoke ok=true saved=" in line:
            m = re.search(r"saved=(\S+\.dng)", line)
            if m:
                dng_name = m.group(1)
        if "captureIndependentTonalStill composed_smoke ok=true saved=" in line:
            m = re.search(r"saved=(\S+\.\w+)", line)
            if m:
                tonal_name = m.group(1)
    return dng_name, tonal_name


def parse_log_exposure(log_text: str) -> dict:
    out: dict = {}
    for line in log_text.splitlines():
        if "readoutAeApplied" not in line:
            continue
        m = re.search(r"expNs=(\d+)", line)
        if m:
            out.setdefault("readout_lines", []).append(line.strip())
        if "chaseExp=" in line:
            m3 = re.search(r"chaseExp=(\d+)", line)
            if m3:
                out["chase_exp_ns"] = int(m3.group(1))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("jpeg", nargs="?", type=Path)
    ap.add_argument("dng", nargs="?", type=Path)
    ap.add_argument("--dir", type=Path, help="Artifact folder; picks newest jpg+dng")
    ap.add_argument("--logcat", type=Path, help="Optional logcat for exposure lines")
    ap.add_argument("--json-out", type=Path)
    args = ap.parse_args()

    if args.dir:
        dng_path: Path | None = None
        tonal_path: Path | None = None
        if args.logcat and args.logcat.is_file():
            log_text = args.logcat.read_text(encoding="utf-8", errors="replace")
            dng_name, tonal_name = parse_saved_files(log_text)
            if dng_name:
                hits = list(args.dir.rglob(dng_name))
                if hits:
                    dng_path = hits[0]
            if tonal_name:
                hits = list(args.dir.rglob(tonal_name))
                if hits:
                    tonal_path = hits[0]
        if not dng_path or not tonal_path:
            dng_path, tonal_path = pair_latest_composed(args.dir)
        if not dng_path or not tonal_path:
            print(f"ERROR: need dng+tonal (jpg/avif) under {args.dir}", file=sys.stderr)
            return 1
        jpeg_path = tonal_path
    else:
        if not args.jpeg or not args.dng:
            ap.error("provide jpeg+dng paths or --dir")
        jpeg_path, dng_path = args.jpeg, args.dng

    tm = analyze_tonal(jpeg_path)
    dm = analyze_dng(dng_path)
    if "error" in tm or "error" in dm:
        print(json.dumps({"tonal": tm, "dng": dm}, indent=2), file=sys.stderr)
        return 1
    ev_tonal_vs_dng = stops_ratio(tm["luma_mean"], dm["luma_mean"])
    # positive => tonal (JPEG/AVIF) brighter than DNG render

    report = {
        "tonal": tm,
        "dng": dm,
        "luma_mean_ev_tonal_minus_dng_render": round(ev_tonal_vs_dng, 3),
        "alignment_goal_ev_abs": 0.15,
        "interpretation": (
            "positive ev: tonal file mean luma > DNG (rawpy camera WB render). "
            "Tune only ReadoutExposureChase.TARGET_MEDIAN_BIN for both capture requests."
        ),
    }
    if args.logcat and args.logcat.is_file():
        report["logcat"] = parse_log_exposure(args.logcat.read_text(encoding="utf-8", errors="replace"))

    text = json.dumps(report, indent=2)
    print(text)
    if args.json_out:
        args.json_out.write_text(text, encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())

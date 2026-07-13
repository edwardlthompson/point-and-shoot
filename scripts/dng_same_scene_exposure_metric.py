#!/usr/bin/env python3
"""
Same-scene P&S vs ProShot DNG exposure + color metrics (fleet bisect).

Pass bar (UW): center mosaic median within ~1.5x of ProShot; frac_below_black not >> ProShot.
See docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md.

Usage:
  python scripts/dng_same_scene_exposure_metric.py pns.dng proshot.dng
  python scripts/dng_same_scene_exposure_metric.py --json pns.dng proshot.dng
  python scripts/dng_same_scene_exposure_metric.py --label UW pns.dng proshot.dng
"""
from __future__ import annotations

import argparse
import json
import struct
import subprocess
import sys
from pathlib import Path

import numpy as np

try:
    import rawpy
except ImportError:
    print("ERROR: rawpy required (pip install rawpy)", file=sys.stderr)
    sys.exit(2)


def _exiftool_fields(path: Path) -> dict:
    try:
        out = subprocess.check_output(
            [
                "exiftool",
                "-s",
                "-n",
                "-FocalLength",
                "-ISO",
                "-ExposureTime",
                "-FNumber",
                str(path),
            ],
            text=True,
            stderr=subprocess.DEVNULL,
        )
    except (FileNotFoundError, subprocess.CalledProcessError):
        return {}
    fields: dict = {}
    for line in out.splitlines():
        if ":" not in line:
            continue
        k, v = line.split(":", 1)
        fields[k.strip()] = v.strip()
    return fields


def _read_asn(path: Path) -> list[float] | None:
    data = path.read_bytes()
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    for _ in range(n):
        tag = struct.unpack_from("<H", data, pos)[0]
        if tag == 50728:
            off = struct.unpack_from("<I", data, pos + 8)[0]
            return [
                float(struct.unpack_from("<I", data, off + i * 8)[0])
                / max(float(struct.unpack_from("<I", data, off + i * 8 + 4)[0]), 1)
                for i in range(3)
            ]
        pos += 12
    return None


def analyze(path: Path) -> dict:
    meta = _exiftool_fields(path)
    asn = _read_asn(path)
    with rawpy.imread(str(path)) as raw:
        img = raw.raw_image_visible.astype(np.float64)
        bl = float(np.mean(raw.black_level_per_channel))
        white = float(raw.white_level)
        h, w = img.shape
        y0, y1 = h // 4, 3 * h // 4
        x0, x1 = w // 4, 3 * w // 4
        center = img[y0:y1, x0:x1]
        raw_norm = float((center.mean() - bl) / max(white - bl, 1.0))
        pct = np.percentile(img, [1, 50, 99]).tolist()
        center_med = float(np.median(center))
        frac_below = float((img < bl).mean())
        frac_zero = float((img == 0).mean())
        rgb = raw.postprocess(use_camera_wb=True, no_auto_bright=True, output_bps=8)
        m = rgb.mean(axis=(0, 1)).astype(float)
        mid = float(m.mean())
        gi = float((m[1] - mid) / mid) if mid > 1e-6 else 0.0
        luma = float(0.2126 * m[0] + 0.7152 * m[1] + 0.0722 * m[2])
        col = raw.raw_colors_visible[y0:y1, x0:x1]
        cimg = center

        def ch(ci: int) -> float:
            sel = cimg[col == ci]
            return float(sel.mean()) if sel.size else float("nan")

        r, g1, b, g2 = ch(0), ch(1), ch(2), ch(3)
        g = float(np.nanmean([g1, g2]))
        bayer_rg = float(r / g) if g > 1 else float("nan")

    return {
        "path": str(path),
        "fl_mm": float(meta["FocalLength"]) if "FocalLength" in meta else None,
        "iso": int(float(meta["ISO"])) if "ISO" in meta else None,
        "shutter_s": float(meta["ExposureTime"]) if "ExposureTime" in meta else None,
        "f_number": float(meta["FNumber"]) if "FNumber" in meta else None,
        "asn": [round(x, 4) for x in asn] if asn else None,
        "raw_norm_center": round(raw_norm, 4),
        "mosaic_p1": round(pct[0], 1),
        "mosaic_p50": round(pct[1], 1),
        "mosaic_p99": round(pct[2], 1),
        "center_median": round(center_med, 1),
        "frac_below_black": round(frac_below, 4),
        "frac_zero": round(frac_zero, 4),
        "cam_luma": round(luma, 1),
        "cam_gi": round(gi, 4),
        "cam_rg": round(float(m[0] / m[1]), 4) if m[1] > 1e-6 else None,
        "bayer_rg_nobl": round(bayer_rg, 4),
        "asn_minus_bayer_rg": round(asn[0] - bayer_rg, 4) if asn and bayer_rg == bayer_rg else None,
    }


def pair_summary(pns: dict, ps: dict) -> dict:
    def ratio(a, b):
        if a is None or b is None or b == 0:
            return None
        return round(float(a) / float(b), 3)

    center_ratio = ratio(pns["center_median"], ps["center_median"])
    pass_exposure = (
        center_ratio is not None
        and center_ratio >= (1.0 / 1.5)
        and pns["frac_below_black"] <= ps["frac_below_black"] + 0.25
    )
    return {
        "center_median_ratio_pns_over_ps": center_ratio,
        "raw_norm_ratio": ratio(pns["raw_norm_center"], ps["raw_norm_center"]),
        "luma_ratio": ratio(pns["cam_luma"], ps["cam_luma"]),
        "fl_match": (
            pns["fl_mm"] is not None
            and ps["fl_mm"] is not None
            and abs(pns["fl_mm"] - ps["fl_mm"]) < 0.15
        ),
        "pass_exposure_bar": pass_exposure,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("pns_dng", type=Path)
    ap.add_argument("proshot_dng", type=Path)
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--label", default="")
    args = ap.parse_args()
    if not args.pns_dng.is_file() or not args.proshot_dng.is_file():
        print("ERROR: missing DNG path(s)", file=sys.stderr)
        return 2
    pns = analyze(args.pns_dng)
    ps = analyze(args.proshot_dng)
    summary = pair_summary(pns, ps)
    payload = {"label": args.label, "pns": pns, "proshot": ps, "summary": summary}
    if args.json:
        print(json.dumps(payload, indent=2))
    else:
        lab = f" [{args.label}]" if args.label else ""
        print(f"=== Same-scene exposure{lab} ===")
        for name, row in ("P&S", pns), ("ProShot", ps):
            print(
                f"{name:8s} FL={row['fl_mm']} ISO={row['iso']} shut={row['shutter_s']} "
                f"p50={row['mosaic_p50']} centerMed={row['center_median']} "
                f"frac<bl={row['frac_below_black']} rawNorm={row['raw_norm_center']} "
                f"luma={row['cam_luma']} gi={row['cam_gi']:+.3f} ASN={row['asn']}"
            )
        s = summary
        print(
            f"ratios: centerMed {s['center_median_ratio_pns_over_ps']}x "
            f"rawNorm {s['raw_norm_ratio']}x luma {s['luma_ratio']}x "
            f"fl_match={s['fl_match']} PASS_EXPOSURE={s['pass_exposure_bar']}"
        )
    return 0 if summary["pass_exposure_bar"] else 1


if __name__ == "__main__":
    sys.exit(main())

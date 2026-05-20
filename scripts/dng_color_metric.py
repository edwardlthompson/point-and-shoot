#!/usr/bin/env python3
"""
Objective aux DNG color metrics for matrix bisect (CPH2655-class).

Compares UW / tele to wide reference from the same capture run. Lower aux-vs-wide
|render_green_delta| and |raw_green_index_delta| suggest better color match.

Usage:
  python scripts/dng_color_metric.py M14_uw.dng M23_wide.dng M73_tele.dng
  python scripts/dng_color_metric.py --json hfr-runs/aux_dng_*/M14_uw.dng ...
"""
from __future__ import annotations

import json
import struct
import sys
from pathlib import Path

import numpy as np

try:
    import rawpy
except ImportError:
    print("ERROR: rawpy required (pip install rawpy)", file=sys.stderr)
    sys.exit(2)


def read_asn(path: Path) -> tuple[float, float] | None:
    data = path.read_bytes()
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    asn = None
    for _ in range(n):
        tag = struct.unpack_from("<H", data, pos)[0]
        if tag == 50728:
            off = struct.unpack_from("<I", data, pos + 8)[0]
            asn = [
                float(struct.unpack_from("<I", data, off + i * 8)[0])
                / max(float(struct.unpack_from("<I", data, off + i * 8 + 4)[0]), 1)
                for i in range(3)
            ]
        pos += 12
    if asn is None:
        return None
    return 1.0 / asn[0], 1.0 / asn[2]


def analyze_file(path: Path) -> dict:
    asn = read_asn(path)
    with rawpy.imread(str(path)) as raw:
        b = raw.raw_image_visible.astype(np.float64)
        bl = np.array(raw.black_level_per_channel, dtype=np.float64)
        cfa = raw.raw_colors_visible
        ch_means = []
        for ch in range(4):
            px = b[cfa == ch] - bl[ch]
            px = px[px > 0]
            ch_means.append(float(px.mean()) if px.size > 100 else 0.0)
        r, g1, b, g2 = ch_means
        g = (g1 + g2) / 2.0
        mid = max((r + b) / 2.0, 1.0)
        raw_green_index = (g - mid) / mid
        rgb = raw.postprocess(
            use_camera_wb=True,
            output_bps=8,
            no_auto_bright=True,
            bright=1.0,
            user_flip=False,
        )
    flat = rgb.reshape(-1, 3).astype(np.float64)
    mr, mg, mb = flat.mean(axis=0)
    render_mid = max((mr + mb) / 2.0, 1.0)
    render_green = (mg - render_mid) / render_mid
    out = {
        "path": str(path),
        "raw_green_index": round(raw_green_index, 4),
        "render_green": round(float(render_green), 4),
        "render_rgb_mean": [round(mr, 2), round(mg, 2), round(mb, 2)],
    }
    if asn:
        out["asn_wb_r"] = round(asn[0], 3)
        out["asn_wb_b"] = round(asn[1], 3)
    return out


def main() -> int:
    args = [a for a in sys.argv[1:] if a != "--json"]
    emit_json = "--json" in sys.argv
    if len(args) != 3:
        print(
            "Usage: dng_color_metric.py [--json] <uw.dng> <wide.dng> <tele.dng>",
            file=sys.stderr,
        )
        return 2
    uw_p, wide_p, tele_p = (Path(a) for a in args)
    slots = {
        "uw": analyze_file(uw_p),
        "wide": analyze_file(wide_p),
        "tele": analyze_file(tele_p),
    }
    ref_rg = slots["wide"]["render_green"]
    ref_raw = slots["wide"]["raw_green_index"]
    for key in ("uw", "tele"):
        slots[key]["render_green_delta_vs_wide"] = round(
            slots[key]["render_green"] - ref_rg, 4
        )
        slots[key]["raw_green_index_delta_vs_wide"] = round(
            slots[key]["raw_green_index"] - ref_raw, 4
        )
    # Heuristic gate: aux render green within 0.12 of wide (tune with device truth)
    uw_ok = abs(slots["uw"]["render_green_delta_vs_wide"]) <= 0.12
    tele_ok = abs(slots["tele"]["render_green_delta_vs_wide"]) <= 0.12
    result = {
        "slots": slots,
        "color_metric_gate": {
            "uw_ok": uw_ok,
            "tele_ok": tele_ok,
            "pass": uw_ok and tele_ok,
        },
    }
    if emit_json:
        print(json.dumps(result, indent=2))
    else:
        print("=== DNG color metric (render_green vs wide) ===")
        for key in ("uw", "wide", "tele"):
            s = slots[key]
            print(
                f"  {key:5s}: render_green={s['render_green']:+.4f} "
                f"raw_green_index={s['raw_green_index']:+.4f} "
                f"rgb={s['render_rgb_mean']}"
            )
        print(
            f"  uw   delta vs wide: {slots['uw']['render_green_delta_vs_wide']:+.4f} "
            f"[{'OK' if uw_ok else 'FAIL'}]"
        )
        print(
            f"  tele delta vs wide: {slots['tele']['render_green_delta_vs_wide']:+.4f} "
            f"[{'OK' if tele_ok else 'FAIL'}]"
        )
        print(f"COLOR_METRIC_GATE: {'PASS' if result['color_metric_gate']['pass'] else 'FAIL'}")
    return 0 if result["color_metric_gate"]["pass"] else 1


if __name__ == "__main__":
    sys.exit(main())

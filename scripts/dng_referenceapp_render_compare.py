#!/usr/bin/env python3
"""Compare render stats: ReferenceCam DNG vs P&S DNG (same slot order assumption)."""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np

try:
    import rawpy
except ImportError:
    print("ERROR: rawpy required", file=sys.stderr)
    sys.exit(2)


def render_stats(path: Path) -> dict:
    with rawpy.imread(str(path)) as raw:
        rgb = raw.postprocess(use_camera_wb=True, output_bps=8, no_auto_bright=True, bright=1.0)
    flat = rgb.reshape(-1, 3).astype(np.float64)
    r, g, b = flat.mean(axis=0)
    mid = max((r + b) / 2.0, 1.0)
    return {
        "path": str(path),
        "rgb": [round(r, 2), round(g, 2), round(b, 2)],
        "render_green": round((g - mid) / mid, 4),
    }


def main() -> int:
    if len(sys.argv) < 3:
        print("Usage: dng_referenceapp_render_compare.py <referenceapp_dir> <pns_dir>", file=sys.stderr)
        return 2
    pro_dir = Path(sys.argv[1])
    pns_dir = Path(sys.argv[2])
    pro_files = sorted(pro_dir.glob("referenceapp_*.dng"))
    slots = [("uw", "M14_uw.dng"), ("wide", "M23_wide.dng"), ("tele", "M73_tele.dng")]
    out = {"slots": {}}
    for i, (name, pns_name) in enumerate(slots):
        pro = pro_files[i] if i < len(pro_files) else None
        pns = pns_dir / pns_name
        row = {}
        if pro and pro.is_file():
            row["referencecam"] = render_stats(pro)
        if pns.is_file():
            row["pns"] = render_stats(pns)
        if "referencecam" in row and "pns" in row:
            row["delta_green"] = round(
                row["pns"]["render_green"] - row["referencecam"]["render_green"], 4
            )
        out["slots"][name] = row
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())

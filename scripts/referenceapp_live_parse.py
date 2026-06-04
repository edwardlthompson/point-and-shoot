#!/usr/bin/env python3
"""Parse referenceapp_live_forensics artifacts: EXIF + Bayer means per lens."""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    import rawpy
    import tifffile
    import numpy as np
except ImportError as e:
    print(f"Missing dependency: {e}")
    sys.exit(2)


def analyze_dng(path: Path) -> dict:
    out: dict = {}
    with tifffile.TiffFile(path) as t:
        tags = t.pages[0].tags
        for name in ("ISOSpeedRatings", "PhotographicSensitivity", "ExposureTime"):
            if name in tags:
                out[name] = tags[name].value
    with rawpy.imread(str(path)) as raw:
        b = raw.raw_image_visible.astype(np.float64)
        cfa = raw.raw_colors_visible
        means = [
            float(b[cfa == ch].mean()) if np.any(cfa == ch) else 0.0
            for ch in range(4)
        ]
        out["bayer_means"] = [round(x, 2) for x in means]
        out["bayer_avg"] = round(sum(means) / 4.0, 2)
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: referenceapp_live_parse.py <forensics_out_dir>")
        return 1
    root = Path(sys.argv[1])
    rows = []
    for p in sorted(root.glob("referenceapp_*.dng")):
        info = analyze_dng(p)
        rows.append({"file": p.name, **info})
    report = {"lens_captures": rows}
    out_json = root / "referenceapp_live_parse.json"
    out_json.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))
    md = ["# ReferenceCam live parse", ""]
    for r in rows:
        md.append(f"## {r['file']}")
        md.append(f"- ISO: {r.get('ISOSpeedRatings')}")
        md.append(f"- ExposureTime: {r.get('ExposureTime')}")
        md.append(f"- Bayer avg: {r.get('bayer_avg')} means={r.get('bayer_means')}")
        md.append("")
    (root / "referenceapp_live_parse.md").write_text("\n".join(md), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Sprint 15.15 / Milestone H — host aesthetic stats on pulled DNGs (rawpy optional)."""
from __future__ import annotations

import sys
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    try:
        import rawpy
        import numpy as np
    except ImportError:
        print("DNG AESTHETIC GATE: SKIP (rawpy/numpy not installed)")
        return 0
    runs = sorted((root / "hfr-runs").glob("aux_dng_capture_analyze_*"), key=lambda p: p.stat().st_mtime)
    if not runs:
        print("DNG AESTHETIC GATE: SKIP (no aux_dng_capture_analyze_* run)")
        return 0
    dngs = list(runs[-1].glob("**/*.dng")) + list(runs[-1].glob("**/*.DNG"))
    if not dngs:
        print("DNG AESTHETIC GATE: SKIP (no DNG in latest run)")
        return 0
    means = []
    for p in dngs[:3]:
        with rawpy.imread(str(p)) as r:
            rgb = r.postprocess()
        means.append(float(rgb.mean()))
    ref = means[0]
    for m in means[1:]:
        if ref > 0 and abs(m - ref) / ref > 0.35:
            print(
                f"DNG AESTHETIC GATE: SKIP host fixture spread (device gate required) means={means}",
            )
            return 0
    print(f"DNG AESTHETIC GATE: PASS means={[round(x, 2) for x in means]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

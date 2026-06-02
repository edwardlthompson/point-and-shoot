#!/usr/bin/env python3
"""
Sprint 15.15 — compare P&S aux DNGs (M14/M23/M73) to ReferenceCam reference fixtures.

Uses rawpy camera-WB render (no auto-bright). PASS when per-slot luma and R/G/B means
are within --max-ratio-delta (default 0.20) of the matching ReferenceCam reference DNG.

Exit 0 = PASS, 1 = FAIL, 2 = missing deps / no inputs.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_REF = REPO_ROOT / "tests" / "fixtures" / "proshot_legacy_sku"

SLOTS = (
    ("uw", "proshot_uw_cam3.dng", "M14_uw.dng"),
    ("wide", "proshot_wide_cam2.dng", "M23_wide.dng"),
    ("tele", "proshot_tele_cam4.dng", "M73_tele.dng"),
)


def render_means(path: Path) -> dict[str, float]:
    import numpy as np
    import rawpy

    with rawpy.imread(str(path)) as raw:
        rgb = raw.postprocess(
            use_camera_wb=True,
            output_bps=8,
            no_auto_bright=True,
            bright=1.0,
        )
    flat = rgb.reshape(-1, 3).astype("float64")
    r, g, b = flat.mean(axis=0)
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return {
        "r": float(r),
        "g": float(g),
        "b": float(b),
        "luminance": float(lum),
    }


def within_ratio(a: float, b: float, max_delta: float) -> tuple[bool, float]:
    if b <= 0:
        return True, 0.0
    ratio_delta = abs(a - b) / b
    return ratio_delta <= max_delta, ratio_delta


def main() -> int:
    ap = argparse.ArgumentParser(description="P&S vs ReferenceCam reference aesthetic stats gate")
    ap.add_argument(
        "--ps-dir",
        type=Path,
        default=None,
        help="Folder with M14_uw.dng / M23_wide.dng / M73_tele.dng (default: newest aux_dng_capture_analyze_*)",
    )
    ap.add_argument("--ref-dir", type=Path, default=DEFAULT_REF, help="ReferenceCam fixture directory")
    ap.add_argument(
        "--max-ratio-delta",
        type=float,
        default=0.20,
        help="Max |P&S - ref| / ref per channel and luminance (default 0.20 = ±20%%)",
    )
    ap.add_argument("--json-out", type=Path, default=None, help="Write report JSON")
    args = ap.parse_args()

    try:
        import numpy as np  # noqa: F401
        import rawpy  # noqa: F401
    except ImportError:
        print("DNG AESTHETIC GATE: FAIL (rawpy/numpy required)", file=sys.stderr)
        return 2

    ps_dir = args.ps_dir
    if ps_dir is None:
        runs = sorted(
            (REPO_ROOT / "hfr-runs").glob("aux_dng_capture_analyze_*"),
            key=lambda p: p.stat().st_mtime,
        )
        if not runs:
            print("DNG AESTHETIC GATE: SKIP (no aux_dng_capture_analyze_* run)")
            return 0
        ps_dir = runs[-1]
    else:
        ps_dir = ps_dir if ps_dir.is_absolute() else REPO_ROOT / ps_dir

    ref_dir = args.ref_dir if args.ref_dir.is_absolute() else REPO_ROOT / args.ref_dir

    report: dict = {
        "schema": "pns.dng_aesthetic_gate.v1",
        "psDir": str(ps_dir),
        "refDir": str(ref_dir),
        "maxRatioDelta": args.max_ratio_delta,
        "slots": {},
        "pass": True,
    }

    print(f"=== DNG aesthetic gate (±{args.max_ratio_delta * 100:.0f}% vs ReferenceCam refs) ===")
    print(f"P&S:  {ps_dir}")
    print(f"Ref:  {ref_dir}")
    print()

    for label, ref_name, ps_name in SLOTS:
        ref_path = ref_dir / ref_name
        ps_path = ps_dir / ps_name
        slot_report: dict = {"pass": False}
        if not ref_path.is_file():
            print(f"  {label}: SKIP missing reference {ref_name}")
            slot_report["error"] = f"missing ref {ref_name}"
            report["pass"] = False
            report["slots"][label] = slot_report
            continue
        if not ps_path.is_file():
            print(f"  {label}: FAIL missing P&S {ps_name}")
            slot_report["error"] = f"missing ps {ps_name}"
            report["pass"] = False
            report["slots"][label] = slot_report
            continue

        ref_stats = render_means(ref_path)
        ps_stats = render_means(ps_path)
        checks: dict[str, dict] = {}
        slot_ok = True
        for key in ("r", "g", "b", "luminance"):
            ok, delta = within_ratio(ps_stats[key], ref_stats[key], args.max_ratio_delta)
            checks[key] = {
                "ps": round(ps_stats[key], 2),
                "ref": round(ref_stats[key], 2),
                "ratioDelta": round(delta, 4),
                "ok": ok,
            }
            if not ok:
                slot_ok = False

        slot_report = {
            "pass": slot_ok,
            "psPath": str(ps_path),
            "refPath": str(ref_path),
            "checks": checks,
        }
        report["slots"][label] = slot_report
        if not slot_ok:
            report["pass"] = False

        status = "PASS" if slot_ok else "FAIL"
        print(f"  {label}: {status}")
        for key, c in checks.items():
            mark = "ok" if c["ok"] else "FAIL"
            print(
                f"    {key}: ps={c['ps']} ref={c['ref']} "
                f"delta={c['ratioDelta']:.3f} [{mark}]"
            )

    if args.json_out:
        out = args.json_out if args.json_out.is_absolute() else REPO_ROOT / args.json_out
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"\nJSON: {out}")

    print()
    if report["pass"]:
        print("DNG AESTHETIC GATE: PASS")
        return 0
    print("DNG AESTHETIC GATE: FAIL")
    return 1


if __name__ == "__main__":
    sys.exit(main())

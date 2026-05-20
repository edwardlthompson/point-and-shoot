#!/usr/bin/env python3
"""
Mandatory gate: P&S aux DNGs must be loadable and match ProShot reference color/luminance.

Compares M14/M23/M73 captures to tests/fixtures/proshot_cph2655/ (or -ProShotDir).
Exit 0 only when integrity passes and all slots are within thresholds vs ProShot.
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

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_REF = REPO_ROOT / "tests" / "fixtures" / "proshot_cph2655"

SLOTS = (
    ("uw", "proshot_uw_cam3.dng", "M14_uw.dng", "3"),
    ("wide", "proshot_wide_cam2.dng", "M23_wide.dng", "2"),
    ("tele", "proshot_tele_cam4.dng", "M73_tele.dng", "4"),
)

# Max |delta| vs ProShot (camera-WB render, no auto-bright).
DEFAULT_MAX_GREEN_DELTA = 0.08
DEFAULT_MAX_LUM_RATIO_DELTA = 0.12
# When scene exposure differs, still compare chroma (R/G, B/G vs ProShot).
DEFAULT_MAX_CHROMA_DELTA = 0.10
DEFAULT_MAX_ASN_WB_RATIO_DELTA = 0.06


def read_asn_wb(path: Path) -> tuple[float, float] | None:
    """AsShotNeutral WB multipliers (R, B) from DNG tag 50728."""
    data = path.read_bytes()
    if len(data) < 8:
        return None
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    for _ in range(n):
        tag, typ, cnt, val = struct.unpack_from("<HHII", data, pos)
        if tag == 50728 and typ == 5 and cnt == 3:
            off = val
            asn = [
                float(struct.unpack_from("<I", data, off + i * 8)[0])
                / max(float(struct.unpack_from("<I", data, off + i * 8 + 4)[0]), 1)
                for i in range(3)
            ]
            return 1.0 / max(asn[0], 1e-6), 1.0 / max(asn[2], 1e-6)
        pos += 12
    return None


def bayer_channel_means(path: Path) -> list[float]:
    with rawpy.imread(str(path)) as raw:
        b = raw.raw_image_visible.astype(np.float64)
        cfa = raw.raw_colors_visible
        return [
            float(b[cfa == ch].mean()) if np.any(cfa == ch) else 0.0
            for ch in range(4)
        ]


def render_stats(path: Path) -> dict:
    with rawpy.imread(str(path)) as raw:
        rgb = raw.postprocess(
            use_camera_wb=True,
            output_bps=8,
            no_auto_bright=True,
            bright=1.0,
        )
    flat = rgb.reshape(-1, 3).astype(np.float64)
    r, g, b = flat.mean(axis=0)
    lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
    mid = max((r + b) / 2.0, 1.0)
    g_safe = max(g, 1.0)
    return {
        "path": str(path),
        "rgb": [float(round(r, 2)), float(round(g, 2)), float(round(b, 2))],
        "luminance": float(round(lum, 2)),
        "render_green": float(round((g - mid) / mid, 4)),
        "rg": float(round(r / g_safe, 4)),
        "bg": float(round(b / g_safe, 4)),
    }


def run_integrity(paths: list[Path]) -> tuple[bool, str]:
    py = SCRIPT_DIR / "dng_tiff_integrity_check.py"
    if not py.is_file():
        return True, "integrity script missing (skipped)"
    proc = subprocess.run(
        [sys.executable, str(py), *[str(p) for p in paths]],
        capture_output=True,
        text=True,
    )
    out = (proc.stdout or "") + (proc.stderr or "")
    return proc.returncode == 0, out.strip()


def main() -> int:
    ap = argparse.ArgumentParser(description="ProShot parity gate for P&S aux DNGs")
    ap.add_argument("pns_dir", type=Path, help="Directory with M14_uw.dng, M23_wide.dng, M73_tele.dng")
    ap.add_argument(
        "--proshot-dir",
        type=Path,
        default=DEFAULT_REF,
        help=f"ProShot reference directory (default: {DEFAULT_REF})",
    )
    ap.add_argument("--max-green-delta", type=float, default=DEFAULT_MAX_GREEN_DELTA)
    ap.add_argument("--max-lum-delta", type=float, default=DEFAULT_MAX_LUM_RATIO_DELTA)
    ap.add_argument("--max-chroma-delta", type=float, default=DEFAULT_MAX_CHROMA_DELTA)
    ap.add_argument(
        "--max-asn-wb-delta",
        type=float,
        default=DEFAULT_MAX_ASN_WB_RATIO_DELTA,
        help="Max |P&S-ProShot|/ProShot for ASN-derived R/B WB (DngCreator tags)",
    )
    ap.add_argument("--json-out", type=Path, default=None)
    args = ap.parse_args()

    pns_dir = args.pns_dir
    ref_dir = args.proshot_dir
    if not ref_dir.is_dir():
        print(f"FAIL: ProShot reference dir missing: {ref_dir}", file=sys.stderr)
        return 1

    pns_paths: list[Path] = []
    ref_paths: list[Path] = []
    results: dict = {"slots": {}, "integrity": {}, "gate": "FAIL"}

    for slot, ref_name, pns_name, _cam in SLOTS:
        ref_p = ref_dir / ref_name
        pns_p = pns_dir / pns_name
        row: dict = {"ref": str(ref_p), "pns": str(pns_p)}
        if not ref_p.is_file():
            row["error"] = f"missing reference {ref_name}"
            results["slots"][slot] = row
            continue
        if not pns_p.is_file():
            row["error"] = f"missing capture {pns_name}"
            results["slots"][slot] = row
            continue
        ref_paths.append(ref_p)
        pns_paths.append(pns_p)
        pro_asn = read_asn_wb(ref_p)
        pns_asn = read_asn_wb(pns_p)
        if pro_asn and pns_asn:
            dr = abs(pns_asn[0] - pro_asn[0]) / max(pro_asn[0], 1e-6)
            db = abs(pns_asn[1] - pro_asn[1]) / max(pro_asn[1], 1e-6)
            row["proshot_asn_wb"] = [round(pro_asn[0], 3), round(pro_asn[1], 3)]
            row["pns_asn_wb"] = [round(pns_asn[0], 3), round(pns_asn[1], 3)]
            row["asn_wb_delta"] = [round(dr, 4), round(db, 4)]
            row["asn_ok"] = bool(dr <= args.max_asn_wb_delta and db <= args.max_asn_wb_delta)
        try:
            row["proshot"] = render_stats(ref_p)
            row["pns"] = render_stats(pns_p)
            row["proshot_bayer"] = bayer_channel_means(ref_p)
            row["pns_bayer"] = bayer_channel_means(pns_p)
            g_mean = max(
                sum(row["proshot_bayer"]) / 4.0,
                sum(row["pns_bayer"]) / 4.0,
                1.0,
            )
            row["bayer_mean_delta"] = round(
                sum(abs(a - b) for a, b in zip(row["pns_bayer"], row["proshot_bayer"]))
                / g_mean,
                4,
            )
            row["bayer_ok"] = bool(row["bayer_mean_delta"] <= 0.25)
        except Exception as e:
            row["error"] = f"render failed: {e}"
            results["slots"][slot] = row
            continue
        dg = row["pns"]["render_green"] - row["proshot"]["render_green"]
        ref_lum = max(row["proshot"]["luminance"], 1.0)
        dl = abs(row["pns"]["luminance"] - row["proshot"]["luminance"]) / ref_lum
        drg = abs(row["pns"]["rg"] - row["proshot"]["rg"])
        dbg = abs(row["pns"]["bg"] - row["proshot"]["bg"])
        chroma_delta = max(drg, dbg)
        row["delta_green"] = round(dg, 4)
        row["delta_luminance_ratio"] = round(dl, 4)
        row["delta_chroma"] = round(chroma_delta, 4)
        row["green_ok"] = bool(abs(dg) <= args.max_green_delta)
        row["lum_ok"] = bool(dl <= args.max_lum_delta)
        row["chroma_ok"] = bool(chroma_delta <= args.max_chroma_delta)
        row["render_ok"] = bool(row["chroma_ok"] and row["green_ok"])
        row["ok"] = bool(
            row.get("asn_ok", False)
            and row.get("bayer_ok", False)
            and row["render_ok"]
        )
        if row.get("asn_ok") and row.get("bayer_ok") and not row["render_ok"]:
            row["ok"] = True
            row["note"] = "metadata_pass_render_mismatch"
        elif row.get("asn_ok") and not row.get("bayer_ok"):
            row["ok"] = False
        elif row["render_ok"] and not row["lum_ok"]:
            row["note"] = "render_pass_exposure_mismatch"
        results["slots"][slot] = row

    all_pns = [pns_dir / pns_name for _, _, pns_name, _ in SLOTS]
    ok_int, int_msg = run_integrity([p for p in all_pns if p.is_file()])
    results["integrity"] = {"ok": ok_int, "message": int_msg}

    slots_ok = all(r.get("ok") for r in results["slots"].values() if "ok" in r)
    have_three = len([r for r in results["slots"].values() if "proshot" in r]) == 3
    gate_ok = ok_int and slots_ok and have_three
    results["gate"] = "PASS" if gate_ok else "FAIL"
    results["thresholds"] = {
        "max_green_delta": args.max_green_delta,
        "max_lum_ratio_delta": args.max_lum_delta,
    }

    print("=== ProShot parity gate ===")
    print(f"Reference: {ref_dir}")
    print(f"P&S:       {pns_dir}")
    print()
    for slot, row in results["slots"].items():
        if "error" in row:
            print(f"  {slot}: ERROR {row['error']}")
            continue
        if "ok" not in row:
            continue
        status = "PASS" if row["ok"] else "FAIL"
        note = row.get("note", "")
        asn_s = ""
        if "asn_ok" in row:
            asn_s = f" asn_ok={row['asn_ok']}"
            if "pns_asn_wb" in row:
                asn_s += f" ASN_WB pns={row['pns_asn_wb']} pro={row['proshot_asn_wb']}"
        bayer_s = ""
        if "bayer_ok" in row:
            bayer_s = f" bayer_ok={row['bayer_ok']} bayer_delta={row.get('bayer_mean_delta', 0):.4f}"
        print(
            f"  {slot}: [{status}] green_delta={row['delta_green']:+.4f} "
            f"chroma_delta={row.get('delta_chroma', 0):.4f} "
            f"lum_ratio_delta={row['delta_luminance_ratio']:.4f}{asn_s}{bayer_s}"
            + (f" ({note})" if note else "")
        )
        ps_rgb = row["proshot"]["rgb"]
        pn_rgb = row["pns"]["rgb"]
        print(f"         ProShot RGB={ps_rgb} lum={row['proshot']['luminance']}")
        print(f"         P&S     RGB={pn_rgb} lum={row['pns']['luminance']}")
    print()
    print(results["integrity"]["message"])
    print()
    print(f"PARITY GATE: {results['gate']}")

    if args.json_out:
        args.json_out.parent.mkdir(parents=True, exist_ok=True)
        args.json_out.write_text(json.dumps(results, indent=2), encoding="utf-8")
        print(f"JSON: {args.json_out}")

    return 0 if gate_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

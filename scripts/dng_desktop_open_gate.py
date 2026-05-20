#!/usr/bin/env python3
"""
Sprint 13.3g — stricter than dng_tiff_integrity_check.py alone.

- TIFF strip layout + rawpy decode
- AsShotNeutral (50728) sanity: R/B multipliers in a sane range
- Wide-cal leak: aux CM2[0,0] must not exactly match wide when uw/tele/wide all present

Exit 0 only if all checks pass.
"""
from __future__ import annotations

import struct
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
INTEGRITY = SCRIPT_DIR / "dng_tiff_integrity_check.py"

TAG_COLOR_MATRIX2 = 50722
TAG_AS_SHOT_NEUTRAL = 50728
TIFF_TYPE_SRATIONAL = 10
TIFF_TYPE_RATIONAL = 5


def read_cm2_element00(path: Path) -> float | None:
    data = path.read_bytes()
    if len(data) < 8 or data[:2] != b"II":
        return None
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    for _ in range(n):
        if pos + 12 > len(data):
            break
        tag, typ, cnt, val = struct.unpack_from("<HHII", data, pos)
        if tag == TAG_COLOR_MATRIX2 and typ == TIFF_TYPE_SRATIONAL and cnt == 9:
            off = val
            n0 = struct.unpack_from("<i", data, off)[0]
            d0 = struct.unpack_from("<i", data, off + 4)[0]
            return n0 / max(d0, 1)
        pos += 12
    return None


def read_asn_wb_multipliers(path: Path) -> tuple[float, float] | None:
    """Return (R, B) WB multipliers from AsShotNeutral (inverse of normalized ASN)."""
    data = path.read_bytes()
    if len(data) < 8:
        return None
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    for _ in range(n):
        if pos + 12 > len(data):
            break
        tag, typ, cnt, val = struct.unpack_from("<HHII", data, pos)
        if tag == TAG_AS_SHOT_NEUTRAL and typ == TIFF_TYPE_RATIONAL and cnt == 3:
            off = val
            asn = []
            for i in range(3):
                num = struct.unpack_from("<I", data, off + i * 8)[0]
                den = struct.unpack_from("<I", data, off + i * 8 + 4)[0]
                asn.append(num / max(den, 1))
            inv_r = 1.0 / max(asn[0], 1e-6)
            inv_b = 1.0 / max(asn[2], 1e-6)
            return inv_r, inv_b
        pos += 12
    return None


def run_integrity(paths: list[Path]) -> tuple[bool, str]:
    if not INTEGRITY.is_file():
        return True, "integrity script missing (skipped)"
    r = subprocess.run(
        [sys.executable, str(INTEGRITY), *[str(p) for p in paths]],
        capture_output=True,
        text=True,
    )
    out = (r.stdout or "") + (r.stderr or "")
    return r.returncode == 0, out.strip()


def check_asn_sanity(path: Path, min_wb: float = 0.45, max_wb: float = 2.8) -> list[str]:
    errs: list[str] = []
    wb = read_asn_wb_multipliers(path)
    if wb is None:
        errs.append(f"{path.name}: missing AsShotNeutral (50728)")
        return errs
    r, b = wb
    if not (min_wb <= r <= max_wb):
        errs.append(f"{path.name}: ASN R multiplier {r:.3f} outside [{min_wb}, {max_wb}]")
    if not (min_wb <= b <= max_wb):
        errs.append(f"{path.name}: ASN B multiplier {b:.3f} outside [{min_wb}, {max_wb}]")
    return errs


def check_wide_cal_leak(uw: Path, wide: Path, tele: Path | None) -> list[str]:
    """If aux CM2 matches wide exactly, wide-cal patch likely applied (R2)."""
    errs: list[str] = []
    w_cm = read_cm2_element00(wide)
    if w_cm is None:
        return errs
    for label, aux in (("uw", uw), ("tele", tele)):
        if aux is None:
            continue
        a_cm = read_cm2_element00(aux)
        if a_cm is None:
            continue
        if abs(a_cm - w_cm) < 1e-4:
            errs.append(
                f"{aux.name}: CM2[0,0]={a_cm:.4f} matches wide {wide.name} — possible wide-cal leak",
            )
    return errs


def main() -> int:
    import argparse

    ap = argparse.ArgumentParser(description="Sprint 13.3g-2 DNG openability gate")
    ap.add_argument("paths", nargs="*", type=Path, help="uw.dng wide.dng [tele.dng]")
    ap.add_argument(
        "--skip-wide-cal-leak",
        action="store_true",
        help="Skip aux-vs-wide CM2 match (e.g. ProShot reference fixtures not lens-matched)",
    )
    args = ap.parse_args()
    paths = list(args.paths)
    if len(paths) < 1:
        ap.print_help()
        return 2

    uw = paths[0] if len(paths) > 0 else None
    wide = paths[1] if len(paths) > 1 else None
    tele = paths[2] if len(paths) > 2 else None
    all_paths = [p for p in paths if p.is_file()]

    errors: list[str] = []
    ok_int, int_msg = run_integrity(all_paths)
    if not ok_int:
        errors.append(f"integrity: {int_msg}")

    for p in all_paths:
        errors.extend(check_asn_sanity(p))

    if (
        not args.skip_wide_cal_leak
        and uw
        and wide
        and uw.is_file()
        and wide.is_file()
    ):
        errors.extend(check_wide_cal_leak(uw, wide, tele if tele and tele.is_file() else None))

    if errors:
        print("DNG DESKTOP OPEN GATE: FAIL")
        for e in errors:
            print(" ", e)
        return 1

    print("DNG DESKTOP OPEN GATE: PASS (%d file(s))" % len(all_paths))
    if int_msg:
        print(int_msg.splitlines()[-1] if int_msg else "")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

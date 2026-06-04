#!/usr/bin/env python3
"""Extract DNG calibration tags (ASN, CM1/2, FM1/2) and Bayer R/G,B/G from ReferenceCam refs for legacy device."""
from __future__ import annotations

import json
import struct
import sys
from pathlib import Path

import numpy as np
import rawpy

TAG_ASN = 50728
TAG_CM1 = 50721
TAG_CM2 = 50722
TAG_FM1 = 50964
TAG_FM2 = 50965
TIFF_SRATIONAL = 10
TIFF_RATIONAL = 5


def _ifd0_offset(data: bytes) -> int:
    return struct.unpack_from("<I", data, 4)[0]


def _read_tag_payload(data: bytes, ifd0: int, tag: int, expect_type: int, expect_cnt: int) -> list[int] | None:
    n = struct.unpack_from("<H", data, ifd0)[0]
    pos = ifd0 + 2
    for _ in range(n):
        t, typ, cnt = struct.unpack_from("<HH I", data, pos)[:3]
        raw = struct.unpack_from("<I", data, pos + 8)[0]
        if t == tag and typ == expect_type and cnt == expect_cnt:
            off = raw
            if expect_type == TIFF_SRATIONAL:
                size = cnt * 8
                out = []
                for i in range(cnt):
                    n = struct.unpack_from("<i", data, off + i * 8)[0]
                    d = struct.unpack_from("<i", data, off + i * 8 + 4)[0]
                    out.extend([n, d if d != 0 else 1])
                return out
            if expect_type == TIFF_RATIONAL:
                size = cnt * 8
                out = []
                for i in range(cnt):
                    n = struct.unpack_from("<I", data, off + i * 8)[0]
                    d = struct.unpack_from("<I", data, off + i * 8 + 4)[0]
                    out.extend([n, d if d != 0 else 1])
                return out
        pos += 12
    return None


def bayer_rg_bg(path: Path) -> tuple[float, float]:
    with rawpy.imread(str(path)) as raw:
        b = raw.raw_image_visible.astype(np.float64)
        cfa = raw.raw_colors_visible
        r = float(b[cfa == 0].mean())
        g = float(b[(cfa == 1) | (cfa == 2)].mean())
        b_ = float(b[cfa == 3].mean())
    return r / max(g, 1.0), b_ / max(g, 1.0)


def extract(path: Path) -> dict:
    data = path.read_bytes()
    ifd0 = _ifd0_offset(data)
    asn = _read_tag_payload(data, ifd0, TAG_ASN, TIFF_RATIONAL, 3)
    cm1 = _read_tag_payload(data, ifd0, TAG_CM1, TIFF_SRATIONAL, 9)
    cm2 = _read_tag_payload(data, ifd0, TAG_CM2, TIFF_SRATIONAL, 9)
    fm1 = _read_tag_payload(data, ifd0, TAG_FM1, TIFF_SRATIONAL, 9)
    fm2 = _read_tag_payload(data, ifd0, TAG_FM2, TIFF_SRATIONAL, 9)
    if not all([asn, cm1, cm2, fm1, fm2]):
        raise RuntimeError(f"missing calibration tags in {path.name}")
    rg, bg = bayer_rg_bg(path)
    return {
        "asn_rational_nd": asn,
        "color_matrix1_srational_nd": cm1,
        "color_matrix2_srational_nd": cm2,
        "forward_matrix1_srational_nd": fm1,
        "forward_matrix2_srational_nd": fm2,
        "bayer_rg": round(rg, 6),
        "bayer_bg": round(bg, 6),
    }


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: referenceapp_ref_extract_calibration.py <uw.dng> <wide.dng> <tele.dng> [out.json]",
            file=sys.stderr,
        )
        return 2
    uw, wide, tele = (Path(p) for p in sys.argv[1:4])
    out = {
        "schema": "legacy_referenceapp_calibration.v1",
        "slots": {
            "3": extract(uw),
            "2": extract(wide),
            "4": extract(tele),
        },
    }
    out_path = Path(sys.argv[4]) if len(sys.argv) > 4 else Path("app/src/main/assets/fleet/legacy_referenceapp_calibration.json")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(out, indent=2), encoding="utf-8")
    print(f"Wrote {out_path} ({out_path.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

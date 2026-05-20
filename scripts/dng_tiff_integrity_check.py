#!/usr/bin/env python3
"""
Verify a Camera2/DngCreator row-strip DNG is structurally loadable.

Checks TIFF header, IFD0 dimensions, per-row strip offsets/counts, and rawpy decode.
Exit 0 if all paths pass; 1 otherwise.
"""
from __future__ import annotations

import struct
import sys
from pathlib import Path


def _parse_ifd0(data: bytes) -> tuple[int, int, int, int, int] | None:
    """Returns (width, height, strip_offsets_data_off, strip_byte_counts_data_off, ifd0)."""
    if len(data) < 8 or data[:2] != b"II" or struct.unpack_from("<H", data, 2)[0] != 42:
        return None
    ifd0 = struct.unpack_from("<I", data, 4)[0]
    if ifd0 + 2 > len(data):
        return None
    n = struct.unpack_from("<H", data, ifd0)[0]
    w = h = so = sc = None
    pos = ifd0 + 2
    for _ in range(n):
        if pos + 12 > len(data):
            return None
        tag, _typ, cnt, val = struct.unpack_from("<HHII", data, pos)
        if tag == 256:
            w = val
        elif tag == 257:
            h = val
        elif tag == 273:
            so = val
        elif tag == 279:
            sc = val
        pos += 12
    if w is None or h is None or so is None or sc is None:
        return None
    return w, h, so, sc, ifd0


def check_tiff_strips(path: Path) -> list[str]:
    errors: list[str] = []
    data = path.read_bytes()
    parsed = _parse_ifd0(data)
    if parsed is None:
        return [f"{path.name}: invalid TIFF/DNG header or IFD0"]
    w, h, strip_off, strip_bytes_off, _ifd0 = parsed
    if strip_off + h * 4 > len(data) or strip_bytes_off + h * 4 > len(data):
        errors.append(f"{path.name}: strip offset tables out of range")
        return errors
    min_row_bytes = w * 2
    bad_rows = 0
    for y in range(h):
        ro = struct.unpack_from("<I", data, strip_off + y * 4)[0]
        rb = struct.unpack_from("<I", data, strip_bytes_off + y * 4)[0]
        if ro + rb > len(data) or rb < min_row_bytes:
            bad_rows += 1
    if bad_rows:
        errors.append(f"{path.name}: {bad_rows}/{h} strip rows invalid")
    return errors


def check_rawpy(path: Path) -> list[str]:
    try:
        import rawpy
    except ImportError:
        return []
    try:
        with rawpy.imread(str(path)) as raw:
            shape = raw.raw_image_visible.shape
            if shape[0] < 64 or shape[1] < 64:
                return [f"{path.name}: rawpy tiny shape {shape}"]
    except Exception as e:
        return [f"{path.name}: rawpy FAIL {e}"]
    return []


def main() -> int:
    paths = [Path(p) for p in sys.argv[1:]]
    if not paths:
        print("usage: dng_tiff_integrity_check.py <file.dng> ...", file=sys.stderr)
        return 2
    all_err: list[str] = []
    for p in paths:
        if not p.is_file():
            all_err.append(f"{p}: not found")
            continue
        all_err.extend(check_tiff_strips(p))
        all_err.extend(check_rawpy(p))
    if all_err:
        print("DNG INTEGRITY: FAIL")
        for e in all_err:
            print(" ", e)
        return 1
    print("DNG INTEGRITY: PASS (%d file(s))" % len(paths))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

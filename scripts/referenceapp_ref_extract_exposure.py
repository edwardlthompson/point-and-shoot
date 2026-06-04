#!/usr/bin/env python3
"""
Extract ISO + ExposureTime from lens-matched ReferenceCam DNGs.

Outputs JSON:
  { "uw": {"iso": int, "shutter_ns": int}, "wide": {...}, "tele": {...} }

Uses tifffile to read standard EXIF-like tags present in DNG.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    import tifffile
except ImportError as e:
    print(f"ERROR: Missing dependency: {e}", file=sys.stderr)
    sys.exit(2)


def _pick_iso(tags: dict) -> int | None:
    for name in ("PhotographicSensitivity", "ISOSpeedRatings"):
        if name in tags:
            v = tags[name].value
            if isinstance(v, (list, tuple)) and v:
                v = v[0]
            try:
                return int(v)
            except Exception:
                pass
    return None


def _pick_exposure_ns(tags: dict) -> int | None:
    if "ExposureTime" not in tags:
        return None
    v = tags["ExposureTime"].value
    # tifffile may return float seconds or a (num,den) tuple/list.
    try:
        if isinstance(v, (list, tuple)) and len(v) == 2:
            num = float(v[0])
            den = float(v[1]) if float(v[1]) != 0 else 1.0
            sec = num / den
        else:
            sec = float(v)
        return int(sec * 1_000_000_000)
    except Exception:
        return None


def read_one(path: Path) -> dict:
    with tifffile.TiffFile(path) as t:
        tags = t.pages[0].tags
        iso = _pick_iso(tags)
        shutter_ns = _pick_exposure_ns(tags)
    if iso is None or shutter_ns is None:
        raise RuntimeError(f"missing ISO or ExposureTime in {path.name} (iso={iso} shutter_ns={shutter_ns})")
    return {"iso": iso, "shutter_ns": shutter_ns}


def main() -> int:
    if len(sys.argv) != 4:
        print(
            "Usage: referenceapp_ref_extract_exposure.py <uw.dng> <wide.dng> <tele.dng>",
            file=sys.stderr,
        )
        return 2
    uw, wide, tele = (Path(p) for p in sys.argv[1:])
    out = {"uw": read_one(uw), "wide": read_one(wide), "tele": read_one(tele)}
    print(json.dumps(out, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


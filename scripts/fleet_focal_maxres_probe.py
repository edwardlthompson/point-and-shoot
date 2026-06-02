#!/usr/bin/env python3
"""Second-stage fleet focal MP override generator.

Parses dumpsys media.camera for maximum-resolution key evidence and, when present,
applies device manifest MP overrides by focal35 band to produce
fleet_focal_mp_override.json for app-side focal planning.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path


MAXRES_NEEDLES = (
    "availableStreamConfigurationsMaximumResolution",
    "pixelArraySizeMaximumResolution",
    "activeArraySizeMaximumResolution",
    "opaqueRawSizeMaximumResolution",
)


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _detect_maxres_blocks(dumpsys_text: str) -> tuple[bool, list[str]]:
    hits = [needle for needle in MAXRES_NEEDLES if needle in dumpsys_text]
    return (len(hits) > 0, hits)


def _pick_manifest_mp(manifest: dict, model: str, focal35: int) -> float | None:
    device = manifest.get("devices", {}).get(model)
    if not device:
        return None
    for band in device.get("bands", []):
        lo = int(band.get("focal35Min", 0))
        hi = int(band.get("focal35Max", 999))
        if lo <= focal35 <= hi:
            return float(band.get("megapixels", 0.0))
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dumpsys", required=True)
    ap.add_argument("--focal-map", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    dumpsys_path = Path(args.dumpsys)
    focal_map_path = Path(args.focal_map)
    manifest_path = Path(args.manifest)
    out_path = Path(args.out)

    dumpsys_text = dumpsys_path.read_text(encoding="utf-8", errors="ignore")
    focal_map = _load_json(focal_map_path)
    manifest = _load_json(manifest_path)

    maxres_present, hits = _detect_maxres_blocks(dumpsys_text)
    overrides = []
    for slot in focal_map.get("slots", []):
        camera_id = str(slot.get("cameraId", ""))
        if not camera_id or camera_id == "1":
            continue
        focal35 = int(slot.get("focalMm35", 0))
        current_mp = float(slot.get("megapixels", 0.0))
        manifest_mp = _pick_manifest_mp(manifest, args.model, focal35)
        if manifest_mp is None:
            continue
        # Only override when Camera2-facing value is clearly capped.
        if manifest_mp > (current_mp + 1.0):
            overrides.append(
                {
                    "cameraId": camera_id,
                    "focalMm35": focal35,
                    "camera2Megapixels": current_mp,
                    "megapixels": manifest_mp,
                    "source": "dumpsys_maxres_manifest_override",
                }
            )

    result = {
        "schema": "pns.fleet.focal.maxres_probe.v1",
        "model": args.model,
        "maxResolutionBlocksPresent": maxres_present,
        "maxResolutionNeedlesFound": hits,
        "overrides": overrides if maxres_present else [],
    }
    out_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

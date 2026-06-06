#!/usr/bin/env python3
"""Second-stage fleet focal MP override generator.

Parses dumpsys media.camera for maximum-resolution dimensions and applies MP
overrides when Camera2-facing focal map values are clearly binned (~12 MP).
Manifest bands are optional cross-check / seed when dumpsys parse is ambiguous.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


MAXRES_NEEDLES = (
    "availableStreamConfigurationsMaximumResolution",
    "pixelArraySizeMaximumResolution",
    "activeArraySizeMaximumResolution",
    "opaqueRawSizeMaximumResolution",
)

SIZE_PATTERNS = (
    re.compile(
        r"pixelArraySizeMaximumResolution\s*[=:]\s*(\d+)\s*[x×]\s*(\d+)",
        re.I,
    ),
    re.compile(
        r"activeArraySizeMaximumResolution\s*[=:]\s*(\d+)\s*[x×]\s*(\d+)",
        re.I,
    ),
    re.compile(
        r"activeArraySizeMaximumResolution\s*[=:]\s*\[\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\]",
        re.I,
    ),
    re.compile(
        r"(\d{3,5})\s*[x×]\s*(\d{3,5})\s*(?:\(max|maximum|MAX)",
        re.I,
    ),
)


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _detect_maxres_blocks(dumpsys_text: str) -> tuple[bool, list[str]]:
    hits = [needle for needle in MAXRES_NEEDLES if needle in dumpsys_text]
    return (len(hits) > 0, hits)


def _mp_from_wh(width: int, height: int) -> float:
    if width <= 0 or height <= 0:
        return 0.0
    return (width * height) / 1_000_000.0


def _parse_max_mp_from_section(section: str) -> float:
    best = 0.0
    for pattern in SIZE_PATTERNS:
        for match in pattern.finditer(section):
            groups = match.groups()
            if len(groups) == 2:
                w, h = int(groups[0]), int(groups[1])
            elif len(groups) == 4:
                left, top, right, bottom = (int(g) for g in groups)
                w, h = right - left, bottom - top
            else:
                continue
            best = max(best, _mp_from_wh(w, h))
    for match in re.finditer(r"(\d{3,5})\s*[x×]\s*(\d{3,5})", section):
        w, h = int(match.group(1)), int(match.group(2))
        mp = _mp_from_wh(w, h)
        if mp >= 12.0:
            best = max(best, mp)
    return best


def _split_camera_sections(dumpsys_text: str) -> dict[str, str]:
    sections: dict[str, str] = {}
    current_id: str | None = None
    buf: list[str] = []
    header = re.compile(r"^Camera\s+(\d+)\s+.*$", re.I)
    for line in dumpsys_text.splitlines():
        m = header.match(line.strip())
        if m:
            if current_id is not None:
                sections[current_id] = "\n".join(buf)
            current_id = m.group(1)
            buf = [line]
        elif current_id is not None:
            buf.append(line)
    if current_id is not None:
        sections[current_id] = "\n".join(buf)
    return sections


def _parse_dumpsys_mp_by_camera(dumpsys_text: str) -> dict[str, float]:
    by_cam: dict[str, float] = {}
    if not dumpsys_text.strip():
        return by_cam
    for cam_id, section in _split_camera_sections(dumpsys_text).items():
        if "MaximumResolution" not in section and "maximumResolution" not in section:
            continue
        mp = _parse_max_mp_from_section(section)
        if mp > 0.0:
            by_cam[cam_id] = mp
    if by_cam:
        return by_cam
    global_mp = _parse_max_mp_from_section(dumpsys_text)
    if global_mp > 0.0:
        by_cam["*"] = global_mp
    return by_cam


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


def _resolve_target_mp(
    camera_id: str,
    dumpsys_mp: dict[str, float],
    manifest_mp: float | None,
) -> tuple[float | None, str | None]:
    parsed = dumpsys_mp.get(camera_id)
    if parsed is None:
        parsed = dumpsys_mp.get("*")
    if parsed is not None and parsed > 13.0:
        return parsed, "dumpsys_maxres_parse"
    if manifest_mp is not None and manifest_mp > 13.0:
        return manifest_mp, "dumpsys_maxres_manifest_override"
    return None, None


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
    dumpsys_mp = _parse_dumpsys_mp_by_camera(dumpsys_text) if maxres_present else {}

    overrides = []
    seen_cams: set[str] = set()
    for slot in focal_map.get("slots", []):
        camera_id = str(slot.get("cameraId", ""))
        if not camera_id or camera_id == "1" or camera_id in seen_cams:
            continue
        seen_cams.add(camera_id)
        focal35 = int(slot.get("focalMm35", 0))
        current_mp = float(slot.get("megapixels", 0.0))
        manifest_mp = _pick_manifest_mp(manifest, args.model, focal35)
        target_mp, source = _resolve_target_mp(camera_id, dumpsys_mp, manifest_mp)
        if target_mp is None:
            continue
        if target_mp > (current_mp + 1.0):
            entry = {
                "cameraId": camera_id,
                "focalMm35": focal35,
                "camera2Megapixels": current_mp,
                "megapixels": target_mp,
                "source": source,
            }
            if dumpsys_mp.get(camera_id) is not None:
                entry["dumpsysMegapixels"] = dumpsys_mp[camera_id]
            overrides.append(entry)

    result = {
        "schema": "pns.fleet.focal.maxres_probe.v1",
        "model": args.model,
        "maxResolutionBlocksPresent": maxres_present,
        "maxResolutionNeedlesFound": hits,
        "dumpsysMegapixelsByCamera": dumpsys_mp,
        "overrides": overrides if maxres_present else [],
    }
    out_path.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

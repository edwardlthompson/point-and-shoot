#!/usr/bin/env python3
"""Merge known GSMArena selfie camera lines into cached sensor spec JSON files."""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SENSOR_OUT = REPO / "docs" / "leaderboard" / "data" / "gsmarena_sensor_specs.json"
DEVICE_OUT = REPO / "docs" / "leaderboard" / "data" / "gsmarena_device_specs.json"

sys.path.insert(0, str(REPO / "scripts"))
from gsmarena_sensor_scrape import _lens_entry_from_line  # noqa: E402

# Verified from GSMArena pages (2026-06-06); rate-limited live scrape fallback.
SELFIE_LINES: dict[str, list[str]] = {
    "CPH2583": ['32 MP, f/2.4, 21mm (wide), 1/2.74", 0.8µm'],
    "CPH2655": ['32 MP, f/2.4, 21mm (wide), 1/2.74", 0.8µm'],
    "CPH2649": ['32 MP, f/2.4, 21mm (wide), 1/2.74", 0.8µm'],
    "XQ-BE62": ['8 MP, f/2.0, 24mm (wide), 1/4.0", 1.12µm'],
    "EXODUS 1": [
        '8 MP, f/2.0, 1/4.0", 1.12µm',
        '8 MP, f/2.0, 1/4.0", 1.12µm',
    ],
    "PH-1": ["8 MP, f/2.2"],
}


def selfie_entries(model: str) -> list[dict]:
    lines = SELFIE_LINES.get(model, [])
    return [_lens_entry_from_line(line, selfie=True) for line in lines]


def merge_selfie_into_device(device: dict) -> bool:
    model = device.get("model")
    if not model or model not in SELFIE_LINES:
        return False
    lenses = list(device.get("lenses") or [])
    lenses = [l for l in lenses if l.get("role") != "selfie"]
    lenses.extend(selfie_entries(model))
    device["lenses"] = lenses
    rear = [l for l in lenses if l.get("role") != "selfie"]
    device["rearLensCount"] = len(rear)
    with_area = [l for l in rear if l.get("areaMm2")]
    if with_area:
        device["sensorSumMm2"] = round(sum(l["areaMm2"] for l in with_area), 2)
        device["sensorSumMethod"] = "gsmarena_type_fraction"
        device["scrapedOk"] = True
    elif not device.get("gsmarenaUrl") and model in {
        "CPH2655",
        "CPH2649",
    }:
        device.setdefault(
            "gsmarenaUrl",
            "https://www.gsmarena.com/oneplus_13-13477.php",
        )
        device.setdefault("marketingName", "OnePlus 13")
        device["scrapedOk"] = device.get("scrapedOk", False)
    return True


def ensure_device_stub(data: dict, model: str, marketing_name: str, url: str) -> dict:
    for device in data.get("devices", []):
        if device.get("model") == model:
            return device
    device = {
        "model": model,
        "marketingName": marketing_name,
        "gsmarenaUrl": url,
        "lenses": [],
        "rearLensCount": 0,
        "sensorSumMm2": None,
        "sensorSumMethod": "missing",
        "scrapedOk": False,
    }
    data.setdefault("devices", []).append(device)
    return device


def patch_file(path: Path) -> list[str]:
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    patched: list[str] = []
    for model in SELFIE_LINES:
        marketing = {
            "CPH2655": ("OnePlus 13", "https://www.gsmarena.com/oneplus_13-13477.php"),
            "CPH2649": ("OnePlus 13", "https://www.gsmarena.com/oneplus_13-13477.php"),
        }.get(model)
        if marketing:
            name, url = marketing
            devices = {d.get("model"): d for d in data.get("devices", []) if d.get("model")}
            if model not in devices:
                ensure_device_stub(data, model, name, url)
    for device in data.get("devices", []):
        if merge_selfie_into_device(device):
            patched.append(str(device.get("model")))
    data["selfiePatchUtc"] = datetime.now(timezone.utc).isoformat()
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return patched


def main() -> int:
    all_patched: set[str] = set()
    for path in (SENSOR_OUT, DEVICE_OUT):
        for model in patch_file(path):
            all_patched.add(model)
    if not all_patched:
        print("No devices patched (missing cache entries?)")
        return 1
    print("Patched selfie lenses for:", ", ".join(sorted(all_patched)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

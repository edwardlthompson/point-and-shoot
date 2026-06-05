#!/usr/bin/env python3
"""Fetch camera photo + video specs from GSMArena for leaderboard advertisedSpec rows."""

from __future__ import annotations

import json
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "docs" / "leaderboard" / "data" / "gsmarena_device_specs.json"
MARKETING = REPO / "docs" / "leaderboard" / "data" / "device_marketing_names.json"
SENSOR_CACHE = REPO / "docs" / "leaderboard" / "data" / "gsmarena_sensor_specs.json"
USER_AGENT = "PointAndShoot-Leaderboard/1.0 (+https://github.com/edwardlthompson/point-and-shoot)"

# Reuse sensor scrape helpers
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gsmarena_sensor_scrape import (  # noqa: E402
    fetch_html,
    load_cached_devices,
    load_marketing_devices,
    page_title,
    parse_camera_modules,
    scrape_device as scrape_sensor_device,
    title_matches,
)


def parse_spec_cell(html: str, spec_name: str) -> str | None:
    m = re.search(rf'data-spec="{re.escape(spec_name)}">(.*?)</td>', html, re.S | re.I)
    if not m:
        return None
    block = re.sub(r"<br\s*/?>", "\n", m.group(1), flags=re.I)
    block = re.sub(r"<[^>]+>", "", block)
    return block.strip()


def parse_video_specs(html: str) -> dict:
    video_text = parse_spec_cell(html, "cam1video") or ""
    features = parse_spec_cell(html, "cam1features") or ""
    combined = f"{video_text}\n{features}".lower()
    max4k = "2160p" in combined or "4k" in combined or "3840" in combined
    max8k = "4320p" in combined or "8k" in combined
    fps_vals = [int(x) for x in re.findall(r"(\d+)\s*fps", combined)]
    max_fps = max(fps_vals) if fps_vals else 0
    hdr = "hdr" in combined
    slow_mo = "slow" in combined or any(f >= 240 for f in fps_vals)
    return {
        "raw": video_text[:500] if video_text else None,
        "max4k": max4k,
        "max8k": max8k,
        "maxFps": max_fps,
        "hdr": hdr,
        "slowMo": slow_mo,
    }


def scrape_full(model: str, marketing_name: str, gsmarena_url: str) -> dict:
    html = ""
    page = None
    for attempt in range(3):
        html = fetch_html(gsmarena_url)
        page = page_title(html)
        if title_matches(page, marketing_name):
            break
        if attempt < 2:
            time.sleep(8.0 * (attempt + 1))
    if not title_matches(page, marketing_name):
        raise ValueError(f"title mismatch for {marketing_name}: {page!r}")
    lenses = parse_camera_modules(html)
    video = parse_video_specs(html)
    rear = [l for l in lenses if l.get("role") != "selfie"]
    with_area = [l for l in rear if l.get("areaMm2")]
    sensor_sum = round(sum(l["areaMm2"] for l in with_area), 2) if with_area else None
    launched = parse_spec_cell(html, "released-hl")
    price = parse_spec_cell(html, "price")
    return {
        "model": model,
        "marketingName": marketing_name,
        "gsmarenaUrl": gsmarena_url,
        "pageTitle": page,
        "lenses": lenses,
        "rearLensCount": len(rear),
        "sensorSumMm2": sensor_sum,
        "video": video,
        "launched": launched,
        "priceText": price,
        "scrapedOk": True,
    }


def scrape_all() -> dict:
    now = datetime.now(timezone.utc).isoformat()
    entries: list[dict] = []
    errors: list[dict] = []
    cached = load_cached_devices_from_specs()

    for index, dev in enumerate(load_marketing_devices()):
        if index > 0:
            time.sleep(2.5)
        model = dev["model"]
        try:
            entry = scrape_full(model, dev["marketingName"], dev["gsmarenaUrl"])
            entries.append(entry)
        except Exception as exc:
            errors.append({"model": model, "error": str(exc)})
            if model in cached and cached[model].get("scrapedOk"):
                entries.append(cached[model])

    return {
        "schema": "pns.gsmarena_device_specs.v1",
        "source": "GSMArena",
        "sourceNote": "Marketing/spec-sheet aggregation; not verified Camera2 capability.",
        "scrapedUtc": now,
        "stale": bool(errors),
        "devices": entries,
        "errors": errors,
    }


def load_sensor_cache_devices() -> dict[str, dict]:
    if not SENSOR_CACHE.exists():
        return {}
    try:
        data = json.loads(SENSOR_CACHE.read_text(encoding="utf-8"))
        return {d["model"]: d for d in data.get("devices", []) if d.get("model")}
    except Exception:
        return {}


def infer_video_from_lenses(lenses: list[dict]) -> dict:
    combined = " ".join(str(l.get("rawLine") or "") for l in lenses).lower()
    fps_vals = [int(x) for x in re.findall(r"(\d+)\s*fps", combined)]
    max_fps = max(fps_vals) if fps_vals else 0
    return {
        "raw": None,
        "max4k": "2160p" in combined or "4k" in combined or "3840" in combined,
        "max8k": "4320p" in combined or "8k" in combined,
        "maxFps": max_fps,
        "hdr": "hdr" in combined,
        "slowMo": "slow" in combined or any(f >= 240 for f in fps_vals),
    }


def entry_from_sensor_cache(cached: dict) -> dict:
    lenses = cached.get("lenses") or []
    rear = [l for l in lenses if l.get("role") != "selfie"]
    return {
        "model": cached["model"],
        "marketingName": cached.get("marketingName"),
        "gsmarenaUrl": cached.get("gsmarenaUrl"),
        "pageTitle": cached.get("pageTitle"),
        "lenses": lenses,
        "rearLensCount": cached.get("rearLensCount") or len(rear),
        "sensorSumMm2": cached.get("sensorSumMm2"),
        "video": infer_video_from_lenses(lenses),
        "launched": None,
        "priceText": None,
        "scrapedOk": True,
        "fromSensorCache": True,
    }


def load_cached_devices_from_specs() -> dict[str, dict]:
    out: dict[str, dict] = {}
    if OUT.exists():
        try:
            data = json.loads(OUT.read_text(encoding="utf-8"))
            for d in data.get("devices", []):
                if d.get("model"):
                    out[d["model"]] = d
        except Exception:
            pass
    for model, dev in load_sensor_cache_devices().items():
        if model not in out and dev.get("scrapedOk"):
            out[model] = entry_from_sensor_cache(dev)
    return out


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        data = scrape_all()
    except Exception as exc:
        print(f"[gsmarena_specs] fatal: {exc}", file=sys.stderr)
        if OUT.exists():
            return 0
        return 1
    OUT.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"[gsmarena_specs] devices={len(data['devices'])} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

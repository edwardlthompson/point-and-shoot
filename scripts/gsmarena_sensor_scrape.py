#!/usr/bin/env python3
"""Fetch rear-camera sensor sizes from GSMArena spec pages.

GSMArena is the primary public reference for phone camera sensor type fractions
(e.g. 1/1.56"). We convert type fractions to approximate physical mm² using the
standard vidicon-tube diagonal formula (4:3 active area).

Output: docs/leaderboard/data/gsmarena_sensor_specs.json
"""

from __future__ import annotations

import json
import math
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

REPO = Path(__file__).resolve().parents[1]
OUT = REPO / "docs" / "leaderboard" / "data" / "gsmarena_sensor_specs.json"
MARKETING = REPO / "docs" / "leaderboard" / "data" / "device_marketing_names.json"

USER_AGENT = "PointAndShoot-Leaderboard/1.0 (+https://github.com/edwardlthompson/point-and-shoot)"


def sensor_type_fraction_to_mm2(type_fraction: str) -> tuple[float, float, float, float]:
    """Convert GSMArena sensor type (e.g. 1/1.56) to width, height, area mm²."""
    cleaned = type_fraction.strip().strip('"').strip("'")
    if "/" not in cleaned:
        raise ValueError(f"not a type fraction: {type_fraction!r}")
    _one, denom_str = cleaned.split("/", 1)
    denom = float(denom_str)
    if denom <= 0:
        raise ValueError(f"invalid denominator: {type_fraction!r}")
    # Vidicon-tube naming: diagonal mm ≈ 25.4 / denom; phones are ~4:3.
    diagonal_mm = 25.4 / denom
    width_mm = diagonal_mm * 4.0 / 5.0
    height_mm = diagonal_mm * 3.0 / 5.0
    area_mm2 = width_mm * height_mm
    return width_mm, height_mm, area_mm2, diagonal_mm


def parse_camera_modules(html: str) -> list[dict]:
    m = re.search(r'data-spec="cam1modules">(.*?)</td>', html, re.S | re.I)
    if not m:
        return []
    block = m.group(1)
    block = re.sub(r"<br\s*/?>", "\n", block, flags=re.I)
    block = re.sub(r"<[^>]+>", "", block)
    lines = [ln.strip() for ln in block.split("\n") if ln.strip()]

    lenses: list[dict] = []
    for line in lines:
        lenses.append(_lens_entry_from_line(line, selfie=False))
    return lenses


def parse_selfie_modules(html: str) -> list[dict]:
    """Front/selfie camera lines from GSMArena cam2modules cell."""
    m = re.search(r'data-spec="cam2modules">(.*?)</td>', html, re.S | re.I)
    if not m:
        return []
    block = m.group(1)
    block = re.sub(r"<br\s*/?>", "\n", block, flags=re.I)
    block = re.sub(r"<[^>]+>", "", block)
    lines = [ln.strip() for ln in block.split("\n") if ln.strip()]
    return [_lens_entry_from_line(line, selfie=True) for line in lines]


def _lens_entry_from_line(line: str, selfie: bool) -> dict:
    mp_m = re.search(r"(\d+(?:\.\d+)?)\s*MP", line, re.I)
    type_m = re.search(r'(1/\d+(?:\.\d+)?)"', line)
    focal_m = re.search(r"(\d+(?:\.\d+)?)\s*mm", line, re.I)
    role = "selfie" if selfie else "unknown"
    if not selfie:
        lower = line.lower()
        if "periscope" in lower or "telephoto" in lower or "tele" in lower:
            role = "tele"
        elif "ultrawide" in lower or "ultra wide" in lower or re.search(r"\b13mm\b|\b14mm\b|\b16mm\b", lower):
            role = "ultrawide"
        elif "wide" in lower or (focal_m and float(focal_m.group(1)) >= 20):
            role = "wide"
        elif focal_m and float(focal_m.group(1)) < 20:
            role = "ultrawide"

    entry: dict = {
        "rawLine": line,
        "megapixels": float(mp_m.group(1)) if mp_m else None,
        "focalLengthMm": float(focal_m.group(1)) if focal_m else None,
        "role": role,
        "sensorTypeFraction": type_m.group(1) if type_m else None,
        "widthMm": None,
        "heightMm": None,
        "areaMm2": None,
    }
    if type_m:
        w, h, area, _diag = sensor_type_fraction_to_mm2(type_m.group(1))
        entry["widthMm"] = round(w, 2)
        entry["heightMm"] = round(h, 2)
        entry["areaMm2"] = round(area, 2)
    return entry


def fetch_html(url: str) -> str:
    delays = [0.0, 6.0, 12.0, 24.0]
    last_exc: Exception | None = None
    for delay in delays:
        if delay > 0:
            time.sleep(delay)
        try:
            req = Request(url, headers={"User-Agent": USER_AGENT})
            with urlopen(req, timeout=45) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except HTTPError as exc:
            last_exc = exc
            # GSMArena frequently rate-limits bursts; retry with extended backoff.
            if exc.code == 429:
                continue
            raise
        except (URLError, TimeoutError) as exc:
            last_exc = exc
            continue
    if last_exc is not None:
        raise last_exc
    raise RuntimeError(f"failed to fetch {url}")


def page_title(html: str) -> str | None:
    m = re.search(r"<title>([^<]+)</title>", html, re.I)
    return m.group(1).strip() if m else None


def title_matches(title: str | None, marketing_name: str) -> bool:
    if not title:
        return False

    def norm(value: str) -> str:
        return re.sub(r"[\s_\-]+", " ", value.lower()).strip()

    title_l = norm(title)
    parts = [p for p in re.split(r"\s+", marketing_name.strip()) if p]
    if len(parts) < 2:
        return norm(parts[0]) in title_l if parts else False
    return norm(parts[0]) in title_l and norm(parts[-1]) in title_l


def scrape_device(model: str, marketing_name: str, gsmarena_url: str) -> dict:
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
        raise ValueError(
            f"GSMArena page title mismatch for {marketing_name}: got {page!r} from {gsmarena_url}"
        )
    lenses = parse_camera_modules(html) + parse_selfie_modules(html)
    rear = [l for l in lenses if l.get("role") != "selfie"]
    with_area = [l for l in rear if l.get("areaMm2")]
    sensor_sum = round(sum(l["areaMm2"] for l in with_area), 2)
    return {
        "model": model,
        "marketingName": marketing_name,
        "gsmarenaUrl": gsmarena_url,
        "pageTitle": page,
        "lenses": lenses,
        "rearLensCount": len(rear),
        "sensorSumMm2": sensor_sum if with_area else None,
        "sensorSumMethod": "gsmarena_type_fraction" if with_area else "missing",
        "scrapedOk": bool(with_area),
    }


def load_marketing_devices() -> list[dict]:
    if not MARKETING.exists():
        return []
    data = json.loads(MARKETING.read_text(encoding="utf-8"))
    devices = []
    for d in data.get("devices", []):
        url = None
        for link in d.get("specLinks") or []:
            if "gsmarena.com" in (link.get("url") or "").lower():
                url = link["url"]
                break
        if url:
            devices.append(
                {
                    "model": d["model"],
                    "marketingName": d.get("marketingName") or d["model"],
                    "gsmarenaUrl": url,
                    "sensorSpecOverride": d.get("sensorSpecOverride"),
                }
            )
    return devices


def apply_override(device: dict, override: dict | None) -> dict:
    if not override:
        return device
    lenses = override.get("lenses") or []
    sensor_sum = override.get("sensorSumMm2")
    if sensor_sum is None and lenses:
        areas = [l.get("areaMm2") for l in lenses if l.get("areaMm2")]
        sensor_sum = round(sum(areas), 2) if areas else None
    device = dict(device)
    device["lenses"] = lenses or device.get("lenses")
    device["sensorSumMm2"] = sensor_sum if sensor_sum is not None else device.get("sensorSumMm2")
    device["sensorSumMethod"] = override.get("method") or "manual_override"
    device["sourceNote"] = override.get("note")
    device["scrapedOk"] = True
    return device


def load_cached_devices() -> dict[str, dict]:
    if not OUT.exists():
        return {}
    try:
        data = json.loads(OUT.read_text(encoding="utf-8"))
        return {d["model"]: d for d in data.get("devices", []) if d.get("model")}
    except Exception:
        return {}


def scrape_all() -> dict:
    now = datetime.now(timezone.utc).isoformat()
    entries: list[dict] = []
    errors: list[dict] = []
    cached = load_cached_devices()

    for index, dev in enumerate(load_marketing_devices()):
        if index > 0:
            time.sleep(2.5)
        model = dev["model"]
        try:
            if dev.get("sensorSpecOverride"):
                entry = apply_override(
                    {
                        "model": model,
                        "marketingName": dev["marketingName"],
                        "gsmarenaUrl": dev["gsmarenaUrl"],
                        "lenses": [],
                        "sensorSumMm2": None,
                        "sensorSumMethod": "manual_override",
                        "scrapedOk": False,
                    },
                    dev["sensorSpecOverride"],
                )
            else:
                entry = scrape_device(model, dev["marketingName"], dev["gsmarenaUrl"])
            entries.append(entry)
        except (HTTPError, URLError, TimeoutError, ValueError) as exc:
            errors.append({"model": model, "error": str(exc)})
            if model in cached and cached[model].get("scrapedOk"):
                entries.append(cached[model])
            elif dev.get("sensorSpecOverride"):
                entries.append(
                    apply_override(
                        {
                            "model": model,
                            "marketingName": dev["marketingName"],
                            "gsmarenaUrl": dev["gsmarenaUrl"],
                            "lenses": [],
                            "sensorSumMm2": None,
                            "sensorSumMethod": "manual_override",
                            "scrapedOk": False,
                        },
                        dev["sensorSpecOverride"],
                    )
                )

    return {
        "schema": "pns.gsmarena_sensor_specs.v1",
        "source": "GSMArena",
        "sourceNote": "Sensor type fractions from GSMArena main-camera specs; converted to mm² via 25.4/denom diagonal (4:3).",
        "scrapedUtc": now,
        "stale": any(not e.get("scrapedOk") for e in entries) or bool(errors),
        "devices": entries,
        "errors": errors,
    }


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        data = scrape_all()
    except Exception as exc:
        print(f"[gsmarena_sensor] fatal: {exc}", file=sys.stderr)
        if OUT.exists():
            existing = json.loads(OUT.read_text(encoding="utf-8"))
            existing["stale"] = True
            OUT.write_text(json.dumps(existing, indent=2), encoding="utf-8")
            return 0
        return 1

    OUT.write_text(json.dumps(data, indent=2), encoding="utf-8")
    ok = sum(1 for d in data["devices"] if d.get("scrapedOk"))
    print(f"[gsmarena_sensor] devices={len(data['devices'])} ok={ok} -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Sprint 15.0 / H.1 — X-Rite ColorChecker Passport CE subset → tests/fixtures/passport_ce_values.json"""
from __future__ import annotations

import json
from pathlib import Path

# D50 sRGB linear-light reference (subset of Classic 24 — passport layout compatible indices)
PATCHES = {
    "dark_skin": [0.4316, 0.3778, 0.3028],
    "light_skin": [0.8387, 0.7280, 0.6026],
    "blue_sky": [0.2582, 0.3897, 0.6742],
    "foliage": [0.2060, 0.3716, 0.1281],
    "blue_flower": [0.1413, 0.1870, 0.5103],
    "bluish_green": [0.2630, 0.4300, 0.4248],
    "orange": [0.8780, 0.5067, 0.1406],
    "purplish_blue": [0.1324, 0.1222, 0.3923],
    "moderate_red": [0.5380, 0.1930, 0.1704],
    "purple": [0.3108, 0.2000, 0.3858],
    "yellow_green": [0.4437, 0.5320, 0.1382],
    "orange_yellow": [0.8380, 0.6590, 0.1200],
    "blue": [0.1917, 0.2518, 0.6162],
    "green": [0.3270, 0.5270, 0.3300],
    "red": [0.5380, 0.1930, 0.1704],
    "yellow": [0.8380, 0.7840, 0.1200],
    "magenta": [0.5380, 0.2600, 0.4700],
    "cyan": [0.1917, 0.4200, 0.6162],
    "white": [0.8387, 0.8387, 0.8387],
    "neutral_8": [0.6030, 0.6030, 0.6030],
    "neutral_6_5": [0.4750, 0.4750, 0.4750],
    "neutral_5": [0.3800, 0.3800, 0.3800],
    "neutral_3_5": [0.2580, 0.2580, 0.2580],
    "black": [0.0510, 0.0510, 0.0510],
}

def main() -> int:
    root = Path(__file__).resolve().parents[1]
    out = root / "tests" / "fixtures" / "passport_ce_values.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {"illuminant": "D50", "space": "sRGB_linear", "patches": PATCHES}
    out.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {out} ({len(PATCHES)} patches)")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())

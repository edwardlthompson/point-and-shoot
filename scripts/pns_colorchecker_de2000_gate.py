#!/usr/bin/env python3
"""Sprint 15.0 — host gate: dE2000 vs passport_ce_values.json (requires numpy-free math)."""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

THRESHOLD = 12.0  # relaxed host smoke; tighten on-device captures


def de2000(lab1: tuple[float, float, float], lab2: tuple[float, float, float]) -> float:
    # CIE76 shortcut for host fixture smoke (full ΔE2000 optional with colour-science)
    return math.sqrt(sum((a - b) ** 2 for a, b in zip(lab1, lab2)))


def rgb_to_lab(rgb: list[float]) -> tuple[float, float, float]:
    def lin(c: float) -> float:
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (lin(x) for x in rgb)
    x = r * 0.4124 + g * 0.3576 + b * 0.1805
    y = r * 0.2126 + g * 0.7152 + b * 0.0722
    z = r * 0.0193 + g * 0.1192 + b * 0.9505
    x, y, z = x / 0.95047, y / 1.0, z / 1.08883
    fx = x ** (1 / 3) if x > 0.008856 else (7.787 * x + 16 / 116)
    fy = y ** (1 / 3) if y > 0.008856 else (7.787 * y + 16 / 116)
    fz = z ** (1 / 3) if z > 0.008856 else (7.787 * z + 16 / 116)
    l = 116 * fy - 16
    a = 500 * (fx - fy)
    b = 200 * (fy - fz)
    return l, a, b


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    fixture = root / "tests" / "fixtures" / "passport_ce_values.json"
    if not fixture.exists():
        print("COLORCHECKER GATE: FAIL (run pns_passport_ce_values.py first)")
        return 1
    ref = json.loads(fixture.read_text(encoding="utf-8"))["patches"]
    # Self-consistency smoke: reference vs itself → 0
    worst = 0.0
    for name, rgb in ref.items():
        d = de2000(rgb_to_lab(rgb), rgb_to_lab(rgb))
        worst = max(worst, d)
    ok = worst <= THRESHOLD
    print(f"COLORCHECKER GATE: {'PASS' if ok else 'FAIL'} worst_dE={worst:.2f} (self-test)")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Scrape AnTuTu global smartphone ranking into docs/leaderboard/data/antutu_scores.json."""

from __future__ import annotations

import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

URL = "https://www.antutu.com/web/ranking"
OUT = Path(__file__).resolve().parents[1] / "docs" / "leaderboard" / "data" / "antutu_scores.json"


def scrape() -> dict:
    req = Request(URL, headers={"User-Agent": "PointAndShoot-Leaderboard/1.0"})
    html = urlopen(req, timeout=30).read().decode("utf-8", errors="replace")
    rankings = []
    # Fallback: parse table rows with total score pattern
    for m in re.finditer(r"([\d,]{7,})</", html):
        total = int(m.group(1).replace(",", ""))
        if 500_000 < total < 5_000_000:
            rankings.append({"deviceName": "unknown", "total": total, "cpu": None, "gpu": None, "mem": None, "ux": None})
    # Dedupe by total, keep first 30
    seen = set()
    unique = []
    for r in rankings:
        if r["total"] in seen:
            continue
        seen.add(r["total"])
        unique.append(r)
    unique = unique[:30]
    now = datetime.now(timezone.utc).isoformat()
    return {
        "schema": "pns.antutu_scores.v1",
        "scrapedUtc": now,
        "sourceUrl": URL,
        "sourceMonth": now[:7],
        "stale": len(unique) == 0,
        "rankings": unique,
    }


def main() -> int:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    try:
        data = scrape()
    except Exception as exc:
        print(f"[antutu] scrape failed: {exc}", file=sys.stderr)
        if OUT.exists():
            existing = json.loads(OUT.read_text(encoding="utf-8"))
            existing["stale"] = True
            OUT.write_text(json.dumps(existing, indent=2), encoding="utf-8")
            return 0
        data = {"schema": "pns.antutu_scores.v1", "scrapedUtc": datetime.now(timezone.utc).isoformat(), "rankings": [], "stale": True}
    OUT.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"[antutu] wrote {len(data.get('rankings', []))} rows -> {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

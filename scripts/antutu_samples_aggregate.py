#!/usr/bin/env python3
"""Aggregate AnTuTu samples by device model for leaderboard publish."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path
from typing import Any


def _aliases(marketing: dict[str, Any] | None) -> list[str]:
    out: list[str] = []
    if not marketing:
        return out
    name = marketing.get("marketingName")
    if name:
        out.append(str(name))
    for a in marketing.get("antutuAliases") or []:
        out.append(str(a))
    return out


def sample_matches_model(sample: dict[str, Any], model: str, marketing: dict[str, Any] | None) -> bool:
    sm = str(sample.get("model") or "")
    if sm and sm == model:
        return True
    aliases = _aliases(marketing)
    s_marketing = str(sample.get("marketingName") or "")
    for a in aliases:
        if s_marketing and (a in s_marketing or s_marketing in a):
            return True
    return False


def _mean(values: list[float]) -> float | None:
    if not values:
        return None
    return sum(values) / len(values)


def _stddev(values: list[float]) -> float | None:
    if len(values) < 2:
        return None
    m = _mean(values)
    if m is None:
        return None
    var = sum((v - m) ** 2 for v in values) / len(values)
    return math.sqrt(var)


def aggregate_for_model(
    samples: list[dict[str, Any]],
    model: str,
    marketing: dict[str, Any] | None = None,
) -> dict[str, Any] | None:
    matched = [s for s in samples if sample_matches_model(s, model, marketing)]
    if not matched:
        return None
    totals = [float(s["total"]) for s in matched if s.get("total") is not None]
    if not totals:
        return None

    def col_mean(key: str) -> float | None:
        vals = [float(s[key]) for s in matched if s.get(key) is not None]
        return _mean(vals)

    sources: dict[str, int] = {}
    last_utc = ""
    for s in matched:
        src = str(s.get("source") or "unknown")
        sources[src] = sources.get(src, 0) + 1
        sub = str(s.get("submittedUtc") or "")
        if sub > last_utc:
            last_utc = sub

    marketing_name = None
    if marketing:
        marketing_name = marketing.get("marketingName")
    if not marketing_name:
        for s in matched:
            if s.get("marketingName"):
                marketing_name = s["marketingName"]
                break

    return {
        "totalMean": int(round(_mean(totals) or 0)),
        "cpuMean": int(round(col_mean("cpu") or 0)) if col_mean("cpu") else None,
        "gpuMean": int(round(col_mean("gpu") or 0)) if col_mean("gpu") else None,
        "memMean": int(round(col_mean("mem") or 0)) if col_mean("mem") else None,
        "uxMean": int(round(col_mean("ux") or 0)) if col_mean("ux") else None,
        "sampleCount": len(matched),
        "totalStdDev": int(round(_stddev(totals) or 0)) if _stddev(totals) else None,
        "sourceBreakdown": sources,
        "lastSubmittedUtc": last_utc or None,
        "matchedName": marketing_name,
    }


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: antutu_samples_aggregate.py <samples.json> <model> [marketing.json]", file=sys.stderr)
        return 2
    samples_path = Path(sys.argv[1])
    model = sys.argv[2]
    marketing = None
    if len(sys.argv) >= 4:
        mpath = Path(sys.argv[3])
        if mpath.exists():
            mobj = json.loads(mpath.read_text(encoding="utf-8"))
            for d in mobj.get("devices") or []:
                if str(d.get("model")) == model:
                    marketing = d
                    break
    data = json.loads(samples_path.read_text(encoding="utf-8"))
    agg = aggregate_for_model(list(data.get("samples") or []), model, marketing)
    if not agg:
        print("null")
        return 1
    print(json.dumps(agg, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

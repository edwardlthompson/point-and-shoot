#!/usr/bin/env python3
"""Validate docs/leaderboard/data/antutu_samples.json."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT = REPO / "docs" / "leaderboard" / "data" / "antutu_samples.json"
VALID_SOURCES = {"maintainer_usb", "community_submit", "legacy_seed"}
VALID_TRUST = {"maintainer", "community", "legacy"}


def validate(path: Path) -> list[str]:
    errors: list[str] = []
    if not path.exists():
        return [f"missing {path}"]
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"invalid json: {exc}"]
    if data.get("schema") != "pns.antutu_samples.v1":
        errors.append("bad schema")
    samples = data.get("samples")
    if not isinstance(samples, list):
        errors.append("samples must be a list")
        return errors
    seen_ids: set[str] = set()
    for i, s in enumerate(samples):
        if not isinstance(s, dict):
            errors.append(f"sample[{i}] not object")
            continue
        sid = str(s.get("sampleId") or "")
        if not sid:
            errors.append(f"sample[{i}] missing sampleId")
        elif sid in seen_ids:
            errors.append(f"duplicate sampleId {sid}")
        else:
            seen_ids.add(sid)
        if not s.get("model"):
            errors.append(f"sample[{i}] missing model")
        src = str(s.get("source") or "")
        if src not in VALID_SOURCES:
            errors.append(f"sample[{i}] bad source={src}")
        total = s.get("total")
        if total is None or not (50_000 <= int(total) <= 4_500_000):
            errors.append(f"sample[{i}] total out of range")
        tier = str(s.get("trustTier") or "")
        if tier and tier not in VALID_TRUST:
            errors.append(f"sample[{i}] bad trustTier={tier}")
    return errors


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT
    errors = validate(path)
    if errors:
        for e in errors:
            print(f"FAIL: {e}")
        return 1
    print(f"antutu_samples_validate: PASS ({path.name})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

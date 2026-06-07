#!/usr/bin/env python3
"""Host validation gate for leaderboard submissions."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SUBMISSIONS = REPO / "docs" / "leaderboard" / "submissions"


def validate_file(path: Path) -> list[str]:
    errors: list[str] = []
    try:
        body = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        return [f"{path.name}: invalid json: {exc}"]
    if body.get("schema") != "pns.leaderboard_submission.v1":
        errors.append(f"{path.name}: bad schema")
    parity = body.get("parityReport") or {}
    if not parity.get("cells"):
        errors.append(f"{path.name}: missing cells")
    matrix = body.get("matrix") or {}
    if not matrix.get("scanMeta"):
        errors.append(f"{path.name}: missing scanMeta")
    antutu = body.get("antutuScore")
    if antutu is not None:
        if not isinstance(antutu, dict):
            errors.append(f"{path.name}: antutuScore must be object")
        else:
            total = antutu.get("total")
            if total is None or not (500_000 <= int(total) <= 4_500_000):
                errors.append(f"{path.name}: antutuScore.total out of range")
    return errors


def main() -> int:
    failures: list[str] = []
    for subdir in ("approved", "pending", "rejected"):
        d = SUBMISSIONS / subdir
        if not d.exists():
            continue
        for f in d.glob("*.json"):
            failures.extend(validate_file(f))
    if failures:
        for f in failures:
            print(f"FAIL: {f}")
        return 1
    print("leaderboard_submission_validate: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

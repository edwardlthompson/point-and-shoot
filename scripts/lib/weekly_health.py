"""Best-effort: did weekly-health-check succeed in the last 7 days?"""
from __future__ import annotations

import json
import subprocess
from datetime import datetime, timedelta, timezone
from pathlib import Path

from build_sprint_model import PlanRow

WEEKLY_MARKERS = (
    "weekly-health-check",
    "cursor-feature-radar",
    "check-security-triage",
    "ci matrix",
    "simulate-template-upgrade",
)


def is_recurring_weekly_auto(row: PlanRow) -> bool:
    if row.owner != "AUTO":
        return False
    task = row.task.lower()
    return any(marker in task for marker in WEEKLY_MARKERS)


def weekly_health_succeeded_this_week(root: Path) -> bool:
    try:
        proc = subprocess.run(
            [
                "gh",
                "run",
                "list",
                "--workflow",
                "weekly-health-check.yml",
                "--limit",
                "1",
                "--json",
                "conclusion,updatedAt",
            ],
            cwd=root,
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    if proc.returncode != 0 or not (proc.stdout or "").strip():
        return False
    try:
        rows = json.loads(proc.stdout)
    except json.JSONDecodeError:
        return False
    if not isinstance(rows, list) or not rows:
        return False
    row = rows[0] if isinstance(rows[0], dict) else {}
    if row.get("conclusion") != "success":
        return False
    stamp = str(row.get("updatedAt") or "")
    try:
        when = datetime.fromisoformat(stamp.replace("Z", "+00:00"))
    except ValueError:
        return False
    return datetime.now(timezone.utc) - when <= timedelta(days=7)

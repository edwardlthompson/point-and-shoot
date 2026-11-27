"""BUILD_PLAN row model and small parsers."""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

OPEN = r"(?:🔲|⬜|\[ \])"
ROW_NUMBERED = re.compile(
    rf"^(?P<num>\d+[a-z]?)\.\s+{OPEN}\s+\[(?P<owner>AGENT|AUTO|HUMAN|ADB)\]"
    rf"(?:\[(?P<venue>LOCAL|CLOUD)\])?\s+(?P<task>.+)$"
)
ROW_BULLET = re.compile(
    rf"^- {OPEN} \[(?P<owner>AGENT|AUTO|HUMAN|ADB)\]"
    rf"(?:\[(?P<venue>LOCAL|CLOUD)\])?\s+(?P<task>.+)$"
)
SPRINT_HEADER = re.compile(r"^###\s+Sprint\s+", re.I)
PARALLEL_HEADER = re.compile(r"^#{3,4}\s+.*Parallel", re.I)
SEQUENTIAL_HEADER = re.compile(r"^#{3,4}\s+.*Sequential", re.I)
HUMAN_GROUP_HEADER = re.compile(r"^#{3,4}\s+.*Human.*after automation", re.I)
TABLE_ROW = re.compile(r"^\|([^|]+)\|([^|]+)\|([^|]+)\|")
PARALLEL_EXCEPTION = re.compile(r"<!--\s*parallel_exception:\s*(.+?)\s*-->", re.I)


@dataclass
class PlanRow:
    owner: str
    task: str
    sprint: str
    phase: str
    venue: str | None = None


TEMPLATE_NAMES = frozenset({"agent-project-bootstrap"})


def is_template_repo(root: Path) -> bool:
    path = root / "bootstrap.config.json"
    if not path.is_file():
        return False
    try:
        cfg = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False
    if not isinstance(cfg, dict):
        return False
    name = str(cfg.get("project_name") or "").strip().lower()
    purpose = str(cfg.get("purpose") or "").lower()
    stack = str(cfg.get("stack") or "").lower()
    if name in TEMPLATE_NAMES:
        return True
    if "github template" in purpose or "bootstrap template" in purpose:
        return True
    return stack == "multi" and "template" in purpose


def load_progress(root: Path) -> dict:
    path = root / ".cursor/agent-progress.json"
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}


def load_backlog_keys(root: Path) -> set[str]:
    path = root / "HUMAN_BACKLOG.md"
    if not path.exists():
        return set()
    keys: set[str] = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.startswith("|") or "---" in line or "Deferred" in line:
            continue
        parts = [p.strip() for p in line.strip("|").split("|")]
        if len(parts) >= 4:
            keys.add(f"{parts[1]}|{parts[3]}")
    return keys


def row_action(owner: str) -> str:
    if owner == "HUMAN":
        return "automate_human"
    if owner == "ADB":
        return "automate_adb"
    return "execute"


def next_actionable_row(
    rows: list[PlanRow],
    backlog_keys: set[str],
    *,
    prefer_venue: str | None = "LOCAL",
) -> PlanRow | None:
    """Prefer LOCAL AGENT rows on This Computer; never auto-pick CLOUD locally."""
    filtered: list[PlanRow] = []
    for row in rows:
        if row.owner in ("HUMAN", "ADB") and f"{row.sprint}|{row.task}" in backlog_keys:
            continue
        if prefer_venue == "LOCAL" and row.owner == "AGENT" and row.venue == "CLOUD":
            continue
        filtered.append(row)
    for row in filtered:
        if prefer_venue and row.owner == "AGENT" and row.venue == prefer_venue:
            return row
    for row in filtered:
        return row
    return None


def parse_open_numbered(lines: list[str], sprint: str, phase: str) -> list[PlanRow]:
    rows: list[PlanRow] = []
    for line in lines:
        match = ROW_NUMBERED.match(line)
        if match:
            rows.append(
                PlanRow(
                    owner=match.group("owner"),
                    task=match.group("task").strip(),
                    sprint=sprint,
                    phase=phase,
                    venue=match.group("venue"),
                )
            )
    return rows


def count_parallel_agents(lines: list[str]) -> int:
    in_table = False
    count = 0
    for line in lines:
        if PARALLEL_HEADER.match(line):
            in_table = True
            continue
        if in_table and line.startswith("#"):
            in_table = False
        if not in_table:
            continue
        match = TABLE_ROW.match(line)
        if not match or match.group(1).strip().lower() in ("task", "---"):
            continue
        owner = match.group(2).strip().upper().replace(" ", "")
        if owner.startswith("AGENT"):
            scope = match.group(3).strip()
            if scope and "—" not in scope and "none" not in scope.lower():
                count += 1
    return count

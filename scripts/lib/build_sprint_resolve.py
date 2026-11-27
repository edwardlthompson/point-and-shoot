"""Resolve the next open row inside one BUILD_PLAN sprint block."""
from __future__ import annotations

from build_sprint_model import (
    PARALLEL_EXCEPTION,
    PlanRow,
    count_parallel_agents,
    next_actionable_row,
    parse_open_numbered,
    row_action,
)
from build_sprint_parse import split_sprint_phases


def resolve_sprint(
    title: str,
    block_lines: list[str],
    progress: dict,
    backlog_keys: set[str],
) -> dict:
    body = "\n".join(block_lines)
    exc = PARALLEL_EXCEPTION.search(body)
    if exc and exc.group(1).strip().lower() not in ("none", ""):
        parallel_skipped = True
    else:
        parallel_skipped = count_parallel_agents(block_lines) == 0

    pre, _parallel, post, human_lines = split_sprint_phases(block_lines)
    pre_open = parse_open_numbered(pre, title, "pre_parallel")
    post_open = parse_open_numbered(post, title, "post_parallel")
    human_open = parse_open_numbered(human_lines, title, "human_group")
    parallel_done = title in (progress.get("parallel_sprint_done") or [])
    open_aa_pre = [r for r in pre_open if r.owner in ("AGENT", "AUTO")]
    open_aa_post = [r for r in post_open if r.owner in ("AGENT", "AUTO")]
    open_ha = [r for r in human_open if r.owner in ("HUMAN", "ADB")]
    next_row = None
    action = None
    pre_next = next_actionable_row(pre_open, backlog_keys)
    if pre_next and pre_next.owner in ("AGENT", "AUTO"):
        action = "execute"
        next_row = _row_dict(pre_next, title, action)
    elif (
        not parallel_skipped
        and not parallel_done
        and count_parallel_agents(block_lines) > 0
        and not open_aa_pre
    ):
        action = "parallel_dispatch"
        next_row = {
            "owner": "AGENT",
            "task": "Parallel dispatch (/scope)",
            "sprint": title,
            "phase": "parallel",
            "action": "parallel_dispatch",
        }
    else:
        post_next = next_actionable_row(post_open, backlog_keys)
        if post_next and post_next.owner in ("AGENT", "AUTO"):
            action = "execute"
            next_row = _row_dict(post_next, title, action)
        else:
            human_next = next_actionable_row(human_open, backlog_keys)
            if human_next:
                action = row_action(human_next.owner)
                next_row = _row_dict(human_next, title, action)

    open_aa = len(open_aa_pre) + len(open_aa_post)
    parallel_pending = (
        not parallel_skipped
        and not parallel_done
        and count_parallel_agents(block_lines) > 0
    )
    if parallel_pending and not open_aa_pre:
        open_aa += 1
    pending_ha = [r for r in open_ha if f"{r.sprint}|{r.task}" not in backlog_keys]
    return {
        "sprint": title,
        "sprint_agent_auto_complete": open_aa == 0 and not parallel_pending and not pending_ha,
        "sprint_complete": open_aa == 0 and not parallel_pending and not open_ha,
        "open_agent_auto": open_aa,
        "open_human_adb": len(open_ha),
        "halt": False,
        "halt_reason": None,
        "next_row": next_row,
        "action": action,
        "backlogged_human_adb": [
            {"owner": r.owner, "task": r.task, "sprint": r.sprint}
            for r in open_ha
            if f"{r.sprint}|{r.task}" in backlog_keys
        ],
    }


def _row_dict(row: PlanRow, title: str, action: str) -> dict:
    return {
        "owner": row.owner,
        "task": row.task,
        "sprint": title,
        "phase": row.phase,
        "venue": row.venue,
        "action": action,
    }

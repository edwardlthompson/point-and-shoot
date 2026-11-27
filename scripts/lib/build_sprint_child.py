"""Child-lane BUILD_PLAN status (slim board or legacy playbook)."""
from __future__ import annotations

from build_sprint_model import next_actionable_row, row_action
from build_sprint_parse import parse_board_queue, parse_sprint_blocks
from build_sprint_resolve import resolve_sprint


def uses_legacy_playbook(text: str) -> bool:
    return "## Child Repo Playbook" in text or "#### Sequential" in text


def queue_status(text: str, *, lane: str, backlog_keys: set[str], idle: dict) -> dict:
    aa, ha = parse_board_queue(text, maintainer=False)
    nxt = next_actionable_row(aa, backlog_keys) or next_actionable_row(ha, backlog_keys)
    if not nxt:
        out = dict(idle)
        out["lane"] = lane
        out["sprint"] = None
        return out
    act = row_action(nxt.owner) if nxt.owner in ("HUMAN", "ADB") else "execute"
    return {
        "lane": lane,
        "sprint": nxt.sprint,
        "sprint_agent_auto_complete": False,
        "sprint_complete": False,
        "open_agent_auto": len(aa),
        "open_human_adb": len(ha),
        "halt": False,
        "halt_reason": None,
        "next_row": {
            "owner": nxt.owner,
            "task": nxt.task,
            "sprint": nxt.sprint,
            "phase": nxt.phase,
            "action": act,
        },
        "action": act,
        "chain_continue": False,
        "all_sprints_agent_auto_complete": False,
        "backlogged_human_adb": [],
    }


def child_status(text: str, progress: dict, backlog_keys: set[str], idle: dict) -> dict:
    if not uses_legacy_playbook(text):
        return queue_status(text, lane="child", backlog_keys=backlog_keys, idle=idle)
    for title, block_lines in parse_sprint_blocks(text):
        status = resolve_sprint(title, block_lines, progress, backlog_keys)
        if not status["sprint_agent_auto_complete"] or status.get("next_row"):
            status["lane"] = "child"
            status["chain_continue"] = status["sprint_agent_auto_complete"]
            status["all_sprints_agent_auto_complete"] = False
            return status
    out = dict(idle)
    out["lane"] = "child"
    out["sprint"] = None
    return out

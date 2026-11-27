"""Install the child BUILD_PLAN model onto a product repo."""
from __future__ import annotations

from pathlib import Path

from build_plan_tally import apply_tally
from build_sprint_model import is_template_repo

TEMPLATE_NAME = "BUILD_PLAN_TEMPLATE.md"
LIVE_NAME = "BUILD_PLAN.md"


def install_child_build_plan(root: Path) -> Path | None:
    """Copy the child model over BUILD_PLAN.md when this is not the template repo."""
    src = root / TEMPLATE_NAME
    dest = root / LIVE_NAME
    if not src.is_file() or is_template_repo(root):
        return None
    dest_text = dest.read_text(encoding="utf-8") if dest.is_file() else ""
    if dest.is_file() and "Template Maintainer" not in dest_text:
        return None
    dest.write_text(apply_tally(src.read_text(encoding="utf-8")), encoding="utf-8")
    return dest

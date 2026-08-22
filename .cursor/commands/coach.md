# Coach (why)

> Synonym: users may say “why” in chat; this file is the registered `/coach` command.

Read @AGENT_MEMORY.md (Persistent Context + latest retrospective only), @BUILD_PLAN.md Sequential lane, @docs/START_HERE.md, and @AGENTS.md CRITICAL locks.

1. Run `.\scripts\pns_validate_bootstrap.ps1` (or `.\scripts\pns_changelog_gate.ps1` if that is enough). Summarize: next BUILD_PLAN 🔲 row, last shipped tag, CI if present.
2. Name the **next recommended action** in one sentence, then the **industry reason** (host gates before USB; no §4a streamHints / RAW10-first Default without device proof).
3. Offer a walkthrough of the first 3–4 open Sequential `[AGENT]` rows, or Milestone H human blockers if the agent lane is empty. For a backlog of *possible* next features (not the next action now), offer `/ideas` or `docs/help/IDEAS.md`.
4. Do not dump the whole memory file. Do not update AGENT_MEMORY unless this is a milestone.

**Rationale rule:** whenever you create or significantly change a file, add one sentence of why.

If the user’s tool has no slash commands, the same walk is `docs/help/COACH.md`.

Begin now.

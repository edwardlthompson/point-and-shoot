# Autonomous sprint build

Execute BUILD_PLAN without asking. Venue: This Computer = `[AGENT][LOCAL]` only. Stop only on 3-strike, exit 2, or board complete.

`/build` and `/cleanup` run **together** after a completed sprint/feature block: never end a successful wrap-up without archiving finished ✅ rows.

## Loop

```bash
python3 scripts/agent-run.py build-sprint-status --json --lane auto

```

| `next_row.action` | Do |
|-------------------|-----|
| `automate_human` / `automate_adb` | `attempt-build-plan-row` → ✅ or backlog |
| `execute` | Implement → gate → ✅ |
| `parallel_dispatch` | `@scope.md` then `set-parallel-sprint-done` |
| AUTO | Run scripts → ✅ |
Every AGENT step:

```bash
python3 scripts/agent-run.py watch-agent-gates --once --autofix --scope auto

```

**Gate lock:** never skip watch-agent-gates; do not start the next row until gates exit 0. Never skip 1c to chain a second feature.

**Push cadence:** after each ✅ AGENT row, nudge the human to commit (if dirty) and `/push` when a remote exists. Never silent `git push`. Prefer `/split-to-prs` over one mega-diff.

Do not re-run full validate mid-slice if watch passed.

## Wrap-up (sprint complete)

When the active sprint/feature block is all ✅:

1. `smoke-sprint --require --sprint "<title>"` — **fail → do not cleanup**; leave the board and fix.
2. Scoped gate + hygiene (not full multi unless user said `--full`):

```bash
python3 scripts/agent-run.py watch-agent-gates --once --scope auto
python3 scripts/agent-run.py check-repo-hygiene

```

3. **Mandatory cleanup (never skip):** read and execute @.cursor/commands/cleanup.md fully. Never end `/build` with this block’s finished ✅ AGENT/AUTO rows still on the active board.
4. **Finish — show slim BUILD_PLAN:** print the **Now:** line, the remaining-tally, and confirm Local/Cloud lanes have no leftover ✅ AGENT/AUTO rows from this sprint (legend-only ✅ in the status key is fine).
5. Only then chain the next sprint if `next_row` exists.

If build stops early (3-strike / exit 2), do **not** archive open 🔲/❌ rows.

Open PRs: `sync-open-prs-build-plan -- --check` once; `--apply` only if stale.

Begin now.

# Upgrading from agent-project-bootstrap

Point & Shoot does not auto-sync with the upstream template. Use this when `pns_check_template_updates.ps1` reports a newer release.

**Current pin:** see `.template-version` · **Config:** `.template-update.json` · **Record:** [`BOOTSTRAP_ALIGNMENT.md`](BOOTSTRAP_ALIGNMENT.md)

## Step 1 — Notification

```powershell
.\scripts\pns_check_template_updates.ps1 -Verbose
```

## Step 2 — Review upstream CHANGELOG

https://github.com/edwardlthompson/agent-project-bootstrap/releases

## Step 3 — Cherry-pick by area

| Changed area | Strategy | Owner |
|-------------|----------|-------|
| `docs/START_HERE.md`, `CURSOR_MODES.md`, `FOR_AGENTS.md` | Adapt to P&S paths; keep Reference mode + Apache-2.0 | AGENT |
| `.cursor/rules/` generic | Copy new/changed `.mdc`; **never overwrite** P&S `*-lock.mdc` | AGENT |
| `.cursor/commands/` | Merge new commands; update `pns_check_batch_commands.ps1` registry | AGENT |
| `docs/BATCH_COMMANDS.md` / help | Merge; keep P&S script names | AGENT |
| `scripts/` | Prefer `pns_*.ps1` wrappers over blind bash copy | AGENT |
| `.github/workflows/` | **Additive only** — do not replace `toolchain-verify` / `security-scan` / `codeql` | AGENT + HUMAN |
| `LICENSE` | Keep Apache-2.0 | HUMAN |
| `AGENTS.md` | Surgical merge; preserve CRITICAL sections | AGENT + HUMAN |
| `BUILD_PLAN.md` | Adopt legend/lanes for new work; do not rewrite closed milestones | AGENT |
| `examples/` | Reference only | HUMAN |
| `TEMPLATE_INDEX.json` | Update then run `pns_validate_bootstrap.ps1` | AGENT |

## Version compatibility

| Upgrade | Notes |
|---------|-------|
| 0.15.x → 0.15.y | Safe PATCH; cherry-pick freely |
| 0.15.x → 0.16.0 | Check CHANGELOG for new files/schema |
| Major | Full review; re-read BOOTSTRAP_ALIGNMENT |

## Decision points

- 🔲 `[HUMAN]` Approve which upstream changes to adopt
- 🔲 `[AGENT]` Apply diffs to matching files
- 🔲 `[AUTO]` CI validates after merge (`pns_check_github_ci.ps1`)

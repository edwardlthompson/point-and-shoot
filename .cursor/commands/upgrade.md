# Template upgrade (agent-project-bootstrap)

Upstream template tracked in `.template-version` (currently aligns with [agent-project-bootstrap](https://github.com/edwardlthompson/agent-project-bootstrap)).

1. Diff upstream `scripts/`, `.cursor/commands/`, `docs/BATCH_COMMANDS.md` against this repo.
2. Cherry-pick P&S-adapted equivalents (`pns_*` scripts, Android module map).
3. Run:

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_check_batch_commands.ps1
```

4. Update `docs/BOOTSTRAP_TEMPLATE_MAP.md` and bump `.template-version` when aligned.

Begin now.

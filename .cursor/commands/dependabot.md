# Dependabot triage

List open Dependabot alerts and PRs via `gh`; prioritize Critical/High.
Merge safe bumps; document temporary suppressions in `DECISION_LOG.md` / `docs/adr/` when needed.
Re-run Tier 2 after dependency changes:

```powershell
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

Begin now.

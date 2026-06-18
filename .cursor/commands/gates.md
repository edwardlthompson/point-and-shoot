# Local validation gates

Run Tier 0–1 host validation (PowerShell from repo root):

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_check_batch_commands.ps1
.\scripts\pns_local_dev_parallel.ps1
.\scripts\pns_prerelease_gate.ps1 -SkipGradle
```

Report pass/fail per script. Fix failures in scope before marking BUILD_PLAN items complete.

Begin now.

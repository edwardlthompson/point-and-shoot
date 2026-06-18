# Post-release regression

After a GitHub release or major merge to main:

```powershell
.\scripts\pns_repro_build_verify.ps1
.\scripts\pns_sbom.ps1 -Verify
.\scripts\pns_fixture_dng_gates.ps1
.\scripts\pns_check_github_ci.ps1 -WaitSeconds 300
```

Append regressions to `docs/AGENT_REGRESSION_MEMORY.md` and promote intake rows in `BUILD_PLAN.md` when needed.

Begin now.

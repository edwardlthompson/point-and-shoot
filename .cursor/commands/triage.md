# Weekly security triage

Review Dependabot alerts (Critical/High first); triage open PRs.
Confirm **Toolchain verify**, **Security scan**, and **CodeQL** green on main:

```powershell
.\scripts\pns_check_github_ci.ps1 -WaitSeconds 300
```

See `SECURITY.md` and `.github/dependabot.yml`.

Begin now.

# Post-push CI poll

After pushing to main, poll required GitHub workflows until green:

```powershell
.\scripts\pns_check_github_ci.ps1 -WaitSeconds 300
```

Required: **Toolchain verify**, **Security scan**, **CodeQL**. Skip if nothing pushed yet.

Begin now.

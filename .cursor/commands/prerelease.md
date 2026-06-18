# Pre-release gate

```powershell
.\scripts\pns_prerelease_gate.ps1 -SkipGradle
```

Optional USB lane when device online:

```powershell
.\scripts\pns_prerelease_gate.ps1 -IncludeUsb
```

Confirm zero Critical/High Dependabot alerts when `gh` is authenticated. Do not `/push` until this gate passes.

Begin now.

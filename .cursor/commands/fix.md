# Gate autofix (feature scope)

Autonomous feature step with changelog autofix + Tier 0:

```powershell
.\scripts\pns_watch_agent_gates.ps1 -Step tier0 -Autofix
```

If exit 1: read gate output under `hfr-runs/`; fix lint/tests in active feature scope; re-run Tier 2:

```powershell
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

Push to remote still requires `/push`, `/ship`, or explicit user approval.

Begin now.

# Feature vertical slice step

Execute the active BUILD_PLAN feature row only (one feature per task). Read `KNOWLEDGE_BASE.md` and relevant `.cursor/rules/*-lock.mdc` before capture/fleet/chrome edits.

After each [AGENT] step:

```powershell
.\scripts\pns_watch_agent_gates.ps1 -Step tier0
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

When capture/preview/fleet session code changes and a device is online (sequential, one serial):

```powershell
.\scripts\pns_capture_pipeline_verify.ps1
.\scripts\pns_chrome_ux_gate.ps1
adb shell am force-stop dev.pointandshoot
```

On failure, use `/debug` or escalate.

Begin now.

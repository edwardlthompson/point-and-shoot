# Session bootstrap (mature repo)

Point & Shoot is past Sprint 0. Refresh agent context instead of greenfield init:

1. Read `AGENT_MEMORY.md`, `BUILD_PLAN.md` active board, and `KNOWLEDGE_BASE.md` index.
2. Confirm `scripts/pns_adb_device.env` when more than one ADB target exists.
3. Run:

```powershell
.\scripts\pns_validate_bootstrap.ps1
.\scripts\pns_check_batch_commands.ps1
```

4. Pick Cursor mode per task (Plan for architecture; Agent for implementation).

Begin now.

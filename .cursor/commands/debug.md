# Defect investigation (Debug Mode)

Collect runtime evidence first (logcat, `hfr-runs/` artifacts, CI log URL, repro steps).
Grep `docs/AGENT_REGRESSION_MEMORY.md` and `KNOWLEDGE_BASE.md` before editing locked subsystems (DNG, chrome, fleet, GLES preview).

Confirm repro on device via repo scripts when applicable. Switch to Agent Mode to apply fix; append `REG-*` row after USB-proven fix.

Begin now.

# Save session checkpoint

Before clearing chat, write `.cursor-session-state` from current context (schema: `.cursor-session-state.example`):

- active milestone / sprint from `BUILD_PLAN.md`
- device serial / `pns_adb_device.env` status
- last gate results and `hfr-runs/` artifact paths
- open blockers and next steps

Do not commit this file (gitignored). Delete after the next chat reads it via `/restore`.

Begin now.

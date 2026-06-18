# Local device + GitHub setup

1. Copy `scripts/pns_adb_device.env.example` → `scripts/pns_adb_device.env` (gitignored). Set `PNS_ADB_SERIAL` from `adb devices`.
2. Optional: `PNS_ADB_ROOT_AVAILABLE=1` when `adb shell su` works.
3. With `gh` authenticated: verify branch protection and required checks (**Toolchain verify**, **Security scan**, **CodeQL**). Manual [HUMAN] steps in `CONTRIBUTING.md` if API 422.

Begin now.

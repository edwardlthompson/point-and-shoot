# Fleet multi-device test regiment (Milestone 18.4)

**Primary USB device:** OnePlus 12 **CPH2583** (`scripts/pns_adb_device.env` → `PNS_ADB_SERIAL`).

**Optional OP13 regression:** CPH2655 — `pns_op13_regression_pack.ps1` only; not default fleet truth.

## Env file extensions

Copy `scripts/pns_adb_device.env.example` and set:

```env
PNS_ADB_SERIAL=b5214fc6
# Optional multi-device list (comma-separated) for regression pack:
PNS_FLEET_SERIALS=b5214fc6,other_serial
PNS_FLEET_OS_FLAVOR=lineage
PNS_ADB_ROOT_AVAILABLE=0
```

## Tiers (`pns_fleet_regression_pack.ps1`)

| Tier | Scope |
|------|--------|
| **1** | Host: toolchain + catalog gate |
| **2** | USB primary: matrix scan + parity sweep **Quick** |
| **3** | All `PNS_FLEET_SERIALS`: matrix + parity **Quick** per serial |

## Rules

- Do **not** run `pns_chrome_ux_gate` and `pns_photo_capture_verify` **in parallel** on one device.
- After every ADB session: `adb shell am force-stop dev.pointandshoot`.
- Attach `pns_fleet_matrix_diff.ps1` output when matrix-affecting changes land.

## Artifacts

- `hfr-runs/fleet_regression_pack_*/regression_pack.json`
- `docs/FLEET_REGRESSION_PACK_HISTORY.jsonl`

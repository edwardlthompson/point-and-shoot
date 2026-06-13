# ADR-0004 — Fleet device matrix as capability source of truth

- **Status:** Accepted
- **Date:** 2026-06-12

## Context

Early development used legacy SKU-only branches (`LegacyDeviceFleetPolicy`, per-device markdown policies, `fleet_focal_map.json` alone). Onboarding new devices required copying OnePlus-13-specific gates, causing drift and false fleet blockers.

## Decision

1. **SoT:** `files/fleet_device_matrix.json` built by **`FleetDeviceMatrixBuilder`** (quick/full scan tiers).
2. **Policy:** default **`GenericFleetPolicy`** for new SKUs; **`LegacyDeviceFleetPolicyPlugin`** opt-in only.
3. **Consumer chrome:** **`FleetUiVisibilityGate`** reads matrix + catalog — hide unavailable controls, root-only toast for privileged features.
4. **No new legacy SKU-only gates** without a `FleetDevicePolicy` plugin + USB proof on an onboarded verify-matrix row.
5. **Parity:** **`pns_fleet_parity_sweep.ps1 -Mode Full|Delta`** drives honesty/debt intake into **`BUILD_PLAN.md`**.

## Consequences

- Matrix-affecting changes require rescan or `pns_fleet_matrix_scan.ps1` + diff in PR notes.
- `docs/FLEET_ONEPLUS13_RAW_POLICY.md` remains a **legacy plugin reference**, not default fleet behavior.
- Primary USB development device: OnePlus 12 **CPH2583**.

## References

- [`docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`](../FLEET_DEVICE_CAPABILITY_MATRIX.md)
- [`docs/FLEET_DEVICE_VERIFY_MATRIX.md`](../FLEET_DEVICE_VERIFY_MATRIX.md)
- [`.cursor/rules/fleet-generic-policy.mdc`](../../.cursor/rules/fleet-generic-policy.mdc)
- Milestone 16–18 in [`BUILD_PLAN_COMPLETED.md`](../../BUILD_PLAN_COMPLETED.md)

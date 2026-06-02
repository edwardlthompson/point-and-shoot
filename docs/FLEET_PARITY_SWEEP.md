# Fleet Parity Sweep (FPS)

**Log tag:** `PNS.FleetParity` · **Script:** `scripts/pns_fleet_parity_sweep.ps1` · **Gates:** `scripts/pns_m21_gate.ps1`, `scripts/pns_m22_gate.ps1`

Compares **advertised** fleet capabilities (matrix + catalog) vs **proven** behavior on a USB device. Outputs honest gap breakdown (schema **v2**), consumer-impact tiers, and auto-generated closure plan.

## Modes (required)

| Mode | Duration | Use |
|------|----------|-----|
| `Quick` | ~2–3 min | CI smoke; ≥54 quick cells |
| `Full` | ~4–8 min | Every catalog row; supports `-IncludeRecord` and `-IncludeProofPack` |
| `Delta` | varies | Partial / Planned rows only |

```powershell
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Quick -SkipMatrixRefresh
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Full
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Full -IncludeRecord -IncludeProofPack
.\scripts\pns_fleet_parity_sweep.ps1 -HostOnlyFixture   # logcat parser fixture (no device)
.\scripts\pns_m21_gate.ps1 -Serial b5214fc6
```

**Do not** invoke without `-Mode` (exit **2**). Host pass uses in-app JSON (`run-as files/parity_report_{mode}.json`); logcat is fallback only.

## Artifacts

Under `hfr-runs/parity_sweep_*/`:

- `in_app_parity_report.json` — schema `pns.fleet_parity_sweep.v2` (source of truth)
- `parity_report.json` — host wrapper with `gapBreakdown`, `shipBlockerGapCount`
- `proof_pack/parity_proof_results.json` — schema `parity_proof_results.v1` (when `-IncludeProofPack`)
- `parity_closure_plan.md`, `parity_ship_blockers.md`
- Rolling: `docs/FLEET_PARITY_LATEST.json`, `docs/FLEET_PARITY_HISTORY.jsonl`

## Gap classes (M21/M22)

See `FleetParitySweep.GapClass` — Full pass fails only on **ship_blocker** + blocking gap (`GAP_ADVERTISED_NOT_PROVEN`, `GAP_DELIVERY_MISMATCH`, `GAP_REGRESSION_SINCE_BASELINE`).

M22 proof-pack merge behavior:
- `parity_proof_results.v1` rows with `pass=true` set `provenOk=true` in merged in-app cells.
- `skippedReason=matrix_gate:*` rows are treated as honest matrix-gated closure in merge.
- Remaining `GAP_UNAUTOMATED`/`GAP_ADVERTISED_NOT_PROVEN` after merge are closure blockers for `pns_m22_gate.ps1`.

## In-app

Hub → **Run Parity Sweep** → mode sheet. Reports written to `files/parity_report_{mode}.json` + appended to `PROBE_EXPORT_LATEST.md`.

ADB: `pns_auto_parity_sweep=true` + `pns_parity_sweep_mode=quick|full|delta`.

## Integration

- Tier 2 of `scripts/pns_fleet_regression_pack.ps1` calls `-Mode Quick`
- M21 gate: JVM golden sweep + catalog gate + USB Quick/Full on CPH2583

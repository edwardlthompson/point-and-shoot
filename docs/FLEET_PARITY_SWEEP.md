# Fleet Parity Sweep (FPS)

**Log tag:** `PNS.FleetParity` · **Script:** `scripts/pns_fleet_parity_sweep.ps1`

Compares **advertised** fleet capabilities (matrix + catalog) vs **proven** behavior on a USB device. Outputs a measurable gap report and auto-generated closure plan.

## Modes (required)

| Mode | Duration | Use |
|------|----------|-----|
| `Quick` | ~3–5 min | CI smoke; rows with existing script coverage |
| `Full` | ~15–30 min | Every catalog row; optional `-IncludeRecord` |
| `Delta` | varies | Rows changed since last `catalogVersion` / matrix diff |

```powershell
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Quick
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Full -IncludeRecord
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Delta
```

**Do not** invoke without `-Mode` (exit **2**). Agents must **AskQuestion** before running when the user did not specify a mode — see `AGENTS.md`.

## Artifacts

Under `hfr-runs/parity_sweep_*/`:

- `parity_report.json` / `parity_report.md`
- `parity_closure_plan.md` (ordered sprint hints)
- `delivery_mismatch.json` / `delivery_mismatch.md` (**Full** mode + record cells)
- Rolling: `docs/FLEET_PARITY_LATEST.json`, `docs/FLEET_PARITY_HISTORY.jsonl`

## In-app

Hub → **Run Parity Sweep** → mode sheet (Quick / Full / Delta).

ADB unattended: `pns_auto_parity_sweep=true` + `pns_parity_sweep_mode=quick|full|delta`.

## Integration

- Tier 2 of `scripts/pns_fleet_regression_pack.ps1` calls `-Mode Quick`
- M19 gate: `GAP_ADVERTISED_NOT_PROVEN` count must decrease vs M18 baseline

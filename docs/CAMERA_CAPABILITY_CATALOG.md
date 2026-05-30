# Camera capability catalog (Milestone 17)

Human-maintained taxonomy + auto-generated per-device evaluation from `files/fleet_device_matrix.json`.

## On-device artifacts

| File | Role |
|------|------|
| `files/fleet_device_matrix.json` | Machine SoT (optional `capabilityCatalog` + `catalogVersion`) |
| `files/fleet_device_capability_summary.md` | Human-readable summary for PC debugging |

## ADB pull

```powershell
adb exec-out run-as dev.pointandshoot cat files/fleet_device_matrix.json
adb exec-out run-as dev.pointandshoot cat files/fleet_device_capability_summary.md
```

Host: `scripts/pns_fleet_matrix_scan.ps1` pulls both after hub scan.

## Code

- `CameraCapabilityCatalog.kt` — static registry (~40 seed rows; expand toward ~200)
- `CameraCapabilityCatalogBuilder.kt` — evaluates registry against matrix JSON
- `FleetCapabilitySummaryMarkdown.kt` — markdown renderer
- `FleetDeviceMatrixStore.saveWithArtifacts()` — writes JSON + summary on every scan save
- `FleetMatrixHubScreen.kt` — unified **Device capability matrix** hub (Milestone **17.3**): Summary / By camera / Features (search) / Raw JSON tabs; ADB copy; export JSON + summary

## Rescan

Quick tier on hub open; **Rescan full** for complete stream/format/face inventory. Invalidates on fingerprint or `appVersionCode` change.

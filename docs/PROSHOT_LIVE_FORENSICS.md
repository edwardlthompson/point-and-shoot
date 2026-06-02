# ReferenceCam live ADB forensics

Monitor **ReferenceCam** (`com.riseupgames.proshot2`) while capturing RAW/DNG on each rear lens — same leaf ids as P&S (**3** UW, **2** wide, **4** tele).

## Script

```powershell
.\scripts\pns_proshot_live_forensics.ps1 -Serial legacy serial
```

| Flag | Meaning |
|------|---------|
| (default) | **Manual** — 20 s per lens; you switch lens + shoot; logcat + DNG pull |
| `-TryUiAutomation` | Tap approximate lens row + shutter (1440×3168); use `-Calibrate` first |
| `-PerLensSec 25` | Longer window per lens |
| `-Calibrate` | Screenshot → tune tap coords in script header |

## Artifacts (`hfr-runs/proshot_live_forensics_*`)

- `proshot_live_logcat.txt` — full stream while session runs
- `proshot_live_logcat_filtered.txt` — Camera2 / HAL / DngCreator needles
- `camera_events_after_*.txt` — `CameraService::connect … camera ID N` for ReferenceCam PID
- `proshot_uw_3.dng`, `proshot_wide_2.dng`, `proshot_tele_4.dng` — pulled when detected
- `proshot_live_parse.json` — ISO, exposure, Bayer means per file

## May 2026 automation note

Blind UI taps often **do not** change ReferenceCam lens (session stayed on **camera 2**). For reliable per-lens forensics, run **manual** mode and confirm logcat shows `connect call … camera ID 3` then `2` then `4`.

## Compare to P&S

After ReferenceCam pulls, run P&S parity in the **same scene**:

```powershell
.\scripts\pns_proshot_reference_sync.ps1 -Serial legacy serial   # optional: refresh fixtures
.\scripts\pns_proshot_parity_gate.ps1 -Serial legacy serial
```

See decompile still-IQ checklist: `docs/PROSHOT_APK_FLEET_ANALYSIS.md` (AE precapture, lens shading map, tonemap on still).

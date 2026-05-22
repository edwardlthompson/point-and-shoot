# Sprint 13V.12 — Battery / thermal monitoring

## Behavior

- On **video-primary** preview (or while recording), when the session is **high-drain**:
  - **HFR:** preview FPS target ≥ **120**
  - **DCG:** research DCG HDR enabled in HUD, or ADB `pns_preview_video_dcg`
- A compact chip at the **bottom-start** of the finder shows:
  - Battery **%** and estimated **%/hr** drain (30 s rolling window minimum)
  - **THERMAL** line when [PowerManager.getCurrentThermalStatus] is **MODERATE** or worse (API 29+)
- Toggle: **Settings → HUD → Power + thermal (HFR / DCG)**

## ADB gate

```powershell
.\scripts\pns_power_thermal_verify.ps1
```

Extras:

- `pns_preview_primary_photo=false`
- `pns_preview_video_fps=120`
- `pns_preview_force_power_thermal=true` (shows HUD without waiting for user FPS pick)

## Sprint PO.2 — adaptive FPS + background pause

- **Adaptive FPS:** [PreviewAdaptiveFpsPolicy] polls every 3 s; clamps preview FPS when battery ≤30% or thermal ≥ MODERATE. User intent stored in `userSelectedFps`; effective `selectedFps` restores when conditions improve. Log: `PNS.PowerThermal adaptiveFpsCap userFps=… effective=…`.
- **Background pause:** `ON_PAUSE` sets [PreviewLongRunningPause] + `PreviewController.lifecycleBackgroundPaused` (skips optional YUV). FPS sweep waits in place and resumes after `ON_RESUME` (`kickPreviewPipelineRestart` reattaches analysis). Log: `longRunningPaused=true/false`.

### PO.2 ADB gate

```powershell
.\scripts\pns_battery_life_test.ps1
```

Extras (phase 1): `pns_preview_adaptive_battery_pct=15`, `pns_preview_video_fps=120`, video-primary.

## Log needles

| Tag | Needle |
|-----|--------|
| `PNS.AdbValidation` | `preview forcePowerThermalOverlay=true` |
| `PNS.PowerThermal` | `battery=… highDrain=true fps=120` |
| `PNS.PowerThermal` | `adaptiveFpsCap … effective=60` (PO.2 gate) |
| `PNS.PowerThermal` | `longRunningPaused=true` / `longRunningPaused=false` |

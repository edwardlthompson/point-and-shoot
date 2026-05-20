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

## Log needles

| Tag | Needle |
|-----|--------|
| `PNS.AdbValidation` | `preview forcePowerThermalOverlay=true` |
| `PNS.PowerThermal` | `battery=… highDrain=true fps=120` |

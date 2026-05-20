# Sprint 13V.10 — Focus peaking for manual-focus video

## Behavior

- **Peaking** is drawn in the GLES preview path (`lut_preview_external.frag.glsl`) using luminance gradients on the external-OES camera texture.
- **HUD:** Settings → Preview & keys → Focus peaking (color + sensitivity), or quick access from the same sheet in the 7×3 grid expander.
- **Manual focus:** Set the command dial to **M**. Preview uses fixed focus distance (`LENS_FOCUS_DISTANCE`, AF off). **Drag vertically** on the live finder to move focus (down = closer).
- **Video:** In **M** dial while in-app recording, peaking is forced on (default **Red**) even if HUD peaking is **Off**, so manual-focus video is visible without changing saved prefs.

## ADB gate

```powershell
.\scripts\pns_focus_peaking_verify.ps1
```

Intent extras:

- `pns_preview_primary_photo=false` (video-primary)
- `pns_preview_dial=M`
- `pns_preview_focus_peaking=Red`
- `pns_preview_automation_in_app_video_sec=6`

Pass criteria: logcat contains `PNS.FocusPeaking` with `manualFocus active=true` during recording, plus `start/finished in-app video automation`.

## Log needles

| Tag | Needle |
|-----|--------|
| `PNS.FocusPeaking` | `manualFocus active=true diopters=… recording=true` |
| `PNS.AdbValidation` | `preview seeded focusPeakingColor=Red` |

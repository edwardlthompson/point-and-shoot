# Sprint 13V.11 — LUT preview for video

## Behavior

- GLES preview applies a 3D LUT via `lut_preview_external.frag.glsl` (`LutCameraPreviewRenderer`).
- **Photo-primary:** idle preview uses **still LUT**; when recording video, switches to **video LUT**.
- **Video-primary:** preview always uses **video LUT** (before and during record).
- **HUD:** Readout strip **Video LUT** chip, or Settings → HUD → LUT chips (STILL / VIDEO).

LUT affects **live preview only** — encoded MP4 from `MediaRecorder` is not LUT-graded in this sprint.

## ADB gate

```powershell
.\scripts\pns_video_lut_preview_verify.ps1
```

Extras:

- `pns_preview_primary_photo=false`
- `pns_preview_video_lut=PnsCinematic`
- `pns_preview_automation_in_app_video_sec=6`

## Log needles

| Tag | Needle |
|-----|--------|
| `PNS.AdbValidation` | `preview seeded videoLut=PnsCinematic` |
| `PNS.LutPreview` | `previewLut=PnsCinematic videoPrimary=true lutEnabled=true` |
| `PNS.LutPreview` | `previewLut=PnsCinematic recording=true lutEnabled=true` |

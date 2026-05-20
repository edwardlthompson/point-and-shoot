# Sprint 13V.13 — Storage remaining indicator

## Behavior

- On **video-primary** preview or while **recording**, a bottom-start chip shows estimated record time left:
  - **REC · N min left** — encoded MP4 (`VideoFormatPresets.calculateBitrate` + audio/container overhead)
  - **RAW · N min left** — MCRAW-class lane (16-bit payload × resolution × fps)
- **LOW STORAGE** + red text when **&lt; 5 minutes** remain at the current estimate.
- Toggle: **Settings → HUD → Storage remaining (video)**.

Free space is read from primary shared storage (`StatFs` on `Environment.getExternalStorageDirectory()`), same tree as `DCIM/Point & Shoot`.

## ADB gate

```powershell
.\scripts\pns_storage_remaining_verify.ps1
```

Extras:

- `pns_preview_primary_photo=false`
- `pns_preview_video_fps=120`
- `pns_preview_storage_available_bytes=600000000` (override for deterministic math check)

## Log needles

| Tag | Needle |
|-----|--------|
| `PNS.AdbValidation` | `storageAvailableBytesOverride=600000000` |
| `PNS.StorageRemain` | `minutes=… bytesPerSec=… avail=600000000 warn=false` |

The gate script verifies `minutes ≈ avail / bytesPerSec / 60` within 1 minute.

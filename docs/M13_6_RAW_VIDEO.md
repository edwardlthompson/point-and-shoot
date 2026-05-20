# Sprint 13.6 — RAW video (MCRAW-class)

## What ships

- **Container:** P&S `PNMRAWV1` … `PNMRAWEND` (documented in [RawVideoWriter.kt](../app/src/main/java/dev/pointandshoot/RawVideoWriter.kt)); `.mcraw` extension in DCIM.
- **Path:** Preview REGULAR session RAW `ImageReader` → [RawVideoRecordingController] (no `MediaRecorder`).
- **Fleet:** OnePlus 13 leaf cameras (`2` / `3` / `4`) via [OnePlus13FleetPolicy].
- **UI:** Settings → HUD → **Video: RAW lane** (mutually exclusive with research DCG HDR).
- **ADB:** `--ei pns_preview_video_raw_sec N` with `--es pns_preview_camera_id 2` (wide recommended).

## USB verification

```powershell
.\scripts\pns_raw_video_verify.ps1 -Serial 8bf09993
```

Pass: `rawVideoSaved ok=true`, `frames≥1`, pulled file magic `PNMRAWV1`, bytes ≥ 64 KiB.

## Logcat needles

- `PNS.RawVideo` / `PNS.AdbValidation`: `rawVideoStart`, `rawVideoSaved ok=true frames=… bytes=…`

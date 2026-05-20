# Sprint 13.4 — DCG session alignment (encoded HDR video)

## What ships

1. **Session:** `DcgSessionParameters` sets Qualcomm **`EnableHDRDCGMode`** on REGULAR `SessionConfiguration.setSessionParameters` when:
   - HUD **Research: DCG HDR mode**, or
   - ADB **`pns_preview_video_dcg=true`**
2. **Encode:** `resolveInAppVideoFormat()` picks **`VideoFormat` DCG** (HEVC Main10HDR10 + `isHdr10`) for the same conditions.
3. **AF bracketing** can merge into the same session template when enabled.

DCG is capped at **60 fps** (Qualcomm ISP — no DCG + HFR).

## USB verification

```powershell
.\scripts\pns_video_hdr10_metadata_verify.ps1 -Serial 8bf09993
```

Pass criteria (`results.json`):

- `dcgSessionTemplate=true` — `PNS.AdbValidation` / `PNS.DcgSession`
- `inAppVideoFormatDcg=true`
- `inAppVideoSaved ok=true`
- ffprobe: **bt2020** + **smpte2084** + **MaxCLL > 0**

## Logcat needles

- `dcgSessionTemplate=EnableHDRDCGMode cam=`
- `sessionTemplate EnableHDRDCGMode type=`
- `inAppVideoFormat=DCG`
- `mcVideoPrepared` / `MediaCodecVideoRecorder started`

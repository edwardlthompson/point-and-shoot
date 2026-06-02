# Milestone 13.6 — RAW video (MCRAW-class) design

**Status:** Shipped May 2026. Device gate: `scripts/pns_raw_video_verify.ps1`. Runbook: **`docs/M13_6_RAW_VIDEO.md`**.

## Reference

MotionCam: native `RawEncoder`, `.mcraw` container, dedicated capture session — see **`docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`**.

**P&S direction:** Java Camera2 REGULAR session (no `MediaRecorder` on RAW lane), MotionCam-informed frame packing, legacy device first via **`FleetCameraProfile`**.

## Session topology

| Lane | Session | Outputs |
|------|---------|---------|
| Preview + still | Existing REGULAR | Preview + RAW still + optional JPEG |
| RAW video | **Dedicated** REGULAR (or shared with preview TBD) | Preview + RAW stream → `RawVideoWriter` |

RAW video **mutually exclusive** with DCG HDR10 encode when ISP cannot sustain both (UI gate).

## Components (planned)

| Class | Role |
|-------|------|
| `RawVideoRecordingController` | Lifecycle, surface, start/stop |
| `RawVideoWriter` | Frame dequeue → `.mcraw` or interim raw sequence |
| `FleetCameraProfile.supportsRawVideo` | legacy device enable; others false |

## ADB automation (planned)

```text
am start … --es pns_screen preview --ei pns_preview_video_raw_sec 5
```

Log needles: `PNS.RawVideo` `rawVideoSaved ok=true bytes=…`

## Verification

- `pns_raw_video_verify.ps1` PASS on legacy SKU
- Artifact under `hfr-runs/raw_video_verify_*`
- `adb shell am force-stop dev.pointandshoot` after run

---

*Host doc — implementation requires USB.*

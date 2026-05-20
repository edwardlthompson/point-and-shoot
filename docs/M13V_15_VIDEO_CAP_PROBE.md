# Sprint 13V.15 — MediaCodec capability probe

## Behavior

On app start, [`MediaCodecCapabilityProbe`](../app/src/main/java/dev/pointandshoot/MediaCodecCapabilityProbe.kt) scans HEVC encoders via `MediaCodecList` and logs a capability matrix to **`PNS.VideoCapProbe`**:

- `capProbeResult` — encoder count, Main10/HDR10/YUVP010 flags, max FPS per tier
- `encoder name=…` — per-codec max resolution and performance-point count
- `perfPoint WxH@Ffps` — confirmed Android **performance-point** guarantees

The cache feeds [`VideoFormatPresets.getHardwareTiers`](../app/src/main/java/dev/pointandshoot/VideoFormatConfig.kt) and [`fpsOptionsForResolution`](../app/src/main/java/dev/pointandshoot/MediaCodecCapabilityProbe.kt).

## ADB gate

```powershell
.\scripts\pns_video_capability_probe.ps1
```

Cold preview launch (`pns_screen=preview`); probe runs from [`PnsApplication`](../app/src/main/java/dev/pointandshoot/PnsApplication.kt) on a background coroutine.

**Pass criteria (CPH2655-class reference):**

| Check | Needle |
|-------|--------|
| Probe ran | `capProbeResult` in log |
| 1080p@120 | `perfPoint 1920x1080@120fps` |
| 4K@120 | `perfPoint 3840x2160@120fps` (or 4096×2160@120) |
| Main10 | `main10=True` in `capProbeResult` |
| YUVP010 | `yuvp010=True` in `capProbeResult` |

Artifacts: `hfr-runs/video_cap_probe_*/probe.json`, `logcat.txt`.

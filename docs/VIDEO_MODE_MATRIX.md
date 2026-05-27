# Video mode matrix (Milestone 15.3)

Generated: 20260526_074218 device run. Artifacts: `hfr-runs\video_matrix_verify_20260526_074218`.

| Row | Saved | A/V | fps ratio | Codec | Pass | Note |
|-----|-------|-----|-----------|-------|------|------|
| 1080p60_h264 | True | True | 1.004 | h264 | True |  |
| 1080p30_hevc | True | True | 0.994 | hevc | True |  |
| 1080p120_h264 | True | True | 1 | h264 | True |  |
| 4k30_h264 | True | True | 1 | h264 | True |  |
| 4k30_hevc | True | True | 0.994 | hevc | True |  |
| 8k30_h264 | True | True | 1 | h264 | True |  |

**8K (15.4):** On **CPH2655** (`20260526_074218`), **8k30_h264** records with A/V present via MediaCodec + aligned preview buffer. 
If a device row shows **unavailable**, session configure failed or HAL lacks 8K outputs — see picker banner and `gate.json` `maxFps8k` / `supports8k`.


# Sprint 13.8c — HDR still

## Product choice (MVP)

- **Burst of 3 DNGs** per shutter (not a single merged RAW).
- **EV spacing:** ±1 EV around metered middle (`BracketPattern.Three`, `evStep = 1.0`).
- **Filenames:** `hdr1of3-{groupingId}` … `hdr3of3-{groupingId}`.
- **Post:** merge in Lightroom / ACR / external HDR tool.

Deferred: in-app ISP-neutral blend into one DNG before `DngCreator`.

## Code path

1. HUD / ADB `StillCaptureMode.HdrStill` (`pns_preview_still_mode=hdr`).
2. Shutter → `captureRawStill` → `captureHdrStillBurst`.
3. `captureBracketBurst(purpose = HdrStill)` — no **BKT** dial required.

## USB verification

```powershell
.\scripts\pns_still_mode_benchmark.ps1 -Serial legacy serial -Mode hdr -Repeats 1
```

Grep:

- `preview seeded stillMode=HdrStill`
- `captureHdrStill 1/1 ok=true frames=3`
- `dng save diag stillMode=HdrStill … bracketStop=`

**2026-05-20** (`hfr-runs/still_mode_bench_20260520_014958/`): M14/M23/M73 each saved `hdr3of3-…` DNG; desktop open gate **PASS**.

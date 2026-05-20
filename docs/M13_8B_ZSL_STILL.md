# Sprint 13.8b — ZSL still

## Behavior

- **HUD / ADB:** `StillCaptureMode.ZslStill` via Settings or `pns_preview_still_mode=zsl`.
- **Ring:** `ZslStillFrameRing` (capacity **6** on OnePlus 13 via `OnePlus13FleetPolicy.zslStillRingCapacity()`).
- **Fill:** preview `TotalCaptureResult` on every frame; RAW `Image` when `attachZslRawRingListener()` receives buffers and capture is not busy.
- **Shutter:** `takeBestForStill()` → same `Dng12Saver` path as Standard (`zslFromRing=true` in `PNS.CaptureStill` `dng save diag`).
- **Miss:** `zsl still ring miss … fallback=standard_capture` → existing one-shot RAW still.

## USB verification (CPH2655)

```powershell
.\scripts\pns_still_mode_benchmark.ps1 -Serial 8bf09993 -Mode zsl -Repeats 1
```

**2026-05-20:** `hfr-runs/still_mode_bench_20260520_014059/` — capture + desktop openability **PASS**; all three slots logged `ring miss` / `zslFromRing=false` (HAL does not deliver preview RAW into the ring on cold scripted runs; fallback still saves valid DNGs).

## Grep needles

- `preview seeded stillMode=ZslStill`
- `zsl still ring hit` / `zsl still ring miss`
- `dng save diag … zslFromRing=`

# Milestone 13.7 — Fleet RAW gate

Automated rows for **Milestone 13** closure are **PASS** on reference fleet **CPH2655** (`8bf09993`, May 2026). **Milestone 13.7** cannot fully close until **Milestone H → Sprint H.7** human sign-off.

## Automated checklist (PASS)

| Check | Script / evidence |
|-------|-------------------|
| Host toolchain | `pns_verify_toolchain.ps1 -RunTests` |
| RAW still regression | `pns_capture_pipeline_verify.ps1` |
| DNG openability | `pns_m13_3g2_gate.ps1` / `pns_aux_dng_capture_analyze.ps1` |
| Aux capture 3/3 | `hfr-runs/aux_dng_capture_analyze_20260519_235745/` |
| Still modes + benchmark | `pns_m13_8d_gate.ps1` → `hfr-runs/m13_8d_gate_20260520_020059/` |
| Daylight gate | `hfr-runs/m13_3f_gate_20260520_012341/` |
| DCG encoded video | `pns_video_hdr10_metadata_verify.ps1` |
| RAW video lane | `pns_raw_video_verify.ps1` |
| Lock / wide-cal bisect | No lock shipped; **13.3h** H1–H3 FAIL open gate (documented) |

## Human blocker (H.7)

| Item | Artifact |
|------|----------|
| ACR 3/3 M14/M23/M73 | `ACR_HUMAN_VERIFY.md` in aux DNG run folder |
| Visual aux color vs ProShot | Same folder + daylight notes |
| Standard / ZSL / HDR ACR compare | `STILL_MODE_COMPARE.md` in **13.8d** gate folder |

Record pass: `pns_m13_3g2_gate.ps1 -Dir <aux_dir> -RecordAcrPass` and optional `pns_m13_8d_gate.ps1 -RecordHumanPass`.

## Known HAL gap (documented, not gate-blocking for ship prep)

- **ProShot parity (rawpy):** UW/tele **FAIL** vs reference fixtures — HAL ColorMatrix2 / leaf calibration; see **`docs/M13_3F_DAYLIGHT_GATE.md`**.

## Host-only gate (no device)

```powershell
.\scripts\pns_m13_7_host_gate.ps1
```

Runs toolchain tests and writes `hfr-runs/m13_7_host_gate_*/gate.json` with `humanBlocker=H.7` until sign-off files exist.

## Battery

All USB scripts call `am force-stop dev.pointandshoot` when finished.

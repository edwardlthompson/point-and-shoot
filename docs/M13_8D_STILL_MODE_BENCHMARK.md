# Sprint 13.8d — Still mode benchmark + three-way quality

## Scripts

| Script | Role |
|--------|------|
| `pns_still_mode_benchmark.ps1` | Per-mode capture (`-Mode standard\|zsl\|hdr\|all`); `results.json` + `report.md` |
| `pns_m13_8d_gate.ps1` | Pipeline verify (standard) + benchmark all + optional ProShot three-way session |
| `pns_dng_proshot_pns_session.ps1` | `-PnsStillModes standard,zsl,hdr`; `-PullMotionCamReference` optional |

## USB (CPH2655)

```powershell
.\scripts\pns_m13_8d_gate.ps1 -Serial 8bf09993
```

Shorter (benchmark only):

```powershell
.\scripts\pns_still_mode_benchmark.ps1 -Serial 8bf09993 -Mode all -Repeats 1
```

## Logcat needles

- `still timing stillMode=Standard|ZslStill|HdrStill`
- `captureHdrStill 1/1 ok=true frames=3`
- `zsl still ring hit` / `zsl still ring miss`
- `DNG DESKTOP OPEN GATE: PASS`

## Human sign-off

Fill `STILL_MODE_COMPARE.md` in the gate folder, then:

```powershell
.\scripts\pns_m13_8d_gate.ps1 -RecordHumanPass -Dir hfr-runs\m13_8d_gate_<ts> -ColorNote "ACR daylight OK on wide; UW/tele vs ProShot noted"
```

## Sprint check

- `pns_capture_pipeline_verify.ps1` with **`-PreviewStillMode standard`** must stay green.
- ZSL/HDR: openable DNGs via benchmark; HDR = 3 files per shutter.

**2026-05-20** (`hfr-runs/m13_8d_gate_20260520_020059/`): automated gate **PASS** (pipeline + standard/zsl/hdr openability). Human ACR: **`STILL_MODE_COMPARE.md`**.

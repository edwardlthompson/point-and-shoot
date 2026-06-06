# Camera2 vs OEM disparity methodology

Point & Shoot publishes the [Camera Parity Leaderboard](https://edwardlthompson.github.io/point-and-shoot/leaderboard/) to answer: **what can third-party Camera2 apps actually use on real devices?**

## What we measure

| Layer | Method | Ranked? |
|-------|--------|---------|
| **Camera2 tested** | Full in-app parity sweep (USB) | Yes |
| **Camera2 on stock** | Same sweep on stock ROM (separate line item) | Yes |
| **GSMArena advertised** | Host scrape of public spec pages | No — informational only |

We **do not** automate the OEM camera app. Stock app ISP pipelines are proprietary and not reproducible via Camera2.

## Resolution withholding

Android HALs may expose:

- **Default** `SCALER_STREAM_CONFIGURATION_MAP` — what most Camera2 apps get
- **Maximum-resolution** maps / high-res output sizes — often larger MP

The app records `stillResolutionAdvertised[]` per camera and computes **resolution betrayal index** (0–100): share of rear cameras where alternate maps exceed default path resolution.

## Camera2 full MP breakthrough

When a device **proves >12 MP per rear sensor** through Camera2 (default JPEG/RAW path, max-resolution HAL map with `hasLargerThanDefault`, or verified RAW capture), the public leaderboard highlights it as a **breakthrough** — the inverse of betrayal. Manifest/dumpsys focal MP overrides alone do **not** qualify; HAL or capture proof is required.

Per-SKU full-resolution capture unlock paths (beyond labeling):

| Device class | Labeling (host) | Capture unlock |
|--------------|-----------------|----------------|
| OnePlus 12 (CPH2583) | `fleet_focal_maxres_probe.py` + manifest bands | Root: `ExperimentalMaxResolutionUnlock` vendor prop; `PhotoResolutionMode.MaxResolution` when HAL exposes max-res map |
| OnePlus 13 (CPH2655/2649) | Same host probe + manifest seed | USB-verify `PhotoResolutionMode.MaxResolution` + parity sweep |
| Sony Xperia PRO-I (XQ-BE62) | ~12 MP is OEM truth | No override expected; 12 MP default is correct |
| Other fleet SKUs | Dumpsys WxH parse first; GSMArena fallback only when max-res blocks present | Matrix `camera2FullMpBreakthrough` from `stillResolutionAdvertised` |

## ROM pairing protocol

For custom-ROM buyers, publish **both** when possible:

1. Camera2 sweep on **custom ROM** (tested)
2. Camera2 sweep on **stock ROM** (tested)
3. **GSMArena advertised** (always separate column — never merged)

Product pages show fixed columns with `—` when a line item is missing.

## Restriction Index

Per-OEM composite from published fleet devices:

- **Openness %** — proven ÷ HAL-advertised parity cells
- **Gate honesty %** — sessionOk ÷ advertised feature gates
- **Restriction Index** = 100 − weighted composite (higher = more restrictive to third-party apps)

## Citation format

> Camera2 parity data from Point & Shoot Fleet Parity Leaderboard (Full sweep, API level noted per device).  
> URL: https://edwardlthompson.github.io/point-and-shoot/leaderboard/

## Automation

- In-app: `FleetParitySweepRunner` · `ResolutionBetrayal` · `LeaderboardReadiness`
- Host: `scripts/pns_leaderboard_site_publish.ps1` · `scripts/pns_fleet_parity_sweep.ps1`

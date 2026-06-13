# Store screenshots (en-US)

F-Droid expects PNGs under:

- `phoneScreenshots/` — at least one phone capture
- `sevenInchScreenshots/` — at least one 7" tablet-class capture

## Refresh from USB (preferred)

```powershell
.\scripts\pns_sideload_and_launch.ps1 -LaunchScreen preview
.\scripts\pns_device_screencap.ps1 -OutPath metadata\en-US\images\phoneScreenshots\01_preview.png
# Tablet: repeat on tablet device or resize only after maintainer sign-off
adb shell am force-stop dev.pointandshoot
```

## Interim assets

Current PNGs were seeded from `docs/screenshots/` (2026-06 engineering captures) until a maintainer USB refresh for F-Droid submission. Replace before fdroiddata MR.

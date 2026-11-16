# Fastlane images (F-Droid scrape path)

F-Droid reads **`fastlane/metadata/android/`** at the repo root (not `examples/android/fastlane/`).

| Path | Source |
|------|--------|
| `icon.png` | Copied from `metadata/en-US/icon.png` |
| `phoneScreenshots/` | Real device captures (same PNGs as `metadata/en-US/images/phoneScreenshots/`) |
| `sevenInchScreenshots/` | Real device captures (same PNGs as `metadata/en-US/images/sevenInchScreenshots/`) |

Refresh via `scripts/pns_device_screencap.ps1` and copy into **both** trees (or update `metadata/` then re-copy). Do not invent UI screenshots.

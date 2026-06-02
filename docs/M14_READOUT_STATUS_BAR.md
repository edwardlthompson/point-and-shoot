# Milestone 14 — readout chips + top status bar (canonical)

**Purpose:** Prevent regressions where video mode shows photo-only chips (Still LUT, IMG) or hides the recording timer.

**Last verified:** 2026-05-21 on legacy device `legacy serial` (`pns_video_status_bar_verify.ps1`).

---

## Photo vs video readout (`PreviewReadoutStrip`)

Controlled by tray **Photo/Video** → [`primaryPhoto`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt) → [`PreviewReadoutChipMode`](app/src/main/java/dev/pointandshoot/PreviewReadoutChipMode.kt).

| Chip / control | Photo (`primaryPhoto == true`) | Video (`primaryPhoto == false`) |
|----------------|-------------------------------|--------------------------------|
| ISO, Ss, WB, AF | Yes | Yes |
| Still LUT | **Yes** | **No** |
| IMG (RAW/JPEG pipeline) | **Yes** | **No** |
| Video LUT | **No** | **Yes** |
| Video format picker | **No** (tray FAB) | **No** (tray FAB) |
| Legacy RES chip | No | No |

**Required wiring in `PreviewEngineContent`:**

```kotlin
PreviewReadoutStrip(
    primaryPhoto = primaryPhoto,
    // no video format slot in readout row
)
```

**Log on mode change:** `PNS.ChromeUx readoutMode=photo|video`.

**Do not** pass pipeline hints into the readout row — use [`PreviewTopStatusBar`](app/src/main/java/dev/pointandshoot/PreviewTopStatusBar.kt) + [`previewStatusBarLine`](app/src/main/java/dev/pointandshoot/PreviewTopStatusBar.kt).

---

## Video format FAB (shutter tray, gallery cluster)

| Control | Location | Style |
|---------|----------|--------|
| [`PreviewTrayVideoFormatFab`](app/src/main/java/dev/pointandshoot/VideoFormatPickerSheet.kt) | **Shutter tray** start row — immediately **right of gallery thumb** when `PreviewTrayVideoChrome.showVideoFormatFab` | 52dp bordered FAB (same family as Photo/Video toggle) |

Opens [`VideoFormatPickerSheet`](app/src/main/java/dev/pointandshoot/VideoFormatPickerSheet.kt). Shutter stays **geometric center** of the tray (not grouped with the FAB). Log: `PNS.ChromeUx trayVideoFormatFab=visible anchor=galleryThumb`.

**Do not** put the format picker in the readout chip row — it does not fit at phone widths.

---

## Top inset band (status bar + selfie ring)

1. [`PreviewTopStatusBar`](app/src/main/java/dev/pointandshoot/PreviewTopStatusBar.kt) — timecode + audio meters + status line.
2. [`PreviewSelfieRingIndicator`](app/src/main/java/dev/pointandshoot/PreviewSelfieRingIndicator.kt) — centered when front camera active.

---

## Smile to capture (photo mode only)

- Toggle: Eye AF menu or Settings → `enableSmileTriggeredStill`.
- **Scans continuously** on the YUV analysis stream while enabled (`wantYuv` + `processYuvForHighlight`); not a one-shot.
- **Photo mode only** — video tray ignores smile (`PNS.SmileStill smileCapture skipped: video mode`).
- Requires a **face in frame** (ML Kit); logs `PNS.SmileStill smileScan prob=…` every ~3s while hunting.
- After a trigger, **4.5 s cooldown** before the next auto still.
- **Gallery thumb** — successful stills must update `lastGalleryUri` (`PNS.ChromeUx galleryThumbUpdated`); smile path must not use an empty `captureComposedStill` callback.

---

## Automation gates

| Script | Asserts |
|--------|---------|
| `scripts/pns_video_status_bar_verify.ps1` | `statusBar=visible`, `readoutMode=video`, `audioMeters=true` |
| `PreviewReadoutChipModeTest` / `PreviewTrayVideoChrome` | Chip + FAB visibility matrix |

---

## Common regression signatures

- **IMG + Still LUT in video mode:** `primaryPhoto` not passed to `PreviewReadoutStrip`.
- **Format chip back in readout:** `videoFormatChipSlot` reintroduced — use tray FAB only.
- **No record timer:** `PreviewTopStatusBar` missing from top inset.
- **Smile never fires in video:** expected — switch to **Photo** on the tray.

---

## Related

- [`docs/preview-chrome-layout-style-guide.md`](preview-chrome-layout-style-guide.md)
- `.cursor/rules/preview-readout-video-mode-lock.mdc`

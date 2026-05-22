# Preview chrome layout & style guide (canonical)

This document is the **source of truth** for the approved **portrait** preview-engine chrome: vertical bands, insets, finder sizing, faint dividers, and how that layout interacts with the **frozen** **7×3** quick-grid name + focal row + **two** sticky shortcut rows (see `.cursor/rules/preview-chrome-ui-lock.mdc`).

**Baseline:** User-approved UI **locked in code** as of **2026-05** (`PreviewEngineScreen` portrait column). When the stack or inset policy changes, update this file in the same change.

---

## Stack order (top → bottom)

On the live preview route (`PreviewEngineScreen` → `PreviewEngineContent`), the main column is always, in order:

1. **Top inset band** — Clears **status bar** and **display cutout** using merged window insets from `rememberSystemInsetsDp()` (system bars ∪ cutout). Padding passed into chrome is **`SystemInsetsDp.asPaddingValues()`** — **single** top value, not doubled. Implemented as its own charcoal **`Box`** with `height = padding.calculateTopPadding()`; **not** part of the finder clip rect. Horizontal and bottom insets stay on the outer `Modifier.padding(...)` of the chrome root (`start` / `end` / `bottom`; top is **0** there so the band is a real layout slot).
   - **`PreviewTopStatusBar`** — timecode + audio meters (video record) + orange status line (`previewStatusBarLine`). See **`docs/M14_READOUT_STATUS_BAR.md`**.
   - **`PreviewSelfieRingIndicator`** (front camera) — centered rotating orange arc in this band; not over the finder.

2. **Section divider** — `PreviewChromeSectionDivider()` (faint horizontal rule).

3. **Finder (preview) band** — `BoxWithConstraints` with **`Modifier.weight(PreviewChromeFinderFlexWeight)`** (baseline **2.9f**), full width, **`clip(RectangleShape)`** so overlays do not paint into lower bands when collapsed.

   **3:4 tile inside the band (canonical behavior):**

   - Target aspect **width / height = 3 / 4**.
   - `idealTileH = maxWidth / (3/4)` (full-width 3:4 height).
   - If **`idealTileH <= maxHeight`**: tile is **`maxWidth` × `idealTileH`** (full width, no side letterboxing inside the band). Content is **`Alignment.BottomCenter`** so any extra band height is **above** the live preview (toward the status bar); the preview sits flush above the readout.
   - Else if the slot is “wide” vs height: height-limited tile with **side** padding inside the band (centered).
   - Else: width-limited tile (centered).

   Inner **`PreviewMainViewport`** fills the tile; stream **cover vs contain** remains a **behavior** toggle (`previewTextureCoverCrop`), not a change to this band’s outer geometry unless spec’d.

4. **Section divider** — `PreviewChromeSectionDivider()`.

5. **Readout chips** — `PreviewReadoutStrip` (ISO, shutter, WB, AF, mode-specific LUT). **Photo:** Still LUT + IMG. **Video:** Video LUT only (no IMG / Still LUT). Requires `primaryPhoto` at the call site — **`docs/M14_READOUT_STATUS_BAR.md`**. Own height; **clipped** to its lane.

6. **Section divider** — `PreviewChromeSectionDivider()`.

7. **Quick settings** — `PreviewRightRail` / `PreviewChromeGrid7x3` (focal row + **two** logical shortcut icon rows + shortcut dialogs). **`Modifier.weight(PreviewChromeRailFlexWeight)`** (baseline **1f**); **clipped** to its lane. **Settings** expand tile at **row 2, column 6** (`settingsAt=r2c6` in **`PNS.ChromeUx`**). Total **physical** grid rows in the rail = **3** (focal + two shortcut rows).

8. **Shutter bar** — `PreviewBottomCaptureTray` when gallery thumb, on-screen shutter, or command dial is shown. Fixed height; **clipped** to its lane. **Tray:** gallery thumb + **video format FAB** (start row, video mode), **geometric-center** shutter (breathing room vs left/right clusters), **Photo/Video** toggle + mode dial (end). Divider above when present follows the same divider component.

---

## Visual language

- **Separators:** Thin, low-contrast **horizontal** rules between major bands (`PreviewChromeSectionDivider`) so regions read as distinct **without** altering quick-grid cell math, gaps, or chip styling inside the grid.
- **Chrome background:** Charcoal band under system insets; finder tile sits on the same family of tones as the rest of preview chrome (see `PnsColors` usage in `PreviewEngineScreen`).
- **Grid / readout / tray:** Locked styling and spacing per **`preview-chrome-ui-lock.mdc`** — this guide does not redefine tile internals; it defines **where** they sit relative to the finder and readout.

---

## Rules

- **Collapsed chrome must not intrude** on an adjacent band: no drawing, shadows, or hit targets from one band should spill into another. Use **clip rects** on each major band; popups are the intentional exception.
- **Popups** (`DropdownMenu`, modal `Dialog`, etc.) may draw over the finder or other bands.
- **Do not merge bands** (e.g. do not move the readout inside the finder `Box`) without redesigning this guide and the lock rule together.
- **Device evidence:** Capture checks with `scripts/pns_device_screencap.ps1` (use **`-Serial`** when more than one device is online or to override `PNS_ADB_SERIAL`). Store under `hfr-runs/` or `docs/screenshots/` as needed.

---

## Settings popups (menus)

In-preview **Settings**, **Guides**, and related modal sheets use **`docs/preview-chrome-settings-style-guide.md`** and **`PreviewChromeMenuUi.kt`** (not this layout guide).

---

## Related code (pointers)

| Piece | Location |
|--------|-----------|
| Portrait column, bands, dividers, clips, flex constants | `PreviewEngineScreen.kt` — `PreviewChromeFinderFlexWeight`, `PreviewChromeRailFlexWeight`, `PreviewEngineContent` |
| Insets → padding for preview route | `PreviewEngineScreen.kt` — `insets.asPaddingValues()` into `PreviewEngineContent` |
| Merged system-bar + cutout insets | `SystemInsets.kt` — `rememberSystemInsetsDp()`, `asPaddingValues()` |
| Optional **2×** top helper (not used by preview route) | `SystemInsets.kt` — `asPaddingValuesWithExtraTopBarBand()` |
| Readout strip + photo/video chip policy | `PreviewReadoutStrip.kt`, `PreviewReadoutChipMode.kt`, **`docs/M14_READOUT_STATUS_BAR.md`** |
| Top status bar + selfie ring | `PreviewTopStatusBar.kt`, `PreviewSelfieRingIndicator.kt` |
| Quick grid (7 cols × focal row + **two** shortcut rows; **Settings** at **r2c6**) | `PreviewEngineScreen.kt` — `previewChromeGridSlots`, `PreviewChromeGrid7x3` |
| Section divider | `PreviewChromeSectionDivider` in `PreviewEngineScreen.kt` |
| Settings popup chrome | `PreviewChromeMenuUi.kt`, **`docs/preview-chrome-settings-style-guide.md`** |

---

## Checklist for future changes

- [ ] Bands stay in the order above unless the product spec changes.
- [ ] Finder overlays stay inside the finder’s clipped subtree, or use a popup layer.
- [ ] New rows between finder and shutter get their own divider + clip story.
- [ ] Any intentional visual change updates **`preview-chrome-ui-lock.mdc`** and **this guide** together.

# Preview chrome — settings popups & menu style guide

Canonical styling for **in-preview menus** opened from the **7×3** quick grid (Settings, Guides, Preview & keys, Capture & tools) and nested sub-pages. Layout of the portrait stack (finder, grid, tray) lives in **`docs/preview-chrome-layout-style-guide.md`** — this guide covers **popups only**.

**Code:** [`PreviewChromeMenuUi.kt`](../app/src/main/java/dev/pointandshoot/PreviewChromeMenuUi.kt)  
**Lock:** Behavioral fixes only in [`preview-chrome-ui-lock.mdc`](../.cursor/rules/preview-chrome-ui-lock.mdc); visual/menu changes require explicit product approval.

---

## Popup shell (modal `Dialog`)

| Token | Value | Usage |
|--------|--------|--------|
| Surface shape | `RoundedCornerShape(16.dp)` | All grid-expanded sheets |
| Surface color | `PreviewChromeMenuColors.dialogSurface` (`#1A1A1A`) | Same as `Surface` in `PreviewEngineScreen` settings dialog |
| Tonal elevation | `6.dp` | Material3 `Surface` |
| Inner padding | `12.dp` | Column inside surface |
| Max width | `420.dp` | `widthIn(max = 420.dp)` |
| Column spacing | `10.dp` | Between blocks |
| Scroll | `verticalScroll` on column | Long sheets (About, HUD, Capture) |

### Header row (every sheet)

- **Title:** `MaterialTheme.typography.titleMedium`, white, centered, `padding(horizontal = 72.dp)` (room for back + Close).
- **Back** (nested sub-pages): `TextButton` + `Icons.AutoMirrored.Outlined.ArrowBack`, white 0.9 alpha, start-aligned.
- **Close:** `TextButton` + `"Close"`, white 0.85 alpha, end-aligned — dismisses dialog and clears sub-page state.
- **Divider:** `HorizontalDivider(color = PreviewChromeMenuColors.divider)` under header.

---

## Component families

### Intro line

- **`ChromeSettingsIntroText`** — `bodySmall`, `introText` (white 65%). One short paragraph under the divider.

### Section headings

- **`PreviewRailSectionTitle`** — `titleSmall`, `PnsColors.PhotoOrange`, top 10.dp / bottom 4.dp padding.

### Navigation rows (drill-down)

- **`RailSettingsMenuEntryCard`** — `Card` 12.dp radius, `menuCardFill` (white 8%). Title `titleSmall` white; subtitle `bodySmall` intro color; trailing `ArrowForward` 45% white.

### Toggles (quick settings in sheets)

- **`PreviewRailSettingToggle`** — title `bodyLarge` white; subtitle `bodySmall` 62% white; trailing Material3 **`Switch`**. Row vertical padding 6.dp.

### Action chips (presets & primary actions)

- **`FpsQuickChip`** — 44.dp height (or `fillMaxTile` in grid); 10.dp radius; border/bg per selected / root / disabled; label `labelLarge`. Used for FPS, crop/grid presets, Venmo/reset-style actions in sheets.

### Inset panels (dense text / probe data)

- **`ChromeInsetPanel`** / **`ChromeMonospaceBlock`** — 12.dp radius, `insetPanelFill` (black 45%). Body `bodySmall`, white 85%.

---

## Sheet inventory (must match this guide)

| Sheet | Entry | Components |
|--------|--------|------------|
| Settings home | Settings tile → home | `ChromeSettingsIntroText`, `RailSettingsMenuEntryCard` |
| Guides & framing | Settings → Guides, or Guides tile | intro + menu cards; crop/grid sub-panes use **`FpsQuickChip`** |
| Target FPS | Settings → Target frame rate | intro + **`FpsQuickChip`** grid |
| HUD & readouts | Settings → HUD | section titles, toggles, **`HudToggle`**, intro |
| Preview & behavior | Settings → Preview | section titles, **`PreviewRailSettingToggle`**, menu cards |
| Capture & stills | Settings → Capture | section titles, toggles, menu cards |
| **About & heritage** | Settings → About | section titles, intro, inset panels, Venmo **`FpsQuickChip`** |
| Preview & keys | Grid shortcut | same as Preview sub-page |
| Capture & tools | Grid shortcut | same as Capture sub-page |

**Do not** use full-screen `OutlinedButton` Back, default Material3 `Button`, or raw `LazyColumn` full-bleed About on the preview route — About belongs in the Settings dialog sub-page ([`AboutRailSheetContent`](../app/src/main/java/dev/pointandshoot/AboutScreen.kt)).

---

## FABs & tray (cross-reference)

- **Video format FAB:** `PreviewTrayVideoFormatFab` — 52dp, gallery-thumb anchor; see **`docs/M14_READOUT_STATUS_BAR.md`**.
- **Shutter / mode dial / Photo·Video toggle:** `PreviewBottomCaptureTray` — locked in layout guide; not settings popups.

---

## Grid quick tiles (cross-reference)

- **`IconCubeVectorButton`** + `chromeChipStyle` — 7×3 grid; focal row uses **`FpsQuickChip`** with `fillMaxTile`.
- Colors align with **`FpsQuickChip`** / `PnsIcons` tile family.

---

## Maintenance

When adding a new settings sub-page:

1. Reuse types from **`PreviewChromeMenuUi.kt`** (do not duplicate colors/radii in screen files).
2. Add a row to the **Sheet inventory** table above.
3. Wire nested back + title in `PreviewEngineScreen` settings `Dialog` (`settingsSubPage` / `guidesPane`).
4. Log `PNS.ChromeUx expandShortcuts=surface=modalDialog` unchanged (host tag only).

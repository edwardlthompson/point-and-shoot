# Settings persistence audit (May 2026)

Scope: inventory of primary persisted stores after the IMG composed-intent work. Uninstall/reinstall remains out of scope per product direction.

## Confirmed persisted (SharedPreferences / repo helpers)

- **`HudSettings`** (`pns_hud_settings`) — Pro HUD toggles, LUT names, OIS / stab / HDR preview / research flags, hardware JPEG ISP bias, software JPEG companion quality, AF settle toggles, bracket pattern, legacy global command dial mode (synced from active tray), **composed IMG tiers** (`img_menu_*`), **last raw imaging profile id** for BKT, legacy `imaging_profile` key (kept in sync from composed storage profile).
- **`PreviewLastSurfacePrefs`** (`pns_preview_last_surface`) — last Photo / Video / Gallery tray surface for cold start.
- **`PreviewTrayModeStore`** (`pns_tray_mode_settings`) — per-tray FPS, OIS/EIS, shutter angle, prime eq, readout locks, and **command dial** (`photo_dial` / `video_dial`); cold start + Photo↔Video restore.
- **`PreviewChromePreferences`** — preview chrome: geotag, self-timer, flash, texture crop, tap-to-capture, chart corners, DND, on-screen shutter, **stillCaptureJpegCompanion** (synced from IMG `-JPEG-` row when RAW is on), in-app video encode size, etc.
- **Composition guides / welcome hints** — via `rememberCompositionGuideSettings` / related stores (see `CompositionGuide*` in codebase).
- **`PnsUpdatePrefs`** (`pns_updates`) — last-seen app version, last GitHub check timestamp, dismissed update version, last-known GitHub APK version, last cached release notes, optional pending Install (url/sha/version) after unknown-sources Settings, optional ETag + cached `/releases` JSON. Automatic checks skip metered / offline networks. **Device-local only:** not in Android backup allow-lists and not in [SettingsExportBundle].
- **`PnsJpegCreditPrefs`** (`pns_jpeg_credits`) — optional JPEG Artist / Copyright. Exported in [SettingsExportBundle] and Android-backed up. **Never** written to DNG.

## Session-only / automation (by design)

- **`pns_preview_*` ADB extras** — many seeds apply via `applySessionOnly` and are not written to disk.
- **Ephemeral UI** — snackbars, transient menus, sweep jobs.

## Gaps / follow-up (not fully enumerated)

- A full line-by-line pass over **every** `remember { mutableStateOf(...) }` in Compose trees (probe hub, diagnostics, chrome overlays) was not completed in this pass; several are intentionally non-persistent (animation / gesture tips).
- **`DataStore`**: not used in this app; no migration needed.

When adding new user-facing sliders or toggles, wire through an existing prefs object (`HudSettings.save`, `PreviewChromePreferences.update`, or `HudSettings.saveComposedStillIntent`) and extend this document with one line.

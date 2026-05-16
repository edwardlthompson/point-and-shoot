# Settings persistence audit (May 2026)

Scope: inventory of primary persisted stores after the IMG composed-intent work. Uninstall/reinstall remains out of scope per product direction.

## Confirmed persisted (SharedPreferences / repo helpers)

- **`HudSettings`** (`hud_prefs`) — Pro HUD toggles, LUT names, OIS / stab / HDR preview / research flags, hardware JPEG ISP bias, software JPEG companion quality, AF settle toggles, bracket pattern, command dial mode, **composed IMG tiers** (`img_menu_*`), **last raw imaging profile id** for BKT, legacy `imaging_profile` key (kept in sync from composed storage profile).
- **`PreviewChromePreferences`** — preview chrome: geotag, self-timer, flash, texture crop, tap-to-capture, chart corners, DND, on-screen shutter, **stillCaptureJpegCompanion** (synced from IMG `-JPEG-` row when RAW is on), in-app video encode size, etc.
- **Composition guides / welcome hints** — via `rememberCompositionGuideSettings` / related stores (see `CompositionGuide*` in codebase).

## Session-only / automation (by design)

- **`pns_preview_*` ADB extras** — many seeds apply via `applySessionOnly` and are not written to disk.
- **Ephemeral UI** — snackbars, transient menus, sweep jobs.

## Gaps / follow-up (not fully enumerated)

- A full line-by-line pass over **every** `remember { mutableStateOf(...) }` in Compose trees (probe hub, diagnostics, chrome overlays) was not completed in this pass; several are intentionally non-persistent (animation / gesture tips).
- **`DataStore`**: not used in this app; no migration needed.

When adding new user-facing sliders or toggles, wire through an existing prefs object (`HudSettings.save`, `PreviewChromePreferences.update`, or `HudSettings.saveComposedStillIntent`) and extend this document with one line.

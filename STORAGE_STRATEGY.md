# Storage strategy

This document records the decision and rationale for **where Point & Shoot writes capture outputs** (DNG, AVIF, JXL) and how those outputs become discoverable by gallery apps and desktop tooling. It satisfies BUILD_PLAN §9 ("Storage + media indexing strategy").

## TL;DR (default per profile)

| Imaging profile | Format(s) | Default destination | Indexed in MediaStore? | Manual export available? |
|---|---|---|---|---|
| **Standard Pro** | `.dng` (lossless) + `.avif` (10-bit HDR) | `MediaStore.Images` (`DCIM/Point & Shoot/`) | Yes | Yes (SAF "Save as ...") |
| **Ultra-Max** | `.dng` (RAW12 uncompressed) + `.jxl` (12-bit) | `MediaStore.Images` (`DCIM/Point & Shoot/Ultra-Max/`) | Yes for DNG; AVIF / JXL added when the system MediaProvider learns the MIME types | Yes (SAF "Save as ...") |
| **Probe artifacts** | `.json`, `.md`, `.txt` | App-private external files (`getExternalFilesDir(null)`) | No | Yes (`adb pull` or in-app SAF export) |

Rationale and the constraints behind each row are below.

## Constraints

1. **FOSS-only.** No Play Services SDKs. Optional **UX.3 cloud backup** copies captures to a **user-chosen SAF folder** (Syncthing / Nextcloud / Drive app, etc.) — see `CloudCaptureBackup.kt`. Android Auto Backup still restores HUD/chrome prefs only (not DCIM blobs).
2. **legacy device / LineageOS 23 / Android 16 / API 36.** All scoped-storage rules apply (`READ_MEDIA_IMAGES`, `MANAGE_EXTERNAL_STORAGE` deliberately not requested).
3. **No `READ_EXTERNAL_STORAGE` legacy fallback.** `minSdk = 28` already implies scoped storage; we keep the permission set minimal.
4. **Outputs must be openable in desktop tooling** (`darktable`, `RawTherapee`, `dcraw`, `libavif`, `libjxl` reference decoder).
5. **Probe artifacts must NOT pollute the user's gallery.** They are developer / engineering data, not photos.

## Capture outputs (DNG / AVIF / JXL)

### Default: `MediaStore` insert into `DCIM/Point & Shoot/`

* Subfolder per imaging profile (`DCIM/Point & Shoot/` for Standard Pro, `DCIM/Point & Shoot/Ultra-Max/` for Ultra-Max). Stills and video share the same DCIM tree so gallery apps index alongside the device camera roll.
* Filenames use a deterministic pattern: `pns_<utc>_<profile>_<seq>.<ext>` (e.g., `pns_20260507T203015Z_standard_pro_0001.dng`).
* MIME types:
  * `image/x-adobe-dng` for DNG (recognized by AOSP MediaProvider since Q).
  * `image/avif` for AVIF (recognized in Android 14+).
  * `image/jxl` for JXL (still being adopted by AOSP MediaProvider; we write the file regardless and best-effort insert).
* Pending bit: `IS_PENDING = 1` while the saver is writing; cleared on flush so partial files are never visible to gallery apps.

### Alternative: SAF "Save as ..." (one-shot export)

* Backed by `ActivityResultContracts.CreateDocument(mimeType)`.
* Used when the user wants to write directly into another app (Files / Material Files / Termux $HOME).
* The probe screen already uses this pattern for `PROBE_RESULTS_*.md` exports (`exportLauncher` in `CameraCapabilitiesProbe.kt`); the capture pipeline will reuse it.

### Alternative: app-private external files

* `context.getExternalFilesDir("captures")` -> `/storage/emulated/0/Android/data/dev.pointandshoot/files/captures/`.
* Used in **diagnostics mode** so we can spam captures during testing without touching the user's gallery.
* Cleaned up automatically when the app is uninstalled.

## Probe artifacts (JSON / MD / TXT)

* Always written to **app-private external files** under `getExternalFilesDir(null)` so:
  * They survive `adb pull` for `pns_hfr_autorun.ps1`.
  * They are removed on uninstall.
  * They never appear in the gallery.
* The Markdown probe report can additionally be exported via SAF when the user taps "Export Markdown" on the probe home.

## Validation gates (BUILD_PLAN §9)

* [ ] [ADB] Outputs are written reliably with predictable naming.
  * Implementation gate (Phase 1): unit test on the filename builder; on-device smoke after first capture.
* [ ] [ADB] Files appear in gallery apps (when MediaStore) / export works (when app-private).
  * Validation gate: scroll the LineageOS Gallery + run `cmd content query --uri content://media/external/images/media` after a capture; **`pns_adb_preview_validate.ps1`** writes **`mediastore_probe.json`** (`dcimHasPnsCapture`, `mediaTailPnsRows`, optional `mediaTailPnsDisplayNameHits`) plus **`ls_dcim_point_and_shoot.txt`** (ampersand-safe **`ls`** + **`Ultra-Max/`** + **`find`**) and **`mediastore_images_tail.txt`** under the run folder (BUILD_PLAN Sprint 7.3). **`dcimHasPnsCapture`** reflects **`pns_*.{dng,jxl,avif}`** on disk; **`mediaTailPnsRows`** may stay **0** on OEMs where the tail does not include **`relative_path`**. Prior false **`dcimHasPnsCapture=false`** was caused by **`adb shell`** splitting **`Point & Shoot`** on **`&`** when **`ls`** arguments were not one quoted shell command.
* [ ] [HOST] Pull + verify files open in desktop tooling.
  * Validation gate: **`scripts/pns_pull_dcim_captures.ps1`** (or manual **`adb pull '/sdcard/DCIM/Point & Shoot'`** …), then open in `darktable` (DNG), `imv`/`gthumb` (AVIF), and `djxl` (JXL).

## Open questions (to resolve in Phase 1)

* **JXL MediaProvider support on LineageOS 23:** if the MIME type is rejected by the insert call, fall back to a `.jxl` file in the Documents collection or app-private storage and surface a one-time UI hint.
* **HEIF / HEIC compatibility:** out of scope (proprietary HEVC encoder dependency). We deliberately ship AVIF (FOSS AV1) and JXL instead.
* **DCIM vs `Pictures/`:** The app inserts under **`DCIM/Point & Shoot/`** via **`MediaStore` `RELATIVE_PATH`** (see **`CaptureStorage.kt`**) so outputs sit in the conventional camera-roll tree; we are not using a `Pictures/…`-only layout for indexed stills.

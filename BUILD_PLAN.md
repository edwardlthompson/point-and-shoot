## Build plan (Point & Shoot)

**Purpose:** Milestones → sprints → gates. Active work here; shipped bodies in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

- **Settings truth:** `docs/PNS_TECHNICAL_SETTINGS.md` — update on every settings/pipeline change
- **Audit log:** `PROBE_BUILD_PLAN.md` §5/§6 · `CHANGELOG.md` · `CLI_BUILD_AND_SIDELOAD.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` (bisect locks §9)
- **Fleet/DNG:** `docs/FLEET_ONEPLUS13_RAW_POLICY.md` · `docs/DNG_OPENABILITY_REGRESSIONS.md` · `docs/RAW_REFERENCE_APP_MATRIX.md` · `docs/M13_7_GATE.md` · `docs/M14_12_DUAL_VIDEO.md`

---

### How agents must execute

1. **One milestone at a time.** Finish every sprint before starting the next.
2. **Tasks in order within a sprint.** Blockers → log in `PROBE_BUILD_PLAN.md` §5.
3. **After each sprint:** run the sprint gate. On failure, stop and fix.
4. **After all sprints:** run the Milestone gate before proceeding.
5. **Tick rules:** Never `[x]` without Appendix A. Host: `pns_verify_toolchain.ps1 -RunTests` + `ReadLints`. Device: §5 evidence.
6. **UI gate:** Visible changes → `assembleDebug`, sideload, `pns_device_screencap.ps1` proof.
7. **JAVA_HOME / ADB:** Android Studio JBR; `platform-tools` first; `scripts/pns_adb_device.env` for `PNS_ADB_SERIAL`.
8. **Git:** commit + push after each numbered milestone gate passes.
9. **Hard rules — do not regress:** No `automationSuppressFacePipeline` for sequential RAW alone; no §4a `streamHints` or §2 RAW10-first `Default` without USB proof; capture/session/DNG changes → `pns_capture_pipeline_verify.ps1`; settings changes → update `docs/PNS_TECHNICAL_SETTINGS.md` same commit. Full locks: `AGENTS.md`, `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8, `.cursor/rules/`.
10. **Archive:** All `[x]` except `[HUMAN]` → move sprint to `BUILD_PLAN_COMPLETED.md`. Human rows stay in Milestone H.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, Detekt, lint, SBOM |
| `scripts/pns_capture_pipeline_verify.ps1` | USB RAW still gate |
| `scripts/pns_chrome_ux_gate.ps1` | Chrome UX (`-FocalMmSlot` for tele proof) |
| `scripts/pns_dual_video_verify.ps1` | Dual video (stacked composite + record) |
| `scripts/pns_about_links_verify.ps1` | Settings → About heritage |
| `scripts/pns_release_packaging.ps1` / `pns_release_automation.ps1` | Release APK + GitHub upload |
| `scripts/pns_adb_device.env` (gitignored) | Default **`PNS_ADB_SERIAL`** |

Full script index: **`AGENTS.md`**.

### Performance & responsiveness backlog — archived

All seven rows **`[x]`** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Performance & responsiveness backlog*).

### Backlog consolidation (active)

| Area | Status |
|------|--------|
| **Milestones 0–12** | Gates passed → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** |
| **Milestone 13 — Fleet RAW** | **Archived** — automated/USB **PASS**; human **H.7** only |
| **Milestone 13V — Video expansion** | **Archived** — **13V.1–13V.18** USB-verified **`8bf09993`** |
| **Milestone 14 — Preview polish & pro UX** | **Archived** — **14.1–14.13** → completed file; **H.8** subjective |
| **Milestone 15** | **Active** — sprints 15.0, 15.1–15.38, 15.B (see below) |
| **Milestone H** | **Active** — residual **[HUMAN]** work (M13 DNG, M14 glass/dual-video color; most tasks automated in Sprint 15.A) |
| **Bespoke Gallery (BG.1–BG.3)** | **Archived** — integration + device verify + UX polish (**maintainer sign-off 2026-05-22**) |
| **Audio & Sound (AS.1–AS.3)** | **Archived** — agent + human sign-off **2026-05-22** |
| **User Interface & Experience (UX.1–UX.3)** | **Archived** — theme, nav, workflow, cloud backup **2026-05-25** |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Future features (deferred — unscheduled)

- **OpenCamera-style toolbox** — former Sprint 10.14; descoped unless product requests.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | **M0–M7**; **M8–12**; performance backlog; **M13** **13.1–13.8** + **13.7 gate**; **M13V** **13V.1–13V.18**; **M14** **14.1–14.13** + gate; **Bespoke Gallery** **BG.1–BG.3**; **Audio & Sound** **AS.1–AS.3** |

**Open in this file:** **Milestone 15** · **Milestone H**

### Archiving completed sprints — procedure

1. Move a **`### Sprint`** only when **every** **`- [x]`** is done **except** **`[HUMAN]`** — those stay in **Milestone H**.
2. Cut sprint body → append under the right **`## Milestone`** in **`BUILD_PLAN_COMPLETED.md`**.
3. Replace in this file with a pointer to the archive.
4. Update the archive table and **`### Backlog consolidation`**.

---

## Bespoke Gallery Integration — archived

**BG.1–BG.3** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Bespoke Gallery Integration*). **Maintainer UX/UI sign-off:** 2026-05-22.

---

## Performance & Optimization — archived

**PO.1–PO.2** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Performance & Optimization*). **PO optimization gate PASS:** 2026-05-22 (`pns_po_optimization_gate.ps1`, `8bf09993`).

---

## Video Format & Quality Enhancements — archived

**VF.1–VF.3** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Video Format & Quality Enhancements*). **Docs:** [`docs/VIDEO_FORMAT_RECOMMENDATIONS.md`](docs/VIDEO_FORMAT_RECOMMENDATIONS.md). **Gate:** `scripts/pns_video_quality_gate.ps1` (host + USB when device attached).

---

## Audio & Sound Enhancements — archived

**AS.1–AS.3** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Audio & Sound Enhancements*). **Maintainer sign-off:** 2026-05-22 (agent + human). **Gates:** `scripts/pns_audio_sprint_gate.ps1`. **Docs:** [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) §10.1.

---

## Camera & Capture Enhancements — archived

**CC.1–CC.2** and **CC.3** (pro I/O: profiles, tether, flash strength, cal export/import) → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Camera & Capture Enhancements*). **Gates:** `scripts/pns_capture_modes_test.ps1`, `scripts/pns_pro_features_test.ps1`. **Docs:** [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) §5.3, §5.3.1.

---

## User Interface & Experience — archived

**UX.1–UX.3** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*User Interface & Experience*). **Maintainer sign-off:** 2026-05-25 (agent + human spot-check). **Gates:** `scripts/pns_ux_sprint_adb_gate.ps1`, `scripts/pns_cloud_backup_test.ps1`. **Docs:** `docs/PNS_TECHNICAL_SETTINGS.md` (UX + cloud backup).

---

## Integration & Platform Features — archived

**IP.1–IP.2** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Integration & Platform Features*). **USB gates (2026-05-25):** `scripts/pns_platform_integration_test.ps1`, `scripts/pns_connectivity_test.ps1` on fleet **`8bf09993`**. **Docs:** `docs/PNS_TECHNICAL_SETTINGS.md` (IP extras, LAN/WebDAV/social). **Note:** FTP/SMB are not embedded (FOSS); use LAN HTTP pull, WebDAV PUT, or [CloudCaptureBackup] SAF folder.

---

## Chart calibration — deferred tasks moved

Exit mode shipped (`exitChartCalibrationMode()` 2026-05-21). Remaining tasks → **Sprint 15.0**.

---

## Milestone 15 — Pro Camera Polish, Fleet Hardening & Color Fidelity

### Agent execution order

> Work sprints strictly in the sequence below. Complete and gate-pass each before starting the next. Cross-cutting rules apply to every sprint.

**Priority 1 — Blockers / cleanup (do first)**
15.0 → 15.1 → 15.2 → 15.3 → 15.4 → 15.5 → 15.8 → 15.9 → 15.10 → 15.12 → 15.15 → 15.B

**Priority 2 — Core pipeline & polish**
15.6 → 15.7 → 15.11 → 15.13 → 15.14

**Priority 2 — Pro Cinema & Color**
15.16 → 15.17 → 15.18 → 15.19 → 15.20 → 15.21 → 15.22

**Priority 2 — Pro Video Tools (Round 3)**
15.23 → 15.24 → 15.25 → 15.26 → 15.27 → 15.28 → 15.29 → 15.30 → 15.31

**Priority 2 — Pro UX & Sensor (Round 4)**
15.32 → 15.33 → 15.34 → 15.35 → 15.36 → 15.37 → 15.38

**Cross-cutting gate rules (every sprint):**
- Any change to `PreviewEngineScreen.kt`, `RawCaptureSupport.kt`, session create, or DNG pipeline → `pns_capture_pipeline_verify.ps1`
- Any video recording change → `pns_in_app_video_verify.ps1`
- Any audio change → `pns_in_app_video_verify.ps1` + verify logcat audio tags
- Any readout strip / chrome UI change → `pns_chrome_ux_gate.ps1`
- Every sprint → `pns_verify_toolchain.ps1 -RunTests` PASS

---

### Sprint 15.0 — Chart calibration cleanup *(Priority 1 — do before 15.1)*

- [ ] **[AGENT]** `ChartQuadDetector.kt`: auto-detect robustness on real ColorChecker (glare, skew, partial frame)
- [ ] **[AGENT]** `CalibrationWorkflow.kt`: post-apply parity — chart neutrals on JPEG + DNG sidecar
- [ ] **[AGENT]** Optional: continuous auto-detect while overlay on (debounced)
- [ ] **[AGENT]** `scripts/pns_colorchecker_de2000_gate.py` — rawpy + Macbeth patch location + dE2000 vs D50 reference; PASS when all patches < threshold
- [ ] **[AGENT]** `scripts/pns_passport_ce_values.py` — X-Rite constants → `tests/fixtures/passport_ce_values.json`

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + `pns_capture_pipeline_verify.ps1` PASS
**Code:** `ChartCalibrationApplyOverlay.kt`, `ChartQuadDetector.kt`, `CalibrationWorkflow.kt`, `docs/PNS_TECHNICAL_SETTINGS.md` §9.1

---

### Sprint 15.1 — Eye/face tracking coordinate fix *(Priority 1 — blocks H.8.1)*

**Problem:** Eye landmarks from `STATISTICS_FACES` / ML Kit misalign with actual positions. `FaceDetectAdapter` non-HFR path uses independent X/Y stretch instead of uniform scale+offset.

**Fix:** For all fps paths, apply uniform center-crop scale `max(W/frameW, H/frameH)` + centered offsets (same as ≥120 fps path / `TexturePreviewFit.mapBufferToView(coverCrop=true)`).

**Code:** `FaceDetectAdapter.kt`, `MlKitFaceTrackSupport.kt`, `TexturePreviewFit.kt`

**Tasks:**
- [ ] **[AGENT]** Log sensor orientation, active array, crop region, buffer size, fit mode on face frame
- [ ] **[AGENT]** Fix `FaceDetectAdapter` non-HFR path — uniform scale+centered offset
- [ ] **[AGENT]** Verify ML Kit path uses `mapYuvRectToFaceTrackBoxBuffer` consistently
- [ ] **[AGENT]** `scripts/pns_eye_af_pixel_gate.ps1` — screencap during H-dial + face visible; PIL diff eye-box vs expected region; PASS when delta < threshold
- [ ] **[ADB][HUMAN]** H.8.1: screencap proof — eye marks land on actual eyes (portrait + landscape)

**Gate:** `pns_chrome_ux_gate.ps1` + `pns_eye_af_pixel_gate.ps1` PASS; H.8.1 closes

---

### Sprint 15.2 — HEVC / H.265 color fix (≤60 fps) *(Priority 1 — blocks H.8.3)*

**Problem:** H.265 video has discolored output at ≤60 fps — missing explicit BT.709 VUI keys on HEVC encoder path.

**Fix:** Set `KEY_COLOR_STANDARD = COLOR_STANDARD_BT709`, `KEY_COLOR_RANGE = COLOR_RANGE_LIMITED`, `KEY_COLOR_TRANSFER = COLOR_TRANSFER_SDR_VIDEO` on HEVC `MediaFormat` before `configure()`. Same for `MediaRecorder` path.

**Code:** `MediaCodecVideoRecorder.kt`, `VideoRecordingController.kt`, `VideoFormatConfig.kt`

**Tasks:**
- [ ] **[AGENT]** Capture H.264 + H.265 1080p30 reference; diff VUI with ffprobe
- [ ] **[AGENT]** Add explicit BT.709 limited keys to HEVC encoder path (MediaCodec + MediaRecorder)
- [ ] **[AGENT]** Log `colorVui=` on every encode start
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §10
- [ ] **[AGENT]** `scripts/pns_hfr_color_compare_frames.ps1` — record 10 s H.264 + H.265 1080p30; ffmpeg decode 10 frames; compute mean YCbCr; assert Cb/Cr delta < 8
- [ ] **[ADB]** `pns_video_codec_color_compare.ps1` + `pns_hfr_color_compare_frames.ps1` PASS
- [ ] **[ADB][HUMAN]** H.8.3 visual: HEVC vs H.264 color match on real scene

**Gate:** `pns_video_codec_color_compare.ps1` PASS; H.8.3 closes

---

### Sprint 15.3 — Full video format A/V matrix verify *(Priority 1)*

**Scope:** Every format in `VideoFormatPresets.ALL_TIERS` × codec × fps shown in picker.

**Tasks:**
- [ ] **[AGENT]** Create `scripts/pns_video_matrix_verify.ps1` — record 5 s per format; ffprobe A/V stream presence + container fps ≥ 75% target + color VUI
- [ ] **[AGENT]** `scripts/pns_still_mode_compare_gate.ps1` — ADB capture Standard/ZSL/HDR; run `readout_jpeg_dng_luminance_compare.py`; write `STILL_MODE_COMPARE.md`
- [ ] **[AGENT]** Fix any format missing audio track or with 0-packet video
- [ ] **[AGENT]** Document failures in `docs/VIDEO_MODE_MATRIX.md`
- [ ] **[ADB]** Run on USB device; attach artifact

**Gate:** `pns_video_matrix_verify.ps1` — all picker rows: `avPresent=true`, fps ≥ 75% target

---

### Sprint 15.4 — 8K video diagnose and fix *(Priority 2)*

**Problem:** 8K (`7680×4320`) is in `ALL_TIERS` but recording fails.

**Tasks:**
- [ ] **[AGENT]** Probe `maxFps8k` from `MediaCodecCapabilityProbe`; log to `PNS.MCVideoRec`
- [ ] **[AGENT]** Add 8K diagnostic banner if unsupported; fix surface size negotiation if supported but broken
- [ ] **[AGENT]** Update `docs/VIDEO_MODE_MATRIX.md`
- [ ] **[ADB]** USB record test or confirmed unavailable log

**Gate:** `pns_video_matrix_verify.ps1` 8K row: confirmed unavailable with banner, or `avPresent=true`

---

### Sprint 15.5 — Dual video front-camera real fix *(Priority 1 — blocks H.8.2)*

**Problem:** Dual rear+front recording saves only back camera footage. Root cause: front `SurfaceTexture` not calling `updateTexImage()` on the GL thread before each composite frame.

**Fix:** Ensure `DualVideoFrontCameraController` provides a valid `SurfaceTexture` update listener that calls `updateTexImage()` on the GL thread before each composite frame.

**Code:** `DualVideoFrontCameraController.kt`, `DualVideoGlEncoderSink.kt`, `LutCameraPreviewRenderer.kt`

**Tasks:**
- [ ] **[AGENT]** Add front OES update-frame diagnostic log
- [ ] **[AGENT]** Fix front camera surface texture update path in stacked composite
- [ ] **[ADB]** `pns_dual_video_verify.ps1 -RecordSec 5` PASS + `inAppVideoSaved ok=true`
- [ ] **[HUMAN]** H.8.2 visual: stacked framing correct (rear top, front bottom)

**Gate:** `pns_dual_video_verify.ps1 -RecordSec 5` PASS; H.8.2 closes

---

### Sprint 15.6 — Preview shrink-to-fit letterboxing *(Priority 2)*

**Problem:** 16:9 video mode stretches the preview in a 3:4 tile. Dual-video halves should shrink-to-fit.

**Fix:** Pass video stream AR into `LutCameraPreviewRenderer.setGeometry` via existing `OnLayoutChangeListener` path. Derive letterbox/pillarbox sub-rect using `min(tileW/bufW, tileH/bufH)`.

**Code:** `LutCameraPreviewRenderer.kt`, `TexturePreviewFit.kt`, `PreviewMainViewport`

**Tasks:**
- [ ] **[AGENT]** Add `coverCrop: Boolean` flag to `setGeometry`; add `shrinkToFit` mode
- [ ] **[AGENT]** Wire video mode buffer AR → `setGeometry(coverCrop=false)` in video tray
- [ ] **[AGENT]** Dual-video stacked path: split tile into two `shrinkToFit` rects
- [ ] **[AGENT]** Unit test: `TexturePreviewFit` 16:9 buffer in 3:4 tile → expected pillarbox rects
- [ ] **[ADB]** Screencap: 16:9 video shows pillarbox bars; still shows full tile

**Gate:** `pns_chrome_ux_gate.ps1` PASS + screencap proof

---

### Sprint 15.7 — Gallery media aspect / letterbox *(Priority 2)*

**Problem:** Gallery viewer does not match preview tile geometry.

**Tasks:**
- [ ] **[AGENT]** Lock gallery display tile to 3:4 AR
- [ ] **[AGENT]** Apply `ContentScale.Fit` inside tile (letterbox/pillarbox)
- [ ] **[ADB]** `pns_device_screencap.ps1` proof: 16:9 photo shows horizontal bars

**Gate:** screencap proof + `pns_verify_toolchain.ps1 -RunTests` compile clean

---

### Sprint 15.8 — Settings grouping + engineering item removal *(Priority 1)*

**Problem:** Engineering/diagnostic items appear in user-facing Settings. QS grid missing key toggles. Groups not logical.

**New groups:** Capture · Video · Focus & Metering · Display · Connection & Backup · About · Developer (long-press gate for `enableResearch*` items).

**Tasks:**
- [ ] **[AGENT]** Move all `enableResearch*` items behind developer long-press gate
- [ ] **[AGENT]** Remove diagnostic probe text from user settings rail
- [ ] **[AGENT]** Reorganize `RailSettingsHomeContent` into new groups
- [ ] **[AGENT]** Add OIS + EIS toggles to QS grid
- [ ] **[AGENT]** Add ISO band cycle to QS grid
- [ ] **[AGENT]** `scripts/pns_a11y_dump_gate.ps1` — `uiautomator dump`; parse XML; assert zero interactive nodes lack `content-desc`
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md`
- [ ] **[ADB]** `pns_device_screencap.ps1` — no research items visible in user settings
- [ ] **[ADB]** `pns_a11y_dump_gate.ps1` PASS

**Gate:** `pns_chrome_ux_gate.ps1` PASS + `pns_a11y_dump_gate.ps1` PASS + no `enableResearch*` items in user rail screencap

---

### Sprint 15.9 — EIS / OIS separate toggles *(Priority 1)*

**Problem:** EIS and OIS exist as separate `HudSettings` but are not exposed as separately-labeled, clearly-described controls.

**Tasks:**
- [ ] **[AGENT]** Settings → Video: "Optical stabilization (OIS)" + "Electronic stabilization (EIS)" with descriptions
- [ ] **[AGENT]** Wire both to QS grid (added in 15.8)
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §9

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + `pns_chrome_ux_gate.ps1` PASS

---

### Sprint 15.10 — ISO band highlight + locked-axis auto fix *(Priority 1)*

**Problem:** (A) No orange highlight on selected ISO band. (B) Locked-SS → auto-ISO chase not working.

**Tasks:**
- [ ] **[AGENT]** Render selected `ReadoutIsoBand` with `PnsColors.PhotoOrange` tint in ISO menu
- [ ] **[AGENT]** Audit + fix `ReadoutExposureChase` locked-SS→auto-ISO chase loop
- [ ] **[AGENT]** Audit + fix locked-ISO→auto-SS chase loop
- [ ] **[AGENT]** Add `readoutChase iso=… ss=… coupling=…` diagnostic log (3 s throttle)
- [ ] **[ADB]** Logcat `readoutChase iso=` changes over time while SS locked

**Gate:** `pns_chrome_ux_gate.ps1` PASS + logcat proof of ISO chase while SS locked

---

### Sprint 15.11 — Shutter angle presets for video *(Priority 2)*

**Design:** `VideoShutterAngle` enum: `FREE`, `ANGLE_360`, `ANGLE_180`, `ANGLE_90`, `ANGLE_45`. Derives `exposureNs = (angle/360) × (1/fps) × 1e9`; locks SS with `LOCKED_SS_AUTO_ISO` coupling. Video-mode only.

**Code:** `VideoShutterAngle.kt` (new), `ReadoutAeCoupling.kt`, `PreviewReadoutStrip.kt`, `HudSettings.kt`

**Tasks:**
- [ ] **[AGENT]** Create `VideoShutterAngle.kt` with fps-derived exposure formula
- [ ] **[AGENT]** Wire angle → `LOCKED_SS_AUTO_ISO` coupling in `PreviewController`
- [ ] **[AGENT]** Display angle label on SS chip when locked (e.g. `180°`)
- [ ] **[AGENT]** Persist in `HudSettings`; add to Settings → Video + QS
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §10
- [ ] **[ADB]** At 30 fps: `ANGLE_180` → SS chip shows `180°`, logcat `readoutManual ss=33333333ns`

**Gate:** `pns_chrome_ux_gate.ps1` PASS + logcat SS value matches `1/(2×fps)` for 180°

---

### Sprint 15.12 — Haptic delay fix for RAW+JPEG dual capture *(Priority 1)*

**Problem:** `scheduleStillTick()` fires after RAW readout, before tonal (JPEG) capture completes.

**Fix:** Suppress `scheduleStillTick()` on RAW completion; fire only from tonal `onCaptureCompleted` in dual-capture path (`raw != null && tonal != null`).

**Code:** `PreviewEngineScreen.kt`, `CaptureHaptics.kt`

**Tasks:**
- [ ] **[AGENT]** Pass `suppressHapticUntilTonal: Boolean` through dual-capture chain
- [ ] **[AGENT]** Fire `scheduleStillTick()` only in tonal `onCaptureCompleted` for dual path
- [ ] **[AGENT]** Unit test: mock dual-capture → tick fires after tonal, not after RAW

**Gate:** `pns_verify_toolchain.ps1 -RunTests` unit test PASS

---

### Sprint 15.13 — Fleet hardware scan + focal map at first startup *(Priority 2)*

**Design:** On first launch, `FleetCameraStartupScan` reads `CameraCharacteristics` for each camera ID, computes 35mm-equiv focal lengths, builds `FleetFocalMap`, marks slots < 12 MP as `grayscaled=true`. Persists to `fleet_focal_map.json`.

**Code:** `FleetCameraStartupScan.kt` (new), `FocalLensStripSupport.kt`, `BackCameraRoleResolver.kt`

**Tasks:**
- [ ] **[AGENT]** Create `FleetCameraStartupScan.kt`
- [ ] **[AGENT]** Persist scan to `fleet_focal_map.json`
- [ ] **[AGENT]** Wire `FocalLensStripSupport` to gray out unavailable slots
- [ ] **[AGENT]** Unit tests: 35mm equiv computation; < 12 MP gate
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §7
- [ ] **[ADB]** `fleet_focal_map.json` present with correct grayout flags on CPH2655

**Gate:** `pns_chrome_ux_gate.ps1 -FocalMmSlot 150` PASS + `fleet_focal_map.json` correct

---

### Sprint 15.14 — DNG metadata completeness (EXIF fields) *(Priority 2)*

**Design:** Use `DngCreator` setters (before `writeImage()`): `setLocation`, `setCaptureTime`, `setDescription` (expanded). 35mm focal length via in-place TIFF byte write if `DngCreator` API unavailable. **No `ExifInterface.saveAttributes()` on DNG.**

**Code:** `Dng12Saver.kt`, `StillCaptureMetadata.kt`

**Tasks:**
- [ ] **[AGENT]** `setLocation` when geotag pref + location available
- [ ] **[AGENT]** `setCaptureTime` on all DNG paths
- [ ] **[AGENT]** Focal slot + lens model in `setDescription`
- [ ] **[AGENT]** Patch EXIF `0xA405` in `StillCaptureMetadata` in-place byte writer if needed (no ExifInterface)
- [ ] **[AGENT]** `dng_tiff_integrity_check.py` PASS after additions
- [ ] **[ADB]** `exiftool` on pulled DNG — GPS + focal length + capture time present

**Gate:** `pns_capture_pipeline_verify.ps1` PASS + `dng_tiff_integrity_check.py` PASS + exiftool shows GPS + focal

---

### Sprint 15.15 — DNG aux color fix: UW green/black cast *(Priority 1 — blocks H.7)*

**Problem:** UW DNG green/black cast. Values from uncommitted `DngForwardMatrixFix.kt`: UW FM1[0,0]=0.3083 scaleR=1.147 scaleB=1.036; Tele FM1[0,0]=0.5032 scaleR=1.602 scaleB=1.147.

**Phase 1:** Enable ProShot still IQ + ASN reconcile for UW. Gate: `dng_color_metric.py` `uw_delta ≤ 0.12`.
**Phase 2 (if Phase 1 fails):** Create `DngDeviceColorProfile.kt` + CPH2655 JSON; apply in-place TIFF FM+ASN patches in `Dng12Saver` (under `dng-save-pipeline-lock` rules).

**Code:** `Dng12Saver.kt`, `DngDeviceColorProfile.kt` (new), `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`

**Tasks:**
- [ ] **[AGENT]** Enable ProShot IQ + ASN reconcile for UW path
- [ ] **[AGENT]** Run `pns_aux_dng_capture_analyze.ps1` Phase 1 — record `uw_delta`
- [ ] **[AGENT]** If `uw_delta > 0.12`: create `DngDeviceColorProfile.kt` + CPH2655 JSON with recovered values
- [ ] **[AGENT]** Apply in-place TIFF FM+ASN patches; `dng_tiff_integrity_check.py` PASS
- [ ] **[AGENT]** `dng_color_metric.py` `uw_delta ≤ 0.12` gate PASS
- [ ] **[AGENT]** `scripts/pns_dng_rawpy_decode_gate.ps1` — rawpy.imread M14/M23/M73 from latest hfr-run; assert no exception + shape + mean > 0
- [ ] **[AGENT]** `scripts/pns_dng_aesthetic_gate.py` — rawpy decode M14/M23/M73; assert luma/channel stats within ±20% of reference
- [ ] **[AGENT]** Update `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`, `docs/FLEET_ONEPLUS13_RAW_POLICY.md`
- [ ] **[HUMAN]** H.7: ACR open UW + tele + wide — all neutral, no green cast

**Gate:** `pns_aux_dng_capture_analyze.ps1` 3/3 PASS + `dng_color_metric.py` `uw_delta ≤ 0.12` + `pns_dng_rawpy_decode_gate.ps1` PASS + H.7 closes

---

### Sprint 15.B — Release readiness scripts *(Priority 1 — run before Milestone H)*

Script-only sprint; no app code changes. Creates the 4 publication/security automation scripts that have no feature sprint to merge into.

- [ ] **[AGENT]** Extend `scripts/pns_gitlab_setup.ps1 -Verify` — GitLab API assert `ANDROID_KEYSTORE_BASE64` `masked=true`
- [ ] **[AGENT]** `scripts/pns_keystore_verify.ps1` — `keytool -list`; assert alias + SHA-256 vs `scripts/pns_keystore_expected.json`
- [ ] **[AGENT]** `scripts/pns_release_asset_check.ps1` — `gh release view`; assert APK asset size > 1 MB
- [ ] **[AGENT]** `scripts/pns_crash_triage.ps1` — `adb logcat -b crash -d`; parse fatal exceptions; write report to `hfr-runs/crash_triage_<timestamp>.md`

**Gate:** all 4 scripts exit 0 on host (device required for `pns_crash_triage.ps1`). `pns_verify_toolchain.ps1 -RunTests` PASS.

---

### Sprint 15.16 — HLG + Flat/Cine video color profiles *(Priority 2)*

**Design:** `VideoColorProfile` enum: `SDR`, `HLG`, `FlatCine`. HLG → HEVC Main10 + `bt2020-hlg` VUI + GLSL de-gamma LUT. FlatCine → shadow lift + saturation reduction + shoulder roll-off GLSL LUT. Picker in Settings → Video.

**Code:** `VideoFormatConfig.kt`, `MediaCodecVideoRecorder.kt`, `lut_preview_external.frag.glsl`, `HudSettings.kt`

**Tasks:**
- [ ] **[AGENT]** Add `VideoColorProfile` enum + pass through recorder config
- [ ] **[AGENT]** Return `"bt2020-hlg"` VUI tag for HLG + 10-bit
- [ ] **[AGENT]** Bake GLSL 1D LUT for HLG de-gamma preview from `HdrCurves.hlgToLinear`
- [ ] **[AGENT]** Bake GLSL LUT for FlatCine (shadow lift + saturation + shoulder)
- [ ] **[AGENT]** Profile picker in Settings → Video; persist in `HudSettings`
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §10
- [ ] **[ADB]** ffprobe: `color_transfer=arib-std-b67` for HLG output

**Gate:** `pns_video_codec_color_compare.ps1` PASS for both profiles; `pns_verify_toolchain.ps1 -RunTests` PASS

---

### Sprint 15.17 — ICC profile embedding in JPEG / AVIF *(Priority 2)*

**Design:** `IccProfileBuilder.kt` (new, ~100 lines) generates minimal ICC v4 RGB profile from `WorkingSpace` primaries + D65 white point. Embed in JPEG via `ExifInterface.setAttribute(TAG_ICC_PROFILE, bytes)`. AVIF on API ≥ 34. **Not on DNG.**

**Code:** `IccProfileBuilder.kt` (new), `StillCaptureMetadata.kt`, JPEG/AVIF save paths

**Tasks:**
- [ ] **[AGENT]** Create `IccProfileBuilder.kt` (magic `0x61637370`, profile class `spac`)
- [ ] **[AGENT]** Embed in JPEG save path
- [ ] **[AGENT]** Embed in AVIF save path (API ≥ 34)
- [ ] **[AGENT]** Unit test: valid ICC header magic bytes
- [ ] **[ADB]** `exiftool -icc_profile:all` on pulled JPEG — "Display P3" profile name

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + exiftool shows P3 ICC + DNG integrity unchanged

---

### Sprint 15.18 — ZSL ring histogram (RAW-faithful RGB) *(Priority 2)*

**Design:** When ZSL ring is armed + histogram enabled, sample last ring frame instead of live preview YUV. Show "ZSL" badge on histogram overlay.

**Code:** `PreviewLumaHistogram.kt`, `ZslStillFrameRing.kt`, `PreviewLumaHistogramOverlay`

**Tasks:**
- [ ] **[AGENT]** Add `reduceYuv420RGB` to `PreviewLumaHistogram`
- [ ] **[AGENT]** Wire `ZslStillFrameRing.peekLastFrame()` → RGB histogram on background thread
- [ ] **[AGENT]** Add `zslHistogramActive` state + "ZSL" badge to overlay composable
- [ ] **[AGENT]** Unit test: `reduceYuv420RGB` on flat image → all channels equal
- [ ] **[ADB]** Screencap: RGB channels + ZSL badge visible

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + screencap proof

---

### Sprint 15.19 — BLE / AVRCP remote shutter *(Priority 2)*

**Design:** Register `MediaSession` to handle `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_HEADSETHOOK`. When `volumeKeysCapture` on + app foregrounded, media button fires shutter. Toggle in Settings → Capture: "Bluetooth remote shutter".

**Code:** `PnsMediaSessionManager.kt` (new/extend), `PreviewChromePreferences.kt`

**Tasks:**
- [ ] **[AGENT]** Handle `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_HEADSETHOOK` → shutter fire
- [ ] **[AGENT]** Toggle `btRemoteShutter` in Settings → Capture + persist
- [ ] **[AGENT]** Guard: only when foregrounded
- [ ] **[ADB]** `adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE` → logcat `shutterFired source=bt_media`

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `shutterFired source=bt_media`

---

### Sprint 15.20 — Segmented PPM audio meters *(Priority 2)*

**Design:** `PpmAudioMeter.kt` — 12 segments (green/amber/red), peak hold 2 s, dBFS tick marks at −20/−12/−3. Replaces `AudioLevelMeter` in `PreviewTopStatusBar`.

**Code:** `PpmAudioMeter.kt` (new), `PreviewTopStatusBar.kt`

**Tasks:**
- [ ] **[AGENT]** Create `PpmAudioMeter.kt`
- [ ] **[AGENT]** Replace `AudioLevelMeter` in `PreviewTopStatusBar`
- [ ] **[AGENT]** Unit test: amplitude 0.708 (−3 dBFS) → 11 segments lit + red top
- [ ] **[ADB]** Screencap during recording — segmented meters visible
- [ ] **[HUMAN]** H.8.4: peak hold visible and decaying correctly

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + screencap; H.8.4 closes

---

### Sprint 15.21 — Video zebra + false color *(Priority 2, depends on 15.16)*

**Design:** Part A — remove recording gate from `wantZebra`; add `zebraIreThreshold: Int = 95` + IRE slider (75–100). Part B — `FalseColorMode` enum: Off/ZebraOnly/FalseColor; color-tint regions by Y: blue(<35)/normal(35–100)/orange(180–210)/red(>210).

**Code:** `HudSettings.kt`, `PreviewLumaHistogram.kt`, `FalseColorOverlay.kt` (new), `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Remove video gate from `wantZebra`
- [ ] **[AGENT]** Add `zebraIreThreshold` + IRE slider in Settings → HUD
- [ ] **[AGENT]** Add `FalseColorMode` enum + `FalseColorOverlay.kt`
- [ ] **[AGENT]** Add `buildFalseColorGridYuv420Y` to `PreviewLumaHistogram`
- [ ] **[AGENT]** Wire overlay into preview stack
- [ ] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` §6
- [ ] **[ADB]** Screencap: false color regions on high-contrast scene
- [ ] **[HUMAN]** H.8.5: false color correct on known scene (grey card + highlight)

**Gate:** `pns_chrome_ux_gate.ps1` PASS + screencap; H.8.5 closes

---

### Sprint 15.22 — Face priority AE: eye-region sub-crop + video mode *(Priority 2, depends on 15.1)*

**Fix A:** Narrow `CONTROL_AE_REGIONS` rect to eye-level sub-rect (top 20%–55% of face box). AF keeps full face box.
**Fix B:** `allowsFacePriorityMetering()` returns true when `isRecording && hudFaceOverlayEnabled`.

**Code:** `PreviewEngineScreen.kt` — `meteringRectangleFromBufferFaceBox`, `allowsFacePriorityMetering`

**Tasks:**
- [ ] **[AGENT]** Narrow AE rect to eye sub-crop
- [ ] **[AGENT]** Extend `allowsFacePriorityMetering` to include recording + overlay
- [ ] **[AGENT]** Log `PNS.FaceMeter aeSub=true eyeRectSensor=…`
- [ ] **[AGENT]** Unit test: AE rect height < full face box height
- [ ] **[ADB]** Logcat `aeSub=true` during recording; `pns_photo_capture_verify.ps1` not regressed

**Gate:** `pns_photo_capture_verify.ps1` PASS + `pns_capture_pipeline_verify.ps1` PASS + logcat `aeSub=true`

---

### Sprint 15.23 — Pillar-bar video HUD *(Priority 1, depends on 15.20)*

**Design:** When `videoPrimary && pillarBarWidthDp ≥ 24.dp`: right pillar = tall `PpmAudioMeter` (full tile height); left pillar = timecode + battery + `ThermalChip`. Falls back to top-bar layout when no pillar bars.

**Code:** `VideoSidePanels.kt` (new), `PreviewTopStatusBar.kt`, `PowerThermalOverlay.kt` (extract `ThermalChip`)

**Tasks:**
- [ ] **[AGENT]** Compute `pillarBarWidthDp` from buffer AR vs tile AR
- [ ] **[AGENT]** Create `VideoSidePanels.kt` (left + right columns)
- [ ] **[AGENT]** Extract `ThermalChip` from `PowerThermalOverlay`
- [ ] **[AGENT]** Gate on `videoPrimary && isRecording && pillarBarWidthDp ≥ 24.dp`
- [ ] **[AGENT]** Suppress top-bar meters when side panels active
- [ ] **[AGENT]** Add `showVideoPillarHud` toggle in Settings → Video
- [ ] **[ADB]** Screencap during 16:9 recording — right bar PPM meters, left bar timecode + battery + heat
- [ ] **[HUMAN]** H.8.6: no overlap with shutter tray, focal strip, readout chips

**Gate:** `pns_chrome_ux_gate.ps1` PASS + screencap; H.8.6 closes

---

### Sprint 15.24 — Audio source selection *(Priority 2)*

**Design:** `VideoAudioSource` enum: `Mic`, `Camcorder`, `Unprocessed` (API 24+). Picker in Settings → Video. Log `PNS.MCVideoRec audioSource=…`.

**Code:** `MediaCodecVideoRecorder.kt`, `HudSettings.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add `VideoAudioSource` enum + `videoAudioSource` to `HudSettings`
- [ ] **[AGENT]** Thread through `MediaCodecVideoRecorder.Config` + `AudioRecord` init; API 24 guard
- [ ] **[AGENT]** Picker in Settings → Video
- [ ] **[ADB]** Logcat `PNS.MCVideoRec audioSource=CAMCORDER` after selecting

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `audioSource=` confirmed

---

### Sprint 15.25 — Wind noise filter toggle *(Priority 2, depends on 15.24)*

**Design:** When `videoAudioSource == Camcorder && windNoiseFilterEnabled`: enable `NoiseSuppressor` + `AcousticEchoCanceler` on `AudioRecord.audioSessionId`. Toggle greyed out when source ≠ Camcorder.

**Code:** `MediaCodecVideoRecorder.kt`, `HudSettings.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add `windNoiseFilterEnabled: Boolean = false` to `HudSettings`
- [ ] **[AGENT]** Enable `NoiseSuppressor` + `AcousticEchoCanceler` post-`startRecording()` when conditions met
- [ ] **[AGENT]** Greyed-out toggle in Settings → Video
- [ ] **[ADB]** Logcat `windFilter=on nsAvail=… aecAvail=…`

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `windFilter=on`

---

### Sprint 15.26 — AE lock: separate AE/AF lock + padlock indicator *(Priority 2)*

**Design:** Long-press (600 ms) ISO/SS chip → toggle `aeLocked`. When true: `CONTROL_AE_LOCK = true` on every preview repeating request. Padlock icon (amber 12×12dp) beside ISO chip. AF still moves freely.

**Code:** `PreviewEngineScreen.kt`, `PreviewReadoutStrip.kt`

**Tasks:**
- [ ] **[AGENT]** Add `aeLocked` state + long-press toggle on ISO/SS chip
- [ ] **[AGENT]** Inject `CONTROL_AE_LOCK = aeLocked` into repeating request
- [ ] **[AGENT]** Padlock icon when locked; clear on camera close / dial change
- [ ] **[AGENT]** Unit test: `aeLocked = true` → request sets `CONTROL_AE_LOCK = true`
- [ ] **[ADB]** Logcat `aeLock=true` after long-press; `pns_photo_capture_verify.ps1` not regressed

**Gate:** `pns_photo_capture_verify.ps1` PASS + logcat `aeLock=true` + screencap padlock

---

### Sprint 15.27 — Time-lapse video encoding *(Priority 2)*

**Design:** `TimeLapseMode` enum: Off/Photo/Video. Video mode: background `MediaCodec` H.264 encoder; on each interval tick capture JPEG → decode → encode one frame at PTS = `frameIdx * (1e6/30)`. Output MP4 via MediaStore.

**Code:** `TimeLapseVideoEncoder.kt` (new), `HudSettings.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Create `TimeLapseVideoEncoder.kt` — `MediaMuxer` + `MediaCodec` frame-by-frame
- [ ] **[AGENT]** Add `timeLapseMode` to `HudSettings`; branch in intervalometer `LaunchedEffect`
- [ ] **[AGENT]** Settings → Advanced Capture: "Time-lapse output → Photo / Video"
- [ ] **[AGENT]** Disable DNG + normal video rec while time-lapse active
- [ ] **[ADB]** `pns_in_app_video_verify.ps1` not regressed; time-lapse MP4 in MediaStore

**Gate:** `pns_in_app_video_verify.ps1` PASS + `pns_verify_toolchain.ps1 -RunTests` PASS + MP4 produced

---

### Sprint 15.28 — Focus breathing compensation *(Priority 2)*

**Design:** Track `LENS_FOCUS_DISTANCE` from `onCaptureCompleted`. When manual diopters change > 0.3 in tele mode: compute crop nudge `Δscale = 1 + (Δdiopters × kBreathing)`, apply EMA-smoothed `SCALER_CROP_REGION` adjustment. Gate: M-dial + tele slot + toggle on.

**Code:** `PreviewEngineScreen.kt`, `HudSettings.kt`

**Tasks:**
- [ ] **[AGENT]** Add `enableFocusBreathingComp: Boolean = false` + `focusBreathingCompK: Float = 0.005f`
- [ ] **[AGENT]** Track focus distance result; compute + apply EMA crop nudge
- [ ] **[AGENT]** Toggle in Settings → Video
- [ ] **[ADB]** `pns_capture_pipeline_verify.ps1` PASS + logcat `PNS.FocusBreathing` during M-dial rack

**Gate:** `pns_capture_pipeline_verify.ps1` PASS + logcat `PNS.FocusBreathing` + `pns_photo_capture_verify.ps1` PASS

---

### Sprint 15.29 — NightScape long-exposure stacking *(Priority 2)*

**Design:** Night dial → capture 4–8 JPEGs at HAL max sensitivity → decode → block-matcher align → average-blend → encode as 12-bit AVIF. Show progress `PNS.NightScape frame=N/M`.

**Code:** `NightScapeCapture.kt` (new), `HudSettings.kt` (`nightScapeFrameCount` 4/6/8)

**Tasks:**
- [ ] **[AGENT]** Create `NightScapeCapture.kt` — burst JPEG + decode + align + blend + AVIF encode
- [ ] **[AGENT]** Wire to Night-dial shutter path; progress status line
- [ ] **[ADB]** `pns_photo_capture_verify.ps1` not regressed; AVIF in MediaStore

**Gate:** `pns_photo_capture_verify.ps1` PASS + `pns_verify_toolchain.ps1 -RunTests` PASS + AVIF produced

---

### Sprint 15.30 — Spatial audio metadata for video *(Priority 3)*

**Design:** Set `KEY_CHANNEL_MASK = CHANNEL_IN_STEREO` + `KEY_PCM_ENCODING` on audio `MediaFormat` before `addTrack` (API 28+). Log `PNS.MCVideoRec spatialAudioMeta=stereo`.

**Code:** `MediaCodecVideoRecorder.kt`

**Tasks:**
- [ ] **[AGENT]** Set `KEY_CHANNEL_MASK` + `KEY_PCM_ENCODING`; API 28 guard
- [ ] **[ADB]** ffprobe on MP4 — `stereo` channel layout; `pns_in_app_video_verify.ps1` PASS

**Gate:** `pns_in_app_video_verify.ps1` PASS + ffprobe `stereo` confirmed

---

### Sprint 15.31 — Macro video mode *(Priority 2, depends on 15.23)*

**Design:** Extend `commandDialModesFor(Video)` to include `Macro`. When `Macro + video`: cap fps at 60, locally force EIS on, call `onEnsureMacroUltraWide`. Show "MACRO VIDEO" amber badge in readout strip. Add `MacroVideo` workflow preset.

**Code:** `CaptureMediaFamily.kt`, `PreviewEngineScreen.kt`, `PreviewReadoutStrip.kt`, `WorkflowPresets.kt`

**Tasks:**
- [ ] **[AGENT]** Extend `commandDialModesFor(Video)` with `Macro`
- [ ] **[AGENT]** Cap fps 60 + force EIS + `onEnsureMacroUltraWide` in macro+video
- [ ] **[AGENT]** "MACRO VIDEO" badge + `MacroVideo` workflow preset
- [ ] **[ADB]** `pns_in_app_video_verify.ps1` PASS + logcat `macroVideo=true`

**Gate:** `pns_in_app_video_verify.ps1` PASS + `pns_photo_capture_verify.ps1` PASS + logcat `macroVideo=true`

---

### Sprint 15.32 — Stabilisation readout chip *(Priority 2)*

**Design:** Derive `stabChipLabel: String?` from `VideoEffectsProcessor.stabilizationDiag()`: `oisOn && eisOn` → `"OIS+EIS"`, `oisOn` only → `"OIS"`, `eisOn` only → `"EIS"`, advertised but off → `"STAB·off"`, none → hidden. Non-interactive `ReadoutMetricChip` labelled `"STAB"` after AF chip.

**Code:** `PreviewReadoutStrip.kt`, `PreviewEngineScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Derive `stabChipLabel` in `PreviewEngineContent`
- [ ] **[AGENT]** Add `stabChipLabel: String? = null` param to `PreviewReadoutStrip`; render chip; hide when null
- [ ] **[AGENT]** Log `PNS.ChromeUx stabChip=…` on change (debounced 3 s)
- [ ] **[AGENT]** Unit test: `oisOn=true, eisOn=true` → label `"OIS+EIS"`
- [ ] **[ADB]** Screencap shows STAB chip

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + `pns_chrome_ux_gate.ps1` PASS + screencap

---

### Sprint 15.33 — Live colour temperature readout *(Priority 2)*

**Design:** In `onCaptureCompleted`, read `COLOR_CORRECTION_GAINS`, compute `tilt = R_gain / B_gain`, map to Kelvin via Robertson 7-point LUT (2500–8000 K). Throttle 800 ms. Override WB chip to `"${K}K"` when AWB auto.

**Code:** `KelvinEstimator.kt` (new), `PreviewEngineScreen.kt`, `PreviewReadoutStrip.kt`

**Tasks:**
- [ ] **[AGENT]** Create `KelvinEstimator.kt` — Robertson LUT, `estimateFromRgGainTilt(tilt: Float): Int`
- [ ] **[AGENT]** Post `liveColorTempK` state (throttle 800 ms) from `onCaptureCompleted`
- [ ] **[AGENT]** Override WB chip value to `"${K}K"` when AWB auto
- [ ] **[AGENT]** Unit tests: tilt 0.7f → ≤ 3200 K; tilt 1.4f → ≥ 6000 K
- [ ] **[ADB]** Screencap shows `"5600K"` in WB chip; logcat `colorTempK=`

**Gate:** `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `colorTempK=` + screencap

---

### Sprint 15.34 — Video histogram display *(Priority 2)*

**Design:** Add `showHistogramDuringVideo: Boolean = false` to `HudSettings`. Update `wantYuv` gate to include histogram during video when enabled. Guard `setPreviewHistogramEnabled` to not call `maybeRestart()` when `isRecording`. Toggle in Settings → Video.

**Code:** `HudSettings.kt`, `PreviewEngineScreen.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add `showHistogramDuringVideo` to `HudSettings`
- [ ] **[AGENT]** Update `wantYuv` condition
- [ ] **[AGENT]** Guard `setPreviewHistogramEnabled` against session churn mid-recording
- [ ] **[AGENT]** Toggle in Settings → Video
- [ ] **[ADB]** `pns_in_app_video_verify.ps1` PASS; histogram visible during recording when enabled

**Gate:** `pns_in_app_video_verify.ps1` PASS + `pns_photo_capture_verify.ps1` PASS

---

### Sprint 15.35 — Audio gain control per recording *(Priority 2)*

**Design:** `audioGainDb: Float = 0f` (−12 to +12, step 0.5 dB). Compute `gainLinear = 10^(gainDb/20)`. Apply per-sample in PCM loop after `AudioEffects.applyPcmProcessing`; skip when `gainLinear == 1f`. Slider in Settings → Video.

**Code:** `MediaCodecVideoRecorder.kt`, `HudSettings.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add `audioGainDb` to `HudSettings` + `MediaCodecVideoRecorder.Config`
- [ ] **[AGENT]** Compute `gainLinear` at recorder start; apply in PCM loop
- [ ] **[AGENT]** Slider in Settings → Video (−12 to +12, 0.5 step)
- [ ] **[AGENT]** Unit test: `gain(0f)==1f`, `gain(6f)≈2f`, `gain(-6f)≈0.5f`
- [ ] **[ADB]** `pns_in_app_video_verify.ps1` PASS; logcat `audioGainDb=` on start

**Gate:** `pns_in_app_video_verify.ps1` PASS + `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `audioGainDb=`

---

### Sprint 15.36 — Rack focus pulls with waypoints *(Priority 2)*

**Design:** Two waypoint slots in `HudSettings` (`rackFocusWaypointNear/Far: Float?`). Long-press focus chip → set near/far WP sheet + duration picker (500 ms/1 s/2 s/3 s). "▶ Rack" button when both set + M-dial. Coroutine interpolates `manualFocusDiopters` at 30 Hz. Second tap aborts.

**Code:** `HudSettings.kt`, `PreviewEngineScreen.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add waypoint + `rackFocusDurationMs` to `HudSettings`
- [ ] **[AGENT]** Long-press focus chip → `SetWaypointSheet`
- [ ] **[AGENT]** "▶ Rack" button + rack coroutine (30 Hz, abort on second tap)
- [ ] **[ADB]** `pns_photo_capture_verify.ps1` PASS; logcat `rackFocus from=…` during rack

**Gate:** `pns_photo_capture_verify.ps1` PASS + `pns_capture_pipeline_verify.ps1` PASS + logcat `rackFocus from=`

---

### Sprint 15.37 — Wi-Fi Direct tethered shooting *(Priority 3)*

**Design:** `TetheredCaptureServer` dual-bind (`0.0.0.0` + loopback) when `wifiDirectMode=true`. `NsdManager.registerService` as `_pns-tether._tcp`. Runtime permission `NEARBY_WIFI_DEVICES` / `ACCESS_FINE_LOCATION`. Toggle in Settings → Advanced. `WifiDirectTetherBanner` chip in top band. Create `docs/TETHER_API.md`.

**Code:** `TetheredCaptureServer.kt`, `HudSettings.kt`, `HudSettingsScreen.kt`, `docs/TETHER_API.md` (new)

**Tasks:**
- [ ] **[AGENT]** Dual bind + NSD service registration
- [ ] **[AGENT]** Runtime permission request
- [ ] **[AGENT]** Toggle + banner in UI
- [ ] **[AGENT]** Create `docs/TETHER_API.md`
- [ ] **[ADB]** `pns_chrome_ux_gate.ps1` PASS; logcat `wifiDirectBound=true`

**Gate:** `pns_chrome_ux_gate.ps1` PASS + `pns_verify_toolchain.ps1 -RunTests` PASS + logcat `wifiDirectBound=true`

---

### Sprint 15.38 — Dual-ISO video HLG infrastructure *(Priority 3, experimental)*

**Design (M15 foundation only):** Probe `SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP` (API 31+). Stub `DualIsoVideoMerger.kt` (pass-through). Toggle "Dual ISO video (experimental)" in Settings → Video; greyed when probe fails. Real merge deferred to M16.

**Code:** `HudSettings.kt`, `DualIsoVideoMerger.kt` (new stub), `PreviewEngineScreen.kt`, `HudSettingsScreen.kt`

**Tasks:**
- [ ] **[AGENT]** Add `dualIsoVideoEnabled` to `HudSettings`
- [ ] **[AGENT]** Probe `SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP` in `createSession` (API 31+ guard)
- [ ] **[AGENT]** Create `DualIsoVideoMerger.kt` stub
- [ ] **[AGENT]** Settings → Video toggle (disabled when probe fails)
- [ ] **[AGENT]** Unit test: stub `merge()` returns input unchanged
- [ ] **[ADB]** `pns_in_app_video_verify.ps1` PASS; logcat `PNS.DualIso multiResSupported=`

**Gate:** `pns_in_app_video_verify.ps1` PASS + `pns_photo_capture_verify.ps1` PASS + logcat `PNS.DualIso`

---

## Milestone 15 gate

```
scripts/pns_verify_toolchain.ps1 -RunTests
scripts/pns_capture_pipeline_verify.ps1
scripts/pns_chrome_ux_gate.ps1 -FocalMmSlot 150
scripts/pns_dual_video_verify.ps1 -RecordSec 5
scripts/pns_video_matrix_verify.ps1
scripts/pns_video_codec_color_compare.ps1
scripts/pns_aux_dng_capture_analyze.ps1
scripts/dng_tiff_integrity_check.py
```

Human gates closing with M15: **H.7** (DNG color ACR), **H.8.1** (eye AF), **H.8.2** (dual video), **H.8.3** (HEVC color), **H.8.4** (PPM meters), **H.8.5** (false color), **H.8.6** (pillar-bar HUD).

---

## Suggested features for Milestone 16

> Sprints 15.0–15.38 consumed items from rounds 1–4. Items below are unscheduled.
> Custom WB picker already implemented (gray card WB in WB chip menu).

1. **Anamorphic desqueeze preview** — horizontal GLES stretch (1.33×/1.5×/2×) + `MDTA` `com.apple.prores.anamorphicRatio` embed.
2. **Live LUT preview on gallery viewer** — non-destructive GLES LUT preview on saved stills (swipe to compare).
3. **Full dual-ISO HDR video merge** — complete `DualIsoVideoMerger` log-domain blend + HLG remap (deferred from 15.38).
4. **RAW NightScape stacking** — extend 15.29 JPEG stacking to full RAW burst → 12-bit DNG.
5. **Wi-Fi Direct companion browser UI** — serve minimal web UI from `TetheredCaptureServer` (`GET /`); extends 15.37.
6. **Push notifications for tether** — SSE/WebSocket endpoint on `TetheredCaptureServer` for live ISO/SS/histogram; extends 15.33 + 15.37.

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment: creative, security, perceptual.

**Depends on:** Sprint 15.B gate scripts PASS; Sprint 15.15 (DNG color); Sprint 15.1 (eye AF); Sprint 15.5 (dual video); Sprint 15.2 (HEVC); Sprint 15.20, 15.21, 15.23 (PPM/false color/pillar HUD).

### Sprint H.1 — Desktop visual verification

- [ ] **[AGENT]** `pns_dng_aesthetic_gate.py` — rawpy decode M14/M23/M73; luma+channel stats PASS
- [ ] **[AGENT]** `pns_passport_ce_values.py` — X-Rite constants → `tests/fixtures/passport_ce_values.json`

### Sprint H.2 — Physical calibration capture

- [ ] **[HUMAN]** Set up ColorChecker under controlled illuminant (irreducible — physical setup)
- [ ] **[AGENT]** Trigger ADB capture; run `pns_colorchecker_de2000_gate.py`; assert all Macbeth patches dE2000 < threshold

### Sprint H.3 — Account ownership

- [ ] **[AGENT]** `pns_gitlab_setup.ps1 -Verify` — assert `ANDROID_KEYSTORE_BASE64` `masked=true` via GitLab API
- [ ] **[HUMAN]** Confirm you are logged into the owner GitLab account (irreducible — identity custody)

### Sprint H.4 — Signing authority

- [ ] **[AGENT]** `pns_keystore_verify.ps1` — `keytool -list`; assert alias + SHA-256 vs `pns_keystore_expected.json`
- [ ] **[AGENT]** `pns_release_asset_check.ps1` — `gh release view`; assert APK asset > 1 MB
- [ ] **[HUMAN]** Confirm you hold custody of the keystore file (irreducible — security)

### Sprint H.5 — Publication & community

- [ ] **[HUMAN]** Store listing copy (Play / F-Droid) — irreducible creative writing
- [ ] **[HUMAN]** Community announcements — irreducible public communication
- [ ] **[AGENT]** `pns_crash_triage.ps1` — post-launch: `adb logcat -b crash -d`; parse fatals; write report

### Sprint H.6 — Subjective UX sign-off

- [ ] **[AGENT]** `pns_eye_af_pixel_gate.ps1` — screencap + PIL diff eye-box vs expected region; PASS when delta < threshold
- [ ] **[AGENT]** `pns_a11y_dump_gate.ps1` — `uiautomator dump`; assert all interactive nodes have `content-desc`
- [ ] **[HUMAN]** HUD / LUT default aesthetics — irreducible perceptual
- [ ] **[HUMAN]** Immersive mode feel — irreducible perceptual

### Sprint H.7 — Milestone 13 DNG & still modes

**Artifacts:** `hfr-runs/aux_dng_capture_analyze_20260519_235745/`, `hfr-runs/m13_3f_gate_20260520_012341/`, `hfr-runs/m13_8d_gate_20260520_020059/`.

- [ ] **[AGENT]** `pns_dng_rawpy_decode_gate.ps1` — rawpy.imread M14/M23/M73; assert no exception + shape + mean > 0
- [ ] **[AGENT]** `pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "auto"`
- [ ] **[AGENT]** `dng_proshot_parity_gate.py` — aux color vs ProShot reference; all slots within chroma thresholds
- [ ] **[AGENT]** `pns_still_mode_compare_gate.ps1` — Standard/ZSL/HDR captures; luminance compare; write `STILL_MODE_COMPARE.md`
- [ ] **[HUMAN]** ACR / Lightroom: open UW + tele + wide DNGs — final visual confirm no green cast (closes H.7, M15 sprint 15.15)

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass (14.5 + 15.1) — pixel gate passes; on-face rubber-stamp
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing usability (14.12 + 15.5)
- [ ] **[AGENT] H.8.3** `pns_hfr_color_compare_frames.ps1` — H.265 vs H.264 YCbCr delta < 8; PASS closes H.8.3
- [ ] **[HUMAN] H.8.4** PPM meters peak hold visible + decaying (15.20)
- [ ] **[HUMAN] H.8.5** False color correct on grey card + highlight scene (15.21)
- [ ] **[HUMAN] H.8.6** Pillar-bar HUD no overlap with chrome (15.23)

**Milestone H gate:** Owner-approved checklist; **H.7** closes M13 publication claims; **H.8** closes M14/M15 subjective claims.

---

## Appendix A — Verification protocol (abbreviated)

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED  
2. `ReadLints` clean on touched Kotlin  
3. Claimed paths/symbols exist  
4. Unit tests: `failures="0" errors="0"`  
5. `CHANGELOG.md` for user-visible changes; **§5** for gates  
6. **[ADB]/[ROOT]:** device evidence  
7. **[MIXED]:** parent stays `[ ]` until every child venue is satisfied  

---

## Appendix B — Baseline already shipped (high level)

| Area | Status |
|------|--------|
| FOSS gates + CI toolchain | Shipped |
| Probe JSON + About hydration | Shipped |
| Dodge profile + crop geometry | Shipped |
| Pro HUD + chrome (locked layout) | Shipped |
| M14 readout/status bar, QR, dual video, About heritage | Shipped (see archive) |
| LUT / calibration / DNG library path | Shipped |
| Diagnostics + failure matrix docs | Shipped |

---

## Appendix C — Agent quick grep

| Need | Pattern |
|------|---------|
| Open human | `^- \[ \] \[HUMAN\]` |
| Open mixed | `^- \[ \] \[MIXED\]` |
| Sprint headers | `^### Sprint` |

---

## Document control

- **Version:** Active plan **2026-05-25** — **M13, M13V, M14** + **BG/AS/UX/CC/IP** archived; active: **Milestone 15** (sprints 15.0, 15.A, 15.1–15.38) + **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

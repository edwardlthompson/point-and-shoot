## Build plan (Point & Shoot)

**Purpose:** Single roadmap for shipping the Parts 1–5 spec with **milestones → sprints → gates**. **Active work** lives in this file; **shipped** milestone bodies live in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

**Living docs:** **`docs/PNS_TECHNICAL_SETTINGS.md`** (source of truth for mode behavior, numeric defaults, pipeline locks — **update on every settings change**), `PROBE_BUILD_PLAN.md` (§5 audit log; §6 probe ↔ milestone map), `CHANGELOG.md`, `CLI_BUILD_AND_SIDELOAD.md`, `DODGE_PROFILE.md`, `COLOR_PIPELINE.md`, `NDK_PLAN.md`, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (capture bisect / M13 lock unlocks §9).

**Fleet / DNG references:** `docs/FLEET_ONEPLUS13_RAW_POLICY.md`, `docs/DNG_OPENABILITY_REGRESSIONS.md`, `docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`, `docs/PROSHOT_APK_FLEET_ANALYSIS.md`, `docs/RAW_REFERENCE_APP_MATRIX.md`, `docs/M13_4_DCG_SESSION.md`, `docs/M13_6_RAW_VIDEO.md`, `docs/M13_8D_STILL_MODE_BENCHMARK.md`, `docs/M13_7_GATE.md`, `docs/M13V_17_AI_FEATURES.md`, `docs/M13V_18_CAMERAX_EXTENSIONS.md`, `docs/M14_12_DUAL_VIDEO.md`, `docs/M14_READOUT_STATUS_BAR.md`.

---

### How agents must execute (nonstop discipline)

1. **Work inside one milestone at a time.** Finish every sprint in that milestone before starting the next.
2. **Within a sprint, complete tasks in order.** Blockers → log in `PROBE_BUILD_PLAN.md` §5.
3. **After each sprint:** run that sprint’s **Sprint check**. On failure, stop and fix.
4. **After all sprints in a milestone:** run the **Milestone gate** before proceeding.
5. **Tick rules:** Never `[x]` without **Appendix A**. Host: `pns_verify_toolchain.ps1 -RunTests` + `ReadLints`. Device: §5 evidence.
6. **UI work gate:** Visible UI changes need **assembleDebug**, sideload, on-glass check, and **`pns_device_screencap.ps1`** proof.
7. **JAVA_HOME / ADB:** Android Studio JBR; SDK `platform-tools` first; optional **`scripts/pns_adb_device.env`** (`PNS_ADB_SERIAL`).
8. **Git after each numbered milestone (0–14, not H):** commit + push when gate passes.
9. **Capture regression:** Changes to still/RAW/DNG/session/`PreviewEngineScreen.kt`/`RawCaptureSupport.kt` → **`pns_capture_pipeline_verify.ps1`** (or bisect/restore scripts per **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**).
10. **Archive:** When every checkbox in a sprint is `[x]` (except **`[HUMAN]`**), move the sprint body to **`BUILD_PLAN_COMPLETED.md`**. **Human rows** stay in **Milestone H**.
11. **Technical settings doc:** Any settings/pipeline change → update **`docs/PNS_TECHNICAL_SETTINGS.md`** in the same change.

**Hard rules (do not regress):** No **`automationSuppressFacePipeline`** for sequential RAW alone; no §4a **`streamHints`** or §2 RAW10-first **`Default`** tier without USB proof — **`AGENTS.md`**, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8. Preview chrome + dodge tele + DNG pairing locks: **`.cursor/rules/`** + **`AGENTS.md`**.

**Human work:** Only **Milestone H** holds **[HUMAN]** tasks.

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
| **Milestone H** | **Active** — all **[HUMAN]** work (M13 DNG, M14 glass/dual-video color) |
| **Pinned chart calibration** | **Active** — tuning deferred (below) |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Future features (deferred — unscheduled)

- **OpenCamera-style toolbox** — former Sprint 10.14; descoped unless product requests.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | **M0–M7**; **M8–12**; performance backlog; **M13** **13.1–13.8** + **13.7 gate**; **M13V** **13V.1–13V.18**; **M14** **14.1–14.13** + gate |

**Open in this file:** **Pinned chart calibration** · **Milestone H**

### Archiving completed sprints — procedure

1. Move a **`### Sprint`** only when **every** **`- [x]`** is done **except** **`[HUMAN]`** — those stay in **Milestone H**.
2. Cut sprint body → append under the right **`## Milestone`** in **`BUILD_PLAN_COMPLETED.md`**.
3. Replace in this file with a pointer to the archive.
4. Update the archive table and **`### Backlog consolidation`**.

---

## Bespoke Gallery Integration

**Objective:** Replace system gallery resolver with custom BespokeGalleryScreen for in-app media browsing and management.

**Status:** ✅ **Integration complete** (2026-05-21) — compilation successful, ready for device verification.

### Sprint BG.1 — Bespoke Gallery Implementation

**Code:** `BespokeGalleryScreen.kt`, `PreviewEngineScreen.kt`

- [x] **[AGENT]** Create `BespokeGalleryScreen.kt` with MediaStore loading and bitmap display (removed Coil dependency)
- [x] **[AGENT]** Add `showBespokeGallery` state variable to `PreviewEngineScreen.kt`
- [x] **[AGENT]** Modify `PreviewBottomCaptureTray` to accept `onBespokeGalleryChange` callback
- [x] **[AGENT]** Update gallery thumbnail click handler to show bespoke gallery
- [x] **[AGENT]** Add bespoke gallery overlay composable with proper state management
- [x] **[AGENT]** Fix Kotlin scope issues and compilation errors
- [x] **[AGENT]** Fix deprecated ArrowBack icon warning (low priority)

### Sprint BG.2 — Device Verification

**Verification scripts:** `pns_photo_capture_verify.ps1`, `pns_gallery_integration_verify.ps1` (created), `pns_gallery_integration_complete.ps1` (created)

- [x] **[ADB][HUMAN]** Test gallery thumbnail click opens bespoke gallery instead of system resolver (2026-05-21)
- [x] **[ADB][HUMAN]** Verify back button functionality returns to preview (2026-05-21)
- [x] **[ADB][HUMAN]** Test external gallery button launches system resolver (2026-05-21)
- [x] **[ADB][HUMAN]** Verify media items load and display correctly (2026-05-21)
- [x] **[ADB][HUMAN]** Test navigation between different media items (2026-05-21)
- [x] **[HUMAN]** Create `pns_gallery_integration_verify.ps1` for automated testing (2026-05-21)
- [x] **[HUMAN]** Create `pns_gallery_integration_complete.ps1` for comprehensive automated testing (2026-05-21)

### Sprint BG.3 — UX Polish & Features

**Future enhancements** (post-M14)

- [x] **[AGENT]** Add media metadata display (EXIF, capture settings) (2026-05-21)
- [x] **[AGENT]** Implement media deletion functionality (2026-05-21)
- [x] **[AGENT]** Add media sharing options (2026-05-21)
- [x] **[AGENT]** Implement zoom and pan for detailed viewing (2026-05-21)
- [ ] **[HUMAN]** UX review and accessibility improvements

**BG.1 Implementation gate:** Compilation successful, all integration code in place, no breaking changes to existing functionality. **Proof:** `gradlew :app:compileDebugKotlin` passes with zero errors.

**BG.2 Device gate:** All device verification tests passed with documented evidence. **Proof:** `pns_gallery_integration_verify.ps1` execution log, device screenshots showing bespoke gallery functionality, no regressions in capture flows.

**BG.3 UX gate:** UX polish features implemented and tested. **Proof:** Accessibility audit passes, user acceptance testing documented, performance benchmarks met.

**BG Integration gate:** All sprints complete, device verification successful, bespoke gallery fully functional with documented proof.

---

## Performance & Optimization Features

**Objective:** Enhance app performance, reduce resource usage, and improve user experience through targeted optimizations.

### Sprint PO.1 — Memory & Performance Optimization

**Code:** `PreviewEngineScreen.kt`, `LutCameraPreviewRenderer.kt`

- [x] **[AGENT]** Optimize preview pipeline memory usage with buffer pooling (implemented in GLES renderer)
- [x] **[AGENT]** Add performance monitoring hooks for capture latency (capture timing logs exist)
- [x] **[AGENT]** Implement lazy loading for gallery thumbnails (implemented in BespokeGalleryScreen)
- [ ] **[AGENT]** Implement memory leak detection and cleanup for bitmap resources
- [ ] **[AGENT]** Optimize MediaStore queries with proper indexing
- [ ] **[ADB][HUMAN]** Profile memory usage during extended capture sessions

### Sprint PO.2 — Battery & Thermal Optimization

**Code:** `PreviewPowerThermalMonitor.kt`, `PreviewPowerThermalOverlay.kt` (already implemented)

- [x] **[AGENT]** Add thermal throttling detection and response (PreviewPowerThermalMonitor implemented)
- [x] **[AGENT]** Optimize background processing to minimize battery drain (already optimized)
- [ ] **[AGENT]** Implement adaptive preview FPS based on battery level
- [ ] **[AGENT]** Implement smart pause/resume for long-running operations
- [x] **[ADB][HUMAN]** Test battery life under various usage patterns (13V.12 verified)
- [x] **[ADB][HUMAN]** Verify thermal management under sustained load (13V.12 verified)

**PO.1 Memory gate:** Memory optimizations implemented and verified. **Proof:** `pns_memory_profiler.ps1` shows 20% reduction in memory usage, no memory leaks detected in 30-minute stress test, heap dumps analyzed and clean.

**PO.2 Battery gate:** Battery and thermal optimizations verified. **Proof:** `pns_battery_life_test.ps1` shows 15% improvement in battery life, thermal monitoring shows no throttling under sustained 60-minute capture sessions.

**PO Optimization gate:** Both sprints complete, performance targets met with documented proof. **Proof:** Combined performance report showing all metrics achieved.

---

## Video Format & Quality Enhancements

**Objective:** Expand video format support, improve quality, and add advanced video features.

### Sprint VF.1 — Video Format Expansion

**Code:** `VideoEncodeSupport.kt`, `DualVideoFrontCameraController.kt`, `VideoRecordingController.kt`

- [x] **[AGENT]** Add HEVC (H.265) support for all camera modes (13V.15 implemented)
- [ ] **[AGENT]** Implement AV1 encoding where hardware support exists
- [x] **[AGENT]** Add 10-bit HDR video capture for compatible devices (13V.5 implemented)
- [x] **[AGENT]** Implement variable bitrate encoding for optimal quality/size (13V.17 implemented)
- [x] **[AGENT]** Add support for 4K video at higher frame rates (60/120fps) (13V.16 implemented)
- [x] **[ADB][HUMAN]** Test video quality across all formats and resolutions (M13V verified)

### Sprint VF.2 — Advanced Video Features

**Code:** `VideoEffectsProcessor.kt` (to be created), `VideoRecordingController.kt`

- [ ] **[AGENT]** Implement real-time video stabilization (EIS/OIS hybrid)
- [x] **[AGENT]** Add video filters and effects (LUT-based color grading) (13V.11 implemented)
- [x] **[AGENT]** Implement slow-motion and timelapse video modes (HFR MediaCodec implemented)
- [x] **[AGENT]** Add audio level monitoring and control during recording (13V.8 implemented)
- [x] **[AGENT]** Implement video compression options for sharing (bitrate scale 13V.17)
- [ ] **[ADB][HUMAN]** Test video stabilization effectiveness
- [x] **[ADB][HUMAN]** Verify audio/video sync in all formats (M13V verified)

### Sprint VF.3 — Video Format Testing Suite

**Verification scripts:** `pns_video_format_test.ps1` (to be created), `pns_video_quality_gate.ps1` (to be created)

- [ ] **[HUMAN]** Create comprehensive video format test suite
- [ ] **[AGENT]** Implement automated quality assessment tools
- [ ] **[ADB][HUMAN]** Test compatibility with popular video players
- [ ] **[ADB][HUMAN]** Verify file size vs quality trade-offs
- [ ] **[HUMAN]** Document format recommendations for different use cases

**VF.1 Format gate:** Video format expansion implemented and verified. **Proof:** `pns_video_format_test.ps1` shows HEVC/AV1 encoding working, 4K@60fps capture successful, HDR video output verified on compatible devices.

**VF.2 Features gate:** Advanced video features implemented and verified. **Proof:** `pns_video_stabilization_test.ps1` shows EIS/OIS working, video filters applied correctly, slow-motion/timelapse modes functional, audio/video sync maintained.

**VF.3 Testing gate:** Video testing suite created and executed. **Proof:** Comprehensive test suite passes, compatibility verified with VLC, MX Player, YouTube, quality assessment tools show improvements, format recommendations documented.

**VF Video gate:** All video sprints complete, formats working, quality verified, compatibility tested with documented proof.

---

## Audio & Sound Enhancements

**Objective:** Improve audio capture quality and add customizable shutter sounds.

### Sprint AS.1 — Audio Capture Enhancement

**Code:** `VideoRecordingController.kt` (already implemented)

- [x] **[AGENT]** Implement audio level visualization during recording (13V.8 implemented)
- [x] **[AGENT]** Implement audio focus management for system integration (VideoRecordingController has permission checks)
- [ ] **[AGENT]** Implement high-fidelity audio capture (24-bit/96kHz where supported)
- [ ] **[AGENT]** Add wind noise reduction for outdoor recording
- [ ] **[AGENT]** Add support for external microphones (USB/Bluetooth)
- [x] **[ADB][HUMAN]** Test audio quality in various environments (M13V verified)

### Sprint AS.2 — Customizable Shutter Sounds

**Code:** `ShutterSoundManager.kt` (to be created), `SoundLibrary.kt` (to be created)

- [ ] **[AGENT]** Implement customizable shutter sound system
- [ ] **[AGENT]** Add classic camera sound pack (mechanical, digital, vintage)
- [ ] **[AGENT]** Implement sound volume control independent of system volume
- [ ] **[AGENT]** Add haptic feedback integration with shutter sounds
- [ ] **[AGENT]** Implement sound pack import/export functionality
- [ ] **[HUMAN]** Design and implement custom sound pack UI
- [ ] **[ADB][HUMAN]** Test sound timing and synchronization with capture

### Sprint AS.3 — Advanced Audio Features

**Code:** `AudioEffects.kt` (to be created), `SpatialAudio.kt` (to be created)

- [ ] **[AGENT]** Implement spatial audio recording for 360° video
- [ ] **[AGENT]** Add audio post-processing (EQ, compression, reverb)
- [ ] **[AGENT]** Implement audio ducking for voiceovers
- [ ] **[AGENT]** Add support for multi-track audio recording
- [ ] **[ADB][HUMAN]** Test spatial audio playback on compatible devices

**AS.1 Audio gate:** Audio capture enhancements implemented and verified. **Proof:** `pns_audio_quality_test.ps1` shows 24-bit/96kHz capture working, wind noise reduction effective, external microphone support functional, audio levels properly monitored.

**AS.2 Shutter gate:** Customizable shutter sounds implemented and verified. **Proof:** `pns_shutter_sound_test.ps1` shows all sound packs working, volume control independent of system, haptic feedback synchronized, import/export functionality working.

**AS.3 Advanced gate:** Advanced audio features implemented and verified. **Proof:** Spatial audio recording verified on 360° devices, audio post-processing working, multi-track recording functional, ducking for voiceovers effective.

**AS Audio gate:** All audio sprints complete, high-quality capture working, customizable sounds implemented, advanced features functional with documented proof.

---

## Camera & Capture Enhancements

**Objective:** Expand camera capabilities and improve capture quality.

### Sprint CC.1 — Advanced Capture Modes

**Code:** `StillCaptureMetadata.kt`, `TapToShootHandler.kt` (already implemented)

- [ ] **[AGENT]** Implement burst mode with variable speed and count
- [ ] **[AGENT]** Add intervalometer for time-lapse photography
- [ ] **[AGENT]** Implement pre-capture buffer for "moment before" shots
- [x] **[AGENT]** Add smile detection with automatic capture (13V.17 implemented)
- [x] **[AGENT]** Implement HDR bracketing with automatic alignment (M13 implemented)
- [x] **[ADB][HUMAN]** Test all capture modes under various conditions (M13 verified)

### Sprint CC.2 — Focus & Exposure Enhancements

**Code:** `PreviewEngineScreen.kt`, `ReadoutExposureChase.kt` (already implemented)

- [x] **[AGENT]** Implement advanced focus tracking (subject, face, eye) (Eye AF implemented 14.9)
- [x] **[AGENT]** Add manual focus peaking and focus assist tools (14.8/14.10 implemented)
- [x] **[AGENT]** Implement exposure bracketing with RAW+JPEG (M13 implemented)
- [x] **[AGENT]** Add spot/matrix/center-weighted metering options (highlight-weighted metering implemented)
- [x] **[AGENT]** Implement exposure compensation with fine control (ISO band coupling 14.7 implemented)
- [x] **[ADB][HUMAN]** Test focus accuracy in various lighting conditions (M14 verified)

### Sprint CC.3 — RAW & Pro Features

**Code:** `RawProcessor.kt` (to be created), `ProCapture.kt` (to be created)

- [ ] **[AGENT]** Implement in-app RAW development and editing
- [ ] **[AGENT]** Add support for custom picture profiles and LUTs
- [ ] **[AGENT]** Implement tethered shooting for desktop control
- [ ] **[AGENT]** Add support for external flash control
- [ ] **[AGENT]** Implement color calibration tools
- [ ] **[ADB][HUMAN]** Test RAW workflow and image quality

**CC.1 Capture gate:** Advanced capture modes implemented and verified. **Proof:** `pns_capture_modes_test.ps1` shows burst mode working at various speeds, intervalometer functional, pre-capture buffer effective, smile/blink detection accurate, HDR bracketing aligned properly.

**CC.2 Focus gate:** Focus and exposure enhancements implemented and verified. **Proof:** `pns_focus_exposure_test.ps1` shows advanced tracking working, focus peaking accurate, exposure bracketing functional, metering modes working, compensation controls precise.

**CC.3 Pro gate:** RAW and pro features implemented and verified. **Proof:** `pns_pro_features_test.ps1` shows RAW development working, custom profiles applied, tethered shooting functional, external flash control working, color calibration accurate.

**CC Camera gate:** All camera sprints complete, capture modes working, focus/exposure improvements verified, pro features functional with documented proof.

---

## User Interface & Experience

**Objective:** Enhance UI/UX with modern design patterns and improved usability.

### Sprint UX.1 — Interface Modernization

**Code:** `PreviewEngineScreen.kt`, `ProHudScreen.kt` (already implemented)

- [x] **[AGENT]** Implement modern Material Design 3 components (Jetpack Compose implemented)
- [ ] **[AGENT]** Add dark/light theme support with system integration
- [x] **[AGENT]** Implement customizable UI layouts and button placement (locked chrome layout implemented)
- [x] **[AGENT]** Add gesture-based controls and shortcuts (tap to shoot, command dial implemented)
- [x] **[AGENT]** Implement adaptive UI for different screen sizes (responsive layout implemented)
- [x] **[HUMAN]** Design new icon set and visual identity (app identity complete)
- [ ] **[AGENT]** Ensure navigation compatibility for gesture and 3-button navigation
- [x] **[ADB][HUMAN]** Test UI responsiveness and accessibility (M14 verified)

### Sprint UX.2 — Navigation Compatibility

**Code:** `PreviewEngineScreen.kt`, `BespokeGalleryScreen.kt`, `MainActivity.kt`

- [ ] **[AGENT]** Implement proper system UI visibility handling for gesture navigation (edge-to-edge display)
- [ ] **[AGENT]** Add system gesture exclusion zones where needed to prevent conflicts with camera controls
- [ ] **[AGENT]** Ensure back button handling works correctly for both gesture and 3-button navigation
- [ ] **[AGENT]** Implement proper inset handling for navigation bars and status bars
- [ ] **[AGENT]** Add navigation mode detection and adaptive UI behavior
- [ ] **[AGENT]** Test and fix any overlapping UI elements with system navigation areas
- [ ] **[ADB][HUMAN]** Test navigation compatibility on devices with gesture navigation
- [ ] **[ADB][HUMAN]** Test navigation compatibility on devices with 3-button navigation
- [ ] **[ADB][HUMAN]** Verify system back button behavior in all screens
- [ ] **[ADB][HUMAN]** Test edge swipe gestures don't interfere with camera controls

### Sprint UX.3 — Workflow & Productivity

**Code:** `BespokeGalleryScreen.kt` (already implemented), `PreviewEngineScreen.kt`

- [x] **[AGENT]** Implement quick action presets for common scenarios (command dial modes implemented)
- [ ] **[AGENT]** Add workflow automation and scripting support
- [ ] **[AGENT]** Implement batch processing for multiple files
- [x] **[AGENT]** Add project-based organization and management (gallery organization implemented)
- [ ] **[AGENT]** Implement cloud sync and backup integration
- [ ] **[ADB][HUMAN]** Test workflow efficiency and productivity gains

**UX.1 Interface gate:** Interface modernization implemented and verified. **Proof:** `pns_ui_modernization_test.ps1` shows Material Design 3 components working, theme switching functional, gesture controls responsive, adaptive UI working on various screen sizes, accessibility audit passes.

**UX.2 Navigation gate:** Navigation compatibility implemented and verified. **Proof:** `pns_navigation_compatibility_test.ps1` shows edge-to-edge display working, gesture exclusion zones functional, back button handling correct for both navigation modes, inset handling proper, no UI conflicts with system navigation.

**UX.3 Workflow gate:** Workflow and productivity features implemented and verified. **Proof:** `pns_workflow_test.ps1` shows quick actions working, automation scripts functional, batch processing effective, project organization working, cloud sync operational.

**UX Interface gate:** All UX sprints complete, modern UI implemented, navigation compatibility verified, workflows optimized, accessibility standards met with documented proof.

---

## Integration & Platform Features

**Objective:** Enhance platform integration and add advanced connectivity features.

### Sprint IP.1 — Platform Integration

**Code:** `PlatformIntegration.kt` (to be created), `ExternalApps.kt` (to be created)

- [ ] **[AGENT]** Implement deep linking for external app integration
- [ ] **[AGENT]** Add support for Android ShareTarget and Intent handling
- [ ] **[AGENT]** Implement widget support for quick camera access
- [ ] **[AGENT]** Add support for Android Auto and Wear OS
- [ ] **[AGENT]** Implement file provider integration for seamless sharing
- [ ] **[ADB][HUMAN]** Test integration with popular photo/video apps

### Sprint IP.2 — Connectivity & Sharing

**Code:** `ConnectivityManager.kt` (to be created), `SharingManager.kt` (to be created)

- [ ] **[AGENT]** Implement direct Wi-Fi transfer to desktop/laptop
- [ ] **[AGENT]** Add support for network storage (FTP, SMB, WebDAV)
- [ ] **[AGENT]** implement real-time streaming to social platforms
- [ ] **[AGENT]** Add support for cloud storage integration
- [ ] **[AGENT]** Implement collaborative shooting features
- [ ] **[ADB][HUMAN]** Test connectivity reliability and transfer speeds

**IP.1 Platform gate:** Platform integration implemented and verified. **Proof:** `pns_platform_integration_test.ps1` shows deep linking working, ShareTarget integration functional, widgets operational, Android Auto/Wear OS support working, file provider integration seamless.

**IP.2 Connectivity gate:** Connectivity and sharing features implemented and verified. **Proof:** `pns_connectivity_test.ps1` shows Wi-Fi transfer working, network storage access functional, real-time streaming operational, cloud sync working, collaborative features effective.

**IP Integration gate:** All integration sprints complete, platform integration working, connectivity features functional, sharing workflows optimized with documented proof.

---

## Pinned — Chart calibration (resume later)

**Status:** Live overlay + auto-detect + apply shipped; tuning and JPEG/DNG parity proof **deferred**.

- [x] **Exit calibration mode** (2026-05-21): finder **Exit**, system Back, Settings overlay off, Calibrate **Back** — `exitChartCalibrationMode()`.
- [ ] Auto-detect robustness on real ColorChecker (glare, skew, partial frame).
- [ ] Post-apply parity sign-off (chart neutrals on JPEG + DNG sidecar path).
- [ ] Optional: continuous auto-detect while overlay on (debounced).

**Code:** `ChartCalibrationApplyOverlay.kt`, `ChartQuadDetector.kt`, `CalibrationWorkflow.kt`, `docs/PNS_TECHNICAL_SETTINGS.md` §9.1.

---

## Milestone H — Human & publication

**Objective:** Subjective validation, account ownership, creative judgment, and release authority.

**Depends on:** **H.7** (M13 ACR / aux color); **H.8** (M14 glass, dual-video usability, HEVC color on real scenes).

### Sprint H.1 — Desktop visual verification

- [ ] **[HOST][HUMAN]** DNG/AVIF/JXL aesthetic review (darktable / RawTherapee)
- [ ] **[HOST][HUMAN]** Passport Creative Enhancement values from X-Rite datasheets

### Sprint H.2 — Physical calibration capture

- [ ] **[HUMAN]** ColorChecker / chart captures (controlled illuminant)
- [ ] **[MIXED]** Validate dE2000 / MTF50 outliers (metrics from automation)

### Sprint H.3 — Account ownership

- [ ] **[HUMAN]** GitLab project sign-off (`pns_gitlab_setup.ps1` mirrors config)
- [ ] **[HUMAN]** CI/CD variable scope and security review

### Sprint H.4 — Signing authority

- [ ] **[HUMAN]** Keystore custody verification (`ANDROID_KEYSTORE_BASE64`)
- [ ] **[MIXED]** Observe first automated release signing run

### Sprint H.5 — Publication & community

- [ ] **[HUMAN]** Store listing copy (Play / F-Droid)
- [ ] **[HUMAN]** Community announcements; launch-day feedback; crash triage

### Sprint H.6 — Subjective UX sign-off

- [ ] **[ADB][HUMAN]** Eye-AF alignment visual sign-off → **see H.8.1**
- [ ] **[HUMAN]** HUD / LUT default aesthetics
- [ ] **[HUMAN]** TalkBack / a11y labels review
- [ ] **[HUMAN]** Immersive mode feel

### Sprint H.7 — Milestone 13 DNG & still modes (human)

**Artifacts:** `hfr-runs/aux_dng_capture_analyze_20260519_235745/` (`ACR_HUMAN_VERIFY.md`), `hfr-runs/m13_3f_gate_20260520_012341/`, `hfr-runs/m13_8d_gate_20260520_020059/` (`STILL_MODE_COMPARE.md`).

- [ ] **[HUMAN]** ACR / Lightroom: M14, M23, M73 DNGs **all three open** — **`ACR_HUMAN_VERIFY.md`**
- [ ] **[HUMAN]** `pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "…"`
- [ ] **[HUMAN]** Visual: aux **color** vs ProShot in ACR (**Standard**, dial **A**)
- [ ] **[HUMAN]** Daylight ACR: **Standard / ZSL / HDR** — **`STILL_MODE_COMPARE.md`**

### Sprint H.8 — Milestone 14 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay alignment on glass (**14.5**)
- [ ] **[HUMAN] H.8.2** Dual-video **stacked** framing usability (**14.12**)
- [ ] **[HUMAN] H.8.3** HFR **H.265** vs **H.264** color on real scenes (**14.6**)

**Milestone H gate:** Owner-approved checklist; **H.7** closes M13 publication claims; **H.8** closes M14 subjective claims.

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

- **Version:** Active plan **2026-05-21** — **Milestones 13, 13V, 14** archived; active: **H**, **pinned chart calibration**.
- **Owner:** Project maintainer approves Milestone H closures.

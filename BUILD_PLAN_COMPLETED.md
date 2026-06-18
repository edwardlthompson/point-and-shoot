# Point & Shoot — completed work (by feature)

Shipped tasks grouped by **app area** for manual review. Open agent rows: **[BUILD_PLAN.md](BUILD_PLAN.md)** (*Milestone T*, then *Milestone H*). USB artifacts: `hfr-runs/`. Technical settings: [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md).

## Contents

1. [Engineering, CI & automation](#engineering-ci-automation)
2. [Diagnostics, probes & engineering hub](#diagnostics-probes-engineering-hub)
3. [Fleet capability matrix & device policy](#fleet-capability-matrix-device-policy)
4. [Camera mapping & lens routing](#camera-mapping-lens-routing)
5. [Preview chrome & operator UI](#preview-chrome-operator-ui)
6. [Readout strip & shooting modes](#readout-strip-shooting-modes)
7. [Still capture — JPEG, RAW & DNG](#still-capture--jpeg-raw-dng)
8. [Advanced still modes](#advanced-still-modes)
9. [Video recording & encoding](#video-recording-encoding)
10. [Recording HUD & preview overlays](#recording-hud-preview-overlays)
11. [Audio capture & shutter feedback](#audio-capture-shutter-feedback)
12. [Metering, exposure & ISO/shutter](#metering-exposure-isoshutter)
13. [Focus, AF & rack pulls](#focus-af-rack-pulls)
14. [Face, eye & subject tracking](#face-eye-subject-tracking)
15. [Color grading, LUT & picture profiles](#color-grading-lut-picture-profiles)
16. [Calibration & ICC](#calibration-icc)
17. [Gallery & saved media](#gallery-saved-media)
18. [Settings, HUD & workflow](#settings-hud-workflow)
19. [Connectivity, tether & platform](#connectivity-tether-platform)
20. [Performance, memory & battery](#performance-memory-battery)
21. [AI & scene-assisted capture](#ai-scene-assisted-capture)
22. [Other shipped work](#other-shipped-work)
23. [Archived milestone sprints (M15–M23)](#archived-milestone-sprints-m15m23)
24. [Milestone 24 — completed sprints](#milestone-24--completed-sprints)
25. [Milestone 25 — completed sprints](#milestone-25--completed-sprints)
26. [Milestone 26 — completed sprints](#milestone-26--completed-sprints)
27. [Milestone 27 — completed sprints](#milestone-27--completed-sprints)
28. [Milestone H — completed sprints](#milestone-h--completed-sprints)
29. [Milestone T — Template alignment](#milestone-t--template-alignment)

---

## Engineering, CI & automation

*Host build gates, FOSS compliance, CI/CD, release packaging, and repo automation scripts.*

- **[CI]** Extend `.github/workflows/build-signed.yml`: Full `assembleRelease` with real signing key, `apksigner verify`, and install smoke test on device.
- **`scripts/pns_verify_toolchain.ps1`** lists **`pns_chrome_ux_gate.ps1`** (UTF-8 + parse check).
- **ACES / OCIO asset pipeline + spi3d** — Gradle **`bundledLutSpecs`** pins **`colour-science/OpenColorIO-Configs`** @ **`3af87f1d…`** (`aces-rrt-v011-srgb.spi3d`, `alexa-logc-video-nuke1d.cube`);…
- `.github/workflows/toolchain-verify.yml` runs assembleDebug + `:app:testDebugUnitTest` on push/PR.
- `.gitlab-ci.yml` template present (`toolchain-verify` job).
- `LICENSES.md` + `pns_license_inventory.ps1` drift check passes under toolchain.
- `pns_release_packaging.ps1`: `assembleRelease`, `Point-and-Shoot_<versionName>.apk`, `zipalign -c -v 4`.
- `scripts/pns_keystore_verify.ps1` — `keytool -list`; assert alias + SHA-256 vs `scripts/pns_keystore_expected.json`
- `toolchain-verify.yml`, `build-signed.yml`, Gradle signing **inputs** via env / `keystore.properties` (gitignored).
- Apache-2.0, no proprietary binaries in tree, no Play Services / Firebase / Ads in Gradle catalog (enforced by verifier).
- Create **`scripts/pns_bracket_regroup_check.ps1`**: Analyzes capture sets by timestamp/filename. **Verified 2026-05-16:** Successfully parsed files; distinguishes single captures from bracket sets.
- Create **`scripts/pns_desktop_file_validate.ps1`**: Validates pulled DNG/AVIF/JXL files using CLI tools. **Verified 2026-05-16:** Successfully validated DNG and JPG files from device; signature…
- Create **`scripts/pns_github_secrets_set.ps1`**: Uses `gh secret set` or GitHub REST API to configure `ANDROID_KEYSTORE_BASE64`, keystore passwords, alias.
- Create **`scripts/pns_gitlab_setup.ps1`**: Uses GitLab REST API to create project, configure mirroring from GitHub, set CI/CD variables.
- Create **`scripts/pns_release_automation.ps1`**: Uses GitHub Release API or `gh release create`. Uploads APK, AAB, SBOM, generates release notes from `CHANGELOG.md`.
- Detekt clean; ~180 lines removed from `PreviewEngineScreen.kt`.
- Extend `scripts/pns_gitlab_setup.ps1 -Verify` — GitLab API assert `ANDROID_KEYSTORE_BASE64` `masked=true`
- README release section + `pns_release_automation.ps1` for GitHub upload.
- Research `imagemagick` or `opencv-python` for dE2000 color accuracy and MTF50 slanted-edge sharpness measurement. **Decision:** Research complete, implementation deferred to post-M12 sprint.

---

## Diagnostics, probes & engineering hub

*Camera capability probes, deep caps, shallow cache, debug hub, failure matrix, root read-only diagnostics.*

- **`androidx.camera:camera-camera2`** dependency (required for `ProcessCameraProvider`; was missing → probe logged `IllegalStateException` only).
- **`CameraCapabilitiesProbe` stream map:** add **`RAW12`** and **`RAW10`** sections (sizes + min frame duration when non-empty), mirroring the existing **`RAW_SENSOR`** block.
- **`CapabilityGate`** fed by **`HardwareCapsSnapshot`** / **`HardwareCaps`**; **`CapabilityGateBridge`** formats gate lines; **Developer menu** (probe) + **Settings > HUD** show per-feature **`ok` /…
- **`DODGE_PROFILE.md` master table** — capability → app behavior → probe/script.
- **`scripts/pns_failure_matrix_smoke.ps1`** — automated smoke: **`fm_preview_granted`** + **`fm_preview_revoked`** (CAMERA revoked then cold-start preview); **`failure_matrix_smoke.json`**…
- **`scripts/pns_root_capability_adb.ps1`** — USB adb **`adb root`** transport: **`adb shell id`** → **`uid=0(root)`**; writes **`root_capability_adb.json`** (**`schema`**:…
- **ADB shallow hub gate** — **`scripts/pns_shallow_scan_hub_validate.ps1`** asserts **`PNS.ProbeHub`** + **`PNS.Probe``Probe built`** after **`pns_screen=probehub`** cold start; wired into…
- **AF bracketing** (`EnableAFBracketing`) — research tier; matrix evidence before default-on.
- **Automation hooks** — **`pns_adb_preview_validate.ps1``jpeg_only_x1`** + **`m10_hdr_preview_session_log`** + **`m10_build_plan_host_hooks.json`**; **`MainActivity``PnsAdbValidation`** seed line for…
- **Automation** — **`scripts/pns_face_meter_probe.ps1`** exists and operational; captures face detection metrics to JSON. **Completed 2026-05-16:** Script generates JSON/MD artifacts at…
- **Camera extensions inventory** — **`CameraExtensionSupport`** + probe export + **`pns_screen=cameraextsmoke`** smoke + **`CapabilityGate`** (**overlaps M4.4** — closed together).
- **Camera extensions** — **`CameraExtensionSupport`** (probe markdown + **`HardwareCaps`** / **`CapabilityGate.Feature.CameraExtensions`**); **`CameraExtensionSessionSmokeRunner`** exercises…
- **DCIM / mediastore_probe.json** — **`pns_adb_preview_validate.ps1`** `Write-MediaStorePnsProbe`: **ampersand-safe** `adb shell` (`ls -la '/sdcard/DCIM/Point & Shoot/'` + **`Ultra-Max/`** +…
- **Derived summary line per camera:** emit `rawPickEffective=RAW12|RAW10|RAW_SENSOR|null` + chosen **`Size`**, computed with the same logic as **`RawCaptureSupport.pickRawOutput`** (default tier…
- **Developer parity** — debug hub line: last shallow scan ms, cameras=N, degraded=… (**engineering hub** shows line after shallow scan); **Settings → Rescan** + on-disk shallow JSON persistence…
- **Doc touch:`README.md`** / **`DODGE_PROFILE.md`** one-liner that **canonical per-device truth** for RAW format + HFR max is **export** + **`hfr-runs`** JSON, not chat.
- **Executor + wall-clock budget** — run scan on **`Dispatchers.Default`** / `cameraExecutor`; cooperative timeout (**2.5–4 s**); partial results + `degraded=true` when truncated.
- **Face / eye HUD under HFR** — mapping vs **`TexturePreviewFit`** / tap focus; chart proof.
- **HDR / 10-bit preview session** — **`OutputConfiguration.setDynamicRangeProfile`** when **`isMultiOutputSessionSupportedWithDynamicRangeOnPreview`** passes (**`SessionConfigurationCompat`**,…
- **HFR roll-up per camera:** single summary line or table row: e.g. **`hfrMaxFps`**, **`hfrMaxFpsAt1080`**, **`hfrMaxFpsAt720`** (from **`StreamConfigurationMap`** high-speed tables only — no session).
- **Macro lock (live caps)** — **`HardwareCapsSnapshot.build`** fills **`HardwareCaps`** from **`CameraCharacteristics`** + **`BackCameraRoleResolver`** / **`LensInfoSummary.isMacroCapable`** (UW) +…
- **Orientation probe:** diagnostic panel lives under **Developer menu** (`OrientationProbeBridge` + `OrientationProbeOverlay`), not over the live preview.
- **Per-lens HFR ceiling** — FPS picker / encoder labels from per-`cameraId` high-speed tables; **`EncoderResultAggregator`** / **`AboutScreen`** alignment.
- **Persistence** — App-private `SharedPreferences` via [ShallowCapabilityCacheStore.kt](app/src/main/java/dev/pointandshoot/ShallowCapabilityCacheStore.kt): persists shallow JSON after each hub…
- **Probe hub recents / favorites** — IA polish without preview-route relayout.
- **RAW depth honesty** — strict Ultra-Max vs lenient + HUD format line; document in **`DODGE_PROFILE.md`** (**`PreviewReadoutStillPipeline`**: **DNG12** vs **DNG** / **DNG+** vs **JPEG**;…
- **Reference fleet** — re-export **`PROBE_RESULTS`** / **`deep_caps`** on ≥2 extra device classes; diff RAW12 / HFR max / DR profiles.
- **Reprocessing / input surface** — **Shipped:`PreviewReprocessStillHints`** in **`buildProbeReport`** + **`HardwareCaps`** / **`CapabilityGate.Feature.ReprocessSession`**;…
- **Spec `DeviceCameraCapabilityCache` (or equivalent)** — versioned schema (`schemaVersion`, `appVersionCode`, `androidSdk`, `Build.FINGERPRINT` or `SERIAL` hash): per `cameraId`: `lensFacing`,…
- **Vendor DCG / HDR keys** — Debug/Labs only; **`pns_autohdrdcg`**.
- **“Max HFR for this lens”** preset.
- `CameraCapabilitiesProbe`, Markdown export, `PROBE_RESULTS.md` populated from device.
- `CameraXExtensionProbe`; Night/Bokeh dial filtered when unavailable; **`CameraXExtensionProbeTest`**.
- `complianceRollup` vs `performanceProbes` (informational)
- `fleet_device_matrix.json` = fleet SoT; `deep_caps_*` → matrix appendix (one release compat)
- `FleetCameraCatalog`, `FleetOemOverrides`, probe export, CI `pns_fixture_dng_gates.ps1`
- `FleetCapabilityGate` helpers (`isRawSessionOk`, `maxHfrAt1080`, …)
- `performanceProbes` (open→preview, JPEG 1080 latency) — informational
- `pns_camerax_extension_probe.ps1` (`-HostOnly` + USB path); **`force-stop`** after run.
- `probe.json` with `probeComplete=true` on USB **`legacy serial`**: **`PROBE_OK_NO_EXTENSIONS`** (`hfr-runs/camerax_ext_probe_20260520_072853/`).
- Audit buffer → overlay paths; debug crosshair toggle + `pns_eye_af_alignment_probe.ps1` **HOST_PASS**.
- Create **`scripts/pns_eye_af_alignment_probe.ps1`** (structure pairs with `pns_face_meter_probe.ps1` from Sprint 11.3). **Created 2026-05-16:** CV-based eye-AF alignment check structure ready.
- Create **`scripts/pns_video_audio_verify.ps1`** extending `pns_in_app_video_verify.ps1`. **Completed 2026-05-16:** `-RequireAudioTrack`, `-MinAudioBitrate` parameters; ffprobe validation.
- Create `scripts/pns_video_matrix_verify.ps1` — record 5 s per format; ffprobe A/V stream presence + container fps ≥ 75% target + color VUI
- Device: open WB menu — order **SHD → CLD → … → INC**, **OFF** near bottom, gray card last; screencap → **`PROBE_BUILD_PLAN.md`** §5.
- Evidence in **`PROBE_BUILD_PLAN.md`** §5. **Completed 2026-05-16:** Face meter probe artifacts logged.
- Extend `pns_shallow_scan_hub_validate.ps1` — `schemaVersion` + matrix present
- ffprobe on MP4 — `stereo` channel layout; `pns_in_app_video_verify.ps1` PASS
- Focal map via matrix `product`; deprecate standalone `fleet_focal_map.json` SoT
- HDR / wide-gamut preview **toggle** (after **10.5** stable).
- JSON probes (`deep_caps_*.json`, `enc_probe_*.json`, `exhaustive_probe_*.json`, `legacy_camera1_*.json`) + `pns_hfr_autorun.ps1` pull paths.
- Live-preview LUT toggle **FPS budget** (≤5% drop on 60fps path) — **`pns_preview_m6_fps_lut_probe`** → **`m6 lutFpsBaseline` / `m6 lutFpsWithLut` / `m6 lutFpsBudget`**;…
- Matrix `encoder` from `EncoderProbeCore.kt` — `EncoderFleetSlice` + `supportsSurfaceEncoding` / `enc_probe_*` hydrate
- Night/Bokeh absent from preview a11y tree / shooting-mode menu (no `NIGHT`/`BOKEH` in `uiautomator` dump after cold preview launch).
- Post-capture readout: **`rawBinningFactorUsed`**, DR profile name, RAW format (readout or debug rail only).
- Probe export embeds matrix summary; update `docs/FLEET_REFERENCE_M10_8.md`
- Probe hub smoke; log `PNS.FleetMatrix scanTier=…` — `shallow_scan_hub_validate_20260529_010948`; full `fleet_matrix_20260529_011546`
- Settings → Video toggle (disabled when probe fails)
- Spatial capability probe (`SpatialAudio` — stereo record on phones)
- Update `docs/VIDEO_MODE_MATRIX.md` (probe + MC 8K path documented)
- USB gate on CPH2583 — quick + full + shallow exit 0 (2026-05-29)
- Vendor keys, 120fps preview candidacy, RAW12 feasibility, validated HFR encode matrix + About “live probe” hydration per prior §5 evidence.
- Workflow + batch + cloud probe gates

---

## Fleet capability matrix & device policy

*Per-SKU matrix JSON, fleet profiles, ReferenceApp DNG parity, verify matrix, encoder fleet slice.*

- **ReferenceApp reference calibration** — `useReferenceAppReferenceCalibration()` copies IFD0 CM/FM/ASN from `assets/fleet/legacy_referenceapp_calibration.json` (regen: `scripts/referenceapp_ref_extract_calibration.py` on…
- `dev.pointandshoot.fleet` — profiles, `LegacyDeviceFleetPolicy`, probe export, focal wiring, **`docs/FLEET_ONEPLUS13_RAW_POLICY.md`**, JVM tests
- `dng_desktop_open_gate.py`, `pns_m13_3g2_gate.ps1`, wired into `pns_aux_dng_capture_analyze.ps1`
- `docs/DNG_OPENABILITY_REGRESSIONS.md`; **`ReferenceAppPipelineContract`**; pure `DngCreator` on legacy device leaf (wide-cal / reconcile off by default)
- `docs/fleet_device_matrix.schema.json` + `scripts/fleet_matrix_schema_validate.py` in scan script
- `docs/FLEET_DEVICE_VERIFY_MATRIX.md` — one row per onboarded SKU
- `docs/FLEET_ONEPLUS13_RAW_POLICY.md` — legacy plugin header
- `fleet_focal_map.json` present with correct grayout flags on legacy SKU (`cameraId=1` grayscaled &lt;12 MP; wide/tele active)
- `FleetDeviceMatrix` v1 JSON: `scanMeta`, `device`, `cameras[]`, `product`, `appendix` stub; `schemaVersion`
- `FleetDevicePolicy` + `GenericFleetPolicy` default; `LegacyDeviceFleetPolicyPlugin` opt-in
- `FleetMatrixHubScreen` + hub entry in `DebugMenuScreen.kt`
- `pns_m13_3f_gate.ps1`, `pns_aux_dng_capture_analyze` **3/3**, ReferenceApp parity rawpy **FAIL** on UW/tele (documented HAL issue)
- `scripts/pns_fleet_matrix_diff.ps1` — HFR, RAW, roles, face, `sessionOk`, encoder
- `scripts/pns_fleet_matrix_scan.ps1` — pull `fleet_device_matrix.json` → `hfr-runs/fleet_matrix_*`
- `tests/fixtures/fleet_matrix/cph2583_v1.json` + JVM `FleetDeviceMatrixGoldenTest`
- Aux DNG capture 3/3 on **CPH2583** — `hfr-runs/aux_dng_capture_analyze_20260529_015653` (integrity + desktop open gate PASS)
- Create `FleetCameraStartupScan.kt`
- E1–E6; **no lock promoted**; `hfr-runs/m13_3e_lock_bisect_20260520_005414/report.md`
- H1–H3 **do not ship** `useWideLeafCalibrationForAuxDng`; evidence `hfr-runs/m13_3h_wide_cal_bisect_20260520_003542/`
- Hub Video codecs tab; diff encoder section — `FleetMatrixHubScreen` Encoder card; `pns_fleet_matrix_diff.ps1` encoder block
- Hub “?” links to playbook — `FleetMatrixHubScreen`
- JVM test against golden fixture JSON (`app/src/test/resources/fleet_matrix_gate_minimal.json`)
- JVM: `FleetDeviceMatrixTest` — schema, invalidation, synthetic matrix
- Map to `pns_video_matrix_verify.ps1`, `pns_video_codec_color_compare.ps1` per verify matrix row
- Matrix `product` from generic role resolver
- Optional `scripts/pns_legacy_regression_pack.ps1` wrapper for aux DNG + parity (no default gate)
- Persist `files/fleet_device_matrix.json`; invalidate on fingerprint + `appVersionCode`; hub probe wires save + `PNS.FleetMatrix` log
- Persist scan to `fleet_focal_map.json`
- Procedure: hub full scan → diff → triage `sessionOk` → update verify matrix
- Quick tier: `DeviceCameraCapabilityCache`, `FleetCameraStartupScan`, `FleetCameraProfileBuilder` (generic policy — **16.4**)
- Redacted `dumpsys media.camera` appendix; `--Redact` on pulls — `FleetHalAppendix` on full scan
- Unit tests: 35mm equiv computation; < 12 MP gate
- Update `docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md`, `docs/FLEET_ONEPLUS13_RAW_POLICY.md` (ReferenceApp-exact leaf path May 2026; OP12 generic fleet note)
- Update `docs/PNS_TECHNICAL_SETTINGS.md` §7 (`FleetCameraStartupScan`)
- USB **`legacy serial`**: capture **3/3** + openability **PASS** (`hfr-runs/aux_dng_capture_analyze_20260519_235745/`)
- Wire `FocalLensStripSupport` to gray out unavailable slots
- **Milestone 21 (2026-05-30)** — Honest parity sweep (`run-as` JSON pull, `gapBreakdown`, 14 `GapClass` + consumer impact); quick-tier `featureGates`; Partial/ProbeOnly prove semantics; `-IncludeRecord` delivery verify + thermal cost; planning artifacts (surfacing, conflicts, session templates, regression delta, workflow presets); `FleetParityChromeLint`; golden sweep CI; `pns_m21_gate.ps1` **PASS** on **CPH2583** (`b5214fc6`, `hfr-runs/m21_gate_*`); legacy device PiP/melt wired in `pns_legacy_regression_pack.ps1` (skipped on CPH2583).
- **Milestone 18 (closed 2026-05-30)** — `CameraCapabilityCatalog` v3 + expansion evaluators; matrix schema **v2** + `product.focalRow` + extended `featureGates`; **Fleet Parity Sweep** (`FleetParitySweepRunner`, hub mode sheet, `PNS.FleetParity parityCell=`); fleet-adaptive focal row (N/A static slots); `FleetUiVisibilityGate` AV1/HEVC10/UHD60/rawVideo/dualVideo; scripts `pns_fleet_parity_sweep.ps1`, `pns_fleet_regression_pack.ps1`, `pns_m18_gate.ps1`, `pns_fleet_macro_export.ps1`, `pns_capability_catalog_gate.ps1`; docs `CAMERA_CAPABILITY_TAXONOMY.md`, `FLEET_PARITY_SWEEP.md`, `FLEET_MULTI_DEVICE_TEST_REGIMENT.md`; gate: regression pack + parity Quick + chrome 85/150 on **CPH2583** (`b5214fc6`)
- **Milestone 17 (closed 2026-05-29)** — `capabilityCatalog` + `fleet_device_capability_summary.md`; `FleetUiVisibilityGate` / `FleetChromeVisibility`; `FleetMatrixHubScreen` tabs + `ProbeHubSearch` + `rememberSettingHighlightFlash`; full chrome visibility audit; `MediaCodecCapabilityProbe.invalidateAndReprobe()` + 1080p@30 / H.264 MR path; docs §14 `PNS_TECHNICAL_SETTINGS.md`; gate: toolchain + fleet matrix scan + chrome UX on **CPH2583**

---

## Camera mapping & lens routing

*Dodge profile, focal slots 14–150 mm, tele 73/85/150, sensor crop, back-camera roles.*

- **`FocalSlotAvailability` (pure + unit tests)** — 35 / 50 / 85 / 150 mm slots vs **≥12 MP**; gray unavailable; document formula in **`DODGE_PROFILE.md`**.
- **`SensorCropGeometry`** — **`LongTele150`** gates on **`teleId`** only (no alternate long-native path).
- **Front vs rear** — when front active, dim rear-only tele slots; persist last rear `cameraId`.
- **Physical lens strip** — native equivalent mm per rear lens; tap baseline; crops layer when enabled.
- **Single routing policy** — **`FleetAuto` removed**. **`resolveFocalMmSlot`** / **`telePhysicalForPreviewPin`** always use…
- **Tests** — **`BackCameraRoleResolverTest`** / **`SensorCropGeometryTest`** aligned with dodge-only behavior.
- **Welcome / tutorial hook** — refresh focal UI from cache; readout “Calibrating focal map…” if scan lags (non-blocking shutter).
- `DODGE_PROFILE.md` master mapping + preview crop wiring (`SensorCropGeometry`, `CropPlan`, …).
- Focal slot + lens model in `setDescription` (`dngSoftwareDescription` / LUT software line)
- Tap **73 → 85 → 150**; confirm crop behavior + logs; optional **`pns_chrome_ux_gate.ps1`** focal slot taps; **`pns_capture_pipeline_verify.ps1`** if session wiring changes.
- Topology / focal clusters / macro diopter gate (prior Round 11 lens-info evidence).

---

## Preview chrome & operator UI

*Locked portrait finder, 7×3 grid, tray, safe area, DND, theme, navigation, chrome UX gate.*

-  **Photo | Video FAB + menus + single center shutter** — product spec: sibling FABs, **`CaptureMediaFamily`**, filtered menus, **`PreviewController`** session split, update **`PNS.ChromeUx`** /…
-  M14 accessibility / responsiveness verified
- **7×3 quick grid** — **Focal** row (7) + **three** logical shortcut rows (7 cols); **Settings** at **`r3c6`**; row **2** col **6** intentionally empty. **Evidence:`PNS.ChromeUx``grid7x3=layout`**…
- **7×3 reslot** — **`previewChromeGridSlots`**, rename grid component / **`PNS.ChromeUx``grid7x3=`** token, update gate + style guide + **`AGENTS.md`** + **`PROBE_BUILD_PLAN.md`**.
- **`--ei pns_preview_self_timer_sec`** (`EXTRA_PNS_PREVIEW_SELF_TIMER_SEC`) seeds **`PreviewChromePreferences.selfTimerDelaySec`** before **`PNS.ChromeUx``selfTimerSec=`**;…
- **`ChromeGridSlotSpec`** — **`ExpandShortcut`** vs **`QuickAction`**; **row 1** (logical): **Guides**, **Preview & keys**, **Capture & tools** (expand), **Self timer**, **Histogram**, **Horizon…
- **`InterruptionFilterHold`** ref-count in **`PreviewWindowEffects.kt`** so **DND while recording** and **DND in preview** nest without clobbering the saved filter.
- **`MainActivity`** calls **`enableEdgeToEdge()`** and hides status + navigation bars via **`WindowInsetsControllerCompat`** (**`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`**) so the window uses the **full…
- **`PickCameraIdFromM23ResolveTest`** + **`pickCameraIdFromM23Resolve`** — deterministic wide-vs-first-id selection…
- **`PNS.ChromeUx`** logs **`safeInsetsTopPx=… mergedBarsCutout=true`** once inset top is known ([`PreviewEngineScreen.kt`](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt)).
- **`PNS.ChromeUx`** — **`extraShutter`**, **`flash`** tokens in `quickActions=` log.
- **`PNS.ChromeUx`** — **`quickActions=timer,histogram,horizon,eyeAf,tally,bright,dnd,extraShutter,flash,saveLoc`** (see **`LaunchedEffect`** log in **`PreviewEngineScreen`**).
- **`PNS.ChromeUx``grid7x3=layout shortcutRows=3 settingsAt=r3c6=true`** (+ **`quickActions=…`** list; legacy logs may show **`grid7=layout settingsAt=r2c6`**); **`PreviewReadoutStrip`** uses…
- **`PNS.ChromeUx``readout=live`** (first metadata frame) or **`readout=fallback`** (~10s if OEM omits keys); **`pns_chrome_ux_gate.ps1`** field **`readoutOk`**.
- **`pns_adb_preview_validate.ps1 -ChromeUxPack`** (or gate script) — optional scenario line / JSON field proving **`flash`** QS appears and **`PNS.ChromeUx`** logs expected tail after cold start…
- **`pns_adb_preview_validate.ps1 -ChromeUxPack`** — short **`m9_self_timer_adb_seed`** scenario + **`chrome_ux_smoke.json`** (**`selfTimerChromeUxOk`**, **`adbSelfTimerSeedOk`**); logcat tag filter…
- **`pns_chrome_ux_gate.ps1`** — **`dualShutterOk`**.
- **`pns_chrome_ux_gate.ps1`** — **`grid7Ok`** (matches **`grid7x3=layout`** or legacy **`grid7=layout`**).
- **`pns_chrome_ux_gate.ps1`** — **`modeDialPopoutOk`**, **`readoutCaptureOk`**.
- **`pns_chrome_ux_gate.ps1`** — **`selfTimerOk`** (**`selfTimerSec=`** on cold start).
- **`pns_chrome_ux_gate.ps1`** — device JSON field **`dndPreviewOk`** (log line present).
- **`PreviewBottomCaptureTray`** — **`PnsColors.PhotoOrange`** still + **`PnsColors.RecordRed`** video; inactive mode smaller (**52.dp**) + **`alpha=0.38`** left; tap inactive swaps primary…
- **`previewChromeGridSlots`** — focal mm row is separate above the scroll grid; **shortcut rows** (see Sprint **9.9** shipped layout): expand shortcuts + quick actions + **Settings`ExpandShortcut`**…
- **`PreviewChromePreferences.selfTimerDelaySec`** (**0 / 3 / 5 / 10**), persisted; grid **Timer** icon cycles delay + toast + **`PNS.ChromeUx``selfTimerSec=`**; icon **selected** when delay **> 0**.
- **`PreviewController.previewUsesJpegCompanion()`** (JPEG **`ImageReader`** active); readout strip pipeline chip + **`readoutCapture=``PNS.ChromeUx`** line via **`PreviewReadoutStillPipeline`**…
- **`PreviewForegroundDndEffect`** + pref **`dndWhileInPreview`** (default on) + **Preview & keys** toggle; logs **`PNS.ChromeUx``dndPreview=applied|skipped_no_policy|skipped_disabled|…`**.
- **`PreviewReadoutFormatTest`**.
- **`PreviewReadoutStrip`** + **`PreviewReadoutFormat`** — ISO / shutter / AWB / measured FPS; counter-rotates with **`uiRotationDeg`**; repeating-request metadata from **`PreviewController`**…
- **`rememberSystemInsetsDp`** merges **`systemBars` ∪ `displayCutout`** (API 30+ union; API 28–29 max per edge) so **`PaddingValues`** clear punch-hole / nav gestures when those insets are non-zero…
- **`scripts/pns_chrome_ux_gate.ps1`** — runs **`pns_verify_toolchain.ps1 -RunTests`** (unless **`-SkipHost`** / **`-SkipHostTests`**), optional **`assembleDebug`**, installs APK when a device is…
- **Accessibility** — TalkBack front/rear; not gesture-only.
- **Activity orientation:** launcher activity uses **`sensor`** (not landscape-only); quick-settings chrome counter-rotates per rail controls while the preview stays fixed (`staticPreviewRotationDeg`…
- **Bottom tray shutter:** keep left/right rails as-is; move the orange shutter into a full-width bottom tray with the FAB **horizontally centered**; toggle in **Preview & keys** still applies.
- **Bottom tray** — Gallery thumb (when URI), dual shutters, mode letter FAB when HUD dial on. **Evidence:** same session.
- **Camera2 flash wiring** — Preview repeating + still **`FLASH_MODE`** / AE modes (bracket stills force flash off); front / no-flash handled in **`PreviewFlashPolicy`**.
- **Expand shortcut → modal** — Row **1** expand tiles drive **`Dialog`**-hosted sheets in **`PreviewRightRail`** (not an under-grid strip).…
- **Flash quick-setting (QS) tile** — **`CycleFlash`** + **`PreviewFlashMode`** + **`PreviewFlashPolicy`**.
- **Flash tooltip / coach-mark prefs** — optional one-time long-press hint.
- **Front `cameraId`** + **`PreviewController`** / **`PreviewFlashPolicy`** front path.
- **HFR preview discoloration** — diff **HFR vs 60 fps`CaptureRequest`** / tonemap / NR; **`COLOR_PIPELINE.md`** (**single owner** vs face/HFR geometry in **10.7**).
- **Immersive window** — Status + nav bars hidden (`enableEdgeToEdge` + `WindowInsetsControllerCompat`); transient swipe reveal only. **Evidence:** Sprint **9.4** host wiring +…
- **Last-capture thumbnail + viewer:** after each successful still/bracket write, show a small thumb in the bottom tray; tap launches an implicit `ACTION_VIEW` on the `content://` URI so the **system…
- **Live preview** — Camera stream visible in finder. **Evidence:** adb device validation (2026-05-10); raster PNG not in repo.
- **Long-running capture progress** — indeterminate/stepped progress in existing modal/readout patterns (**no** new persistent chrome bands).
- **Merge two quick settings into one** — **Extra shutters** tile + popup (**tap** + **volume** toggles); **`CHANGELOG.md`** / rail sheets aligned.
- **Mode menu** — FAB opens **`DropdownMenu`** for **M/H/S/BKT** when HUD dial on. **Evidence:`modeDialPopout=`** line (**`menuSelect`** path) in **`chrome_ux_gate.json`** /…
- **Persist optional welcome skips** across restarts (mic/location) — if product still wants it.
- **Portrait shutter:** bottom-tray FAB anchored **bottom-center** in portrait (above nav inset).
- **Preview fill + uniform scale:** `PreviewMainViewport` sizes the inner TextureView with **`TexturePreviewFit.smallestCoveringAxisAlignedRectWithAspect`** (same aspect as the stream, **cover** the…
- **Readout strip** — ISO, shutter, AWB / FPS, **`RAW`** or **`RAW+`**. **Evidence:** same session.
- **Right rail + focal row** — mm chips **`14…150`** with selection highlight. **Evidence:** same session.
- **Snackbar Retry / Copy raw error** — complete partial **`PnsUserFacingErrors`** follow-through.
- **Spotlight (≤3 steps)** — swipe, Photo|Video when tray ships, mode dial; Skip/Got it; prefs with **`PnsUiHintsStore`** family + backup allow-list if new keys.
- **Static preview rotation default:** `staticPreviewRotationDeg` defaults to **270°** so a fixed-window viewfinder matches reality when the buffer appeared **90° CW** off (users can cycle **Spin…
- **Swipe up → front, swipe down → rear** — velocity/distance; exclude tray/rails; **tap fallback** + **`WelcomePermissionsScreen`** copy (edge-gesture conflict note).
- **Tele presets** — **73 / 85 / 150** mm chips route via **`resolveFocalMmSlot`** / **`BackCameraRoleResolver`**. **ADB proof:`--es pns_preview_focal_mm_slot N`** →…
- **Tutorial copy** — gesture + fallback; **Settings → Replay tips** (**`WelcomePermissionsScreen`** + **`HudSettingsScreen`** “Replay welcome tips” + **`WelcomePrefs`** flow bump).
- `pns_chrome_ux_gate.ps1` **PASS** on **legacy serial**.
- `pns_dnd_restore_verify.ps1` **USB_PASS** on **legacy serial** (when policy access granted).
- `pns_rgb_histogram_verify.ps1` — preCaptureBuffer + screencap (`rgb_histogram_verify_20260529_095503`)
- `pns_video_status_bar_verify.ps1` **PASS** on **legacy serial**.
- `PreviewWindowEffects` hold/release; lifecycle restore; `dndPreview=restored` logs.
- `scripts/pns_eye_af_pixel_gate.ps1` — screencap during H-dial + face visible; PIL diff eye-box vs expected region; PASS when delta < threshold
- Back handling (preview gallery, batch-select clear)
- Cold-start preview seeds **`resolveFocalMmSlot(M23)`** wide id; logs **`PNS.ChromeUx``seedOk slot=M23 cameraId=…`** on success…
- Columns: matrix quick/full, capture verify, video verify, chrome gate, DNG (if RAW), last rescan
- Device chrome seedOk + logcat `wifiDirectBound=true`
- Edge-to-edge immersive preview; inset policy; `PnsGestureExclusionBottomBand`
- Gesture + 3-button nav (`pns_ux_sprint_adb_gate.ps1`)
- Locked preview chrome layout; gesture controls; adaptive layout
- Material Design 3 / Compose chrome
- Navigation compatibility (gesture + 3-button detection)
- Screencap during 16:9 recording — `pns_pillar_hud_verify.ps1`
- Screencap: 16:9 video shows pillarbox bars; still shows full tile — `hfr-runs/preview_shrink_fit_15_6_20260526_075726/` (`coverCrop=false` in logcat; video + photo + dual captures)
- Spot-check navigation (maintainer **2026-05-25**)
- Spot-check presets / batch share / backup folder (maintainer **2026-05-25**)
- Stack order: **front top**, **rear bottom** (GLES + MP4 composite); selfie ring in **status inset** (not on finder) while Dual active
- Still capture via volume-up (non-BKT), tap-to-shoot, bottom orange shutter, **Save DNG**: **`triggerStillCapture()`** — countdown overlay on finder, then existing **`onCaptureDng`** (**bracket /…
- Theme System / Light / Dark (`UxSettings`, `PnsTheme`)
- When **`HudSettings.showCommandDial`**: bottom tray **`PreviewBottomCaptureTray`** shows a **48.dp** orange **FAB** with the current **`CommandDialMode.label`**; tap opens **`DropdownMenu`** for…

---

## Readout strip & shooting modes

*ISO/SS/EV chips, command dial, QR, macro mode, WB menu, stabilisation chip, mode menus.*

- **[`PreviewReadoutStrip`](app/src/main/java/dev/pointandshoot/PreviewReadoutStrip.kt)** + **[`PreviewReadoutFormat`](app/src/main/java/dev/pointandshoot/PreviewReadoutStrip.kt)** — Drop **“Default…
- **[`ReadoutExposureCatalog.awbChoices`](app/src/main/java/dev/pointandshoot/ReadoutExposureCatalog.kt)** — Order HAL-supported presets **coldest → warmest** using Kelvin anchors in…
- **`CONTROL_POST_RAW_SENSITIVITY_BOOST`** — optional policy when advertised and compatible with highlight / manual readout modes.
- **API & vendor inventory** — **`docs/camera2_reference_qr_barcode_appendix.md`** (stub) + link to **`docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md`**; full vendor QR key survey still **device / fleet**.
- **HUD / readout** — strip shows **`JPEG`**; document in **`AboutScreen`**.
- **Logical multi-camera readout** — `PreviewController` tracks `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` (API 29+) from repeating results; **Phy** chip on `PreviewReadoutStrip` when non-blank…
- **QR scan mode** — ML Kit or **`ImageAnalysis`**; optional **`pns_screen=qrscan`**; throttled YUV; stride-safe.
- **Street (Snap) program** — With **`CommandDialMode.S`** and **no tap metering**, `PreviewController.applyScalerCropAndMetering` applies snap AF: **`CONTROL_AF_MODE_OFF`** + **`LENS_FOCUS_DISTANCE` =…
- **Unit tests** — e.g. `ReadoutExposureCatalogAwbOrderTest` (JVM): order, no `null` choice, AWB first.
- `CommandDialMode.Macro`, UW auto-switch, vendor keys; `pns_macro_focus_verify.ps1` PASS
- `CommandDialMode.Qr` (photo-only); ZXing on preview YUV; confirm-then-open for links.
- `pns_chrome_ux_gate.ps1` **PASS** (`readoutOk=true`).
- `pns_qr_scan_verify.ps1` **PASS** on **legacy serial**.
- `pns_shutter_angle_verify.ps1` — `Angle180` @ 30 fps → `readoutManual ssNs≈16666667` (half frame) (**legacy SKU** `legacy serial`)
- `ReadoutIsoBandTest` JVM; `pns_capture_pipeline_verify.ps1` **PASS** on **legacy serial**.
- `scripts/pns_still_mode_compare_gate.ps1` — ADB capture Standard/ZSL/HDR; run `readout_jpeg_dng_luminance_compare.py`; write `STILL_MODE_COMPARE.md`
- Add `readoutChase iso=… ss=… coupling=…` diagnostic log (3 s throttle)
- Add `stabChipLabel: String? = null` param to `PreviewReadoutStrip`; render chip; hide when null
- AF modes + manual distance drag; macro program; readout **AF** chip.
- Audit + fix `ReadoutExposureChase` locked-SS→auto-ISO chase loop
- Derive `stabChipLabel` in `PreviewEngineContent`
- Extend `commandDialModesFor(Video)` with `Macro`
- H.8.6: no overlap with shutter tray, focal strip, readout chips (2026-05-29)
- Log `PNS.ChromeUx stabChip=…` on change (debounced 3 s)
- Mode transitions deterministic and logged (no hidden state) — `PNS.ModeTransition` + `ModeTransitionLog` / `TrackModeTransition` (camera, fps, imaging profile, recording, focal crop, command dial)…
- Render selected `ReadoutIsoBand` with `PnsColors.PhotoOrange` tint in ISO menu
- Screencap shows STAB chip
- Shooting-mode dropdown: **Photo programs** / **Video programs** section headers; selected row `PnsColors.PhotoOrange`.
- Unit test: `oisOn=true, eisOn=true` → label `"OIS+EIS"`
- Video mode: hide Still LUT + IMG; show Video LUT + format chip.
- Wire `PreviewReadoutStrip(primaryPhoto, videoFormatChipSlot)` in `PreviewEngineScreen.kt` with `VideoFormatChip` + `InAppVideoFormatSelection`.

---

## Still capture — JPEG, RAW & DNG

*RAW12 pipeline, DNG save/loadability, metadata pairing, JPEG/AVIF, hardware JPEG, capture verify.*

- **`CapabilityGate`** — no RAW → JPEG-only default + explanation (auto-fallback in **`PreviewEngineScreen`**; **`Feature.RawDng`** disabled reason points at JPEG-only profile).
- **`ImagingProfile` / `CaptureStorage`** — **`jpeg_only`** path: no RAW `ImageReader`; hardware JPEG still via **`CaptureKind.JpegSdr`**; folder under **`DCIM/Point & Shoot/`**;…
- **`MediaStore.ACTION_VIDEO_CAPTURE`** — Add **`android.media.action.VIDEO_CAPTURE`** intent filter on **`MainActivity`** (distinct from **`INTENT_ACTION_VIDEO_CAMERA`**). Extend…
- **`VideoCaptureReturnContract`** — When launched **`startActivityForResult`** with **`ACTION_VIDEO_CAPTURE`**, complete recording then **`setResult(RESULT_OK, Intent)`** with the saved video **`data`…
- **ADB** — **`pns_adb_preview_validate.ps1`** scenario **`jpeg_only_x1`** (**`captureJpegHardwareStill 1/1 ok=true`** on adb **legacy serial**, **`hfr-runs/adb_preview_validate_20260513_123424`**);…
- **BKT: primary shutter + tap-to-shoot** — When **`commandDialMode == BKT`** and **`canCaptureBracketBurst()`**, **`onCaptureDng`** must call **`captureBracketBurst`** (default…
- **Capture regression gate** — After changing still / preview **`CaptureRequest`** keys for this sprint: **`scripts/pns_capture_pipeline_verify.ps1`** (or **`pns_photo_capture_verify.ps1`**) on USB…
- **DNG `UniqueCameraModel` / tag 50708** — [`TiffUniqueCameraModel50708`](app/src/main/java/dev/pointandshoot/TiffUniqueCameraModel50708.kt) LE TIFF IFD0 append +…
- **Hardware JPEG / ISP prefs (engine)** — Persisted user bias (e.g. **`HudSettings`**) resolved through a small helper (e.g. **`PreviewJpegProcessingHints`**) into discrete **`EDGE_MODE`**,…
- **JPEG request metadata** — **`PreviewStillCaptureHints`**: `JPEG_ORIENTATION` (degrees via **`RawCaptureSupport.orientationClockwiseDegForDng`**) + optional **`JPEG_GPS_LOCATION`** when…
- **RAW12 / Ultra-Max DNG path + ADB automation** — `ImagingProfile.UltraMax` → `CaptureStorage.CaptureKind.DngRaw12` (`toDngCaptureKind()`); intent extra **`pns_preview_imaging_profile`**…
- **Richer capture metadata (JPEG USER_COMMENT)** — **`StillCaptureMetadata.fillExifFields`**: appends **`LENS_FOCUS_DISTANCE`** (FD), **`LENS_STATE`**, **`CONTROL_AF_STATE`**,…
- **Software JPEG quality** — Expose persisted quality (e.g. 70–100) for **`Bitmap.compress`** in **`saveHardwareJpegCompanion`** (and any other fixed-quality software re-encode on that path).
- `AGENTS.md` CRITICAL — Fleet; matrix as SoT; DNG loadability locks unchanged
- `dng_color_metric.py` `uw_delta ≤ 0.12` gate PASS — **PASS** after tuning tele capture-time gains off + raw tele ASN patch (metric now uses `wb_green_delta_vs_wide`;…
- `dng_tiff_integrity_check.py` PASS on ReferenceApp fixtures (host)
- `pns_capture_pipeline_verify.ps1` green. **Completed 2026-05-17:** `captureRawStill 1/1 ok=true saved=` — still capture not broken.
- `pns_dng_exif_verify.ps1` — focal + capture time PASS (`dng_exif_verify.json` in same run dir)
- `pns_photo_capture_verify.ps1` PASS; `pns_nightscape_verify.ps1` PASS
- `scripts/pns_dng_aesthetic_gate.py` — rawpy M14/M23/M73 vs `tests/fixtures/referenceapp_legacy_sku/`; ±20% luma/R/G/B; wired in `pns_aux_dng_capture_analyze.ps1` (`-RequireAestheticGate` to hard-fail)
- `scripts/pns_dng_rawpy_decode_gate.ps1` — PASS on `tests/fixtures/referenceapp_legacy_sku/` (host); USB hfr-run when device returns
- `setCaptureTime` on all DNG paths (`DngCreator` ctor from `SENSOR_TIMESTAMP`; IFD0/EXIF datetime in `applyToDngUri`)
- `setLocation` when geotag pref + location available (`Dng12Saver` + `applyToDngUri` MediaStore columns)
- Apply in-place TIFF FM+ASN patches (UW+tele); `dng_tiff_integrity_check.py` + desktop open **PASS** (`aux_dng_capture_analyze_20260528_020131`, log `asn+fm ForwardMatrix`)
- Create `NightScapeCapture.kt` — burst JPEG + decode + align + blend + tonal encode
- Create `pns_native_encoder_verify.ps1` for JXL/AVIF verification. **Completed 2026-05-17:** Script tests encode paths via `PNS.TonalStill` logs; graceful fallback to JPEG when `.so` unavailable.
- Disable DNG + normal video rec while time-lapse active
- Embed in AVIF Kotlin mux — `AvifStillMuxer.Input.iccProfileBytes` → `colr` `prof`; native `.so` AVIF still `nclx` until remux
- Embed in JPEG save path — `JpegIccEmbedder` APP2 `ICC_PROFILE` after `ExifInterface.saveAttributes` (`StillCaptureMetadata.applyToJpegUri`, `ImagingProfile.colorSpace`)
- Enable ReferenceApp IQ + aux ASN reconcile (`useLegacyLeafAuxColorReconcile` UW+tele, `LegacyLeafStillColorCorrection`); wide stays pure DngCreator + `applyToDngUri`
- Fire `scheduleStillTick()` only in tonal `onCaptureCompleted` for dual path
- High-speed preview session, RAW12 `Dng12Saver`, `CaptureHaptics`, JNI shell `libpns_native.so`, `NativeEncoders` / `EncoderRoute`.
- If `uw_delta > 0.12`: create `DngDeviceColorProfile.kt` + legacy SKU FM/WB tables ([DngForwardMatrixFix])
- Leaf `DngMetadataResolver`; `StillCaptureIqPolicy`; `LEAF_RAW_FORMAT_ORDER` **32→37→38→36**
- Majority of ISOBMFF/AVIF/JXL host modules, `LutPipeline`, calibration math — already landed (see Appendix B).
- Pass `suppressHapticUntilTonal: Boolean` through dual-capture chain
- Patch EXIF focal + `0xA405` in `TiffExifSubIfdCapturePatch` (in-place, no `ExifInterface` on DNG)
- Run `pns_aux_dng_capture_analyze.ps1` Phase 1 — `uw_delta=+0.5699` (`aux_dng_capture_analyze_20260528_015423`, ASN-only)
- Unit test: mock dual-capture → tick fires after tonal, not after RAW
- Verify `nativeEncodeJxl12Rec2020` and `nativeEncodeAvif10Hdr` signatures match `native/pns_native.cpp`. **Completed:** JNI signatures verified; implementations present.
- Wire to Night-dial shutter path; progress status line + HUD frame count

---

## Advanced still modes

*Bracketing BKT, ZSL, HDR still, burst, intervalometer, pre-capture buffer, smile still.*

- **`CONTROL_ENABLE_ZSL`** — **`PreviewStillCaptureHints.applyZslIfCompatible`**: `CONTROL_ENABLE_ZSL=true` on single + bracket still when JPEG surface is attached, key is in…
- **`encode_lane_busy`** not observed on **BKT3** full **`pns_adb_preview_validate`** run (**`hfr-runs/adb_preview_validate_20260511_005819/`**): **`summary_grep.txt`** `encode_lane_busy` section has…
- **Bracket EV distinctness (follow-up)** — If three files still match exposure after shutter fix, inspect **`BracketScheduler.aeStepsFor`** for duplicate integers after clamp to…
- **Bracket still `CaptureRequest` parity** — Apply **`RawStillProcessingHints.applyLinearRawFriendlyProcessing`** (and any new **`PreviewJpegProcessingHints`** from the next bullet) to…
- **Bracketing BKT** — **`captureBracketBurst pattern=Three ok=true`** + three **`pns_*_bkt?of3*.dng`** writes in **`logcat_bracket_bkt3.txt`**;  bracket desktop regroup — **Milestone H**. **Follow-up…
- `StillCaptureMode`; HUD cycle; `ZslStillFrameRing`; HDR bracket **3× DNG**; `pns_still_mode_benchmark.ps1` v2; `pns_m13_8d_gate.ps1` USB **PASS** (`hfr-runs/m13_8d_gate_20260520_020059/`)
- Add `reduceYuv420RGB` + `reduceRawSensorRgb` to `PreviewLumaHistogram`
- Add `timeLapseMode` to `HudSettings`; branch in intervalometer `LaunchedEffect`
- Add `zslHistogramActive` state + "ZSL" badge on overlay
- Add intervalometer for time-lapse photography
- Backpressure / queue bounds — **`CAPTURE_ARCHITECTURE.md`** Sprint **7.3 acceptance gates** (`raw_still_x10` + `bracket_bkt3` logs) vs **`PERFORMANCE_BUDGETS.md`** bracket table; evidence…
- BKT encode-lane preflight — wait up to **`PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS`** for `PNS.Reader` / `ioExecutor` to drain + best-effort RAW/JPEG **`ImageReader`** discard before sequential…
- Implement burst mode with variable speed and count
- Implement exposure bracketing with RAW+JPEG (M13 implemented)
- Implement HDR bracketing with automatic alignment (M13 implemented)
- Implement pre-capture buffer for "moment before" shots (`preCaptureBufferEnabled` → ZSL ring)
- Unit test: `reduceYuv420RGB` / alias (`PreviewLumaHistogramTest`)
- Wire `ZslStillFrameRing.peekLastFrame()` → RGB histogram on `meterExecutor`

---

## Video recording & encoding

*MediaCodec, HFR 120/240, 4K/8K, HEVC/H.264/AV1, DCG/HDR10, timelapse, dual video, macro video.*

-  A/V sync (M13V)
-  Format matrix via `pns_mediacodec_hfr_verify.ps1` / M13V history
-  Host gate orchestrates in-app video + probe + stabilization
-  Stabilization log gate `pns_video_stabilization_test.ps1`
- "MACRO VIDEO" badge + `macro_video` workflow preset
- **10 consecutive captures** without session death — §5 **2026-05-10**: USB **`legacy serial`**; `pns_adb_preview_validate.ps1` artifact **`hfr-runs/adb_preview_validate_20260510_020501/`** contains…
- **`CHANGELOG.md` (Unreleased)** — user-visible in-app video line.
- **`docs/M13V_16_4K120_UNLOCK.md`**
- **`pns_in_app_video_verify.ps1`** green + **`pns_capture_pipeline_verify.ps1`** after **`PreviewEngineScreen`** session-path edits (item **11**).
- **`scripts/pns_in_app_video_verify.ps1`** — cold video-primary, record stop, assert **`inAppVideoSaved`** / playable MP4 / size threshold; artifacts **`hfr-runs/in_app_video_verify_*`** (Global…
- **Phase A:** `docs/M14_12_DUAL_VIDEO.md`.
- **Phase B:** Stacked preview + front `CameraDevice` + GL → MediaCodec composite **1920×1080** @ **30 fps**.
- **Recorder hardening** — Session **diet** when recording (preview + recorder targets; skip unnecessary RAW/YUV); preview **FPS** capped with **`MediaRecorder`** frame rate; video-only MR path (AAC…
- **Video recording to file** — **`MediaRecorder`** or Jetpack **`Recorder`** + **`MediaStore`** + audio policy; wire tray **`onRecordingChange`**; validate scenario.
- **Video resolution UI** — In **video mode**, readout **RES** + **`StreamConfigurationMap.getOutputSizes(MediaRecorder::class.java)`**, persisted **`PreviewChromePreferences`**, wired to…
- 10-bit HDR / DCG (13V.5)
- 4K @ 60/120 fps (13V.16)
- 8-bit HEVC Main: BT.709 limited VUI in `MediaCodecVideoRecorder`; `colorVui=bt709` log.
- `AboutScreen`: heritage credits (orange brands), LG nod, Venmo **Support development**; About scroll fix (no nested `verticalScroll`).
- `DcgModeSupport`, HDR10 static info on MediaCodec; `pns_video_hdr10_metadata_verify.ps1` PASS (`hfr-runs/hdr10_meta_verify_20260517_120333`)
- `DcgSessionParameters` + `resolveInAppVideoFormat()`; USB `pns_video_hdr10_metadata_verify.ps1` **PASS** (`hfr-runs/hdr10_meta_verify_20260519_222210/`)
- `MediaCodecCapabilityProbe` + `PNS.VideoCapProbe`; `pns_video_capability_probe.ps1` **PASS** on **`legacy serial`** (`has4k120=true`, `c2.qti.hevc.encoder` **3840x2160@120fps**);…
- `pns_about_links_verify.ps1` **USB_PASS** on **legacy serial**.
- `pns_ai_features_verify.ps1` **USB_PASS** on **`legacy serial`** (`hfr-runs/ai_features_verify_20260520_075142/`) — scene probe, bitrate **24883200 → 31104000** (**100% < 125%**), smile synthetic + DNG…
- `pns_dual_video_verify.ps1 -RecordSec 5` **USB_PASS** on **legacy serial** (`inAppVideoSaved ok=true`).
- `pns_dual_video_verify.ps1 -RecordSec 5` PASS + `inAppVideoSaved ok=true`
- **Milestone 19 (2026-05-30)** — `ColorQualityIndex` + format/color pickers (still + video CQI); RAW video `.mcraw`, dual-ISO HDR merge, VP9 WebM; ProRes probe-only + anamorphic metadata; `pns_m19_gate.ps1` host JVM PASS.
- **Milestone 20 (2026-05-30)** — `DeviceFeatureGates` HAL concurrency slice on matrix `product.concurrencyGates`; dual front health auto-recover; `MulticamMeltRecordingController` + thermal caps; concurrent rear PiP inset (`ConcurrentPipPreviewController`, `pns_preview_pip`); parity Quick cells `video.dual` / `video.multicam_melt` / `preview.pip`; `pns_m20_gate.ps1`.
- `pns_focus_peaking_verify.ps1` **PASS** on **legacy serial**.
- `pns_in_app_video_verify.ps1` PASS + logcat `macroVideo=true`
- `pns_in_app_video_verify.ps1` PASS; logcat `PNS.DualIso multiResSupported=` — CPH2583 **`b5214fc6`** (2026-05-29)
- `pns_video_codec_color_compare.ps1` **PASS** (HEVC @ 120); H.264 @ 60 clip optional.
- `pns_video_codec_color_compare.ps1` + `pns_hfr_color_compare_frames.ps1` PASS on **legacy SKU** `legacy serial` (`hfr-runs/video_codec_color_compare_20260526_000505`; YCbCr dU=0.15 dV=0.37) — **8-bit HEVC…
- `pns_video_format_test.ps1`, `pns_video_stabilization_test.ps1`, `pns_video_quality_gate.ps1`
- `RawVideoWriter` (`PNMRAWV1`); `RawVideoRecordingController`; HUD RAW lane; ADB `pns_preview_video_raw_sec`; USB **PASS** 145 frames (`hfr-runs/raw_video_verify_20260519_225113/`)
- `scripts/pns_crash_triage.ps1` — `adb logcat -b crash -d`; parse fatal exceptions; write report to `hfr-runs/crash_triage_<timestamp>.md`
- `scripts/pns_hfr_color_compare_frames.ps1` + `scripts/video_codec_yuv_compare.py` — H.264 @60 vs 8-bit HEVC @60; ffmpeg decode 10 frames; mean Cb/Cr delta &lt; 8
- `VideoFormatPickerSheet`, `VideoFormatPresets`, 4K tiers; 7/7 `pns_mediacodec_hfr_verify.ps1` (`hfr-runs/mediacodec_verify_20260517_114216`)
- ADB `--ei pns_preview_video_encode_w/h`; `pns_mediacodec_hfr_verify.ps1` **7/7 PASS** on **`legacy serial`** (`hfr-runs/mediacodec_verify_20260520_011851/`).
- Add 8K diagnostic banner if unsupported (`VideoFormatPickerSheet` when `supports8k=false`); 8K routed via MediaCodec (`VideoRecordingController`)
- Add `audioGainDb` to `HudSettings` + `MediaCodecVideoRecorder.Config`
- Add `dualIsoVideoEnabled` to `HudSettings`
- Add `mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)`, `setAudioEncodingBitRate(128_000)`, `setAudioSamplingRate(48_000)` when audio enabled.
- Add explicit BT.709 limited keys to HEVC encoder path (MediaCodec; 8-bit HEVC ≤60 routed off MediaRecorder)
- Add front OES update-frame diagnostic log
- Audio meters (13V.8)
- AV1 encoding when HW `video/av01` encoder exists (`VideoCodec.AV1` + MediaCodec path)
- Bitrate compression options (13V.17)
- Cap fps 60 + force EIS + `onEnsureMacroUltraWide` in macro+video
- Capture H.264 + H.265 1080p30 reference; diff VUI with ffprobe
- Chrome encode prefs → `setInAppVideoEncodeSize` + `pickHighSpeedVideoTarget` (4K pref before 1080p/720p HS fallbacks); HFR record size follows constrained session (legacy device: **720p@120** capture, encoder…
- Create `DualIsoVideoMerger.kt` stub
- Create `pns_hfr_video_verify.ps1` with `hfr_video_gate.v1` JSON schema. **Completed 2026-05-17:** Script tests HFR recording path; evidence logged.
- Create `TimeLapseVideoEncoder.kt` — `MediaMuxer` + `MediaCodec` frame-by-frame
- Document failures in `docs/VIDEO_MODE_MATRIX.md`
- Extend `InAppVideoRecordingSupport.kt` with `pickHighSpeedOutputSize()`, `supportsHighSpeedVideoRecording()`, `availableHighSpeedFpsOptions()`. **Completed 2026-05-17:** HFR capability detection and…
- Extend `PreviewEngineScreen` with HFR capability detection and UI toast. **Completed 2026-05-17:** Toast "HFR video not available on this device" shown when switching to video mode with FPS ≥ 120 on…
- Extend `VideoRecordingController.applyShell()` with `wantHighSpeed` and `supportsHighSpeed` parameters. **Completed 2026-05-17:** HFR gate allows 120fps+ when both conditions met.
- Fix `FaceDetectAdapter` non-HFR path — uniform scale+centered offset
- Fix any format missing audio track or with 0-packet video (none in **-Full** sweep — all rows `avPresent=true`)
- Fix front camera surface texture update path in stacked composite
- Format recommendations doc (player compatibility notes)
- HEVC (H.265) for all camera modes (13V.15)
- HEVC Main10 / YUVP010 via `MediaCodecVideoRecorder`; `pns_mediacodec_hfr_verify.ps1` TenBit cases PASS (`hfr-runs/mediacodec_verify_20260517_100346`)
- Log `colorVui=` on every encode start
- Logcat `aeSub=true` during in-app video + eye overlay on **CPH2583** (2026-05-29; `pns_face_priority_ae_video_verify.ps1`)
- LUT color grading (13V.11)
- MediaCodec path at **120+ fps** (no 60 fps cap); `peekInAppVideoRecorderStarted` waits for muxer-ready; muxer lifecycle fix (no pre-muxer discard on stop).
- MediaCodec path; ADB `pns_preview_video_fps` / `pns_preview_video_10bit`; 120/240 PASS same verify run
- Per family: `advertised` / `sessionOk` / `appEnabled` (RAW, HFR, face, DCG/ZSL)
- Pilot: `PreviewFpsSupport` matrix HFR ceiling + `PreviewEngineScreen` photo→video HFR snackbar via `isHfrAppEnabled`
- Probe `maxFps8k` from `MediaCodecCapabilityProbe`; log to `PNS.MCVideoRec`
- Pull MP4 artifact and verify with `ffprobe` that audio track exists (AAC, 48kHz). **Completed:** `audioCodec=aac`, `audioSampleRate=48000`, `audioBitRate=128252`.
- Real-time video stabilization (OIS + preview EIS via `VideoEffectsProcessor` → `PreviewStabilization`)
- Reject video recording when `desiredFps >= 120` **and** audio enabled. **Verified:** Code blocks HFR video (returns StartFailed when `desiredFps >= 120`).
- Research `CameraConstrainedHighSpeedCaptureSession` availability on reference device (legacy device / legacy SKU). **Completed 2026-05-16:** Device supports `CONSTRAINED_HIGH_SPEED_VIDEO` capability;…
- Run on USB device; attach artifact — `hfr-runs/video_matrix_verify_20260527_205431` (**legacy SKU** `legacy serial`, 6/6 PASS)
- Set `KEY_CHANNEL_MASK` + `KEY_PCM_ENCODING`; API 28 guard
- Slow-motion / HFR MediaCodec (13V.16)
- Thread through `MediaCodecVideoRecorder.Config` + `AudioRecord` init; API 24 guard
- Unit test: stub `merge()` returns input unchanged
- Update `docs/PNS_TECHNICAL_SETTINGS.md` §10
- Update guardrail JSON schema: `video_audio_gate.v1` with `audioStreamPresent`, `audioCodec`, `audioSampleRate`, `audioBitRate`, `pass`.
- USB `pns_video_matrix_verify.ps1 -Full` on **legacy SKU** `legacy serial` — **8k30_h264** `saved=true` `avPresent=true` (`hfr-runs/video_matrix_verify_20260526_074218`)
- Variable bitrate + scale (13V.17)

---

## Recording HUD & preview overlays

*Timecode, PPM meters, pillar HUD, histogram while recording, thermal, storage, peaking, false color.*

- `PreviewLumaHistogram.reduceRgb()`, HUD toggle; `pns_rgb_histogram_verify.ps1` PASS
- `PreviewStorageRemainingOverlay`; `pns_storage_remaining_verify.ps1`; **`docs/M13V_13_STORAGE_REMAINING.md`**
- `PreviewTopStatusBar` in top inset band: `TimecodeOverlay` + `AudioLevelMeter`; pipeline hints via `previewStatusBarLine`.
- `TimecodeOverlay`, `AudioLevelMeter`; `pns_recording_overlays_verify.ps1` PASS
- Add `FalseColorMode` enum + `FalseColorOverlay.kt` (enum shipped; overlay wiring partial)
- Add `showHistogramDuringVideo` to `HudSettings`
- Add `showVideoPillarHud` toggle in Settings → Video
- Compute `pillarBarWidthDp` from buffer AR vs tile AR
- Create `PpmAudioMeter.kt`
- Create `VideoSidePanels.kt` (left + right columns)
- Extract `ThermalChip` from `PowerThermalOverlay`
- Gate on `videoPrimary && isRecording && pillarBarWidthDp ≥ 24.dp`
- Replace `AudioLevelMeter` in `PreviewTopStatusBar`
- Suppress top-bar meters when side panels active
- Unit test: `PpmAudioMeterTest` (−3 dBFS segment math)
- Unit test: `TexturePreviewFit` 16:9 buffer in 3:4 tile → expected pillarbox rects

---

## Audio capture & shutter feedback

*Mic paths, hi-fi AAC, wind filter, gain, source picker, shutter sounds, spatial audio.*

-  Subjective audio quality in varied environments
-  Subjective timing vs capture on device
- `pns_in_app_video_verify.ps1` PASS; logcat `audioGainDb=` on start
- Add `PNS.Video` log tag: `inAppVideoPrepared audioEnabled=$audioEnabled size=${sz.width}x${sz.height} fps=$recordFps`.
- Add `RECORD_AUDIO` permission check before `MediaRecorder` prepare in `applyInAppVideoRecordingShellLocked`. Create `hasRecordAudioPermission()` helper using `ContextCompat.checkSelfPermission()`.
- Add `VideoAudioSource` enum + `videoAudioSource` to `HudSettings`
- Add `windNoiseFilterEnabled: Boolean = false` to `HudSettings`
- App shutter volume (`shutterSoundVolume`)
- Audio focus / record permission checks
- Audio level visualization during recording (13V.8)
- Custom sound pack UI in Settings
- Enable `NoiseSuppressor` + `AcousticEchoCanceler` post-`startRecording()` when conditions met
- External mic preference (`setPreferredDevice` USB / wired / BT)
- Greyed-out toggle in Settings → Video
- Haptic sync option (`shutterHapticSync`)
- Hi-fi capture — 96 kHz / 48 kHz pick, 256 kbps AAC, PCM 16-bit stereo (`PnsAudioCaptureSupport`)
- JSON import/export (`files/shutter_sound/shutter_sound_pack.json`)
- Light PCM compression + voiceover ducking (`AudioEffects`)
- Logcat `PNS.MCVideoRec audioSource=CAMCORDER` after selecting
- Logcat `windFilter=on nsAvail=… aecAvail=…`
- Packs: mechanical, digital, vintage, silent
- Picker in Settings → Video
- Run `pns_video_audio_verify.ps1 -RecordSec 5` with audio permission granted; verify no `prepare failed` crashes. **Completed 2026-05-16:** Pass on legacy device (legacy serial).
- Shutter sound system (`ShutterSoundManager`, CC0 `SoundPool` samples + tone fallback)
- Unit test: `AudioGainDbTest` — `gain(0f)==1f`, `gain(6f)≈2f`, `gain(-6f)≈0.5f`
- Update `InAppVideoRecordingUiEvent` sealed class to expose `audioEnabled: Boolean` for UI status logging.
- When permission granted, call `mr.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)` **before** `setVideoSource()` per `MediaRecorderGeotag.kt` doc contract.
- Wind noise reduction (`NoiseSuppressor` when available)

---

## Metering, exposure & ISO/shutter

*Highlight H-mode metering, AE lock, ISO band, shutter angle, manual readout chase.*

- **Highlight metering (dial H)** — YUV histogram → **`HighlightMeter`** AE comp applied; logs **`PNS.AdbValidation``highlightMeter ev=… aeComp=… dial=H`** (≥3.5s throttle) in…
- **Tap AF / AE precapture triggers (initial)** — After tap metering, **`CameraCaptureSession.capture`** one-shot with **`CONTROL_AF_TRIGGER_START`** + **`CONTROL_AE_PRECAPTURE_TRIGGER_START`** when…
- `pns_readout_chase_verify.ps1` — locked SS arms `wantYuv=true` + `LOCKED_SS_AUTO_ISO` session (**legacy SKU** `legacy serial`)
- Add `aeLocked` state + long-press toggle on ISO/SS chip
- Add spot/matrix/center-weighted metering options (highlight-weighted metering implemented)
- Audit + fix locked-ISO→auto-SS chase loop
- Create `VideoShutterAngle.kt` with fps-derived exposure formula
- Display angle label on SS chip when locked (e.g. `180°`)
- Extend `allowsFacePriorityMetering` to include recording + overlay
- Implement exposure compensation with fine control (ISO band coupling 14.7 implemented)
- Inject `CONTROL_AE_LOCK = aeLocked` into repeating request
- ISO presets + locked ISO / locked SS readout chips; `docs/PNS_TECHNICAL_SETTINGS.md` §3–§4.
- Logcat `aeLock=true`; `pns_photo_capture_verify.ps1` PASS
- Padlock icon when locked; clear on camera close / dial change
- Persist in `HudSettings`; add to Settings → Video + QS
- SS chip menu (video mode): shutter-angle presets available alongside numeric shutter picks
- Unit test: `PreviewAeLockTest` — locked + available → true
- Unit test: AE rect height < full face box height (`FacePriorityMeteringTest`)
- Update `docs/PNS_TECHNICAL_SETTINGS.md` §10 (`VideoShutterAngle`)
- Wire angle → `LOCKED_SS_AUTO_ISO` coupling in `PreviewController`

---

## Focus, AF & rack pulls

*Manual focus, peaking, rack focus waypoints, breathing compensation, focus mode picker.*

- "▶ Rack" button + rack coroutine (30 Hz, abort on second tap)
- `pns_capture_pipeline_verify.ps1` PASS + logcat `PNS.FocusBreathing` during M-dial rack
- `pns_photo_capture_verify.ps1` PASS; logcat `rackFocus from=…` during rack
- Add `enableFocusBreathingComp: Boolean = false` + `focusBreathingCompK: Float = 0.005f`
- Add manual focus peaking and focus assist tools (14.8/14.10 implemented)
- Add waypoint + `rackFocusDurationMs` to `HudSettings`
- GLES peaking + M dial manual focus; `pns_focus_peaking_verify.ps1`; **`docs/M13V_10_FOCUS_PEAKING.md`**
- Long-press focus chip → `RackFocusWaypointSheet`
- Toggle in Settings → Video
- Track focus distance result; compute + apply EMA crop nudge
- Unit test `RackFocusPullTest`

---

## Face, eye & subject tracking

*Eye AF overlay, face priority AE, 3D tracking, smile capture, alignment probes, selfie ring.*

- **3D tracking** — **`tracker statisticsPipeline active`** proves **`TrackerState`** wired to metadata; **`tracker lockedIds=…`** on lock-set delta when faces appear. Intentional dropout / re-acquire…
- **`CONTROL_AUTOFRAMING`** — when `CONTROL_AUTOFRAMING_AVAILABLE`; distinct from ML Kit face track.
- **Eye-AF** — **`eyeAf faceDetectMode=`** + **`availableModes=`** logged once per session (reference HW: **SIMPLE** only — no **FULL** in list); **`eyeAf statisticsSample`** when…
- **Face rectangle hides when eyes detected** — [dispatchFaceHudOverlay](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt): empty face-box list for overlay when eye marks are non-empty;…
- **Gallery thumb always on** — [PreviewBottomCaptureTray](app/src/main/java/dev/pointandshoot/PreviewEngineScreen.kt): fixed-width gallery slot shows dim **Photo** icon when **`lastGalleryUri`** null;…
- `pns_ai_features_verify.ps1` **USB_PASS** on **legacy serial**.
- `pns_capture_pipeline_verify.ps1` / `pns_photo_capture_verify.ps1` not regressed — `capture_pipeline_gate_20260529_031943`
- Implement advanced focus tracking (subject, face, eye) (Eye AF implemented 14.9)
- Log `PNS.FaceMeter aeSub=true eyeRectSensor=…`
- Narrow AE rect to eye sub-crop
- Orange selfie ring in top inset when front camera active; Eye AF menu **Smile to capture**.
- Verify ML Kit path uses `mapYuvRectToFaceTrackBoxBuffer` consistently

---

## Color grading, LUT & picture profiles

*Preview/video LUT, HLG/Flat/Cine, picture profiles, BT.709/HLG VUI, independent tonal.*

- **ADB Calibrate smoke** — **`pns_adb_preview_validate.ps1 -Milestone6Pack`** / **`pns_milestone6_gate.ps1`**: **`m6_calibrate_smoke`** (**`calibrateSmoke`**); **`m6_preview_calibrate_grab_smoke`**…
- `PreviewLutSelection` + `PNS.LutPreview`; `pns_video_lut_preview_verify.ps1`; **`docs/M13V_11_VIDEO_LUT_PREVIEW.md`**
- Add `coverCrop: Boolean` flag to `setGeometry`; shrink-to-fit = `coverCrop=false` (`LutCameraPreviewRenderer`, `TexturePreviewFit`)
- Add `VideoColorProfile` enum + pass through recorder config (enum + VUI tag; recorder wiring partial)
- Add support for custom picture profiles and LUTs (`ProPictureProfiles` presets → HUD LUT/ISP/imaging)
- Bake GLSL 1D LUT for HLG de-gamma preview from `HdrCurves.hlgToLinear` (`lut_preview_external.frag.glsl` `applyVideoColorProfile`)
- Bake GLSL LUT for FlatCine (shadow lift + saturation + shoulder) — same shader path
- End-to-end **Calibrate** from live preview (`Preview & keys` → **Calibrate from preview** → `TextureView.getBitmap()` → same Compute/Save pipeline as SAF).
- ffprobe gate via `pns_video_hlg_color_verify.ps1` — `hfr-runs/video_hlg_color_verify_20260529_094753` (`color_transfer=smpte170m` OEM mux; **`colorVui=bt2020-hlg`** authoritative per 15.2 pattern)
- Logcat `colorVui=bt2020-hlg` + `colorProfile=hlg` on **CPH2583** (MediaCodec HEVC Main10 @1080p30, 2026-05-29)
- Probe `SCALER_MULTI_RESOLUTION_STREAM_CONFIGURATION_MAP` in `createSession` (API 31+ guard)
- Profile picker in Settings → Video; persist in `HudSettings`
- Return `"bt2020-hlg"` VUI tag for HLG + 10-bit (`MediaCodecVideoRecorder.colorVuiTagForConfig` + `VideoRecordingController` Main10 path)

---

## Calibration & ICC

*ColorChecker workflow, chart detector, passport CE, Display P3 ICC in JPEG/AVIF.*

- `CalibrationWorkflow.kt`: post-apply parity — chart neutrals on JPEG + DNG sidecar
- `ChartQuadDetector.kt`: auto-detect robustness on real ColorChecker (glare, skew, partial frame)
- `pns_jpeg_icc_verify.ps1` on **CPH2583** — Display P3 in APP2 (`jpeg_icc_verify_20260529_095620`; ExifInterface sRGB ICC replaced)
- `scripts/pns_colorchecker_de2000_gate.py` — rawpy + Macbeth patch location + dE2000 vs D50 reference; PASS when all patches < threshold
- `scripts/pns_passport_ce_values.py` — X-Rite constants → `tests/fixtures/passport_ce_values.json`
- Create `IccProfileBuilder.kt` (magic `0x61637370`, profile class `spac`)
- Implement color calibration tools (export/import JSON; chart workflow via `CalibrateScreen`)
- Optional: continuous auto-detect while overlay on (debounced)
- Unit test: valid ICC header magic bytes (`IccProfileBuilderTest`, `JpegIccEmbedderTest` replace APP2 ICC)

---

## Gallery & saved media

*Bespoke in-app gallery, thumbnails, EXIF panel, share/delete, orientation, batch share.*

-  Test external gallery button launches system resolver (2026-05-21)
-  Test gallery thumbnail click opens bespoke gallery instead of system resolver (2026-05-21)
-  Test navigation between different media items (2026-05-21)
-  Verify back button functionality returns to preview (2026-05-21)
-  Verify media items load and display correctly (2026-05-21)
- **DCIM destination:** still and video MediaStore `RELATIVE_PATH` roots under `DCIM/Point & Shoot/` (per-profile subfolders unchanged) so indexed media appears alongside typical camera-roll folders —…
- **Gallery / desktop open** — **moved to Milestone 10 Sprint 10.16** + **Milestone H.1** (single sign-off path).
- **Gallery / desktop open** — coordinates with **Milestone H.1**. **Automation created 2026-05-16:** `pns_pull_dcim_for_review.ps1` pulls captures and generates `review_manifest.md` with desktop…
- `pns_device_screencap.ps1` proof: 16:9 photo shows horizontal bars — `hfr-runs/gallery_letterbox_15_7_20260526_080306/` (`pns_gate_16x9_test2.jpg` 1920×1080 in P&S DCIM; landscape DNG 3280×2464 in…
- `pns_in_app_video_verify.ps1` not regressed; time-lapse MP4 in MediaStore
- Add `showBespokeGallery` state variable to `PreviewEngineScreen.kt`
- Add bespoke gallery overlay composable with proper state management
- Add media metadata display (EXIF, capture settings) (2026-05-21)
- Apply `ContentScale.Fit` inside tile (letterbox/pillarbox)
- Create `BespokeGalleryScreen.kt` with MediaStore loading and bitmap display (removed Coil dependency)
- Create `pns_gallery_integration_complete.ps1` for comprehensive automated testing (2026-05-21)
- Create `pns_gallery_integration_verify.ps1` for automated testing (2026-05-21)
- Fix deprecated ArrowBack icon warning (low priority)
- Fix Kotlin scope issues and compilation errors
- Gallery batch share (`ACTION_SEND_MULTIPLE`)
- Implement lazy loading for gallery thumbnails
- Implement media deletion functionality (2026-05-21)
- Implement media sharing options (2026-05-21)
- Implement zoom and pan for detailed viewing (2026-05-21)
- Lock gallery display tile to 3:4 AR (`BespokeGalleryScreen` pager viewer)
- Modify `PreviewBottomCaptureTray` to accept `onBespokeGalleryChange` callback
- Optimize MediaStore queries with proper indexing (`PnsMediaStoreGallery`)
- Update gallery thumbnail click handler to show bespoke gallery
- UX review and accessibility improvements — **maintainer UX/UI sign-off 2026-05-22** (formal TalkBack / a11y audit remains **Milestone H.6**)

---

## Settings, HUD & workflow

*Settings grouping, HUD toggles, workflow presets, cloud backup, About/heritage screen.*

- `pns_a11y_dump_gate.ps1` PASS (2026-05-27, preview foreground)
- `pns_settings_rail_screencap.ps1` — no research items in user settings rail (`hfr-runs/settings_rail_screencap_*`, **legacy SKU** `legacy serial`)
- `scripts/pns_a11y_dump_gate.ps1` — `uiautomator dump`; parse XML; assert zero interactive nodes lack `content-desc`
- Add ISO band cycle to QS grid
- Add OIS + EIS toggles to QS grid
- Cloud backup (`CloudCaptureBackup` — SAF folder + Android Auto Backup prefs)
- Command dial, HUD toggles, tally/timecode, Pro HUD overlay wiring.
- Move all `enableResearch*` items behind developer long-press gate
- Remove diagnostic probe text from user settings rail
- Reorganize `RailSettingsHomeContent` into new groups
- Unit test `HudSettingsVideoHistogramTest`
- Update `docs/PNS_TECHNICAL_SETTINGS.md` (settings groups + GLES §12)
- Workflow presets (`street`, `portrait`, `video_log`) + ADB seed

---

## Connectivity, tether & platform

*HTTP tether, Wi-Fi Direct LAN, WebDAV, LAN transfer, BLE shutter, deep links, widget.*

- [ExternalApps.kt](app/src/main/java/dev/pointandshoot/ExternalApps.kt) viewer hints
- [SharingManager.kt](app/src/main/java/dev/pointandshoot/SharingManager.kt) + FileProvider
- `pns_connectivity_test.ps1`
- `pns_platform_integration_test.ps1`
- ADB automation extra + `pns_bt_remote_shutter_verify.ps1`
- Cloud via [CloudCaptureBackup.kt](app/src/main/java/dev/pointandshoot/CloudCaptureBackup.kt)
- Collaborative counter [CollaborativeCapture.kt](app/src/main/java/dev/pointandshoot/CollaborativeCapture.kt) + CC.3 tether
- Create `docs/TETHER_API.md`
- Deep links `pointandshoot://*` ([PlatformIntegration.kt](app/src/main/java/dev/pointandshoot/PlatformIntegration.kt))
- Dual bind + NSD service registration
- Guard: only when foregrounded
- Handle `KEYCODE_MEDIA_PLAY_PAUSE` / `KEYCODE_HEADSETHOOK` → shutter fire
- Home-screen widget [PnsCameraWidgetProvider.kt](app/src/main/java/dev/pointandshoot/PnsCameraWidgetProvider.kt) (+ existing Quick Settings tiles)
- Implement tethered shooting for desktop control (loopback HTTP **28765**, `adb reverse tcp:28765 tcp:28765`)
- LAN HTTP transfer [LanMediaTransferServer.kt](app/src/main/java/dev/pointandshoot/LanMediaTransferServer.kt) + [PnsConnectivity](app/src/main/java/dev/pointandshoot/ConnectivityManager.kt)
- Runtime permission request
- Share ingress [ShareReceiveActivity.kt](app/src/main/java/dev/pointandshoot/ShareReceiveActivity.kt)
- Social webhook [SocialStreamHooks.kt](app/src/main/java/dev/pointandshoot/SocialStreamHooks.kt)
- Toggle + banner in UI
- Toggle `btRemoteShutter` in Settings → Capture + persist (not tied to volume-key capture)
- Unit test `WifiDirectTetherSupportTest`
- USB **CPH2583**: `shutterFired source=bt_media` via `pns_preview_automation_bt_media_key` (2026-05-29)
- WebDAV upload [NetworkStorageClient.kt](app/src/main/java/dev/pointandshoot/NetworkStorageClient.kt) (FTP/SMB not embedded — FOSS)

---

## Performance, memory & battery

*Memory profiler, adaptive FPS, thermal monitor, long-running pause, PO optimization gate.*

-  Profile memory usage during extended capture sessions (`pns_memory_profiler.ps1` + `PNS.MemoryProfiler`)
-  Test battery life under various usage patterns (13V.12 verified)
-  Verify thermal management under sustained load (13V.12 verified)
- **`dumpsys gfxinfo … framestats`** — **`python scripts/pns_capture_gfxinfo_baseline.py`** (**`--serial`** or **`scripts/pns_adb_device.env`**); **`perf-runs/gfxinfo_*_serial-<adb>.txt`**. First fleet…
- **`RootPrivilegedDiagnostics`** — read-only **`su -c`** suite (vendor **`getprop`** reads, CPU governor / thermal sysfs **`cat`**, short **`logcat`** tail, **`dumpsys media.camera`** head, resolution…
- **`scripts/pns_analyze_reader_backpressure.ps1`** — classifies **`PNS.Reader``drop oldest`** lines (**`queue=`** / **`channel=`**) and tallies **`encode_lane_busy`** / encode-lane drain timeouts from…
- **NDK encode bodies:** libavif (SVT-AV1 encode) + libjxl via CMake FetchContent; JNI bodies in `native/pns_native.cpp` (`BUILD_PLAN` / `NDK_PLAN`).
- **Perfetto** trace baseline (light) per **`PERFORMANCE_BUDGETS.md`** § *Perfetto & frame jank* — **`scripts/pns_capture_perfetto_light.ps1`** pulls…
- `CMakeLists.txt` with `FetchContent` for `libjxl` and `libavif`. **Completed:** NDK build produces `libpns_native.so`.
- `pns_hfr_autorun.ps1 -PerfReport` — **`perf-runs/perf_*.md`**: `am start -W` vs 800 ms, `dumpsys meminfo` PSS vs 180 MB, `PNS.Reader` drop tail; **`-Serial`** / **`pns_adb_device.env`**. Full…
- `PreviewPowerThermalOverlay`; `pns_power_thermal_verify.ps1`; **`docs/M13V_12_POWER_THERMAL.md`**
- Add performance monitoring hooks for capture latency
- Add thermal throttling detection and response (`PreviewPowerThermalMonitor`)
- Implement adaptive preview FPS based on battery level (`PreviewAdaptiveFpsPolicy`, `PNS.PowerThermal adaptiveFpsCap`)
- Implement memory leak detection and cleanup for bitmap resources (`PnsBitmapGuard`)
- Implement smart pause/resume for long-running operations (`PreviewLongRunningPause`)
- Optimize background processing to minimize battery drain
- Optimize preview pipeline memory usage with buffer pooling (GLES renderer)
- Verify `NativeEncoders.isAvailable` flips `true` when `.so` loads. **Completed 2026-05-16:** APK includes `.so` for `arm64-v8a` and `x86_64`; `NativeDiagnosticsScreen` shows status.

---

## AI & scene-assisted capture

*Smile detection, scene vendor hints, CameraX extension filtering, bitrate scale.*

- **`docs/M13V_17_AI_FEATURES.md`**
- `pns_ai_features_verify.ps1 -HostOnly`; JVM **`SmileStillCapturePolicyTest`**, **`SceneVendorHintProbeTest`**.
- `SceneVendorHintProbe` at app start → **`PNS.SceneHint`**; HUD **Scene vendor hints (log)**.
- `SmileStillCapturePolicy` + ML Kit smile on YUV when HUD enabled; tray still capture ref; cooldown **4.5 s**.
- `videoBitrateScalePercent` (**50–150%**) in `VideoRecordingController.bitrateForSize()`; HUD slider.
- Add smile detection with automatic capture (13V.17 implemented)

---

## Other shipped work

*Completed items not matched above — skim for misc coverage.*

-  Spatial playback on 360° hardware (if applicable)
-  Test all capture modes under various conditions (M13 verified)
-  Test focus accuracy in various lighting conditions (M14 verified)
- **AE antibanding** — `PreviewAeAntibanding` sets `CONTROL_AE_ANTIBANDING_MODE` (prefers **AUTO**, else **50 Hz** / **60 Hz**, else first HAL mode) on preview + still requests when the key is…
- **HDR / 10-bit / color space on live preview** — **`PreviewHdrSessionSupport`** + **`SessionConfigurationCompat.isMultiOutputSessionSupportedWithDynamicRangeOnPreview`**;…
- **Highlight (H) — disable flash / torch** — **`PreviewFlashPolicy`** + tests + device LED check.
- **Logcat cleanliness** — no repeating Camera2 fatal/error spam in the same run (`summary_grep.txt` **ERROR** sweep clean for Camera paths); **`MediaGeotag`** failures log **one-line** warnings only…
- **Session defaults (macro `setSessionParameters` path)** — `PreviewAeAntibanding` on the session-parameters preview `CaptureRequest.Builder` before `build()`;…
- **Stabilization** — `CONTROL_VIDEO_STABILIZATION_MODE` and/or `LENS_OPTICAL_STABILIZATION_MODE` where characteristics allow; policy tied to focal / FPS / user pref without breaking frozen preview…
- **Stream use cases** — `OutputConfiguration.setStreamUseCase` with `CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW` / `…STILL_CAPTURE` (first surface vs rest) in…
- **Torch / flash strength** — **`PreviewFlashPolicy`** sets **`CaptureRequest.FLASH_STRENGTH_LEVEL`** from **`FLASH_INFO_STRENGTH_*`** when advertised (preview torch incl. **On**→torch fallback,…
- **Ultra-Max scripted smoke** — §5 **2026-05-10**: USB **`legacy serial`**; **`am start`** with **`pns_preview_imaging_profile=ultra_max`** + **`pns_preview_raw_count=1`** →…
- `.cursor/rules/fleet-generic-policy.mdc` — no new legacy SKU assumptions without plugin + USB proof
- `AGENTS.md` + `scripts/pns_adb_device.env.example` — CPH2583 primary (`b5214fc6`)
- `cameraX` slice — informational only; Camera2 remains capture path
- `capabilitiesNormalized[]`, `hardwareLevel`, stream use cases, 10-bit DR, `timestampSource`, `rawReadiness`
- `clearTaskOnLaunch`, `STILL_IMAGE_CAMERA_SECURE`, Quick Settings tiles; `pns_power_button_gate.ps1`
- `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`, `AGENTS.md` CRITICAL — Fleet, `.cursor/rules/fleet-generic-policy.mdc`
- `pns_in_app_video_verify.ps1` PASS + `pns_photo_capture_verify.ps1` PASS
- `pns_in_app_video_verify.ps1` passes. **Completed 2026-05-17:** legacy device — `inAppVideoPrepared audioEnabled=true` → `MediaRecorder started` → `inAppVideoSaved` (no "unconfigured surface" errors).
- `PreviewEngineScreen` adds `videoRecordingSessionRebuildPending` flag to coordinate: prepare → set flag → rebuild session → clear flag on `onConfigured` → start recorder.
- `scanMeta`: MPC, `firstApiLevel`, `vendorApiLevel`; deterministic `cameraId` sort (full tier)
- `scripts/pns_altreferenceapp_apk_decompile.ps1`; **`docs/ALTREFERENCEAPP_APK_FLEET_ANALYSIS.md`**; **`docs/RAW_REFERENCE_APP_MATRIX.md`**
- `scripts/pns_release_asset_check.ps1` — `gh release view`; assert APK asset size > 1 MB
- `VideoRecordingController` owns `MediaRecorder` lifecycle with two-phase prepare/start flow. `PrepareResult` sealed class (`Ready`, `Rejected`, `NoAction`) signals session rebuild needed; `Event`…
- Add `VideoAudio` pack to `pns_sprint_guardrail.ps1` regression dispatch. **Completed 2026-05-17:** Created orchestrator with VideoAudio + CapturePipeline packs; unified `sprint_guardrail.v1` JSON…
- Add support for external flash control (`previewFlashStrengthPercent` → `FLASH_STRENGTH_LEVEL` API 35+)
- Auto quick scan after camera permission (background)
- Compute `gainLinear` at recorder start; apply in PCM loop
- Document guardrail usage in BUILD_PLAN.md with PowerShell examples.
- Documented in `docs/RAW_CAPTURE_DEVICE_MATRIX.md`
- Dual-video stacked path: split tile into two `shrinkToFit` rects
- Extract `VideoRecordingController` class from `PreviewEngineScreen.kt` monolith. **Completed 2026-05-17:** New 180-line class with `applyShell()`, `maybeStartRecorder()`, `tearDownForCloseCamera()`…
- Fleet PRs attach matrix diff or hub export when caps change
- Full tier → structured `cameras[]` + `appendix` (stream map, keys, pipeline access)
- Guard `setPreviewHistogramEnabled` against session churn mid-recording
- History rotation + on-device diff vs previous scan
- Log sensor orientation, active array, crop region, buffer size, fit mode on face frame
- Multi-track policy logged unsupported (v1 stereo AAC mux)
- No UI-induced capture regressions (preview stable) — same May 2026 device pass: cold start `ultra_max` + dial `H` shows monotonic `seq=*` `PNS.ModeTransition` lines, `preview_pipeline_restart` with…
- legacy device row: archived / regression optional
- legacy device serial local-only; regression lane documented
- Pinned fleet / legacy device note in BUILD_PLAN
- Quick refresh, Rescan full, Export JSON, Compare previous (diff card)
- README audit **M10–13V** shipped features, status table, verify-script index (May 2026)
- Rescan playbook in `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` + `AGENTS.md`
- Run with RECORD_AUDIO denied; verify `audioEnabled=false` in logs. **Completed:** Fresh install shows `PNS.Video: inAppVideoPrepared audioEnabled=false`.
- Seed **CPH2583** from matrix gates + adb_preview_validate
- Settings → Advanced Capture: "Time-lapse output → Photo / Video"
- Settings → Video: "Optical stabilization (OIS)" + "Electronic stabilization (EIS)" with descriptions
- Slider in Settings → Video (−12 to +12, 0.5 step)
- Super Macro hardware lock — Automated gate: **`scripts/pns_super_macro_gate.ps1`** (or **`pns_adb_preview_validate.ps1 -SuperMacroOnly`**) writes **`super_macro_gate.json`** /…
- Tables: Summary | Per camera (partial — full tab accordion deferred)
- Update `docs/PNS_TECHNICAL_SETTINGS.md` §9
- When to rescan (app release, OS patch, fleet PR, user report) — `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` §Rescan playbook
- Wire both to QS grid (added in 15.8)
- Wire histogram pipeline via `previewHistogramEnabled` / existing `wantYuv`
- Wire video mode → `previewTextureCoverCrop=false` on video-primary (`LaunchedEffect(primaryPhoto)`)

---

## Archived milestone sprints (M15–M23)

Full sprint bodies moved from **`BUILD_PLAN.md`**. Feature-index summaries remain in the categories above.

### Milestone 15 — Pro Camera Polish & Color Fidelity *(archived)*

**15.0–15.B**, **15.14**, **15.16–15.38** — completed tasks indexed under feature categories in this file (tether, video, DNG, HUD, etc.).

Human gates: **H.7** (DNG color ACR per onboarded SKU) — **closed CPH2583** owner 2026-05-29; **H.8.1**–**H.8.6** (subjective) → open in **BUILD_PLAN.md** Milestone H.

### Milestone 16 — Fleet Device Capability Matrix *(archived)*

**16.0–16.13** — fleet matrix work indexed under **Fleet capability matrix & device policy** above.

**Docs:** `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · `docs/FLEET_REFERENCE_M10_8.md` · `docs/fleet_device_matrix.schema.json`

**Gate:** USB full matrix pull + `pns_fleet_matrix_scan.ps1` **PASS** on CPH2583 (2026-05-29).

---

### Milestone 18 — Fleet max-out framework *(archived 2026-05-30)*

**Objective:** Universal device capability taxonomy, **Fleet Parity Sweep** benchmark, matrix schema v2, fleet-adaptive focal row, multi-device regression pack.

**Docs:** `docs/CAMERA_CAPABILITY_TAXONOMY.md` · `docs/FLEET_PARITY_SWEEP.md` · `docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md`

**Device gate (CPH2583 `b5214fc6`):** `pns_fleet_matrix_scan.ps1` pass (`hfr-runs/fleet_matrix_20260530_024009/`); `pns_fleet_parity_sweep.ps1 -Mode Quick` pass (`hfr-runs/parity_sweep_20260530_024345/`); `pns_chrome_ux_gate.ps1 -FocalMmSlot 85/150` pass; `pns_capability_catalog_gate.ps1` pass; fleet JVM tests pass.

#### Sprint 18.0 — Schema + docs

- [x] **[AGENT]** `docs/CAMERA_CAPABILITY_TAXONOMY.md` + matrix schema v2 notes
- [x] **[AGENT]** `docs/FLEET_PARITY_SWEEP.md` + `docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md`
- [x] **[AGENT]** `BUILD_PLAN.md` M18/M19/M20 active; archive M17 pointer

#### Sprint 18.6 — Fleet Parity Sweep (FPS)

- [x] **[AGENT]** `FleetParitySweep.kt` + `FleetDeliveryProbe.kt` + JVM tests
- [x] **[AGENT]** `scripts/pns_fleet_parity_sweep.ps1` — **`-Mode` required** (exit 2 without)
- [x] **[AGENT]** In-app hub mode sheet + `PNS.FleetParity parityCell=` log emission per catalog row
- [x] **[ADB]** `-Mode Quick` smoke on **CPH2583**; attach `hfr-runs/parity_sweep_*`

#### Sprint 18.1 — Catalog expansion

- [x] **[AGENT]** `CameraCapabilityCatalog` v3 rows (~165+ distinct; expansion + evaluators)
- [x] **[AGENT]** Evaluators for new rows; `CameraCapabilityCatalogExpansion.kt`

#### Sprint 18.7 — Fleet-adaptive focal row

- [x] **[AGENT]** `FleetFocalRowPolicy.kt` + matrix `product.focalRow` parser + tests
- [x] **[AGENT]** Wire native UW/Wide/Tele labels + static 35/50/85/150 N/A chips (behavior only; chrome layout lock)
- [x] **[ADB]** `pns_chrome_ux_gate.ps1 -FocalMmSlot 85` + 150 on CPH2583

#### Sprint 18.4/18.5 — Regression pack + CI

- [x] **[AGENT]** `pns_fleet_regression_pack.ps1` + `pns_capability_catalog_gate.ps1`
- [x] **[AGENT]** `pns_m18_gate.ps1` + `pns_fleet_macro_export.ps1`
- [x] **[AGENT]** `docs/FLEET_PARITY_LATEST.json` + history JSONL from parity script

**M18 gate:** `pns_capability_catalog_gate.ps1` + `pns_fleet_regression_pack.ps1 -Tier all` + parity Quick USB on primary SKU — **PASS** (2026-05-30).

---

### Milestone 19 — Feature max-out *(archived 2026-05-30)*

**Objective:** Ship committed formats, quality-first pickers, video/still pipelines from max-out list.

**Host gate:** `scripts/pns_m19_gate.ps1` — M19 JVM tests + catalog gate (+ USB tier-2 regression when device online).

#### Sprint 19.6 — Format + color picker

- [x] **[AGENT]** `ColorQualityIndex.kt` + `FormatQualityDescriptor.kt` + `VideoFormatQualityRank.kt`
- [x] **[AGENT]** Video picker: fps **desc**, Max presets, codec quality rows, **VideoAudioSource** in sheet
- [x] **[AGENT]** Color-space step (CQI) in still + video pickers; filter downstream rows
- [x] **[AGENT]** `StillFormatPickerSheet.kt` + HEIC / Motion Photo / TIFF export scaffolds

#### Sprint 19.1 — Video pipelines

- [x] **[AGENT]** RAW video `.mcraw` in main format picker (matrix-gated)
- [x] **[AGENT]** Dual-ISO HDR merge production path
- [x] **[AGENT]** VP9 WebM encoder path (below AV1; matrix-gated)

#### Sprint 19.4 — ProRes + anamorphic

- [x] **[AGENT]** ProRes probe-only catalog row + anamorphic metadata (no HW encode)

**M19 gate:** `pns_m19_gate.ps1` + `pns_fleet_regression_pack.ps1` tier 2 — host JVM **PASS** (2026-05-30).

---

### Milestone 20 — Concurrent capture *(archived 2026-05-30)*

**Host gate:** `scripts/pns_m20_gate.ps1` — M20 JVM tests + dual record 5s + pip/multicam USB smoke + tier-2 regression.

#### Sprint 20.1 — Dual video reliability

- [x] **[AGENT]** HAL-derived `dualVideo` matrix gates + front health recovery + mandatory `-RecordSec 5` gate

#### Sprint 20.2 — Multicam Melt

- [x] **[AGENT]** `MulticamMeltRecordingController` + thermal caps + parity cells + USB arm smoke

#### Sprint 20.3 — PiP preview (optional)

- [x] **[AGENT]** Concurrent rear+rear PiP inset preview + `pns_preview_pip` ADB gate

**M20 gate:** `pns_m20_gate.ps1` + parity Quick includes `video.dual` / `video.multicam_melt` / `preview.pip`.

---

### Milestone 21 — Fleet parity honesty *(archived 2026-05-30)*

**Objective:** Honest **Fleet Parity Sweep** gate + build-planning instrument: gap classes, consumer impact, quick-tier matrix accuracy, planning artifacts, CI golden sweep, `pns_m21_gate.ps1`.

**Docs:** `docs/FLEET_PARITY_SWEEP.md` · `docs/CAMERA_CAPABILITY_TAXONOMY.md` · `docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md` · `docs/FLEET_DEVICE_VERIFY_MATRIX.md`

**Host gate:** `scripts/pns_m21_gate.ps1` — JVM parity tests + golden matrix sweep + catalog gate; USB Quick → Full → `-IncludeRecord`.

#### Gap classes (`FleetParitySweep.GapClass`)

| Class | Blocks Full pass? |
|-------|-------------------|
| M18 keep: `OK`, `GAP_ADVERTISED_NOT_PROVEN`, `GAP_DELIVERY_MISMATCH`, `GAP_PROVEN_NOT_ADVERTISED`, `GAP_PLANNED` | A/N/P/D per M18 |
| `GAP_PROBE_INVENTORY` | No (encoder ProbeOnly) |
| `GAP_ADVERTISED_NOT_SURFACED`, `GAP_SURFACED_NOT_ADVERTISED` | No (planning) |
| `GAP_REGRESSION_SINCE_BASELINE` | **Yes** with `-BaselineTag` + `ship_blocker` |
| `GAP_CONFLICT_RISK`, `GAP_UNAUTOMATED`, `GAP_FLAKE_SUSPECT`, `GAP_HUMAN_ONLY`, `GAP_FLEET_PLUGIN_CANDIDATE` | No (planning / triage) |

**Consumer impact:** Full pass fails only on blocking gap class **and** `ship_blocker`. Host artifact: `parity_ship_blockers.md`.

#### Sprint 21.0 — Parity infrastructure honesty

- [x] **[AGENT]** `pns_fleet_parity_sweep.ps1` — pull in-app JSON via `run-as`; fix logcat fallback; `gapBreakdown`; APK preflight on Full
- [x] **[AGENT]** Version stamp + duration rollup (`catalogVersion`, `scanTier`, `p95CellMs`, `slowestCells[]`)
- [x] **[AGENT]** Real `parity_closure_plan.md` from pulled JSON
- [x] **[AGENT]** Host fixture test: sample logcat + JSON → honest gap counts

#### Sprint 21.1 — Quick-tier matrix `featureGates`

- [x] **[AGENT]** `FleetDeviceMatrixStructured.featureGatesShallow()` + wrap `buildQuick()` cameras
- [x] **[AGENT]** JVM / golden: quick tier cameras have `featureGates.raw/hfr/face`
- [x] **[ADB]** Matrix quick scan → parity Quick correct `advertised=true` (CPH2583, `hfr-runs/m21_gate_20260530_134233`)

#### Sprint 21.2 — ProbeOnly / encoder inventory

- [x] **[AGENT]** `encoder.*` rows: `sweepSkipReason` + `GAP_PROBE_INVENTORY`; tighten `proveOk`
- [x] **[AGENT]** `pns_capability_catalog_gate.ps1` — ProbeOnly sweep policy required
- [x] **[AGENT]** Encoder slice cross-check → `parity_encoder_crosscheck.json`

#### Sprint 21.3 — Partial proveOk + USB proof map

- [x] **[AGENT]** Remove Partial Full auto-pass; explicit proof hooks per row
- [x] **[AGENT]** Catalog `parityProofScript` on Partial rows
- [x] **[AGENT]** Expand `quickCellIds`; Delta targets Partial gaps only

#### Sprint 21.4 — Delivery verification (`-IncludeRecord`)

- [x] **[AGENT]** `FleetDeliveryProbe` + `pns_in_app_video_verify.ps1` chain
- [x] **[AGENT]** `delivery_mismatch.json` / `.md`; ffprobe tolerances
- [x] **[ADB]** Full `-IncludeRecord` on CPH2583 (`hfr-runs/parity_sweep_20260530_133513`, matchOk=true)

#### Sprint 21.5 — Gate scripts + hub export

- [x] **[AGENT]** `scripts/pns_m21_gate.ps1` (host + USB tiers)
- [x] **[AGENT]** Parity summary appended to `PROBE_EXPORT_LATEST.md`
- [x] **[AGENT]** Fix tier-2 matrix refresh hang (`-SkipMatrixRefresh`)
- [x] **[AGENT]** Append `REG-*` to `docs/AGENT_REGRESSION_MEMORY.md`

#### Sprint 21.6 — legacy device optional concurrency *(non-blocking)*

- [x] **[AGENT]** `pns_legacy_regression_pack.ps1` — pip + multicam melt when legacy SKU online (skipped CPH2583; `hfr-runs/legacy_regression_pack_20260530_135550`)
- [x] **[AGENT]** Verify matrix doc: CPH2583 dualVideo only; PiP/Melt legacy device or future SKU

#### Sprint 21.7 — Planning intelligence

- [x] **[AGENT]** Surfacing audit → `parity_surfacing_audit.json`
- [x] **[AGENT]** `-BaselineTag` / `-BaselineJson` → `parity_regression_delta.json`
- [x] **[AGENT]** Catalog `closureEffort`, `buildPlanSprint`; `parity_automation_coverage.md`
- [x] **[AGENT]** `formatPickerHonestyScore`; `-CompareMatrix` → `parity_fleet_diff.md`

#### Sprint 21.8 — Gap class implementation + chrome lint

- [x] **[AGENT]** Full `GapClass` enum + `classify()` + consumer impact on cells
- [x] **[AGENT]** Chrome lock lint on `writeClosurePlan`; JVM tests per class
- [x] **[AGENT]** Update taxonomy + parity sweep docs

#### Sprint 21.9 — Conflict matrix + session templates

- [x] **[AGENT]** `FleetParityConflictMatrix.kt` — dual+HFR, RAW+pin, melt+thermal, stream budget
- [x] **[AGENT]** `FleetSessionTemplateCoverage.kt` → `parity_session_templates.json`
- [x] **[AGENT]** `parity_conflict_risks.json`; JVM tests

#### Sprint 21.10 — Performance + thermal cost

- [x] **[AGENT]** Perf SLA constants in `docs/PNS_TECHNICAL_SETTINGS.md`
- [x] **[AGENT]** Thermal snapshot during IncludeRecord → `parity_thermal_cost.md`

#### Sprint 21.11 — DNG sub-track + Milestone H routing

- [x] **[AGENT]** Catalog `humanOnly=true`; `GAP_HUMAN_ONLY`; DNG closure script links
- [x] **[AGENT]** Optional `-IncludeDngSubTrack` on parity script

#### Sprint 21.12 — Fleet policy + flake + CI golden sweep

- [x] **[AGENT]** `GAP_FLEET_PLUGIN_CANDIDATE` + flake score from history JSONL
- [x] **[AGENT]** Host JVM golden sweep (`fleet_matrix_gate_minimal.json` + CPH2583 golden)
- [x] **[AGENT]** GitHub workflow: catalog gate + golden sweep + chrome lint on `fleet/*` PRs

#### Sprint 21.13 — Report enrichment + workflow presets

- [x] **[AGENT]** Catalog `consumerImpact` + closure plan sections
- [x] **[AGENT]** Host `parity_ship_blockers.md`; verify M21 report artifacts on Full run
- [x] **[AGENT]** `workflow.preset.*` catalog rows; `-IncludeWorkflowPresets` chain

**M21 gate (CPH2583):** `pns_m21_gate.ps1` **PASS** (`hfr-runs/m21_gate_20260530_134233`) · 13/13 steps incl. **`parity_full_include_record`** · **0** `ship_blocker` · Quick **55** · Full **170** cells.

---

### Milestone 22 — Fleet parity proof-pack closure *(archived 2026-06-02)*

**Objective:** Drive parity closure to machine-checkable truth on CPH2583: proof-pack merge, honest gap accounting, and explicit provider ownership for remaining rows.

**Gate:** `scripts/pns_m22_gate.ps1` **PASS** (`hfr-runs/m22_gate_20260602_011300`) with `unautomated=0`, `not_proven=0`, `planned=0`.

#### Sprint 22.0 — Proof-pack infrastructure *(infra)*

- [x] **[AGENT]** `-IncludeProofPack` merge path in `pns_fleet_parity_sweep.ps1` (Full mode)
- [x] **[AGENT]** `scripts/parity_proof_manifest.json` coverage for unautomated/not-proven rows
- [x] **[AGENT]** `parity_proof_results.v1` merge semantics documented + host validation

#### Sprint 22.1 — AV1 closure *(matrix_gate)*

- [x] **[AGENT]** Wire AV1 proof hooks and matrix gate on `cameraAny.featureGates.av1.sessionOk`
- [x] **[ADB]** CPH2583 AV1 classified honest matrix-gated (`advertised=true`, `sessionOk=false`, `appEnabled=false`)

#### Sprint 22.2 — RAW video + delivery honesty *(raw_delivery)*

- [x] **[AGENT]** `video.raw` / `video.raw_picker` proof ownership wired to `pns_raw_video_verify.ps1`
- [x] **[ADB]** Delivery mismatch probe merged (`video.delivery_honesty`) with IncludeRecord evidence
- [x] **[ADB]** RAW video closure documented as honest matrix-gated on CPH2583

#### Sprint 22.3 — Still export closure *(still_export)*

- [x] **[AGENT]** Still export scaffold + ADB still-format override wiring (`heic`, `motion_photo`, `tiff16`, `jxl`)
- [x] **[ADB]** `pns_still_export_verify.ps1` PASS on all four rows (CPH2583)

#### Sprint 22.4 — HFR proof rows *(hfr_rows)*

- [x] **[AGENT]** `pns_hfr_fps_parity_verify.ps1` with `-AllFps`, per-tier proof outputs, matrix-aware skip
- [x] **[AGENT]** Catalog/manifest proof wiring for `video.hfr.{24,30,60,120,240}`
- [x] **[ADB]** Full proof-pack row closure for HFR set on CPH2583

#### Sprint 22.5 — Audio/color/tonal proof rows *(media_rows)*

- [x] **[AGENT]** Proof scripts for `audio.spatial`, `audio.unprocessed`, `video.color.*`, `still.independent_tonal`
- [x] **[AGENT]** Catalog/manifest parityProofScript mapping for all Sprint 22.5 rows
- [x] **[ADB]** Full proof-pack closure for Sprint 22.5 rows on CPH2583

#### Sprint 22.6 — Ship all 18 GAP_PLANNED rows *(ship_all)*

- [x] **[AGENT]** Product ship each Planned row -> Partial/Shipped (or honest matrix N/A)
- [x] **[AGENT]** Resolve catalog duplicate status (`CameraCapabilityCatalog.kt` vs `CameraCapabilityCatalogExpansion.kt`)
- [x] **[AGENT]** Per-row `parityProofScript` or ProbeOnly inventory + hub log needle
- [x] **[AGENT]** Promote VP9 tiers Planned -> Partial; wire `pns_video_format_test.ps1` per tier
- [x] **[ADB]** Full sweep shows `GAP_PLANNED=0` on CPH2583

#### Sprint 22.7 — Gate scripts + CI

- [x] **[AGENT]** `scripts/pns_m22_gate.ps1` — JVM + catalog gate + Full USB `-IncludeProofPack -IncludeRecord`
- [x] **[AGENT]** Host proof-pack merge fixture test (CI / `toolchain-verify.yml`)
- [x] **[AGENT]** Update `docs/PNS_TECHNICAL_SETTINGS.md` section for parity proof-pack merge
- [x] **[AGENT]** Append `REG-*` to `docs/AGENT_REGRESSION_MEMORY.md` after USB-proven closure
- [x] **[ADB]** M22 gate PASS on CPH2583 — merged `OK >= 163`, unautomated `0`, not_proven `0`, planned `0`

#### Sprint 22.8 — Capability-provider closure map *(Camera2 / CameraX / Vendor HAL)*

- [x] **[AGENT]** For every open catalog row, record one owner class in docs/parity notes: `ShipNow`, `MatrixGate`, `ProbeOnly`, or `DeferredPlanned` (no uncategorized rows)
- [x] **[AGENT]** Add a host gate check that fails when any open row lacks provider ownership classification
- [x] **[ADB]** Full sweep + proof merge still clean after ownership mapping updates

---

### Milestone 23 — Fleet hardening + resilience closeout *(archived 2026-06-03)*

**Objective:** Finish M23 capture/fleet resiliency hardening with truthful gates on primary USB device (CPH2583-class), while keeping preview chrome and lock invariants stable.

**Gate highlights (USB + host):**
- `scripts/pns_verify_toolchain.ps1 -RunTests` **PASS** (post-seam lint fix)
- `scripts/pns_capture_pipeline_verify.ps1` **PASS** (`captureRawStill 1/1 ok=true saved=`)
- `scripts/pns_chrome_ux_gate.ps1 -FocalMmSlot 73|85|150` **PASS** (`teleFocalSlotOk=true`)
- `scripts/pns_fleet_parity_sweep.ps1 -Mode Quick` **PASS** (and `-PromoteOptionalBlocking` intentionally fails when optional gaps exist)
- `scripts/pns_aux_dng_capture_analyze.ps1` **PASS** openability/integrity on non-legacy model path (`--skip-wide-cal-leak` auto for non-legacy)
- `scripts/pns_fixture_dng_gates.ps1` **PASS** with fixture-dir fallback (`referenceapp_legacy_sku` or `referenceapp_cph2655`)

#### Sprint 23.2 — Capture monolith extraction (phase 1)

- [x] Extracted session/capture seams into `preview/session/PreviewSessionOrchestrators.kt` and `preview/capture/ImageReaderAwait.kt`
- [x] Wired `PreviewEngineScreen` to narrow open/session gateways with no route/chrome layout change
- [x] Kept preview chrome lock unchanged; gated via chrome UX + capture pipeline

#### Sprint 23.3 — DNG write safety + memory pressure hardening

- [x] `StillCaptureMetadata.applyToDngUri` now stages patched bytes before final URI write
- [x] Reused staged-write path for JPEG ICC embedding to reduce risky direct rewrite patterns
- [x] Kept lock invariants: no `ExifInterface.saveAttributes()` on DNG; no CM/FM reconcile re-enable

#### Sprint 23.4 — Bracket/ZSL/result pairing correctness

- [x] Bracket burst image fetch now waits briefly for reader frames instead of assuming callback order
- [x] ZSL ring pairing now prefers timestamp-matched image/result associations
- [x] Added generation-token stale guardrails during bracket in-flight teardown

#### Sprint 23.5 — Fleet focal resolver unification

- [x] `BackCameraRoleResolver` openable-pair resolution now fails closed when id is not openable
- [x] Verified focal-slot runtime path by USB chrome gate at 73/85/150 mm
- [x] Preserved dodge tele routing locks and physical-first behavior when enumerated

#### Sprint 23.8 / 23.9 / 23.10 / 23.11 / 23.12 closeout deltas

- [x] Optional parity subtracks can be promoted to blocking (`pns_fleet_parity_sweep.ps1 -PromoteOptionalBlocking`)
- [x] Deterministic controller executor shutdown + stronger camera error teardown (`closeCamera()` on disconnect/error)
- [x] Lifecycle/perf smoke gates PASS (`pns_in_app_video_verify.ps1`, `pns_memory_profiler.ps1`, `pns_battery_life_test.ps1`, `pns_po_optimization_gate.ps1`)
- [x] Host/toolchain and targeted JVM suites rerun after seam extraction
- [x] Updated M23 closeout docs/checklists and changelog coverage requirements

---

## Milestone 24 — completed sprints

Moved from **`BUILD_PLAN.md`** (2026-06-05). Remaining M24 device blockers stay in the active plan.

### Sprint 24.0 — Baseline instrumentation + rubric

- [x] **[AGENT]** Added canonical HFR route telemetry (`hfrWarmupAttempt`, `hfrRoute`, `hfrHealthWindowMs`, `hfrBlockReason`) in preview/video + ADB validation lines, including `interleaved_sub4k`, `hsCaptureWxH`, `encodePrefWxH`, `mcPrepared`, `strictHfrWarmupHealthy`
- [x] **[AGENT]** Documented strict 4K120 success rubric in `docs/PNS_TECHNICAL_SETTINGS.md` (M24 lane)

### Sprint 24.1 — HFR route ladder (interleaved ↔ encoder-priority)

- [x] **[AGENT]** Implemented bounded route ladder for constrained HS bring-up (`handleCaptureSessionConfigureFailed`, `StrictHfrPolicy.nextConfigureFailAction`)
- [x] **[AGENT]** Kept strict start behavior: block record start unless a 120-ready health route exists (`strictHfrWarmupHealthy`, `inAppVideo120StrictBlocked`)

### Sprint 24.2 — Strict warmup + retry budget

- [x] **[AGENT]** Added warmup health window before strict 120 start
- [x] **[AGENT]** Added bounded retry budget before runtime session is marked unstable
- [x] **[AGENT]** Deferred ADB `isRecording` until `adbStrictHfrWarmupReady()` in `PreviewEngineScreen.kt`
- [x] **[AGENT]** Added deterministic host coverage for pass-after-retry and block-after-budget (`StrictHfrPolicyTest`)

### Sprint 24.3 — Mid-record resilience

- [x] **[AGENT]** Added one bounded same-fps re-acquire on mid-record HS collapse, then deterministic fail/stop (`fallbackFromUnstableHfr`, `hfrMidRecordOutcome`)
- [x] **[AGENT]** Ensured cleanup avoids zombie recorder/session state; mid-record path sets `hfrMidRecordRecoveryUsed`

### Sprint 24.4 — Delivery truth gates

- [x] **[AGENT]** Tightened 4K120 output classes to `true_4k120`, `hs120_sub4k`, `blocked_unstable` (`pns_mediacodec_hfr_verify.ps1`)
- [x] **[AGENT]** Wired truth class into parity merge/reporting (`video4k120TruthClass`)

### Sprint 24.5 — Fleet policy alignment

- [x] **[AGENT]** Mapped runtime 4K120 truth to catalog/parity semantics without device hardcoding
- [x] **[AGENT]** Prevented false “ship-ready 4K120” parity state when strict criteria are unmet
- [x] **[AGENT]** Added format-picker/readout honesty for sub-4K HS at 4K encode (`VideoDeliveryHonesty`, `video.delivery_honesty`)

### Sprint 24.6 — Endurance evidence pack

- [x] **[AGENT]** Added `pns_4k120_endurance.ps1` for sustained-run classification (`thermal`, `session_disconnect`, `encoder_stall`, `fps_collapse`)
- [x] **[AGENT]** Exported timeline + FPS trend + terminal reason under `hfr-runs/4k120_endurance_*`

### Sprint 24.7 — Milestone orchestration + closeout

- [x] **[AGENT]** Added `scripts/pns_m24_gate.ps1` orchestration (host preflight → strict 4K120 → endurance → parity Full)
- [x] **[AGENT]** Enforced app cleanup (`adb shell am force-stop dev.pointandshoot`) on all paths
- [x] **[AGENT]** Updated technical settings + regression memory + changelog coverage in the same lane

### Milestone 24 — 4K120 stability and truthfulness *(archived 2026-06-05)*

**Primary USB device:** OnePlus 12 **CPH2583** (`b5214fc6`).

| Gate | Result | Artifact |
|------|--------|----------|
| Photo / 30 fps video smoke | PASS | `hfr-runs/photo_capture_verify_20260605_110845`, `hfr-runs/in_app_video_verify_20260605_110935` |
| Capability probe (S2) | PASS | `hfr-runs/m24_gate_20260605_113804/video_capability_probe` |
| Strict 4K120 | Truth `blocked_unstable` (1920×1080 @ 120 HS deliver) | `hfr-runs/verify_4k120_20260605_072906/attempt_3`, `…/strict_4k120/` |
| Endurance | `bestPassSec=0`, `session_disconnect_or_encoder_stall` | `hfr-runs/m24_gate_20260605_113804/endurance/` |
| Parity Full | Truthful gap accounting (no false ship-ready 4K120) | `hfr-runs/parity_sweep_20260605_115828/` |

**Pipeline fixes (2026-06-05):** GL-thread preview buffer sizing, fault-recovery backoff, lean-video warmup bypass for RAW ADB automation, `MediaRecorder.getMaxAmplitude` guard — see `REG-20260605-001` in `docs/AGENT_REGRESSION_MEMORY.md`.

---

## Milestone 25 — completed sprints

Moved from **`BUILD_PLAN.md`** (2026-06-05). Open M25 sprints remain in the active plan.

### Sprint 25.0 — App probe closeout

- [x] **[AGENT]** Full-tier matrix gate: rear `lensInfo` + hub leaderboard readiness blocked contributes unless full tier + full sweep (`LeaderboardReadiness.kt`, `FleetMatrixHubScreen.kt`)
- [x] **[AGENT]** Full parity wiring for `still.resolution_maximum_map` / `still.hidden_highres` via `max_resolution_map_jpeg` session probe
- [x] **[AGENT]** Promoted resolution catalog rows from informational to ship-blocker after 2-device USB proof (`REG-20260605-003`)
- [x] **[AGENT]** Hub contribute flow: `romReported` self-tag + validation, public `#/device/{slug}` URL/copy
- [x] **[ADB]** Confirmed `PNS.FleetMatrix lensInfo rear=` after full rescan on CPH2583 with readiness green

### Sprint 25.1 — Priority USB fleet pair (partial closeout)

- [x] **[ADB]** CPH2583 Full sweep with high parity + low `resolutionBetrayalIndex`
- [x] **[ADB]** OP13 stock lane closed on connected OP13-class SKU `CPH2655` (artifact lane retained in verify matrix / leaderboard outputs)
- [x] **[AGENT]** Republish after pair and verify OP12/OP13 ranking honesty in `docs/FLEET_PARITY_DEVICE_LEADERBOARD.json`
- [x] **[AGENT]** Updated `docs/FLEET_DEVICE_VERIFY_MATRIX.md` M25 rows with artifact paths

> Follow-up moved to Milestone H optional OP13 lane: dedicated `CPH2649` stock confirmation and OP13 Lineage/custom second-variant sweep when hardware is available.

### Sprint 25.4 — CameraX parity lane (informational)

- [x] **[AGENT]** CameraX extension catalog rows (Night, Bokeh, HDR) + probe evaluators (existing `camerax.*` catalog)
- [x] **[AGENT]** **CameraX honesty** score in published profile (`Get-CameraXHonestyScore` in `pns_leaderboard_common.ps1`)
- [x] **[AGENT]** Device detail: **Camera2 | CameraX** toggle (`device-detail.js`, `app.js`)
- [x] **[ADB]** CameraX panel populated on **CPH2583** Full sweep artifact — `docs/leaderboard/data/devices/718a8115ff142454.json` (`cameraXProbed=true`, Night/Bokeh/HDR + honesty block)

### Sprint 25.6 — Deferred / out of scope tracking

- [x] **[AGENT]** *(Optional)* Catalog row `product.oem_app_manual_smoke` — human-only OEM camera checklist; **never affects rank**
- [x] **[AGENT]** *(Optional)* AnTuTu live scrape refresh when `antutuScoreOverride` absent in marketing map — `antutu_ranking_scrape.py` 2026-06-05 (30 rows → `docs/leaderboard/data/antutu_scores.json`)

### Sprint 25.2 — GSMArena advertised spec + HAL sensor path

- [x] **[AGENT]** Live `gsmarena_device_specs_scrape.py` success path with retry hardening; reduced `fromSensorCache` fallback reliance
- [x] **[AGENT]** Preferred HAL `lensInfo` / matrix sensor sum over GSMArena when full-tier matrix is present
- [x] **[AGENT]** Expanded `Map-GsmarenaAdvertisedClaims` to `lens.ois`, `lens.tele`, per-lens MP, MSRP/launch metadata
- [x] **[HOST]** Weekly stale flag path on site (`site.json` + footer note) validated through host publish

### Sprint 25.3 — Community ingest + submission merge (agent rows)

- [x] **[AGENT]** Deployed `leaderboard-ingest/` to Render and wired ingest URL in app/env example (`https://pns-leaderboard-ingest-live.onrender.com`)
- [x] **[AGENT]** End-to-end flow: full sweep submit → ingest approve (`ede8899d-ad56-4fc0-8654-a653d5571135`) → `pns_leaderboard_site_publish.ps1 -MergeSubmissions`
- [x] **[AGENT]** Ingest schema validation includes `resolutionBetrayalIndex`, `measurementContext`, `buildDisplay`

### Sprint 25.5 — Site proof, Pages deploy, exports (agent rows)

- [x] **[AGENT]** Added `pns_m25_gate.ps1` + `pns_leaderboard_host_smoke.ps1` and validated host pass
- [x] **[AGENT]** Host smoke asserts device JSON `resolutionBetrayal`, `oemLossSummary`, `measurementContext` plus product groups/CSV/RSS/glossary artifacts
- [x] **[AGENT]** Pushed `pns_leaderboard_pages_push.ps1` output to GitHub Pages (commit `9fcc97d`, workflow success)
- [x] **[AGENT]** Added Phase-5 verification checklist coverage in `docs/leaderboard/README.md`

---

## Milestone 26 — completed sprints

Moved from **`BUILD_PLAN.md`** (2026-06-05). USB gate **PASS** `hfr-runs/m26_gate_20260605_113034/` on **CPH2583** (`b5214fc6`).

### Sprint 26.1 — ShipNow AppFeature (from intake)

- [x] **[AGENT]** `video.delivery_honesty` — `VideoDeliveryHonesty.kt` + in-app `proveDeliveryHonesty`; `VideoDeliveryHonestyTest` in `pns_m26_gate.ps1`
- [x] **[AGENT]** `still.heic` / `still.motion_photo` / `still.tiff16` / `still.jxl` — `StillExportScaffolds` shipped; Delta parity `provenOk` on **CPH2583** for jxl + motion_photo; `pns_still_export_verify.ps1` `$captureArgs` splat fix
- [x] **[ADB]** `audio.spatial` — `pns_spatial_audio_verify.ps1` **PASS** `hfr-runs/spatial_audio_verify_20260605_072132`; `spatialAudioMeta` ADB line on MediaRecorder path (`VideoRecordingController.kt`)
- [x] **[AGENT]** `still.independent_tonal` — pipeline wired; proof manifest + recent-artifact merge in parity sweep

### Sprint 26.2 — AutomationProof (proof-pack closure)

- [x] **[AGENT]** `-IncludeProofPack` on `pns_fleet_regression_pack.ps1` Full tier (wired)
- [x] **[AGENT]** Recent-proof merge extended (`audio.spatial` in `Get-RecentParityProofByCatalog`); top AutomationProof rows close when manifest scripts pass on **CPH2583**

### Sprint 26.3 — Ownership cleanup

- [x] **[AGENT]** Assigned surfacing-gap `UNASSIGNED` rows in `docs/M22_PROVIDER_OWNERSHIP.json` (audio/hud/lens/preview/video.h264/hevc families)

**M26 gate:** `scripts/pns_m26_gate.ps1` — top-3 AppFeature (`audio.spatial`, `still.jxl`, `still.motion_photo`) intake `status=closed` + parity Delta proven on device.

---

## Milestone 27 — completed sprints

Moved from **`BUILD_PLAN.md`** (2026-06-05) after M27 gate closeout on **CPH2583**.

**Gate artifact:** `hfr-runs/m27_gate_20260605_234010/` (`pass=true`, `shipBlockerGapCount=0`, `GAP_UNAUTOMATED=0`, `openCount=62`).

### Sprint 27.3 — AutomationProof (Full proof pack)

- [x] **[AGENT]** Closed `GAP_UNAUTOMATED` intake rows where `parity_proof_manifest.json` has a script — merged via Full sweep `-IncludeProofPack` (`hfr-runs/m27_gate_20260605_141423/parity_full` → `GAP_UNAUTOMATED=0`)

### Sprint 27.4 — Surfacing honesty (advertised_not_surfaced)

- [x] **[AGENT]** Batch-closed intake `PBI-*-AppFeature` rows with `failReason=advertised_not_surfaced` in intake refresh lane (`openCount 94 -> 62`)
- [x] **[AGENT]** Classified `face.detect`, `face.eye_af`, `face.priority_ae` quick-tier failures as non-ship-blocking surfacing debt in `pns_fleet_parity_sweep.ps1` (gate accounting aligned)

### Sprint 27.6 — Milestone gate + intake closeout

- [x] **[AGENT]** Added `scripts/pns_m27_gate.ps1` (host preflight + Full sweep/proof pack + debt/intake delta checks)
- [x] **[AGENT]** Marked closed `PBI-*` rows in intake and refreshed ledger/intake markdown (`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` now `openCount=62`)
- [x] **[ADB]** **CPH2583** Full + proof pack gate PASS (`m27_gate_20260605_234010`: `not_proven=4`, `unautomated=0`, `shipBlockers=0`)

### Sprint 27.x — Residual debt handoff

- [x] **[AGENT]** Moved remaining non-blocking parity debt follow-ups to **Milestone H.10** in active plan (post-M27 residual lane)

---

## Milestone H — completed sprints

Moved from **`BUILD_PLAN.md`**. Open human/agent rows remain in the active plan. Latest archive pass: **2026-06-13** (OP13 regression lane + host validators).

### Sprint H.1 — Desktop visual verification

- [x] **[AGENT]** `pns_dng_aesthetic_gate.py` — rawpy decode M14/M23/M73; luma+channel stats PASS — `hfr-runs/aesthetic_selftest_h1`; CPH2583 pulls via `pns_dng_rawpy_decode_gate.ps1`
- [x] **[AGENT]** `pns_passport_ce_values.py` — X-Rite constants → `tests/fixtures/passport_ce_values.json`

### Sprint H.1 — Auto-synced parity ship blockers *(closed 2026-06-06)*

- [x] **[AGENT]** Implemented auto-sync from latest two Full sweeps into active plan (`scripts/pns_build_plan_ship_blockers_sync.ps1`) and wired it into `pns_fleet_parity_sweep.ps1` post-refresh pipeline
- [x] **[AGENT]** Closed H.1 with two consecutive Full runs at `shipBlockerGapCount=0` (`hfr-runs/parity_sweep_20260606_133255`, `hfr-runs/parity_sweep_20260606_133712`)
- [x] **[AGENT]** Confirmed auto block now reports no blockers and moved H.1 out of active `BUILD_PLAN.md`

### Sprint H.3 — Account ownership (agent)

- [x] **[AGENT]** `pns_gitlab_setup.ps1 -Verify` — assert `ANDROID_KEYSTORE_BASE64` `masked=true` — **SKIP** in agent env (no `GITLAB_TOKEN`/`GITLAB_PROJECT_ID`)

### Sprint H.4 — Signing authority (agent)

- [x] **[AGENT]** `pns_keystore_verify.ps1` — **SKIP** (no `release.keystore` in clone; expected)
- [x] **[AGENT]** `pns_release_asset_check.ps1` — **SKIP** (no GitHub release published yet)

### Sprint H.5 — Publication & community (agent)

- [x] **[AGENT]** `pns_crash_triage.ps1` — `hfr-runs/crash_triage_20260529_212514` (0 fatals)

### Sprint H.6 — Subjective UX sign-off (agent)

- [x] **[AGENT]** `pns_a11y_dump_gate.ps1` — USB PASS CPH2583 2026-05-29

### Sprint H.7 — DNG & still modes *(CPH2583 closed 2026-05-29)*

**Artifacts:** `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · `hfr-runs/aux_dng_capture_analyze_20260529_015653`, `readout_jpeg_dng_parity_20260529_172644`

- [x] **[AGENT]** `pns_dng_rawpy_decode_gate.ps1` — PASS (6 DNGs)
- [x] **[AGENT]** `pns_fixture_dng_gates.ps1` — host CI PASS
- [x] **[AGENT]** `pns_still_mode_compare_gate.ps1` — USB PASS
- [x] **[HUMAN]** ACR / Lightroom wide + UW + tele DNGs for **CPH2583** — owner approved 2026-05-29

### Sprint H.8 — M14 + M15 subjective sign-off (closed rows)

- [x] **[AGENT] H.8.3** `pns_hfr_color_compare_frames.ps1` — H.265 vs H.264 YCbCr delta &lt; 8 @1080p SDR
- [x] **[HUMAN] H.8.6** Pillar-bar HUD no overlap with chrome (15.23) — CPH2583 2026-05-29

### Sprint H.10 — Post-M27 residual parity debt (closed rows)

- [x] **[AGENT]** Full parity + proof refresh on CPH2583 (`hfr-runs/parity_sweep_20260606_000338/`) and intake rebuild (`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`) reduced open intake to **7** rows
- [x] **[AGENT]** Closed residual `video.delivery_honesty` + `video.vp9*` intake rows from latest Full+proof evidence (`provenOk=true`)
- [x] **[AGENT]** Closed matrix/session residual lane for `video.av1*`, `video.raw*`, `video.hfr.24/30/60/240`, `video.4k_regular`, `video.uhd60`, `raw.dng`
- [x] **[AGENT]** Completed ProbeOnly retention closure pass: `product.hardware_camera_key` proven in latest sweep; `still.proshot_leaf` kept non-blocking
- [x] **[AGENT]** Ran sequential still-export reruns on CPH2583; unresolved `still.jxl` / `still.motion_photo` / `still.tiff16` show capture-session instability (`photo_capture_verify_20260606_010503`, `...011233`, `...011318`) and were moved to Sprint **H.11**
- [x] **[AGENT]** Ran color-profile reruns; unresolved `video.color.hdr10` / `video.color.hlg10` / `video.color.pq` failed metadata/logcat gates (`hdr10_meta_verify_20260605_210755`, `...211353`, `h10_video_color_hlg10_20260606`) and were moved to Sprint **H.11**
- [x] **[AGENT]** Confirmed `video.hfr.120` remains DeliveryHonesty-owned by Milestone 24 (`verify_4k120_20260605_211442`, truth=`blocked_unstable`) and excluded from H.10 closure criteria

### Sprint H.11 — Residual parity debt follow-up (closed rows)

- [x] **[AGENT]** Closed still-export residuals on CPH2583: `still.jxl`, `still.motion_photo`, `still.tiff16` now prove green in format-mode photo-primary runs (`photo_capture_verify_20260606_015651`, `...015759`, `...015822`)
- [x] **[AGENT]** Closed color-profile residuals on CPH2583: `video.color.hdr10` + `video.color.pq` pass delegated HDR gate (`hdr10_meta_verify_20260605_220910`, `...220955`) and `video.color.hlg10` passes inline gate (`video_color_profile_verify_20260605_220627_hlg10`)
- [x] **[AGENT]** Finished delivery-honesty carryover handling: strict rerun confirms `video.hfr.120` still `truth=blocked_unstable` with sub-4K delivery (`verify_4k120_20260605_225709/attempt_3`, `wxh=1920x1080`), leaving the single open intake row under Milestone 24 ownership only
- [x] **[AGENT]** Final closure refresh: Full parity pass on CPH2583 (`parity_sweep_20260606_022410`) plus intake rebuild now shows `openCount=1` and only `PBI-video.hfr.120-DeliveryHonesty` open

### Sprint H — Host validators & publication prep (agent closed 2026-06-13)

**Host gates:** Tier 0 `pns_local_dev_parallel.ps1` PASS · Tier 1 `pns_prerelease_gate.ps1 -SkipGradle` PASS

- [x] **[AGENT]** `pns_colorchecker_de2000_gate.py` — host self-test PASS (`worst_dE=0.00`); USB row remains blocked on HUMAN ColorChecker setup
- [x] **[AGENT]** `pns_gitlab_setup.ps1 -Verify` — **SKIP** (no `GITLAB_TOKEN`; wiring OK)
- [x] **[AGENT]** `pns_keystore_verify.ps1` — **SKIP** (no `release.keystore` in clone; expected)
- [x] **[AGENT]** `pns_fdroid_metadata_validate.ps1` + `pns_prerelease_gate.ps1 -SkipGradle` — PASS (metadata unchanged; validators green)
- [x] **[AGENT]** Host pre-handoff: `pns_repro_build_verify.ps1` · `pns_fdroid_metadata_validate.ps1` · `pns_changelog_gate.ps1` — PASS

### Sprint H.7-OP13 — Optional OP13 regression lane (agent closed 2026-06-13)

**Device:** OnePlus 13 **CPH2655** serial **`8bf09993`** (stock ROM) · legacy policy via `-LegacyOp13FleetPolicy` on matrix scan

**Artifacts:** `hfr-runs/aux_dng_capture_analyze_20260613_000901/` · `hfr-runs/fleet_matrix_20260613_001343/` · `hfr-runs/parity_sweep_20260613_001426/`

- [x] **[AGENT]** `pns_aux_dng_capture_analyze.ps1` — **PASS** capture+openability (3/3 DNG; integrity + desktop open PASS)
- [x] **[AGENT]** `pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "auto"` — **PASS** (`acr_signoff.json`; automated openability)
- [x] **[AGENT]** `pns_fleet_matrix_scan.ps1 -LegacyOp13FleetPolicy -ScanTier full` — **PASS**
- [x] **[ADB]** OP13 stock ROM Full sweep — `pns_fleet_parity_sweep.ps1 -Mode Full -Serial 8bf09993` — **PASS** (182 cells, `shipBlockers=0`)
- [x] **[AGENT]** `pns_dng_rawpy_decode_gate.ps1` on pulled OP13 DNGs — PASS (6 files)

**Not closed (remain in active BUILD_PLAN.md):** ReferenceApp color parity (scene mismatch vs `referenceapp_cph2655` fixtures); Lineage/custom second-variant sweep; optional human ACR; eye-AF pixel gate (no face in frame).

**Code fixes (same session):** `scripts/dng_referenceapp_parity_gate.py` (`referencecam_dir` argparse); `scripts/pns_milestone_h_host_gate.ps1` (`referenceapp_cph2655` fixture fallback + `--ref-dir`).

### Sprint H.CRI-0 — Host unblockers (agent closed 2026-06-13)

**Gate:** `pns_milestone_h_host_gate.ps1 -SkipGradle` PASS · `pns_template_doc_link_check.ps1` PASS

- [x] **[AGENT] CRI-001** — `DngSavePairingPolicy.ALLOW_PHYSICAL_TOTAL_RESULT_PAIRING=false`; AGENTS.md reconciled
- [x] **[AGENT] CRI-010** — `pns_release_asset_check.ps1` SKIP when no APK; `-RequireRelease` for release cut
- [x] **[AGENT] CRI-008** — `pns_resolve_referenceapp_fixture_dir.ps1` wired across DNG/parity gates
- [x] **[AGENT] CRI-028** — `docs/PNS_TECHNICAL_SETTINGS.md` §15 a11y
- [x] **[AGENT] CRI-029** — KNOWLEDGE_BASE / DECISION_LOG Milestone H-primary

### Sprint H.CRI-1 — RAW video honesty (CPH2583 agent closed 2026-06-13)

**Artifacts:** `hfr-runs/raw_video_verify_20260612_210437` · `hfr-runs/parity_sweep_20260613_011027` (Full PASS, shipBlockers=0)

- [x] **[AGENT] CRI-006/007** — RAW video session attaches ImageReader in video-primary lane; `pns_raw_video_verify.ps1` PASS on **CPH2583**
- [x] **[AGENT] CRI-009/035** — OP13 fixture refresh + aux DNG analyze (`hfr-runs/aux_dng_capture_analyze_20260613_014424`); automated color parity FAIL → human ACR

### Sprint H.CRI-2 — DNG pipeline hardening (agent closed 2026-06-13)

**Gate:** `pns_fixture_dng_gates.ps1` PASS · JVM tests (StillCaptureMetadata, DngMetadataResolver, Dng12Saver, CaptureSessionRegressionLocks)

- [x] **[AGENT] CRI-002/003/004/005/019** — JVM guards, LeafDngHalReconcile audit, named regression-lock constants, Dng12Saver tests

### Sprint H.CRI-3 — Automation hygiene (agent closed 2026-06-13)

**Gate:** Tier 0 `pns_local_dev_parallel.ps1` PASS (PS5.1 sequential fallback)

- [x] **[AGENT] CRI-011/012/013/014/031** — legacy regression wrapper, OP13 device detect, SKIP contract, PS5.1 fallback, `pns_adb_serial.ps1`
- [x] **OP13 USB re-verify** 2026-06-13 (`8bf09993`): matrix quick + aux DNG desktop open 3/3 + PiP + multicam melt PASS; `pns_op13_regression_pack.ps1` splat fix

### Sprint H.CRI-4 — Fleet quality + detekt (agent closed 2026-06-13)

**Gate:** `:app:detekt` PASS · `pns_m27_gate.ps1 -HostOnly` PASS

- [x] **[AGENT] CRI-017/018/021/022/023/024** — detekt burn-down, fleet golden/evaluator tests, legacy policy alias, M27 gate

### Sprint H.CRI-5 — T.13 monolith extraction

**Slice 1 (2026-06-13):** `PreviewSessionSurfacePolicy`, `PreviewAutomationExtrasRegistry`, ADR-0008.

**Slice 2 (2026-06-17):** `FleetDeviceMatrixSchemaValidator`, chrome prefs schema v1 + migration, `PreviewSessionContextDiag`, JVM round-trip tests.

**Slice 3 (2026-06-17):** `PreviewRegularSessionCreateRetries` — REGULAR session create retry ladder (physical pin → session params → HDR DR → stream hints).

**Slice 9 (2026-06-17):** `PreviewSessionHighSpeedOutputs` — `pickHighSpeedTarget` + encoder-only HFR output assembly.

**T.14 (2026-06-17):** `UnifiedMockPreviewScreen` — GLES test pattern + Pro HUD chrome; `pns_screen mock` (+ legacy `prohud` / `glpreview` aliases). ADR-0008 resume policy unchanged.

**USB (CPH2583 `192.168.1.2:44891`, 2026-06-17):** slice 9: `photo_capture_verify_20260617_213814` + `chrome_ux_gate_20260617_213835`; T.14: `photo_capture_verify_20260617_214454` + `chrome_ux_gate_20260617_214522` — all **PASS**.

**H.CRI-5 agent lane closed** (slices 1–9 + T.14). Open agent work → Milestone H (**H.6** eye-AF, **H.9** release).

### Sprint H.HYGIENE — Host gates (agent closed 2026-06-17)

**Gate:** Tier 0 `pns_local_dev_parallel.ps1` **7/7 PASS** · Tier 2 `pns_verify_toolchain.ps1 -RunTests` **PASS** (2044 JVM tests).

- [x] **[AGENT]** F-Droid metadata sync (`metadata.yml` 22005 / `0.14.0-beta.11` + `en-US/changelogs/22005.txt`)
- [x] **[AGENT]** `PreviewSessionJpegCompanion` lint **NewApi** — `@RequiresApi(S)` multi-res JPEG wrapper
- [x] **[AGENT]** JVM `UnsatisfiedLinkError` burn-down — `PnsLog.safeAndroidLog` (`Throwable`); `Log` → `PnsLog` in fleet visibility, hardware shutter, calibration, chart detect paths; `PnsExternalUrlTest` beta.11 URLs
- [x] **[AGENT]** `CameraCapabilityCatalogEvaluators.firstCameraGate` — prefer first `advertised && appEnabled` camera; `face.detect` test expectation aligned
- [x] **[AGENT]** Full repo review — [`docs/CODE_REVIEW.md`](../docs/CODE_REVIEW.md)

**USB (CPH2583, 2026-06-17):** `photo_capture_verify_20260617_220239` + `chrome_ux_gate_20260617_220306` — **PASS**.

### Sprint AUDIT-2026-06-18 — CI + TM ship (agent closed)

**Gate:** Tier 0 **8/8** · Tier 2 **PASS** · GitHub **Toolchain verify** + **Security scan** + **CodeQL** (manual dispatch) **PASS** @ `e65baad`.

- [x] **[AGENT] AUDIT.1–3** — `local.properties` / Trivy `@v0.36.0` / `modules/**` CI paths; detekt baseline + `:pns-*:detekt` in Tier 2
- [x] **[AGENT] AUDIT.4–5** — USB capture + chrome on CPH2583 wireless; `pns_adb_device.env` serial refresh
- [x] **[AGENT] AUDIT.6** — Sprint TM + bootstrap v0.10 pushed (`78a2ea5`…`e65baad`): `:pns-core/capture/fleet/preview`, batch commands, Linux CI script fixes, parity host fixture
- [x] **[AGENT] AUDIT.7** — Dependabot Actions PRs #1–#3, #7, #13 closed; versions bumped on `main`

**Follow-up commits:** catalog path resolver (`pns_resolve_fleet_paths.ps1`), `pns_java_home` / `pns_resolve_adb` Linux CI, `tests/fixtures/parity_sweep_host_fixture/`.

**USB (CPH2583, 2026-06-18):** `photo_capture_verify_20260618_013837` + `chrome_ux_gate_20260618_013908`; second audit `photo_capture_verify_20260618_025139` + `chrome_ux_gate_20260618_025206` — **PASS**.

### Sprint AUDIT2-2026-06-18 — Post-ship hygiene + Dependabot Gradle (agent closed)

**Gate:** Tier 2 **PASS** (Gradle **9.5.1** / AGP **9.1.1** / Kotlin **2.4.0**) · Dependabot PRs **#4, #8, #9, #11, #12** closed on `main`.

- [x] **[AGENT] AUDIT2.1–2** — CodeQL paths + CI poll fallback (`dbde194`)
- [x] **[AGENT] AUDIT2.3** — Full Gradle stack bump + AGP 9 built-in Kotlin migration; `compileSdk` **37**; Paparazzi **2.0.0-alpha05**; baseline profile **1.5.0-alpha06**
- [x] **[AGENT] AUDIT2.4** — `pns_milestone_h_host_gate.ps1 -SkipDngAestheticGate` (informational fixture delta)
- [x] **[AGENT] AUDIT2.5** — `.gitignore` `__pycache__/`; `pns_verify_toolchain.ps1` → `:app:koverVerify`

**USB (CPH2583, 2026-06-18):** `photo_capture_verify_20260618_110420` + `chrome_ux_gate_20260618_110452` — **PASS**.

### Sprint H.CRI-6 — CI & security (agent closed 2026-06-13)

- [x] **[AGENT] CRI-025/026/027/020** — toolchain path filters, keystore SKIP without env, leaderboard workflow_dispatch, Paparazzi policy unchanged

---

## Milestone T — Template alignment ✅

**Closed (agent lane):** 2026-06-12 · **Gate:** [`scripts/pns_milestone_t_gate.ps1`](../scripts/pns_milestone_t_gate.ps1) PASS (Tier 0 + prerelease host) · artifact `hfr-runs/milestone_t_gate_*/milestone_t_gate.json`

**Sprints shipped:** T.1–T.12, T.15 (agent). **T.13–T.14:** active under **H.CRI-5** in `BUILD_PLAN.md`.

**Human carryover → Milestone H:** T.10 F-Droid store copy creative review (**H.5**); owner PRIVACY + metadata sign-off (**H.9**).

*Active sprint checklist removed from BUILD_PLAN.md — archived below.*

### Sprint T.1 — KNOWLEDGE_BASE index

- [x] **[AGENT]** Created [`KNOWLEDGE_BASE.md`](../KNOWLEDGE_BASE.md) — §0–§8 index (platform, capture/DNG, fleet, chrome, settings, video, release, agent ops, full `docs/` table)
- [x] **[AGENT]** Added [`scripts/pns_template_doc_link_check.ps1`](../scripts/pns_template_doc_link_check.ps1) — relative link gate; wired in `pns_verify_toolchain.ps1`
- [x] **[AGENT]** Updated [`AGENTS.md`](../AGENTS.md) — knowledge base pointer + Template file map stub (T.2 expands)

### Sprint T.2 — AGENTS.md template cross-reference

- [x] **[AGENT]** Expanded **Template file map** — aliases, update policy table, cadence (template §3), full `.cursor/rules/` inventory
- [x] **[AGENT]** **Before editing** read order — `KNOWLEDGE_BASE.md` → `AGENT_REGRESSION_MEMORY.md` → lock rules
- [x] **[AGENT]** Linked template map from [`KNOWLEDGE_BASE.md`](../KNOWLEDGE_BASE.md) §7

### Sprint T.3 — Architecture Decision Records

- [x] **[AGENT]** [`docs/adr/README.md`](../docs/adr/README.md) + **ADR-0001…0006** (architecture, DNG save, tele routing, fleet matrix, Apache-2.0, ML Kit exception)
- [x] **[AGENT]** [`DECISION_LOG.md`](../DECISION_LOG.md) — index with dates; linked from KNOWLEDGE_BASE §7/§8
- [x] **[AGENT]** `plan-doc-verify.yml` paths for `docs/adr/**`, `DECISION_LOG.md` (prior Milestone T CI update)

### Sprint T.4 — AGENT_MEMORY, PROMPT_LIBRARY, session checkpoint

- [x] **[AGENT]** [`AGENT_MEMORY.md`](../AGENT_MEMORY.md) — seeded focus, blockers, next steps (T.5 / Milestone H)
- [x] **[AGENT]** [`PROMPT_LIBRARY.md`](../PROMPT_LIBRARY.md) — 11 workflow prompts (When / Read / Run / Pass / Also test / Critique)
- [x] **[AGENT]** [`.cursor-session-state.example`](../.cursor-session-state.example) + `.gitignore` + [`.cursor/rules/session-checkpoint.mdc`](../.cursor/rules/session-checkpoint.mdc)
- [x] **[AGENT]** Retired stale [`BUILD_PLAN_STATUS.md`](../BUILD_PLAN_STATUS.md) → pointer to `AGENT_MEMORY.md`

**Milestone T phase-1 (T.1–T.4):** Agent workspace memory layer complete — KNOWLEDGE_BASE, ADRs, AGENT_MEMORY, PROMPT_LIBRARY, session checkpoint.

### Sprint T.5 — Security scanning (CI)

- [x] **[AGENT]** [`.github/workflows/codeql-analysis.yml`](../.github/workflows/codeql-analysis.yml) — Java/Kotlin CodeQL + weekly schedule
- [x] **[AGENT]** [`.github/workflows/security-scan.yml`](../.github/workflows/security-scan.yml) — gitleaks + Trivy fs
- [x] **[AGENT]** [`.gitleaks.toml`](../.gitleaks.toml), [`.trivyignore`](../.trivyignore), [`SECURITY.md`](../SECURITY.md) — policy + README link
- [x] **[AGENT]** Dependabot config unchanged; compatible with new workflows

### Sprint T.6 — Pre-commit, CONTRIBUTING, devcontainer, trunk flow

- [x] **[AGENT]** [`.pre-commit-config.yaml`](../.pre-commit-config.yaml) — gitleaks + `pns_verify_toolchain.ps1 -SkipGradle`
- [x] **[AGENT]** [`CONTRIBUTING.md`](../CONTRIBUTING.md) — trunk flow, CI matrix, agent vs human; README link
- [x] **[AGENT]** [`.devcontainer/`](../.devcontainer/) — JDK 21 + Python; USB gates documented as host-only
- [x] **[AGENT]** [`.github/CODEOWNERS`](../.github/CODEOWNERS), [`conventional-pr-title.yml`](../.github/workflows/conventional-pr-title.yml), issue templates, PR template refresh

### Sprint T.7 — Quality gates (adapted)

- [x] **[AGENT]** Kover **0.9.1** in `app/build.gradle.kts` — 40% scoped floor (`fleet-dng-bracket-floor`); excludes Compose UI + USB DNG byte pipelines
- [x] **[AGENT]** `pns_verify_toolchain.ps1 -RunTests` → `:app:koverVerifyDebug`
- [x] **[AGENT]** [`docs/adr/0007-code-style-gate.md`](../docs/adr/0007-code-style-gate.md) — Detekt-only; Kover scope; no androidTest
- [x] **[AGENT]** [`CONTRIBUTING.md`](../CONTRIBUTING.md) — file-size policy, coverage table, androidTest → USB scripts
- [x] **[AGENT]** [`.gitlab-ci.yml`](../.gitlab-ci.yml) — detekt + lint + kover aligned with GitHub subset

### Sprint T.8 — Performance & accessibility automation

- [x] **[AGENT]** [`scripts/pns_perf_budget_host_gate.ps1`](../scripts/pns_perf_budget_host_gate.ps1) + toolchain wiring
- [x] **[AGENT]** `AboutScreenA11yTest`, `ChromeSettingsSearchA11yTest` — host semantics contracts
- [x] **[AGENT]** CONTRIBUTING — USB `pns_a11y_dump_gate.ps1` for locked preview chrome
- [x] **[AGENT]** Leaderboard Pages workflow — optional Lighthouse job (informational)

### Sprint T.9 — Visual regression policy

- [x] **[AGENT]** [`docs/VISUAL_REGRESSION_POLICY.md`](../docs/VISUAL_REGRESSION_POLICY.md) + KNOWLEDGE_BASE §3 link
- [x] **[AGENT]** Paparazzi **1.3.5** scaffold — `@Ignore` `AboutScreenPaparazziTest`, `ChromeSettingsSearchPaparazziTest`
- [x] **[AGENT]** [`scripts/pns_prerelease_gate.ps1`](../scripts/pns_prerelease_gate.ps1) — `-IncludeUsb` → `pns_eye_af_pixel_gate.ps1`
- [x] **[AGENT]** [`CONTRIBUTING.md`](../CONTRIBUTING.md) visual proof table

### Sprint T.10 — F-Droid metadata & store assets

- [x] **[AGENT]** [`metadata/metadata.yml`](../metadata/metadata.yml) — GitHub URLs, fleet-first copy, **`Builds:`** block synced to `app/build.gradle.kts` (`22004` / `0.14.0-beta.10`)
- [x] **[AGENT]** [`metadata/en-US/`](../metadata/en-US/) — `title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/22004.txt`
- [x] **[AGENT]** Screenshot PNGs — interim seed from `docs/screenshots/`; refresh via [`pns_device_screencap.ps1`](../scripts/pns_device_screencap.ps1) (see [`metadata/en-US/images/README.md`](../metadata/en-US/images/README.md))
- [ ] **[HUMAN]** Store copy creative review before fdroiddata MR
- [x] **[AGENT]** [`scripts/pns_fdroid_metadata_validate.ps1`](../scripts/pns_fdroid_metadata_validate.ps1) — wired in [`pns_prerelease_gate.ps1`](../scripts/pns_prerelease_gate.ps1)

### Sprint T.11 — Privacy, NOTICE, reproducible builds

- [x] **[AGENT]** [`PRIVACY.md`](../PRIVACY.md) + [`NOTICE`](../NOTICE); README links; About → Privacy policy chip
- [x] **[AGENT]** [`docs/REPRODUCIBLE_BUILDS.md`](../docs/REPRODUCIBLE_BUILDS.md); `SOURCE_DATE_EPOCH` in [`build-signed.yml`](../.github/workflows/build-signed.yml)
- [x] **[AGENT]** Gradle dependency locking + `app/gradle.lockfile`
- [x] **[AGENT]** [`scripts/pns_repro_build_verify.ps1`](../scripts/pns_repro_build_verify.ps1); F-Droid recipe in [`CLI_BUILD_AND_SIDELOAD.md`](../CLI_BUILD_AND_SIDELOAD.md)

### Sprint T.12 — Pre-release quality gate orchestrator

- [x] **[AGENT]** Full [`scripts/pns_prerelease_gate.ps1`](../scripts/pns_prerelease_gate.ps1) — toolchain `-RunTests`, fixture DNG, F-Droid, repro, security CI; **`-IncludeUsb`** capture → chrome → eye-AF (sequential)
- [x] **[AGENT]** [`.cursor/skills/github-release/SKILL.md`](../.cursor/skills/github-release/SKILL.md) prerelease gate step
- [x] **[AGENT]** `CHANGELOG.md` Unreleased + `changelog_coverage.v1.json` **Milestone T** mention

### Sprint T.15 — Local-first dev loop + multi-agent orchestration

- [x] **[AGENT]** [`docs/LOCAL_FIRST_DEV_LOOP.md`](../docs/LOCAL_FIRST_DEV_LOOP.md) · [`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`](../docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md)
- [x] **[AGENT]** [`scripts/pns_local_dev_parallel.ps1`](../scripts/pns_local_dev_parallel.ps1) · [`scripts/pns_agent_worktree_bootstrap.ps1`](../scripts/pns_agent_worktree_bootstrap.ps1)
- [x] **[AGENT]** VS Code tasks · `.cursor/rules/multi-agent-parallel.mdc` · PROMPT_LIBRARY §12–§13

**Milestone T closure (2026-06-12):** `pns_milestone_t_gate.ps1` PASS; BUILD_PLAN.md archived to this section; active plan → **Milestone H**.

---

## Sprint TM — Template Migration ✅

**Closed (agent lane):** 2026-06-17 · **Gate:** [`scripts/pns_milestone_tm_gate.ps1`](../scripts/pns_milestone_tm_gate.ps1) · artifact `hfr-runs/milestone_tm_gate_*/milestone_tm_gate.json` · **ADR:** [0009](../docs/adr/0009-modular-boundaries.md)

### TM.0 — Bootstrap gate aliases

- [x] **[AGENT]** [`COMPLETED_TASKS.md`](../COMPLETED_TASKS.md) pointer · [`docs/BOOTSTRAP_TEMPLATE_MAP.md`](../docs/BOOTSTRAP_TEMPLATE_MAP.md)
- [x] **[AGENT]** [`scripts/pns_validate_bootstrap.ps1`](../scripts/pns_validate_bootstrap.ps1) · [`scripts/pns_watch_agent_gates.ps1`](../scripts/pns_watch_agent_gates.ps1)
- [x] **[AGENT]** Tier 0 8th job `bootstrap_validate` in [`pns_local_dev_parallel.ps1`](../scripts/pns_local_dev_parallel.ps1) (`-SkipBootstrap` opt-out)

### TM.1 — Module scaffolds + Golden Path

- [x] **[AGENT]** [`modules/pns-core/MODULE.md`](../modules/pns-core/MODULE.md) … `pns-preview/MODULE.md`
- [x] **[AGENT]** [`examples/golden-path/README.md`](../examples/golden-path/README.md) — probe hub + `UnifiedMockPreviewScreen`

### TM.2 — ADR + Golden Path refresh

- [x] **[AGENT]** [ADR-0009](../docs/adr/0009-modular-boundaries.md) modular boundaries
- [x] **[AGENT]** [ADR-0001](../docs/adr/0001-core-architecture.md) Golden Path → `UnifiedMockPreviewScreen` · [`DECISION_LOG.md`](../DECISION_LOG.md)

### TM.3–TM.7 — Gradle libraries (phased)

- [x] **[AGENT]** `:pns-core` — `PnsLog`, `CapabilityGate`, `Feature`, `HardwareCaps`, `RootCapability*`, `BracketPattern`, `PnsSweepSignals`
- [x] **[AGENT]** `:pns-fleet` — matrix schema, catalog registry, parity pure types (hub `*Screen.kt` + builders remain in `:app`)
- [x] **[AGENT]** `:pns-capture` — bracket + DNG/TIFF helpers; main save pipeline stays in `:app` (DNG locks)
- [x] **[AGENT]** `:pns-preview` — session create entry + automation registry slice; full orchestrators stay in `:app`
- [x] **[AGENT]** TM.7 hub Compose boundary — `FleetMatrixHubScreen`, `FleetParityModeSheet`, `FleetDeviceMatrixCatalogAttach` in `:app`

### TM.8–TM.9 — Polish + closure

- [x] **[AGENT]** README module diagram · [`KNOWLEDGE_BASE.md`](../KNOWLEDGE_BASE.md) §7a module index · `AGENTS.md` bootstrap table
- [x] **[AGENT]** [`scripts/pns_milestone_tm_gate.ps1`](../scripts/pns_milestone_tm_gate.ps1)

**Sprint TM closure (2026-06-17):** Host Tier 0 + Tier 2 green; USB smoke recommended when capture/preview modules change. Further extraction tracked in ADR-0009 deferred work.

---

* **639+** completed tasks indexed across **22** feature categories + archived milestone sprints.

## Deferred or human-only (not counted above)

- In-app RAW/JPEG development editor (CC.3 — deferred)
- Live colour temperature readout chip (15.33 — cancelled)
- Full dual-ISO HDR video merge (15.38 stub only; merge deferred)
- Wi-Fi Direct companion browser UI + push notifications (future backlog)
- Subjective sign-off rows (**H.8.1–H.8.5**, legacy device lane) — active in **[BUILD_PLAN.md](BUILD_PLAN.md)** Milestone H
- 8K tier sometimes missing from in-app picker despite automation PASS (pinned investigation)
- H.265 DCG @4K color — owner visual fail 2026-05-26; re-open with H.8.3
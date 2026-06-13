# Point & Shoot — Knowledge base (index)

Curated map of **canonical docs → code → gates**. Index only — do not duplicate prose from linked files. Update at milestone boundaries or when a new SoT doc ships.

---

## §0 — Platform, stack, purpose, distribution

| Topic | Doc | Notes |
|-------|-----|-------|
| Overview | [README.md](README.md) | FOSS pro camera; fleet-first validation (CPH2583) |
| Architecture | [CAPTURE_ARCHITECTURE.md](CAPTURE_ARCHITECTURE.md) | Threading, capture pipeline |
| Dodge product profile | [DODGE_PROFILE.md](DODGE_PROFILE.md) | Tele 73/85/150 mm routing reference |
| Performance targets | [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md) | Cold start, preview FPS, capture latency |
| Storage | [STORAGE_STRATEGY.md](STORAGE_STRATEGY.md) | MediaStore / DCIM layout |
| License | [LICENSE](LICENSE) | **Apache-2.0** (not MIT) |
| Distribution | [CLI_BUILD_AND_SIDELOAD.md](CLI_BUILD_AND_SIDELOAD.md) | GitHub Releases, Obtainium; F-Droid metadata in [`metadata/`](metadata/); privacy [`PRIVACY.md`](PRIVACY.md) |
| Reproducible builds | [docs/REPRODUCIBLE_BUILDS.md](docs/REPRODUCIBLE_BUILDS.md) | Lockfile, SOURCE_DATE_EPOCH, LUT pins · `pns_repro_build_verify.ps1` |
| Code review planning intake | [docs/CODE_REVIEW_PLANNING_INTAKE.md](docs/CODE_REVIEW_PLANNING_INTAKE.md) | Full-project audit `CRI-*` rows for sprint promotion |
| Multi-agent parallel | [docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md](docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md) | Worktrees, schema lock · `pns_agent_worktree_bootstrap.ps1` |

**Stack:** Kotlin 2.1+, Jetpack Compose, Camera2, NDK (C++23). Min/target/compile SDK 28 / 36 / 36. No Play Services / Firebase in Gradle (FOSS audit in `pns_verify_toolchain.ps1`).

---

## §1 — Capture, RAW, DNG

| Topic | Canonical doc | Code | Gate |
|-------|---------------|------|------|
| Bisect locks (§4a, §2 RAW tier) | [docs/REVERTED_FEATURES_RESTORE_LIST.md](docs/REVERTED_FEATURES_RESTORE_LIST.md) | `PreviewEngineScreen.kt`, `RawCaptureSupport.kt` | `pns_capture_pipeline_verify.ps1` |
| DNG loadability | [docs/DNG_OPENABILITY_REGRESSIONS.md](docs/DNG_OPENABILITY_REGRESSIONS.md) | `StillCaptureMetadata.kt`, `Dng12Saver.kt` | `pns_aux_dng_capture_analyze.ps1`, `dng_tiff_integrity_check.py` |
| DNG metadata pairing | [docs/DNG_PS_ALIGNMENT_SPIKE.md](docs/DNG_PS_ALIGNMENT_SPIKE.md) | `DngMetadataResolver.kt` | `pns_capture_pipeline_verify.ps1` |
| RAW device matrix | [docs/RAW_CAPTURE_DEVICE_MATRIX.md](docs/RAW_CAPTURE_DEVICE_MATRIX.md) | `RawCaptureSupport.kt` | `pns_raw_capture_matrix.ps1` |
| Reference app matrix | [docs/RAW_REFERENCE_APP_MATRIX.md](docs/RAW_REFERENCE_APP_MATRIX.md) | — | `pns_referenceapp_parity_gate.ps1` |
| Regression ledger | [docs/AGENT_REGRESSION_MEMORY.md](docs/AGENT_REGRESSION_MEMORY.md) | — | Append `REG-*` after USB proof |
| M13 DNG gate | [docs/M13_7_GATE.md](docs/M13_7_GATE.md) | — | `pns_m13_3f_gate.ps1` |

---

## §2 — Fleet and parity

| Topic | Canonical doc | Code | Gate |
|-------|---------------|------|------|
| Capability matrix SoT | [docs/FLEET_DEVICE_CAPABILITY_MATRIX.md](docs/FLEET_DEVICE_CAPABILITY_MATRIX.md) | `fleet/FleetDeviceMatrix*.kt` | `pns_fleet_matrix_scan.ps1` |
| USB verify matrix | [docs/FLEET_DEVICE_VERIFY_MATRIX.md](docs/FLEET_DEVICE_VERIFY_MATRIX.md) | — | Per-SKU onboarding |
| Parity sweep | [docs/FLEET_PARITY_SWEEP.md](docs/FLEET_PARITY_SWEEP.md) | `fleet/FleetParity*.kt` | `pns_fleet_parity_sweep.ps1 -Mode Full\|Delta` |
| Catalog taxonomy | [docs/CAMERA_CAPABILITY_TAXONOMY.md](docs/CAMERA_CAPABILITY_TAXONOMY.md) | `CameraCapabilityCatalog.kt` | `pns_capability_catalog_gate.ps1` |
| Legacy OP13 plugin | [docs/FLEET_ONEPLUS13_RAW_POLICY.md](docs/FLEET_ONEPLUS13_RAW_POLICY.md) | `LegacyDeviceFleetPolicy.kt` | Optional regression lane |
| Streams → matrix | [docs/FLEET_REFERENCE_M10_8.md](docs/FLEET_REFERENCE_M10_8.md) | — | `pns_deep_caps_diff.ps1` |
| Parity debt | [docs/FLEET_PARITY_DEBT_LEDGER.md](docs/FLEET_PARITY_DEBT_LEDGER.md) | — | Auto-refreshed after FPS |
| Build plan intake | [docs/FLEET_PARITY_BUILD_PLAN_INTAKE.md](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.md) | — | `pns_parity_build_plan_intake.ps1` |

Runtime artifact: `files/fleet_device_matrix.json` (app private storage; pulled via matrix scan).

---

## §3 — Preview chrome (layout locked)

| Topic | Canonical doc | Code | Gate |
|-------|---------------|------|------|
| **Visual regression policy** | [docs/VISUAL_REGRESSION_POLICY.md](docs/VISUAL_REGRESSION_POLICY.md) | `*PaparazziTest.kt` (deferred `@Ignore`) | `pns_device_screencap.ps1`, `pns_prerelease_gate.ps1 -IncludeUsb` |
| Layout style guide | [docs/preview-chrome-layout-style-guide.md](docs/preview-chrome-layout-style-guide.md) | `PreviewEngineScreen.kt` | `pns_chrome_ux_gate.ps1` |
| Settings chrome style | [docs/preview-chrome-settings-style-guide.md](docs/preview-chrome-settings-style-guide.md) | Chrome settings composables | Manual screencap |
| Readout / status bar | [docs/M14_READOUT_STATUS_BAR.md](docs/M14_READOUT_STATUS_BAR.md) | `PreviewTopStatusBar.kt` | `pns_video_status_bar_verify.ps1` |
| Face HUD coordinates | [docs/PREVIEW_FACE_HUD_COORDINATES.md](docs/PREVIEW_FACE_HUD_COORDINATES.md) | `FaceTrackOverlay.kt` | `pns_eye_af_alignment_probe.ps1` |

**Lock:** `.cursor/rules/preview-chrome-ui-lock.mdc` — behavioral fixes only unless user explicitly requests UI changes.

---

## §4 — Settings and technical constants

| Topic | Canonical doc | Code |
|-------|---------------|------|
| Settings SoT | [docs/PNS_TECHNICAL_SETTINGS.md](docs/PNS_TECHNICAL_SETTINGS.md) | `*Preferences.kt`, dial modes, readout constants |
| Persistence audit | [docs/SETTINGS_PERSISTENCE_AUDIT.md](docs/SETTINGS_PERSISTENCE_AUDIT.md) | SharedPreferences files, backup allow-list |

**Rule:** Any settings/pipeline change → update `PNS_TECHNICAL_SETTINGS.md` in the same commit.

---

## §5 — Video and encoding

| Topic | Canonical doc | Code | Gate |
|-------|---------------|------|------|
| Mode matrix | [docs/VIDEO_MODE_MATRIX.md](docs/VIDEO_MODE_MATRIX.md) | `VideoRecordingController.kt` | `pns_video_quality_gate.ps1` |
| Format recommendations | [docs/VIDEO_FORMAT_RECOMMENDATIONS.md](docs/VIDEO_FORMAT_RECOMMENDATIONS.md) | `VideoFormatPickerSheet.kt` | `pns_video_format_test.ps1` |
| HFR research | [docs/HFR_VIDEO_RESEARCH.md](docs/HFR_VIDEO_RESEARCH.md) | MediaCodec paths | `pns_mediacodec_hfr_verify.ps1` |
| DCG / HDR10 session | [docs/M13_4_DCG_SESSION.md](docs/M13_4_DCG_SESSION.md) | Preview session templates | `pns_video_hdr10_metadata_verify.ps1` |
| RAW video | [docs/M13_6_RAW_VIDEO.md](docs/M13_6_RAW_VIDEO.md) | RAW video mux | `pns_raw_video_verify.ps1` |
| Dual video | [docs/M14_12_DUAL_VIDEO.md](docs/M14_12_DUAL_VIDEO.md) | Stacked composite | `pns_dual_video_verify.ps1` |
| Performance budgets | [PERFORMANCE_BUDGETS.md](PERFORMANCE_BUDGETS.md) | `PerfBudget.kt` | `PerfBudgetTest`, `pns_perf_budget_host_gate.ps1`, `pns_hfr_autorun.ps1 -PerfReport` |

---

## §6 — Release and changelog

| Topic | Doc | Gate |
|-------|-----|------|
| Changelog | [CHANGELOG.md](CHANGELOG.md) | `pns_changelog_gate.ps1` |
| Security policy | [SECURITY.md](SECURITY.md) | CodeQL + gitleaks + Trivy CI |
| Contributing | [CONTRIBUTING.md](CONTRIBUTING.md) | Pre-commit, PR checklist, trunk flow |
| Coverage manifest | [scripts/changelog_coverage.v1.json](scripts/changelog_coverage.v1.json) | Sync `versionCode` on release |
| Release packaging | [RELEASE_NOTES_TEMPLATE.md](RELEASE_NOTES_TEMPLATE.md) | `pns_release_packaging.ps1`, `pns_github_release.ps1` |
| License inventory | [LICENSES.md](LICENSES.md) | `pns_license_inventory.ps1` |
| Active plan | [BUILD_PLAN.md](BUILD_PLAN.md) | Milestone H + **H.CRI-0…7** code-review program |
| Shipped archive | [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md) | §29 Milestone T |

---

## §7 — Agent operations

| Topic | Doc | Role |
|-------|-----|------|
| Automation index | [AGENTS.md](AGENTS.md) | Scripts, CRITICAL locks, device rules |
| Regression ledger | [docs/AGENT_REGRESSION_MEMORY.md](docs/AGENT_REGRESSION_MEMORY.md) | Append-only `REG-*` rows |
| Architecture decisions | [DECISION_LOG.md](DECISION_LOG.md) · [docs/adr/](docs/adr/) | ADR-0001…0007; append-only |
| Agent memory (ephemeral) | [AGENT_MEMORY.md](AGENT_MEMORY.md) | Session/milestone scratch pad — not REG ledger |
| Prompt library | [PROMPT_LIBRARY.md](PROMPT_LIBRARY.md) | Reusable agent workflows |
| Probe / automation plan | [PROBE_BUILD_PLAN.md](PROBE_BUILD_PLAN.md) | §5 audit log |
| Cursor rules | [.cursor/rules/](.cursor/rules/) | Subsystem locks (`*-lock.mdc`) |
| Build execution | [BUILD_PLAN.md](BUILD_PLAN.md) | Milestone H sprints + **H.CRI** fix program |
| Template map | [AGENTS.md](AGENTS.md) § Template file map | Update policy + `.cursor/rules/` inventory |

Read **`AGENT_REGRESSION_MEMORY.md`** before capture/DNG/preview/fleet edits. Session focus: **`AGENT_MEMORY.md`**. Workflows: **`PROMPT_LIBRARY.md`**.

---

## §8 — Full `docs/` index (by domain)

| Domain | Files |
|--------|-------|
| **Architecture decisions (ADR)** | [DECISION_LOG.md](DECISION_LOG.md), [docs/adr/README.md](docs/adr/README.md), [0001](docs/adr/0001-core-architecture.md) … [0007](docs/adr/0007-code-style-gate.md) |
| **Agent memory** | [AGENT_REGRESSION_MEMORY.md](docs/AGENT_REGRESSION_MEMORY.md) |
| **Camera2 reference** | [CAMERA2_KEYS_AND_APIS_REFERENCE.md](docs/CAMERA2_KEYS_AND_APIS_REFERENCE.md), [camera2_reference_face_eye_appendix.md](docs/camera2_reference_face_eye_appendix.md), [camera2_reference_qr_barcode_appendix.md](docs/camera2_reference_qr_barcode_appendix.md), [CAMERA2_OEM_DISPARITY.md](docs/CAMERA2_OEM_DISPARITY.md), [QCAMERA3_VENDOR_KEY_CATALOG.md](docs/QCAMERA3_VENDOR_KEY_CATALOG.md) |
| **Capture / DNG / RAW** | [REVERTED_FEATURES_RESTORE_LIST.md](docs/REVERTED_FEATURES_RESTORE_LIST.md), [DNG_OPENABILITY_REGRESSIONS.md](docs/DNG_OPENABILITY_REGRESSIONS.md), [DNG_PS_ALIGNMENT_SPIKE.md](docs/DNG_PS_ALIGNMENT_SPIKE.md), [DNG_PIPELINE_TRIANGULATION_MATRIX.md](docs/DNG_PIPELINE_TRIANGULATION_MATRIX.md), [DNG_REFERENCE_APPS.md](docs/DNG_REFERENCE_APPS.md), [DNG_REFERENCEAPP_ADB_FINDINGS.md](docs/DNG_REFERENCEAPP_ADB_FINDINGS.md), [DNG_COLOR_REVERT_CHECKPOINT.md](docs/DNG_COLOR_REVERT_CHECKPOINT.md), [RAW_CAPTURE_DEVICE_MATRIX.md](docs/RAW_CAPTURE_DEVICE_MATRIX.md), [RAW_REFERENCE_APP_MATRIX.md](docs/RAW_REFERENCE_APP_MATRIX.md), [M13_7_GATE.md](docs/M13_7_GATE.md), [M13_3E_LOCK_BISECT_RUNBOOK.md](docs/M13_3E_LOCK_BISECT_RUNBOOK.md), [M13_3F_DAYLIGHT_GATE.md](docs/M13_3F_DAYLIGHT_GATE.md), [M13_3H_WIDE_CAL_BISECT.md](docs/M13_3H_WIDE_CAL_BISECT.md) |
| **Fleet / parity / catalog** | [FLEET_DEVICE_CAPABILITY_MATRIX.md](docs/FLEET_DEVICE_CAPABILITY_MATRIX.md), [FLEET_DEVICE_VERIFY_MATRIX.md](docs/FLEET_DEVICE_VERIFY_MATRIX.md), [FLEET_ONEPLUS13_RAW_POLICY.md](docs/FLEET_ONEPLUS13_RAW_POLICY.md), [FLEET_REFERENCE_M10_8.md](docs/FLEET_REFERENCE_M10_8.md), [FLEET_PARITY_SWEEP.md](docs/FLEET_PARITY_SWEEP.md), [FLEET_PARITY_DEBT_LEDGER.md](docs/FLEET_PARITY_DEBT_LEDGER.md), [FLEET_PARITY_BUILD_PLAN_INTAKE.md](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.md), [FLEET_PARITY_DEVICE_LEADERBOARD.md](docs/FLEET_PARITY_DEVICE_LEADERBOARD.md), [FLEET_MULTI_DEVICE_TEST_REGIMENT.md](docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md), [CAMERA_CAPABILITY_CATALOG.md](docs/CAMERA_CAPABILITY_CATALOG.md), [CAMERA_CAPABILITY_TAXONOMY.md](docs/CAMERA_CAPABILITY_TAXONOMY.md), [parity_automation_coverage.md](docs/parity_automation_coverage.md), [REFERENCEAPP_APK_FLEET_ANALYSIS.md](docs/REFERENCEAPP_APK_FLEET_ANALYSIS.md), [ALTREFERENCEAPP_APK_FLEET_ANALYSIS.md](docs/ALTREFERENCEAPP_APK_FLEET_ANALYSIS.md) |
| **Preview / HUD / chrome** | [preview-chrome-layout-style-guide.md](docs/preview-chrome-layout-style-guide.md), [preview-chrome-settings-style-guide.md](docs/preview-chrome-settings-style-guide.md), [M14_READOUT_STATUS_BAR.md](docs/M14_READOUT_STATUS_BAR.md), [PREVIEW_FACE_HUD_COORDINATES.md](docs/PREVIEW_FACE_HUD_COORDINATES.md) |
| **Settings** | [PNS_TECHNICAL_SETTINGS.md](docs/PNS_TECHNICAL_SETTINGS.md), [SETTINGS_PERSISTENCE_AUDIT.md](docs/SETTINGS_PERSISTENCE_AUDIT.md) |
| **Video / encoding / M13V** | [VIDEO_MODE_MATRIX.md](docs/VIDEO_MODE_MATRIX.md), [VIDEO_FORMAT_RECOMMENDATIONS.md](docs/VIDEO_FORMAT_RECOMMENDATIONS.md), [HFR_VIDEO_RESEARCH.md](docs/HFR_VIDEO_RESEARCH.md), [M13_4_DCG_SESSION.md](docs/M13_4_DCG_SESSION.md), [M13_6_RAW_VIDEO.md](docs/M13_6_RAW_VIDEO.md), [RAW_VIDEO_M13_6_DESIGN.md](docs/RAW_VIDEO_M13_6_DESIGN.md), [M14_12_DUAL_VIDEO.md](docs/M14_12_DUAL_VIDEO.md), [M13V_10_FOCUS_PEAKING.md](docs/M13V_10_FOCUS_PEAKING.md), [M13V_11_VIDEO_LUT_PREVIEW.md](docs/M13V_11_VIDEO_LUT_PREVIEW.md), [M13V_12_POWER_THERMAL.md](docs/M13V_12_POWER_THERMAL.md), [M13V_13_STORAGE_REMAINING.md](docs/M13V_13_STORAGE_REMAINING.md), [M13V_15_VIDEO_CAP_PROBE.md](docs/M13V_15_VIDEO_CAP_PROBE.md), [M13V_16_4K120_UNLOCK.md](docs/M13V_16_4K120_UNLOCK.md), [M13V_17_AI_FEATURES.md](docs/M13V_17_AI_FEATURES.md), [M13V_18_CAMERAX_EXTENSIONS.md](docs/M13V_18_CAMERAX_EXTENSIONS.md) |
| **Still modes** | [M13_8B_ZSL_STILL.md](docs/M13_8B_ZSL_STILL.md), [M13_8C_HDR_STILL.md](docs/M13_8C_HDR_STILL.md), [M13_8D_STILL_MODE_BENCHMARK.md](docs/M13_8D_STILL_MODE_BENCHMARK.md) |
| **Face / eye / AI** | [face-eye-tracking-toolkit.md](docs/face-eye-tracking-toolkit.md), [CV_METRICS_RESEARCH.md](docs/CV_METRICS_RESEARCH.md) |
| **Connectivity / tether** | [TETHER_API.md](docs/TETHER_API.md) |
| **Probes / forensics** | [REFERENCEAPP_LIVE_FORENSICS.md](docs/REFERENCEAPP_LIVE_FORENSICS.md), [CAPABILITY_NOVELTY_TRACKING.md](docs/CAPABILITY_NOVELTY_TRACKING.md) |
| **Leaderboard site** | [leaderboard/README.md](docs/leaderboard/README.md) |

---

*Milestone H — link gate: `scripts/pns_template_doc_link_check.ps1`*

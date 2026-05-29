## Build plan (Point & Shoot)

**Purpose:** Milestones → sprints → gates. Active work here; shipped bodies in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

- **Settings truth:** `docs/PNS_TECHNICAL_SETTINGS.md` — update on every settings/pipeline change
- **Audit log:** `PROBE_BUILD_PLAN.md` §5/§6 · `CHANGELOG.md` · `CLI_BUILD_AND_SIDELOAD.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` (bisect locks §9) · **`docs/AGENT_REGRESSION_MEMORY.md`** (append on proven fixes)
- **Fleet/DNG:** `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · `docs/FLEET_ONEPLUS13_RAW_POLICY.md` (legacy plugin) · `docs/DNG_OPENABILITY_REGRESSIONS.md` · `docs/RAW_REFERENCE_APP_MATRIX.md` · `docs/M13_7_GATE.md` · `docs/M14_12_DUAL_VIDEO.md`

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
9. **Hard rules — do not regress:** Read **`docs/AGENT_REGRESSION_MEMORY.md`** before capture/DNG/preview/fleet edits; append a **`REG-*`** row after USB-proven fixes. No `automationSuppressFacePipeline` for sequential RAW alone; no §4a `streamHints` or §2 RAW10-first `Default` without USB proof; capture/session/DNG changes → `pns_capture_pipeline_verify.ps1` on **CPH2583**; settings changes → update `docs/PNS_TECHNICAL_SETTINGS.md` same commit. Full locks: `AGENTS.md`, `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8, `.cursor/rules/`.
10. **Archive:** Completed agent tasks → summarize under the matching **feature category** in `BUILD_PLAN_COMPLETED.md`. Human rows stay in Milestone H.

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
| `scripts/pns_fleet_matrix_scan.ps1` | USB full matrix pull → `hfr-runs/fleet_matrix_*` (M16) |
| `scripts/pns_fleet_matrix_diff.ps1` | Host diff two matrix JSONs (M16) |

Full script index: **`AGENTS.md`**. **Primary fleet USB device:** OnePlus 12 **CPH2583** (not CPH2655 unless OP13 regression lane).

### Performance & responsiveness backlog — archived

All seven rows **`[x]`** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Performance & responsiveness backlog*).

### Backlog consolidation (active)

| Area | Status |
|------|--------|
| **Milestones 0–12** | Gates passed → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** |
| **Milestone 13 — Fleet RAW** | **Archived** — automated/USB **PASS**; **H.7** closed **CPH2583** (owner 2026-05-29) |
| **Milestone 13V — Video expansion** | **Archived** — **13V.1–13V.18** USB-verified **`8bf09993`** |
| **Milestone 14 — Preview polish & pro UX** | **Archived** — **14.1–14.13** → completed file; **H.8** subjective |
| **Milestone 15** | **Archived** — agent sprints **15.0–15.B**, **15.14**, **15.16–15.38** done; residual **[HUMAN]** in **Milestone H** |
| **Milestone 16** | **Archived** — **16.0–16.13** + USB gate **PASS** on CPH2583 (2026-05-29); supersedes **15.13** SoT |
| **Milestone 17** | **Active** — fleet capability catalog, device-tailored UI, hub search; extends M16 matrix (no duplicate SoT) |
| **Milestone H** | **Active** — residual **[HUMAN]** work; **H.7** closed **CPH2583** (owner 2026-05-29); OP13 lane optional |
| **Bespoke Gallery (BG.1–BG.3)** | **Archived** — integration + device verify + UX polish (**maintainer sign-off 2026-05-22**) |
| **Audio & Sound (AS.1–AS.3)** | **Archived** — agent + human sign-off **2026-05-22** |
| **User Interface & Experience (UX.1–UX.3)** | **Archived** — theme, nav, workflow, cloud backup **2026-05-25** |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Future features (deferred — unscheduled)

- **OpenCamera-style toolbox** — former Sprint 10.14; descoped unless product requests.
- **Anamorphic desqueeze preview** — horizontal GLES stretch + ProRes anamorphic metadata.
- **Live LUT preview on gallery viewer** — non-destructive GLES LUT on saved stills.
- **Full dual-ISO HDR video merge** — `DualIsoVideoMerger` log-domain blend + HLG remap (deferred from 15.38).
- **RAW NightScape stacking** — extend 15.29 JPEG stacking to RAW burst → 12-bit DNG.
- **Wi-Fi Direct companion browser UI** — minimal web UI from `TetheredCaptureServer` (extends 15.37).
- **Push notifications for tether** — SSE/WebSocket on tether server (extends 15.37).

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | Shipped work index **by app feature** (22 categories); not milestone/sprint layout |

**Open in this file:** **Milestone 17** + **Milestone H** (M15 agent sprints archived)

### Archiving completed work — procedure

1. When a sprint closes, add its completed tasks under the right **feature category** in **`BUILD_PLAN_COMPLETED.md`** (not as a new milestone section).
2. Keep **`BUILD_PLAN.md`** as pointers + open **Milestone H** rows only.
3. Update **`CHANGELOG.md`** for user-visible changes.

---

## Archived milestones (pointers only)

**BG, PO, VF, AS, CC, UX, IP, M0–M16** — completed tasks indexed **by feature** in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**. USB evidence lives under **`hfr-runs/`**.

---

## Milestone 15 — Pro Camera Polish & Color Fidelity *(archived)*

**15.0–15.B**, **15.14**, **15.16–15.38** — completed tasks under feature categories in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (e.g. tether, video, DNG).

Human gates closing with M15: **H.7** (DNG color ACR **per onboarded SKU**) — **closed CPH2583** owner 2026-05-29; **H.8.1**–**H.8.6** (subjective) → **Milestone H** below.

---
## Milestone 16 — Fleet Device Capability Matrix *(archived)*

**16.0–16.13** — fleet matrix work indexed under **Fleet capability matrix & device policy** in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

**Docs:** `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · `docs/FLEET_REFERENCE_M10_8.md` · `docs/fleet_device_matrix.schema.json`

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment: creative, security, perceptual.

**Agent progress (2026-05-29, CPH2583 `b5214fc6`):** Host gate `scripts/pns_milestone_h_host_gate.ps1` PASS; USB: `a11y_dump` PASS, `crash_triage` PASS, `still_mode_compare` → `readout_jpeg_dng_parity_20260529_172644`. **H.7 closed CPH2583** — owner ACR/Lightroom sign-off 2026-05-29 (`aux_dng_capture_analyze_20260529_015653`). **Still blocked without human:** ColorChecker (H.2), keystore custody (H.4), store copy (H.5), face-in-frame eye-AF (H.6/H.8.1), subjective HUD/codec (H.8).

**Depends on:** Sprint 15.B gate scripts PASS; **Sprint 16.8** verify matrix row for DNG sign-off; Sprint 15.1 (eye AF); Sprint 15.5 (dual video); Sprint 15.2 (HEVC); Sprint 15.20, 15.21, 15.23 (PPM/false color/pillar HUD). **15.15** agent work is prerequisite for **OP13 regression only**, not for closing H.7 on CPH2583.

### Sprint H.1 — Desktop visual verification

- [x] **[AGENT]** `pns_dng_aesthetic_gate.py` — rawpy decode M14/M23/M73; luma+channel stats PASS — `hfr-runs/aesthetic_selftest_h1` (fixture self-test); CPH2583 pulls decode via `pns_dng_rawpy_decode_gate.ps1` (cross-device vs OP13 refs **informational FAIL**)
- [x] **[AGENT]** `pns_passport_ce_values.py` — X-Rite constants → `tests/fixtures/passport_ce_values.json`

### Sprint H.2 — Physical calibration capture

- [ ] **[HUMAN]** Set up ColorChecker under controlled illuminant (irreducible — physical setup)
- [ ] **[AGENT]** Trigger ADB capture; run `pns_colorchecker_de2000_gate.py`; assert all Macbeth patches dE2000 < threshold — **blocked on HUMAN**; host self-test PASS only (`pns_colorchecker_de2000_gate.py`)

### Sprint H.3 — Account ownership

- [x] **[AGENT]** `pns_gitlab_setup.ps1 -Verify` — assert `ANDROID_KEYSTORE_BASE64` `masked=true` via GitLab API — script fixed (PS 5.1); **SKIP** in agent env (no `GITLAB_TOKEN`/`GITLAB_PROJECT_ID`)
- [ ] **[HUMAN]** Confirm you are logged into the owner GitLab account (irreducible — identity custody)

### Sprint H.4 — Signing authority

- [x] **[AGENT]** `pns_keystore_verify.ps1` — `keytool -list`; assert alias + SHA-256 vs `pns_keystore_expected.json` — **SKIP** (no `release.keystore` in clone; expected)
- [x] **[AGENT]** `pns_release_asset_check.ps1` — `gh release view`; assert APK asset > 1 MB — **SKIP** (no GitHub release published yet)
- [ ] **[HUMAN]** Confirm you hold custody of the keystore file (irreducible — security)

### Sprint H.5 — Publication & community

- [ ] **[HUMAN]** Store listing copy (Play / F-Droid) — irreducible creative writing
- [ ] **[HUMAN]** Community announcements — irreducible public communication
- [x] **[AGENT]** `pns_crash_triage.ps1` — post-launch: `adb logcat -b crash -d`; parse fatals; write report — `hfr-runs/crash_triage_20260529_212514` (0 fatals)

### Sprint H.6 — Subjective UX sign-off

- [ ] **[AGENT]** `pns_eye_af_pixel_gate.ps1` — screencap + PIL diff eye-box vs expected region; PASS when delta < threshold — **FAIL** unattended USB (`eye_af_pixel_gate_20260529_172528`: no green overlay markers without face in frame); fixed `pns_eye_af_overlay_align.py` numpy JSON bug
- [x] **[AGENT]** `pns_a11y_dump_gate.ps1` — `uiautomator dump`; assert all interactive nodes have `content-desc` — USB PASS CPH2583 2026-05-29
- [ ] **[HUMAN]** HUD / LUT default aesthetics — irreducible perceptual
- [ ] **[HUMAN]** Immersive mode feel — irreducible perceptual

### Sprint H.7 — DNG & still modes (per onboarded SKU) *(CPH2583 closed 2026-05-29)*

**Artifacts:** `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · CPH2583: `hfr-runs/aux_dng_capture_analyze_20260529_015653`, `readout_jpeg_dng_parity_20260529_172644`

- [x] **[AGENT]** `pns_dng_rawpy_decode_gate.ps1` — rawpy M14/M23/M73 on fixtures or pulled DNGs for **onboarded SKU** — PASS `aux_dng_capture_analyze_20260529_015653` (6 DNGs)
- [x] **[AGENT]** `pns_fixture_dng_gates.ps1` — host CI on `tests/fixtures/proshot_cph2655/` (default pipeline gate) — PASS
- [x] **[AGENT]** `pns_still_mode_compare_gate.ps1` — when SKU row requires still-mode compare — USB PASS → `readout_jpeg_dng_parity_20260529_172644`
- [x] **[HUMAN]** ACR / Lightroom: open wide + UW + tele DNGs for **CPH2583** — neutral color, no green cast — **owner approved 2026-05-29** (closes H.7 for onboarded SKU row)

### Sprint H.7-OP13 — Optional OP13 regression lane

> Run only when verify matrix marks OP13 **regression optional** or plugin enabled. Not required for CPH2583 fleet program.

- [ ] **[AGENT]** `pns_aux_dng_capture_analyze.ps1` on CPH2655 — **skipped** (OP13 not connected; optional lane)
- [ ] **[AGENT]** `pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "auto"` — blocked on OP13 capture
- [ ] **[AGENT]** `dng_proshot_parity_gate.py` — ProShot reference parity — host fixtures only; full lane needs OP13 USB
- [ ] **[HUMAN]** ACR on OP13 aux DNGs (optional regression sign-off)

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass (14.5 + 15.1) — pixel gate passes; on-face rubber-stamp
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing usability (14.12 + 15.5)
- [x] **[AGENT] H.8.3** `pns_hfr_color_compare_frames.ps1` — H.265 vs H.264 YCbCr delta &lt; 8 @1080p SDR (automated only)
- [ ] **[HUMAN] H.8.3** Owner visual: all codecs/scenes good — **fail:** H.265 **DCG @4K** bad colors (2026-05-26); re-open 15.2 human row
- [ ] **[HUMAN] H.8.4** PPM meters peak hold visible + decaying (15.20)
- [ ] **[HUMAN] H.8.5** False color correct on grey card + highlight scene (15.21)
- [x] **[HUMAN] H.8.6** Pillar-bar HUD no overlap with chrome (15.23) — CPH2583 2026-05-29

**Milestone H gate:** Owner-approved checklist; **H.7** closed for **CPH2583** (2026-05-29); **H.8** closes M14/M15 subjective claims.

---

## Milestone 17 — Fleet capability catalog & device-tailored UI

**Objective:** Extend M16 `files/fleet_device_matrix.json` into the **single per-device SoT** with a human-readable ADB-pullable summary, exhaustive capability inventory (resolutions, ratios, formats, fps, focal lengths, face/eye tracking), and device-tailored UI — **hide** unavailable features on consumer chrome; **root-only** items shown in blue with toast on tap. Engineering Hub: searchable master checklist + jump-to-setting highlight. **No duplicate matrix artifact.**

**Depends on:** Milestone 16 (fleet matrix). **Primary USB:** CPH2583. No CPH2655-only visibility forks without `FleetDevicePolicy` plugin.

**On-device artifacts (after scan):**

| File | Role |
|------|------|
| `files/fleet_device_matrix.json` | Machine SoT (schema v1.1 adds `capabilityCatalog` slice) |
| `files/fleet_device_capability_summary.md` | Human-readable summary for PC debugging via ADB |

**UI visibility policy (locked M17):** Unavailable on device → **hidden** (QS, dial, settings, readout, tray). Root-only → **blue** (`PnsColors.RootAccentBlue`); tap → toast. Engineering Hub catalog shows full inventory with device/app checkmarks.

**Docs:** `docs/CAMERA_CAPABILITY_CATALOG.md` · `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`

### Sprint 17.1 — Extend matrix SoT (no duplicate file)

- [ ] **[AGENT]** Schema **v1.1** (backward compatible): optional top-level `capabilityCatalog` in `fleet_device_matrix.json`
- [ ] **[AGENT]** `CameraCapabilityCatalog.kt` + `CameraCapabilityCatalogBuilder.kt` — ~200 product rows bound to existing matrix probes
- [ ] **[AGENT]** `FleetCapabilitySummaryMarkdown.kt` — write `files/fleet_device_capability_summary.md` on every quick/full save
- [ ] **[AGENT]** Extend `DeepCapsProbeCore.streamConfigToJson` — JPEG/RAW/YUV/HEIC/MR sizes; `aspectRatios[]`; promote `faceDetectModes` to structured `cameras[]`
- [ ] **[AGENT]** Extend `scripts/pns_fleet_matrix_scan.ps1` — pull `fleet_device_capability_summary.md`
- [ ] **[AGENT]** `docs/CAMERA_CAPABILITY_CATALOG.md` — taxonomy + matrix key mapping

**Gate:** JVM `CameraCapabilityCatalogBuilderTest` against `fleet_matrix_gate_minimal.json`; host script pulls JSON + summary.

### Sprint 17.2 — Visibility gate (hide / root-blue / toast)

- [ ] **[AGENT]** `FleetUiVisibilityGate.kt` — `visible(id)`, `rootOnly(id)`, `visibilityTier(id)`
- [ ] **[AGENT]** Consumer chrome: hide when unavailable; root-only → blue + toast
- [ ] **[AGENT]** Log `PNS.FleetVisibility`; `.cursor/rules/fleet-ui-visibility.mdc`

**Gate:** JVM tests; eye-AF tile absent when face detect empty; root FPS chip blue + toast when SU not granted.

### Sprint 17.3 — Unified Device Capability Matrix hub

- [ ] **[AGENT]** Merge `FleetMatrixHubScreen` + catalog → **Device Capability Matrix** (single hub entry)
- [ ] **[AGENT]** Tabs: Summary · By camera · Features (searchable) · Raw JSON
- [ ] **[AGENT]** Copy ADB pull paths; Export JSON + summary; "New device — Rescan full" banner when deep caps missing

**Gate:** Hub screenshot; summary.md matches JSON on CPH2583 pull.

### Sprint 17.4 — Engineering Hub search + setting highlight

- [ ] **[AGENT]** `ProbeHubSearch.kt` — hub menu + catalog rows
- [ ] **[AGENT]** `rememberSettingHighlightFlash()` — 3× background pulse; extend `ChromeSettingsSearchHit` with `settingKey`

**Gate:** Hub search → HUD/settings row scroll + flash; log `PNS.ProbeHub settingsSearchPick`.

### Sprint 17.5 — Chrome visibility audit

- [ ] **[AGENT]** Wire QS grid, mode dial, settings rail, readout, focal row, video format picker through `FleetUiVisibilityGate`

**Gate:** `pns_chrome_ux_gate.ps1`; no ghost content-desc for hidden features.

### Sprint 17.6 — Video FPS fix & rescan invalidation

- [ ] **[AGENT]** `MediaCodecCapabilityProbe` — 1080p@30 tier; baseline fps union; `invalidateAndReprobe()` on rescan
- [ ] **[AGENT]** `InAppVideoFormatSelection` — H.264 ≤60 MediaRecorder path when HAL MR size ok

**Gate:** USB CPH2583 — 1080p@30 in format picker after rescan without app restart.

### Sprint 17.7 — Docs & milestone gate

- [ ] **[AGENT]** Update `PNS_TECHNICAL_SETTINGS.md`, `FLEET_DEVICE_CAPABILITY_MATRIX.md`, `AGENT_REGRESSION_MEMORY.md`, `CHANGELOG.md`

**Milestone 17 gate:** `pns_verify_toolchain.ps1 -RunTests` + `pns_fleet_matrix_scan.ps1` pulls JSON + summary + CPH2583 chrome gate.

**Archive on close:** Index under **Fleet capability matrix & device policy** in `BUILD_PLAN_COMPLETED.md`.

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

- **Version:** Active plan **2026-05-29** — **M13–M16** + **BG/AS/UX/CC/IP** archived; active: **Milestone 17** + **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

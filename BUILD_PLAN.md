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
| `scripts/pns_fleet_parity_sweep.ps1` | **Fleet Parity Sweep** — `-Mode Quick\|Full\|Delta` required (M18.6) |
| `scripts/pns_fleet_regression_pack.ps1` | Tiered matrix + parity Quick + catalog gate (M18.4) |
| `scripts/pns_m18_gate.ps1` | Milestone 18 one-shot host + USB gate |
| `scripts/pns_fleet_macro_export.ps1` | Cross-device macro benchmark CSV (M18.4) |
| `scripts/pns_capability_catalog_gate.ps1` | Host catalog row / descriptor gate (M18.5) |

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
| **Milestone 17** | **Archived** — capability catalog + hub search + chrome visibility (**2026-05-29**) |
| **Milestone 18** | **Archived** — catalog v3, matrix schema v2, Fleet Parity Sweep, focal row, regression pack (**2026-05-30**) |
| **Milestone 19** | **Archived** — format/color picker, VP9/RAW/dual-ISO, ProRes probe (**2026-05-30**) |
| **Milestone 20** | **Active** — concurrent capture (dual video + Multicam Melt + PiP) |
| **Milestone H** | **Active** — residual **[HUMAN]** work; **H.7** closed **CPH2583** (owner 2026-05-29); OP13 lane optional |
| **Bespoke Gallery (BG.1–BG.3)** | **Archived** — integration + device verify + UX polish (**maintainer sign-off 2026-05-22**) |
| **Audio & Sound (AS.1–AS.3)** | **Archived** — agent + human sign-off **2026-05-22** |
| **User Interface & Experience (UX.1–UX.3)** | **Archived** — theme, nav, workflow, cloud backup **2026-05-25** |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Future features (deferred — unscheduled)

Items below moved into **Milestone 19** sprints where noted; this list stays empty until new product requests land.

---

## Milestone 18 — Fleet max-out framework *(archived 2026-05-30)*

**Objective:** Universal device capability taxonomy, **Fleet Parity Sweep** benchmark, matrix schema v2, fleet-adaptive focal row, multi-device regression pack.

**Docs:** `docs/CAMERA_CAPABILITY_TAXONOMY.md` · `docs/FLEET_PARITY_SWEEP.md` · `docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md`

**Device gate (CPH2583 `b5214fc6`):** `pns_fleet_matrix_scan.ps1` pass (`hfr-runs/fleet_matrix_20260530_024009/`); `pns_fleet_parity_sweep.ps1 -Mode Quick` pass (`hfr-runs/parity_sweep_20260530_024345/`); `pns_chrome_ux_gate.ps1 -FocalMmSlot 85/150` pass; `pns_capability_catalog_gate.ps1` pass; fleet JVM tests pass.

### Sprint 18.0 — Schema + docs

- [x] **[AGENT]** `docs/CAMERA_CAPABILITY_TAXONOMY.md` + matrix schema v2 notes
- [x] **[AGENT]** `docs/FLEET_PARITY_SWEEP.md` + `docs/FLEET_MULTI_DEVICE_TEST_REGIMENT.md`
- [x] **[AGENT]** `BUILD_PLAN.md` M18/M19/M20 active; archive M17 pointer

### Sprint 18.6 — Fleet Parity Sweep (FPS)

- [x] **[AGENT]** `FleetParitySweep.kt` + `FleetDeliveryProbe.kt` + JVM tests
- [x] **[AGENT]** `scripts/pns_fleet_parity_sweep.ps1` — **`-Mode` required** (exit 2 without)
- [x] **[AGENT]** In-app hub mode sheet + `PNS.FleetParity parityCell=` log emission per catalog row
- [x] **[ADB]** `-Mode Quick` smoke on **CPH2583**; attach `hfr-runs/parity_sweep_*`

### Sprint 18.1 — Catalog expansion

- [x] **[AGENT]** `CameraCapabilityCatalog` v3 rows (~165+ distinct; expansion + evaluators)
- [x] **[AGENT]** Evaluators for new rows; `CameraCapabilityCatalogExpansion.kt`

### Sprint 18.7 — Fleet-adaptive focal row

- [x] **[AGENT]** `FleetFocalRowPolicy.kt` + matrix `product.focalRow` parser + tests
- [x] **[AGENT]** Wire native UW/Wide/Tele labels + static 35/50/85/150 N/A chips (behavior only; chrome layout lock)
- [x] **[ADB]** `pns_chrome_ux_gate.ps1 -FocalMmSlot 85` + 150 on CPH2583

### Sprint 18.4/18.5 — Regression pack + CI

- [x] **[AGENT]** `pns_fleet_regression_pack.ps1` + `pns_capability_catalog_gate.ps1`
- [x] **[AGENT]** `pns_m18_gate.ps1` + `pns_fleet_macro_export.ps1`
- [x] **[AGENT]** `docs/FLEET_PARITY_LATEST.json` + history JSONL from parity script

**M18 gate:** `pns_capability_catalog_gate.ps1` + `pns_fleet_regression_pack.ps1 -Tier all` + parity Quick USB on primary SKU — **PASS** (2026-05-30).

---

## Milestone 19 — Feature max-out *(archived 2026-05-30)*

**Objective:** Ship committed formats, quality-first pickers, video/still pipelines from max-out list.

**Host gate:** `scripts/pns_m19_gate.ps1` — M19 JVM tests + catalog gate (+ USB tier-2 regression when device online).

### Sprint 19.6 — Format + color picker

- [x] **[AGENT]** `ColorQualityIndex.kt` + `FormatQualityDescriptor.kt` + `VideoFormatQualityRank.kt`
- [x] **[AGENT]** Video picker: fps **desc**, Max presets, codec quality rows, **VideoAudioSource** in sheet
- [x] **[AGENT]** Color-space step (CQI) in still + video pickers; filter downstream rows
- [x] **[AGENT]** `StillFormatPickerSheet.kt` + HEIC / Motion Photo / TIFF export scaffolds

### Sprint 19.1 — Video pipelines

- [x] **[AGENT]** RAW video `.mcraw` in main format picker (matrix-gated)
- [x] **[AGENT]** Dual-ISO HDR merge production path
- [x] **[AGENT]** VP9 WebM encoder path (below AV1; matrix-gated)

### Sprint 19.4 — ProRes + anamorphic

- [x] **[AGENT]** ProRes probe-only catalog row + anamorphic metadata (no HW encode)

**M19 gate:** `pns_m19_gate.ps1` + `pns_fleet_regression_pack.ps1` tier 2 — host JVM **PASS** (2026-05-30).

---

## Milestone 20 — Concurrent capture *(archived 2026-05-30)*

**Host gate:** `scripts/pns_m20_gate.ps1` — M20 JVM tests + dual record 5s + pip/multicam USB smoke + tier-2 regression.

### Sprint 20.1 — Dual video reliability

- [x] **[AGENT]** HAL-derived `dualVideo` matrix gates + front health recovery + mandatory `-RecordSec 5` gate

### Sprint 20.2 — Multicam Melt

- [x] **[AGENT]** `MulticamMeltRecordingController` + thermal caps + parity cells + USB arm smoke

### Sprint 20.3 — PiP preview (optional)

- [x] **[AGENT]** Concurrent rear+rear PiP inset preview + `pns_preview_pip` ADB gate

**M20 gate:** `pns_m20_gate.ps1` + parity Quick includes `video.dual` / `video.multicam_melt` / `preview.pip`.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | Shipped work index **by app feature** (22 categories); not milestone/sprint layout |

**Open in this file:** **Milestone H** only (M17–M20 archived 2026-05-30)

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

- **Version:** Active plan **2026-05-29** — **M13–M17** archived; active: **M18–M20** + **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

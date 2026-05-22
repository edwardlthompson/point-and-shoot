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

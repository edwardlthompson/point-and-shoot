## Build plan (Point & Shoot)

**Purpose:** Single roadmap for shipping the Parts 1–5 spec with **milestones → sprints → gates**. **Active work** lives in this file; **shipped** milestone bodies live in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

**Living docs:** `PROBE_BUILD_PLAN.md` (§5 audit log; §6 probe ↔ milestone map), `CHANGELOG.md`, `CLI_BUILD_AND_SIDELOAD.md`, `DODGE_PROFILE.md`, `COLOR_PIPELINE.md`, `NDK_PLAN.md`, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** (capture bisect / M13 lock unlocks §9).

**Fleet / DNG references:** `docs/FLEET_ONEPLUS13_RAW_POLICY.md`, `docs/DNG_OPENABILITY_REGRESSIONS.md`, `docs/MOTIONCAM_APK_FLEET_ANALYSIS.md`, `docs/PROSHOT_APK_FLEET_ANALYSIS.md`, `docs/RAW_REFERENCE_APP_MATRIX.md`, `docs/M13_4_DCG_SESSION.md`, `docs/M13_6_RAW_VIDEO.md`, `docs/M13_8D_STILL_MODE_BENCHMARK.md`, `docs/M13_7_GATE.md`, `docs/M13V_17_AI_FEATURES.md`, `docs/M13V_18_CAMERAX_EXTENSIONS.md`.

---

### How agents must execute (nonstop discipline)

1. **Work inside one milestone at a time.** Finish every sprint in that milestone before starting the next.
2. **Within a sprint, complete tasks in order.** Blockers → log in `PROBE_BUILD_PLAN.md` §5.
3. **After each sprint:** run that sprint’s **Sprint check**. On failure, stop and fix.
4. **After all sprints in a milestone:** run the **Milestone gate** before proceeding.
5. **Tick rules:** Never `[x]` without **Appendix A**. Host: `pns_verify_toolchain.ps1 -RunTests` + `ReadLints`. Device: §5 evidence.
6. **UI work gate:** Visible UI changes need **assembleDebug**, sideload, on-glass check, and **`pns_device_screencap.ps1`** proof (see item 6 in prior revisions — unchanged).
7. **JAVA_HOME / ADB:** Android Studio JBR; SDK `platform-tools` first; optional **`scripts/pns_adb_device.env`** (`PNS_ADB_SERIAL`).
8. **Git after each numbered milestone (0–12, 13, 13V — not H):** commit + push when gate passes.
9. **Capture regression:** Changes to still/RAW/DNG/session/`PreviewEngineScreen.kt`/`RawCaptureSupport.kt` → **`pns_capture_pipeline_verify.ps1`** (or bisect/restore scripts per **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**).
10. **Archive:** When every checkbox in a sprint is `[x]`, move the sprint body to **`BUILD_PLAN_COMPLETED.md`** (procedure below). **Human `[HUMAN]` rows** move to **Milestone H**, not the archive as “done.”

**Hard rules (do not regress):** No **`automationSuppressFacePipeline`** for sequential RAW alone; no §4a **`streamHints`** or §2 RAW10-first **`Default`** tier without USB proof — **`AGENTS.md`**, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8. Preview chrome + dodge tele + DNG pairing locks: **`.cursor/rules/`** + **`AGENTS.md`**.

**Human work:** Only **Milestone H** holds **[HUMAN]** tasks. Agents prepare artifacts; humans close subjective sign-off.

---

### Global toolkit (used in gates)

| Tool | Role |
|------|------|
| `scripts/pns_verify_toolchain.ps1 -RunTests` | Host gate: assembleDebug, unit tests, Detekt, lint, SBOM |
| `scripts/pns_capture_pipeline_verify.ps1` | USB RAW still gate → `docs/CAPTURE_PIPELINE_VERIFY_*.json` |
| `scripts/pns_photo_capture_verify.ps1` | Core `captureRawStill 1/1 ok=true` needle |
| `scripts/pns_capture_bisect_device.ps1` / `pns_capture_restore_verified.ps1` | Bisect / restore per revert doc |
| `scripts/pns_chrome_ux_gate.ps1` | Chrome UX (`-FocalMmSlot` for tele proof) |
| `scripts/pns_sideload_and_launch.ps1` | Build, install, launch preview |
| `scripts/pns_aux_dng_capture_analyze.ps1` | M13 aux DNG capture + openability + optional parity |
| `scripts/dng_desktop_open_gate.py` / `pns_m13_3g2_gate.ps1` | DNG openability; **`-RecordAcrPass`** for human sign-off |
| `scripts/pns_m13_3f_gate.ps1` / `pns_m13_8d_gate.ps1` | Daylight + still-mode benchmark gates |
| `scripts/pns_still_mode_benchmark.ps1` | Per-mode still timing + openability |
| `scripts/pns_video_hdr10_metadata_verify.ps1` | DCG / HDR10 encoded video (ffprobe) |
| `scripts/pns_raw_video_verify.ps1` | RAW video lane (`PNMRAWV1` / `.mcraw`) |
| `scripts/pns_mediacodec_hfr_verify.ps1` | MediaCodec HFR + 10-bit suite |
| `scripts/pns_video_capability_probe.ps1` | **13V.15** — `PNS.VideoCapProbe` matrix |
| `scripts/pns_camerax_extension_probe.ps1` | **13V.18** — OEM extension probe (`-HostOnly` without device) |
| `scripts/pns_ai_features_verify.ps1` | **13V.17** — smile / scene / bitrate USB gate |
| `scripts/pns_m13_7_host_gate.ps1` | **13.7** — host prep + H.7 blocker doc |
| `scripts/pns_focus_peaking_verify.ps1` | **13V.10** — M dial + Red peaking + in-app video gate |
| `scripts/pns_video_lut_preview_verify.ps1` | **13V.11** — video LUT on GLES preview during record |
| `scripts/pns_in_app_video_verify.ps1` | In-app `MediaRecorder` smoke |
| `scripts/pns_motioncam_apk_decompile.ps1` / `pns_proshot_apk_decompile.ps1` | Reference APK decompile |
| `scripts/pns_fixture_dng_gates.ps1` | CI openability on ProShot fixtures |
| `scripts/pns_adb_device.env` (gitignored) | Default **`PNS_ADB_SERIAL`** |

**Lint / CI:** Detekt + `lintDebug` baselines; R8 release **`proguard-rules.pro`** UTF-8 without BOM.

### Performance & responsiveness backlog — archived

All seven rows **`[x]`** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Performance & responsiveness backlog*).

### Backlog consolidation (active)

| Area | Status |
|------|--------|
| **Milestones 0–12** | Gates passed; bodies in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** |
| **Milestone 13 — Fleet RAW** | Sprints **13.1–13.6**, **13.3***, **13.8*** archived; **13.7** automated **PASS** — closes with **H.7** only |
| **Milestone 13V — Video product expansion** | **13V.1–13V.18** archived + USB-verified on **`8bf09993`** (May 2026) |
| **Milestone H** | All **[HUMAN]** work (incl. M13 DNG / still-mode sign-off) |
| **Sprint 9.13** | Three human finder-geometry rows remain in archive |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Future features (deferred — unscheduled)

- **OpenCamera-style toolbox** — former Sprint 10.14; descoped unless product requests.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | **M0–M7**; **M8–9**; performance backlog; **M10–12**; **M13** **13.1–13.8**; **M13V** **13V.1–13V.18** |

**Open in this file:** **Milestone 13.7** (human closure) · **Milestone H**

### Archiving completed sprints — procedure

1. Move a **`### Sprint`** only when **every** **`- [x]`** is done **except** lines tagged **`[HUMAN]`** — those relocate to **Milestone H**.
2. Cut sprint body → append under the right **`## Milestone`** in **`BUILD_PLAN_COMPLETED.md`**.
3. Replace in this file with **`**Completed sprints** … → BUILD_PLAN_COMPLETED.md`**.
4. Update the archive table and **`### Backlog consolidation`**.
5. Descoped sprints: note under **Future features**, do not archive as complete.

---

## Milestone 13 — Fleet RAW parity (gate remaining)

**Objective:** Aux DNG on **OnePlus 13** (`CPH2655` / `8bf09993`); **Standard** ProShot still default; optional **ZSL** / **HDR still**; fleet profiles; **DCG** session + **RAW video** on OP13 leaf cameras.

**Completed sprints:** **13.1**, **13.2**, **13.3** (a–h, e, f automated), **13.3g** (automated), **13.4**, **13.5**, **13.6**, **13.8** (a–d automated) → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone 13 — Fleet RAW parity*).

**Still open:** **Milestone 13.7 gate** (human ACR / visual parity rows → **H.7**). Conditional lock table and bisect evidence: archived milestone + **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §9.

**Suggested order (remaining):** Close **H.7** human items → run **13.7** gate checklist → optional promote locks only with §9 USB proof.

### Sprint 13.7 — Milestone 13 gate

| Check | Pass criterion | Status |
|-------|----------------|--------|
| Host | `pns_verify_toolchain.ps1 -RunTests` exit 0 | ✅ |
| Still regression | `pns_capture_pipeline_verify.ps1` green | ✅ |
| DNG openability | **13.3g** gate PASS on USB | ✅ |
| Aux DNG | `pns_aux_dng_capture_analyze.ps1 -PreviewDial A` **3/3** | ✅ |
| ProShot parity (rawpy) | Documented **FAIL** UW/tele (HAL CM2) | ⚠️ documented |
| Still modes | ZSL + HDR benchmark PASS; Standard default for pipeline verify | ✅ |
| Lock / wide-cal bisect | **13.3e** / **13.3h** — no lock shipped | ✅ |
| DCG | `pns_video_hdr10_metadata_verify.ps1` PASS | ✅ |
| RAW video | `pns_raw_video_verify.ps1` PASS | ✅ |
| Docs / §5 | Policy docs + probe rows | ✅ **`docs/M13_7_GATE.md`** |
| **Human ACR 3/3** | **`ACR_HUMAN_VERIFY.md`** + **`-RecordAcrPass`** | ❌ → **H.7** |
| **Visual aux color** | ACR vs ProShot (**Standard**) | ❌ → **H.7** |
| **STILL_MODE_COMPARE** | **13.8d** daylight ACR across modes | ❌ → **H.7** |
| Battery | `force-stop` after each script | ✅ (scripts) |

**Milestone 13 gate:** Closes when **H.7** is complete and owner records §5 / wiki sign-off.

---

## Milestone 13V — Video product expansion (2026-05-17)

**Objective:** Power-button quick-launch, HFR / 10-bit / DCG encoded video, unified format picker, macro mode, recording overlays, RGB histogram, capability probes, 4K@120, CameraX extensions.

**Naming:** Sprint IDs use prefix **13V.** to avoid collision with **Milestone 13** fleet RAW sprints (**13.1–13.8**).

**Completed sprints:** **13V.1–13V.18** → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone 13V — Video product expansion*). USB: **`pns_ai_features_verify.ps1`** (`hfr-runs/ai_features_verify_20260520_075142/`), **`pns_camerax_extension_probe.ps1`** (`hfr-runs/camerax_ext_probe_20260520_072853/`).

### Milestone 13V gate

| Check | Pass criterion | Status |
|-------|----------------|--------|
| Host | All **13V.*** sprints closed or deferred | ✅ |
| HFR | `pns_mediacodec_hfr_verify.ps1` **7/7** | ✅ **`8bf09993`** |
| Cap probe | **13V.15** `probe.json` | ✅ |
| HDR10 | `pns_video_hdr10_metadata_verify.ps1` | ✅ |
| CameraX | **13V.18** probe ≠ FAIL | ✅ **`PROBE_OK_NO_EXTENSIONS`** |
| Overlays / macro | Archived **13V.6–13V.9** | ✅ |

---

## Milestone H — Human & publication

**Objective:** Subjective validation, account ownership, creative judgment, and release authority. Automatable work lives in completed **M12.6** scripts and M13 gates.

**Depends on:** **Milestone 13.7** gate (after **H.7**); optional **13V** device verifications for video claims in store copy.

### Sprint H.1 — Desktop visual verification

- [ ] **[HOST][HUMAN]** DNG/AVIF/JXL aesthetic review (darktable / RawTherapee) — structure gate: `pns_desktop_file_validate.ps1`
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

**Depends on:** Sprint **11.3** + **`pns_eye_af_alignment_probe.ps1`** (12.6).

- [ ] **[ADB][HUMAN]** Eye-AF alignment visual sign-off on device glass
- [ ] **[HUMAN]** HUD / LUT default aesthetics
- [ ] **[HUMAN]** TalkBack / a11y labels review
- [ ] **[HUMAN]** Immersive mode feel

### Sprint H.7 — Milestone 13 DNG & still modes (human)

**Artifacts:** `hfr-runs/aux_dng_capture_analyze_20260519_235745/` (`ACR_HUMAN_VERIFY.md`), `hfr-runs/m13_3f_gate_20260520_012341/`, `hfr-runs/m13_8d_gate_20260520_020059/` (`STILL_MODE_COMPARE.md` template).

- [ ] **[HUMAN]** ACR / Lightroom: M14, M23, M73 DNGs **all three open** — checklist in **`ACR_HUMAN_VERIFY.md`**
- [ ] **[HUMAN]** Record sign-off: `.\scripts\pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "…"`
- [ ] **[HUMAN]** Visual: aux **color** vs ProShot in ACR (**Standard** mode, dial **A**) — accept or document known HAL gap
- [ ] **[HUMAN]** Daylight ACR: **Standard / ZSL / HDR** comparison — complete **`STILL_MODE_COMPARE.md`**; optional `pns_m13_8d_gate.ps1 -RecordHumanPass` + §5 row

**Milestone H gate:** Owner-approved checklist (§5 or wiki); no unjustified **[HUMAN]** rows; **M13.7** gate may close after **H.7**.

---

## Appendix A — Verification protocol (abbreviated)

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED  
2. `ReadLints` clean on touched Kotlin  
3. Claimed paths/symbols exist  
4. Unit tests: `failures="0" errors="0"`  
5. `CHANGELOG.md` (Unreleased) for user-visible changes; **§5** for gates  
6. **[ADB]/[ROOT]:** device evidence — never close device gates on host-only scaffolding  
7. **[MIXED]:** parent stays `[ ]` until every child venue is satisfied  

---

## Appendix B — Baseline already shipped (high level)

| Area | Status |
|------|--------|
| FOSS gates + CI toolchain | Shipped |
| Probe JSON + About hydration | Shipped |
| Dodge profile + crop geometry | Shipped |
| Pro HUD + chrome (locked layout) | Shipped |
| LUT / calibration / DNG library path | Shipped |
| Diagnostics + failure matrix docs | Shipped |

Historical line-by-line checkboxes: `git log -- BUILD_PLAN.md`, `PROBE_BUILD_PLAN.md` §5.

---

## Appendix C — Agent quick grep

| Need | Pattern |
|------|---------|
| Open host | `^- \[ \] \[HOST\]` |
| Open device | `^- \[ \] \[ADB\]` |
| Open human | `^- \[ \] \[HUMAN\]` |
| Open mixed | `^- \[ \] \[MIXED\]` |
| Sprint headers | `^### Sprint` |

---

## Document control

- **Version:** Active plan slimmed **2026-05-20** — fleet **M13** shipped sprints archived; video expansion relabeled **Milestone 13V**; human M13 work under **H.7**.
- **Owner:** Project maintainer approves Milestone H and **13.7** closures.
- **Archive cadence:** After closing sprints, follow **Archiving completed sprints** above.

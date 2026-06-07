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
9. **Hard rules — do not regress:** Read **`docs/AGENT_REGRESSION_MEMORY.md`** before capture/DNG/preview/fleet edits; append a **`REG-*`** row after USB-proven fixes. No `automationSuppressFacePipeline` for sequential RAW alone; no §4a `streamHints` or §2 RAW10-first `Default` without USB proof; capture/session/DNG changes → `pns_capture_pipeline_verify.ps1` on **CPH2583**; settings changes → update `docs/PNS_TECHNICAL_SETTINGS.md` same commit; user-visible ship → update **`CHANGELOG.md`** + **`scripts/changelog_coverage.v1.json`** same commit (gate: **`pns_changelog_gate.ps1`**). Full locks: `AGENTS.md`, `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8, `.cursor/rules/`.
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
| `scripts/pns_fleet_matrix_scan.ps1` | USB full matrix pull → `hfr-runs/fleet_matrix_*` |
| `scripts/pns_fleet_matrix_diff.ps1` | Host diff two matrix JSONs |
| `scripts/pns_fleet_parity_sweep.ps1` | **Fleet Parity Sweep** — `-Mode Full\|Delta` required |
| `scripts/pns_fleet_regression_pack.ps1` | Tiered matrix + parity Delta + catalog gate |
| `scripts/pns_m18_gate.ps1` | Milestone 18 one-shot host + USB gate |
| `scripts/pns_fleet_macro_export.ps1` | Cross-device macro benchmark CSV |
| `scripts/pns_m25_gate.ps1` | Leaderboard host gate |
| `scripts/pns_m26_gate.ps1` | Parity closure gate |
| `scripts/pns_m27_gate.ps1` | Parity debt burn-down gate |
| `scripts/pns_leaderboard_host_smoke.ps1` | Leaderboard JSON/CSV/RSS host smoke |
| `scripts/pns_capability_catalog_gate.ps1` | Host catalog row / descriptor gate |
| `scripts/pns_leaderboard_site_publish.ps1` | Leaderboard site data build |
| `scripts/pns_leaderboard_pages_push.ps1` | Publish + push `docs/leaderboard` to GitHub Pages |
| `scripts/pns_fleet_parity_leaderboard_refresh.ps1` | Host leaderboard JSON/MD from parity sweeps |

Full script index: **`AGENTS.md`**. **Primary fleet USB device:** OnePlus 12 **CPH2583** (not CPH2655 unless OP13 regression lane).

### Parity intake queue (auto-generated)

Every parity sweep / regression pack tier-2 refreshes:

- [`docs/FLEET_PARITY_DEBT_LEDGER.json`](docs/FLEET_PARITY_DEBT_LEDGER.json) — deduplicated debt with `workType` triage
- [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) — actionable `PBI-{catalogId}-{workType}` rows for sprint promotion

Scripts: `pns_parity_debt_ledger_refresh.ps1` · `pns_parity_build_plan_intake.ps1` (wired at end of `pns_fleet_parity_sweep.ps1`).

**Promotion rule:** move scoped `PBI-*` rows into the active Milestone H lane; mark `status=closed` in intake JSON when USB proof passes.

### Future features (deferred — unscheduled)

Add new requests here only when explicitly scheduled.

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment: creative, security, perceptual.

### Sprint H.1a — Auto-synced HAL honesty gap fixes (latest Full run)

<!-- AUTO_HAL_HONESTY_GAPS_START -->
- Generated: 2026-06-06T17:14:08.9946446Z
- Source run: C:\Users\edwar\AndroidStudioProjects\point-and-shoot\hfr-runs\parity_sweep_20260606_165857
- Open honesty gaps: 1
- Priority order: session_failed -> not_proven -> advertised_not_surfaced -> matrix_tier_quick
- [ ] **[AGENT]** Honesty fix: video.delivery_honesty (reason=unautomated, impact=ENGINEERING_ONLY)
<!-- AUTO_HAL_HONESTY_GAPS_END -->

**Still blocked without human:** ColorChecker (H.2), keystore custody (H.4), store copy (H.5), face-in-frame eye-AF (H.6/H.8.1), subjective HUD/codec (H.8).

### Sprint H.2 — Physical calibration capture

- [ ] **[HUMAN]** Set up ColorChecker under controlled illuminant (irreducible — physical setup)
- [ ] **[AGENT]** Trigger ADB capture; run `pns_colorchecker_de2000_gate.py`; assert all Macbeth patches dE2000 < threshold — **blocked on HUMAN**; host self-test PASS only (`pns_colorchecker_de2000_gate.py`)

### Sprint H.3 — Account ownership

- [ ] **[HUMAN]** Confirm you are logged into the owner GitLab account (irreducible — identity custody)

### Sprint H.4 — Signing authority

- [ ] **[HUMAN]** Confirm you hold custody of the keystore file (irreducible — security)

### Sprint H.5 — Publication & community

- [ ] **[HUMAN]** Store listing copy (Play / F-Droid) — irreducible creative writing
- [ ] **[HUMAN]** Community announcements — irreducible public communication

### Sprint H.6 — Subjective UX sign-off

- [ ] **[AGENT]** `pns_eye_af_pixel_gate.ps1` — screencap + PIL diff eye-box vs expected region; PASS when delta < threshold — **FAIL** unattended USB (`eye_af_pixel_gate_20260529_172528`: no green overlay markers without face in frame); fixed `pns_eye_af_overlay_align.py` numpy JSON bug
- [ ] **[HUMAN]** HUD / LUT default aesthetics — irreducible perceptual
- [ ] **[HUMAN]** Immersive mode feel — irreducible perceptual

### Sprint H.7-OP13 — Optional OP13 regression lane

> Run only when verify matrix marks OP13 **regression optional** or plugin enabled. Not required for CPH2583 fleet program.

- [ ] **[AGENT]** `pns_aux_dng_capture_analyze.ps1` on CPH2655 — **skipped** (OP13 not connected; optional lane)
- [ ] **[AGENT]** `pns_m13_3g2_gate.ps1 -Dir <aux_dng_dir> -RecordAcrPass -AcrNote "auto"` — blocked on OP13 capture
- [ ] **[AGENT]** `dng_referenceapp_parity_gate.py` — ReferenceApp reference parity — host fixtures only; full lane needs OP13 USB
- [ ] **[ADB]** OP13 stock ROM Full sweep (`CPH2649` / `CPH2655` alias lane) with `testedVariants[].romFlavor=stock` and product-group collapse evidence
- [ ] **[ADB]** OP13 Lineage/custom Full sweep second variant (`testedVariants[]`) for custom-vs-stock delta
- [ ] **[HUMAN]** ACR on OP13 aux DNGs (optional regression sign-off)

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass (14.5 + 15.1) — pixel gate passes; on-face rubber-stamp
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing usability (14.12 + 15.5)
- [ ] **[HUMAN] H.8.3** Owner visual: all codecs/scenes good — **fail:** H.265 **DCG @4K** bad colors (2026-05-26); re-open 15.2 human row
- [ ] **[HUMAN] H.8.5** False color correct on grey card + highlight scene (15.21)

### Sprint H.9 — M25 publication/signing handoff

- [ ] **[HUMAN]** Populate `leaderboard-ingest/config/signing_pins.json` with release APK cert SHA-256
- [ ] **[HUMAN]** GitHub Pages smoke after deploy (buyer-facing copy, disclosure banner, GSMArena untested labeling)

**Milestone H gate:** Owner-approved checklist.

---

## Appendix A — Verification protocol (abbreviated)

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED  
2. `ReadLints` clean on touched Kotlin  
3. Claimed paths/symbols exist  
4. Unit tests: `failures="0" errors="0"`  
5. `CHANGELOG.md` + `scripts/changelog_coverage.v1.json` for user-visible changes (`pns_changelog_gate.ps1`); **§5** for gates  
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

- **Version:** Active plan **2026-06-06** — active milestone: **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

---

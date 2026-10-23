## Build plan (Point & Shoot)

**Purpose:** Active open work only. Shipped → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** · stub **[COMPLETED_TASKS.md](COMPLETED_TASKS.md)**.

| SoT | Path |
|-----|------|
| Agent entry | `docs/START_HERE.md` · `docs/FOR_AGENTS.md` · `AGENTS.md` |
| Settings / locks | `docs/PNS_TECHNICAL_SETTINGS.md` · `docs/AGENT_REGRESSION_MEMORY.md` |
| Security cadence | `docs/SECURITY_TRIAGE.md` |
| Human backlog | `HUMAN_BACKLOG.md` |

**Primary device:** OnePlus 12 **CPH2583** (`scripts/pns_adb_device.env`).

### Labels / status

`[AGENT]` · `[HUMAN]` · `[ADB]` · `[AUTO]` — 🔲 open · ✅ done · ❌ blocked

**Execute:** Sequential `[AGENT]` first → USB one serial (capture then chrome) → `force-stop` after. Never ✅ without gates (`AGENTS.md`).

**Active:** **BOOTSTRAP-1.x** (after HUMAN BOOT.1) · **Milestone H**. Recent close: [LAUNCH + H-METER + IDEAS](BUILD_PLAN_COMPLETED.md#recent-archive-2026-10-22).

### Toolkit (short)

| Gate | Script |
|------|--------|
| Host + tests | `pns_verify_toolchain.ps1 -RunTests` |
| RAW / H / chrome | `pns_capture_pipeline_verify` · `pns_highlight_meter_verify` · `pns_chrome_ux_gate` |
| Keystore / F-Droid | `pns_keystore_verify.ps1` · `pns_fdroid_metadata_validate.ps1` |
| Pre-release / release | `pns_prerelease_gate.ps1` · `pns_github_release.ps1` |

Full index: **`AGENTS.md`**.

---

## Open — AGENT

### BOOTSTRAP-1.x (code after HUMAN scope)

Local pin **0.15.0** passes `pns_validate_bootstrap.ps1`. Catch-up vs upstream **v1.10.0**.

- ✅ BOOT.2 — Batch-count drift (**28** commands) — [archived](BUILD_PLAN_COMPLETED.md#sprint-bootstrap-1x--boot2-only-doc-drift)
- 🔲 `[AGENT]` BOOT.3 — After **HUMAN BOOT.1**: `/upgrade` per `docs/UPGRADING_FROM_TEMPLATE.md`; bump `.template-version`; rewrite `docs/BOOTSTRAP_ALIGNMENT.md`; selective `/tour` `/shape` `/resume` `/adr` `/update-deps`

### Milestone H — agent residuals

- 🔲 `[AGENT] CRI-032` `pns_eye_af_pixel_gate.ps1` — FAIL without face in frame (needs USB face scene)
- ❌ `[AGENT]` `pns_colorchecker_de2000_gate.py` — blocked on **HUMAN** ColorChecker capture

### Deferred note

**DNG-FLEET-EXPOSURE P3** (OP13 residual shadow) deferred — `docs/DNG_FLEET_EXPOSURE_BISECT_MATRIX.md`.

---

## Open — AUTO

Script / CI / bot cadence. No human judgment required to *run*; escalate findings to HUMAN or AGENT.

### Cadence (weekly on `main`)

- 🔲 `[AUTO]` BOOT.4 — Weekly **Scorecard** + **template-update** checker on `main` (`.github/workflows/scorecard.yml` · `pns_check_template_updates.ps1` / `.template-update.json`)
- 🔲 `[AUTO]` Dependabot — Assert **0** open Critical/High alerts (or fail closed into triage); list open version PRs for `/dependabot` (see `docs/SECURITY_TRIAGE.md`)
- 🔲 `[AUTO]` Security scan + CodeQL green on `main` (release gate mirror)

### Host verify (promote from former HUMAN-only checks)

- 🔲 `[AUTO]` ADB env health — `PNS_ADB_SERIAL` present in `scripts/pns_adb_device.env` and matches an online `adb devices` serial when a device is connected (`pns_adb_serial.ps1` / gate warn)
- 🔲 `[AUTO]` Keystore + pin drift — `pns_keystore_verify.ps1` vs `scripts/pns_keystore_expected.json`; flag if `leaderboard-ingest/config/signing_pins.json` SHA does not match release cert
- 🔲 `[AUTO]` F-Droid / store metadata structure — `pns_fdroid_metadata_validate.ps1` (does **not** replace creative CRI-034 review)
- 🔲 `[AUTO]` PRIVACY / NOTICE / README link presence — `pns_repro_build_verify.ps1` host subset (does **not** replace creative copy sign-off)

---

## Open — HUMAN

All human judgment in **one list**, top → bottom = setup → process → capture → look → publish.

### 1. Device & accounts

- 🔲 `[HUMAN]` Write primary CPH2583 serial into gitignored `scripts/pns_adb_device.env` (`PNS_ADB_SERIAL=…`) — one-time; AUTO warns if stale
- 🔲 `[HUMAN]` Owner GitLab login confirmed

### 2. Signing custody

- 🔲 `[HUMAN]` Confirm offline backup of gitignored `release.keystore` + `keystore.properties` (AUTO verifies local keystore/pins; custody is human)

### 3. Process decisions

- 🔲 `[HUMAN]` BOOT.1 — Approve bootstrap **0.15 → 1.10** upgrade scope (~19 slash commands / which 1.x docs)
- 🔲 `[HUMAN]` Decide whether OpenSSF Scorecard becomes a **required** branch-protection check (workflow already non-blocking)
- 🔲 `[HUMAN]` Dependabot — merge / defer / dismiss version PRs and any Critical/High exceptions (AUTO lists; human decides)

### 4. Calibration capture

- 🔲 `[HUMAN]` ColorChecker Classic under controlled illuminant (unblocks AGENT de2000 gate)

### 5. Subjective device look (USB / glass)

- 🔲 `[HUMAN]` H.SCENE — Bright window / sky torture for dial **H** (flat indoor can look “broken” by design)
- 🔲 `[HUMAN]` HUD / LUT aesthetics · immersive mode feel
- 🔲 `[HUMAN] H.8.1` Eye/face overlay on glass (AGENT pixel gate is separate)
- 🔲 `[HUMAN] H.8.2` Dual-video stacked framing
- 🔲 `[HUMAN] H.8.3 CRI-033` H.265 DCG @4K colors
- 🔲 `[HUMAN] H.8.5` False color on grey card + highlight scene

### 6. Viewer / ACR sign-off

- 🔲 `[HUMAN] CRI-035` ACR open + color sign-off on OP13 aux DNGs (`hfr-runs/aux_dng_capture_analyze_20260613_014424`; integrity gates already scripted)

### 7. Store copy & community

- 🔲 `[HUMAN] CRI-034` Store listing — `metadata/en-US/` + Play listing creative
- 🔲 `[HUMAN] CRI-034` `PRIVACY.md`, `NOTICE`, F-Droid `metadata/` creative review (AUTO structural checks above)
- 🔲 `[HUMAN]` Community announcements

**Milestone H gate:** this HUMAN list + `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583.

**CRI map:** 032 agent · 033/034/035 human above · 0–6 + H-RESTORE → [COMPLETED](BUILD_PLAN_COMPLETED.md#milestone-h--completed-sprints).

---

## Closed pointers (do not expand)

| Item | Archive |
|------|---------|
| LAUNCH-2026-10-22 | [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-launch-2026-10-22--cold-start--preview-safe-mode) |
| H-METER-2026-10-22 (agent) | [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-h-meter-2026-10-22--save-the-highlights-restore-agent-closed) |
| IDEAS-2026-08-21…m | [COMPLETED](BUILD_PLAN_COMPLETED.md#sprints-ideas-2026-08-21--ideas-2026-08-21m-agent-closed) |
| AUDIT / H-RESTORE / T / TM / M28 | [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md) |

Parity intake: `pns_fleet_parity_sweep.ps1 -Mode Delta` · `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`.

---

## Appendix — Grep

| Need | Pattern |
|------|---------|
| Open human | `^- 🔲 \[HUMAN\]` |
| Open agent | `^- 🔲 \[AGENT\]` |
| Open AUTO | `^- 🔲 \[AUTO\]` |
| Blocked | `^- ❌` |

---

## Document control

- **Version:** 2026-10-22 — HUMAN consolidated + AUTO promotions (keystore/F-Droid/ADB/Dependabot/Scorecard).
- **Owner:** Maintainer closes Milestone H after HUMAN list + release sign-off.

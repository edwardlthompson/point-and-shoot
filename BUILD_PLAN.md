# Build Plan

<!-- remaining-tally -->
**Remaining:** (tally on next sync)
<!-- /remaining-tally -->

Live board. Canonical shape: agent-project-bootstrap `BUILD_PLAN_TEMPLATE.md`.
Finished work: [`COMPLETED_TASKS.md`](COMPLETED_TASKS.md).

**Who:** `AGENT` code · `HUMAN` person · `ADB` device · `AUTO` CI/scripts
**Venue (AGENT only):** `[LOCAL]` This Computer · `[CLOUD]` Cursor Cloud
**State:** 🔲 open · ✅ done · ❌ blocked

Format: `🔲 [AGENT][LOCAL] Short task — scope: path/prefix`.

### Open PRs (synced)

> Auto-managed. Do not hand-edit inside the markers.

<!-- open-prs-sync:begin -->
_No open Dependabot or Release Please PRs._
<!-- open-prs-sync:end -->

### Template gaps (synced)

> Auto-managed. Do not hand-edit inside the markers.

<!-- template-gaps-sync:begin -->
_No template gaps._
<!-- template-gaps-sync:end -->

### Waiting on a person

- 🔲 `[HUMAN]` Write primary CPH2583 serial into gitignored `scripts/pns_adb_device.env` (`PNS_ADB_SERIAL=…`) — one-time; AUTO warns if stale
- 🔲 `[HUMAN]` Owner GitLab login confirmed
- 🔲 `[HUMAN]` Confirm offline backup of gitignored `release.keystore` + `keystore.properties` (AUTO verifies local keystore/pins; custody is human)
- 🔲 `[HUMAN]` BOOT.1 — Approve bootstrap **0.15 → 1.10** upgrade scope (~19 slash commands / which 1.x docs)
- 🔲 `[HUMAN]` Decide whether OpenSSF Scorecard becomes a **required** branch-protection check (workflow already non-blocking)
- 🔲 `[HUMAN]` Dependabot — merge / defer / dismiss version PRs and any Critical/High exceptions (AUTO lists; human decides)
- 🔲 `[HUMAN]` ColorChecker Classic under controlled illuminant (unblocks AGENT de2000 gate)
- 🔲 `[HUMAN]` H.SCENE — Bright window / sky torture for dial **H** (flat indoor can look “broken” by design)
- 🔲 `[HUMAN]` HUD / LUT aesthetics · immersive mode feel
- 🔲 `[HUMAN] H.8.1` Eye/face overlay on glass (AGENT pixel gate is separate)
- 🔲 `[HUMAN] H.8.2` Dual-video stacked framing
- 🔲 `[HUMAN] H.8.3 CRI-033` H.265 DCG @4K colors
- 🔲 `[HUMAN] H.8.5` False color on grey card + highlight scene
- 🔲 `[HUMAN] CRI-035` ACR open + color sign-off on OP13 aux DNGs (`hfr-runs/aux_dng_capture_analyze_20260613_014424`; integrity gates already scripted)
- 🔲 `[HUMAN] CRI-034` Store listing — `metadata/en-US/` + Play listing creative
- 🔲 `[HUMAN] CRI-034` `PRIVACY.md`, `NOTICE`, F-Droid `metadata/` creative review (AUTO structural checks above)
- 🔲 `[HUMAN]` Community announcements

### Local agent (This Computer)

Standing queue for This Computer. Rows: `🔲 [AGENT][LOCAL] … — scope: path`.

<!-- local-agent-lane:begin -->
- `[AGENT][LOCAL]` · `[HUMAN]` · `[ADB]` · `[AUTO]` — 🔲 open · ✅ done · ❌ blocked
- 🔲 `[AGENT][LOCAL]` BOOT.3 — After **HUMAN BOOT.1**: `/upgrade` per `docs/UPGRADING_FROM_TEMPLATE.md`; bump `.template-version`; rewrite `docs/BOOTSTRAP_ALIGNMENT.md`; selective `/tour` `/shape` `/resume` `/adr` `/update-deps`
- 🔲 `[AGENT][LOCAL] CRI-032` `pns_eye_af_pixel_gate.ps1` — FAIL without face in frame (needs USB face scene)
- ❌ `[AGENT][LOCAL]` `pns_colorchecker_de2000_gate.py` — blocked on **HUMAN** ColorChecker capture
- 🔲 `[AUTO]` BOOT.4 — Weekly **Scorecard** + **template-update** checker on `main` (`.github/workflows/scorecard.yml` · `pns_check_template_updates.ps1` / `.template-update.json`)
- 🔲 `[AUTO]` Dependabot — Assert **0** open Critical/High alerts (or fail closed into triage); list open version PRs for `/dependabot` (see `docs/SECURITY_TRIAGE.md`)
- 🔲 `[AUTO]` Security scan + CodeQL green on `main` (release gate mirror)
- 🔲 `[AUTO]` ADB env health — `PNS_ADB_SERIAL` present in `scripts/pns_adb_device.env` and matches an online `adb devices` serial when a device is connected (`pns_adb_serial.ps1` / gate warn)
- 🔲 `[AUTO]` Keystore + pin drift — `pns_keystore_verify.ps1` vs `scripts/pns_keystore_expected.json`; flag if `leaderboard-ingest/config/signing_pins.json` SHA does not match release cert
- 🔲 `[AUTO]` F-Droid / store metadata structure — `pns_fdroid_metadata_validate.ps1` (does **not** replace creative CRI-034 review)
- 🔲 `[AUTO]` PRIVACY / NOTICE / README link presence — `pns_repro_build_verify.ps1` host subset (does **not** replace creative copy sign-off)
- | Open human | `^- 🔲 \[HUMAN\]` |
- | Open agent | `^- 🔲 \[AGENT\]` |
- | Open AUTO | `^- 🔲 \[AUTO\]` |
- | Blocked | `^- ❌` |
- 🔲 [AGENT][LOCAL] Pin pillow>=13.0.0 for JPEG/DNG compare scripts — scope: scripts/ (news P1, 2026-11-13) https://github.com/advisories/GHSA-xcvg-w56q-r89m
- 🔲 [AGENT][LOCAL] Pin pillow>=13.0.0 for JPEG/DNG compare scripts — scope: scripts/ (news P1, 2026-11-13) https://github.com/advisories/GHSA-xcvg-w56q-r89m
<!-- local-agent-lane:end -->

### Cloud agent (Cursor Cloud)

Standing queue for Cursor Cloud. Rows: `🔲 [AGENT][CLOUD] … — scope: path`.

<!-- cloud-agent-lane:begin -->
_No cloud agent items._
<!-- cloud-agent-lane:end -->

## Archive

Older sprints: [`COMPLETED_TASKS.md`](COMPLETED_TASKS.md).

## Preserved source board

Quoted so no prior task is lost. Prefer the lanes above; do not invent work.

<details>
<summary>Previous board</summary>

## Build plan (Point & Shoot)

**Purpose:** Active open work only. Shipped → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** · stub **[COMPLETED_TASKS.md](COMPLETED_TASKS.md)**.

> **MIG.* / multiplatform (persistent):** Before starting or continuing any migration row, **re-read `docs/migration/*`** — [`INVENTORY.md`](docs/migration/INVENTORY.md), [`DESIGN_BRIEF.md`](docs/migration/DESIGN_BRIEF.md) (binding), [`TECH_DECISION.md`](docs/migration/TECH_DECISION.md), [`PARITY_CHECKLIST.md`](docs/migration/PARITY_CHECKLIST.md). Do **not** start Phase 4 implementation until Edward has the brief. Do **not** implement PARTIAL items. Do **not** silently drop inventory rows.

| SoT | Path |
|-----|------|
| Agent entry | `docs/START_HERE.md` · `docs/FOR_AGENTS.md` · `AGENTS.md` |
| Settings / locks | `docs/PNS_TECHNICAL_SETTINGS.md` · `docs/AGENT_REGRESSION_MEMORY.md` |
| Security cadence | `docs/SECURITY_TRIAGE.md` |
| Human backlog | `HUMAN_BACKLOG.md` |
| Multiplatform migration | [`docs/migration/`](docs/migration/) (Phases 0–3 docs; Phase 4 = MIG.* below) |

**Primary device:** OnePlus 12 **CPH2583** (`scripts/pns_adb_device.env`).

### Labels / status

`[AGENT]` · `[HUMAN]` · `[ADB]` · `[AUTO]` — 🔲 open · ✅ done · ❌ blocked

**Execute:** Sequential `[AGENT]` first → USB one serial (capture then chrome) → `force-stop` after. Never ✅ without gates (`AGENTS.md`).

**Active:** **BOOTSTRAP-1.x** (after HUMAN BOOT.1) · **Milestone H** · **Multiplatform migration (docs Phases 0–3 done; Phase 4 not started)**. Recent close: [LAUNCH + H-METER + IDEAS](BUILD_PLAN_COMPLETED.md#recent-archive-2026-10-22).

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

## Open — Multiplatform migration (Phases 0–3 docs done; Phase 4 code not started)

**Re-read:** `docs/migration/INVENTORY.md` · `DESIGN_BRIEF.md` · `TECH_DECISION.md` · `PARITY_CHECKLIST.md` at the start of every MIG sprint.

**Stack (TECH_DECISION):** KMP shared core + Compose Multiplatform for companion/library/settings + **native** shooting (Android Compose/Camera2, iOS SwiftUI/AVFoundation). Desktop = companion (Q1). iOS = full camera, adapted chrome (Q2, Q4). ProRAW OK (Q3). Folder `DCIM/PointAndShoot` (Q11). PARTIAL (do not build): motion trip, Astro/Document recipes, capture journal UI. Out of parity: watchOS, Planned catalog rows, dual-video PiP overlay, news-inbox, fleet debt ledger.

**Phase 4 starts only after Edward’s go.** Each row below is one coherent chunk. Never ✅ without the named gate. USB capture and chrome stay **sequential** on one Android serial (`AGENTS.md`).

### MIG.0 — Freeze, contracts, no feature work

Inventory: Q11–Q15, §1.7, §1.9, Decisions.

- 🔲 `[AGENT]` Confirm Android feature freeze snapshot (commit SHA + versionName) in this section when Phase 4 opens; point DCIM work at the separate `main` fix PR for `DCIM/PointAndShoot`
- 🔲 `[AGENT]` Lock `SettingsExportBundle` schema as the cross-device preset (Q12) — host round-trip test fixture, no UI redesign
- 🔲 `[HUMAN]` Sign DESIGN_BRIEF (or file a Deviation). Unblocks MIG.1+
- **Parity:** freeze = no new consumer Android features in MIG.* except the folder bug PR
- **Design fidelity:** n/a (docs/contract)
- **Gate:** `pns_local_dev_parallel.ps1` (Tier 0) on the migration branch; DESIGN_BRIEF Deviations table still empty

### MIG.1 — KMP shared core extraction

Inventory: §1.10 models, §1.9 settings keys, LUT math, naming, catalog *types* (not Planned rows).

- 🔲 `[AGENT]` Promote expect/actual-safe types from `:pns-core` to KMP `commonMain` (CommandDialMode, FocalMmSlot, ImagingProfile, export JSON, histogram/zebra *math*). **Do not** move Camera2 / `DngCreator` / `PreviewEngineScreen`
- **Parity:** JVM tests still pass; Android app still composes
- **Design fidelity:** no chrome change
- **Gate:** `pns_verify_toolchain.ps1 -RunTests`

### MIG.2 — iOS application skeleton + tokens + permissions

Inventory: C1, §1.11, §1.14, Q9.

- 🔲 `[AGENT]` iOS target (SwiftUI) with charcoal/`#FF5C00`/JetBrains Mono tokens, Info.plist Camera/Mic/Photos/Location/Local Network, welcome analogue
- 🔲 `[HUMAN]` Apple Developer / TestFlight setup when code exists
- **Parity:** C1 Fallback complete; no capture yet
- **Design fidelity:** tokens match brief; no light finder chrome
- **Gate:** iOS simulator launch + permission strings present; screenshot of empty chrome shell vs brief stack (bands may be placeholders)

### MIG.3 — iOS AVFoundation capture (stills + video + ProRAW)

Inventory: §1.5, C2, Q3, Q2.

- 🔲 `[AGENT]` Preview session, JPEG/HEIF, **ProRAW**, H.264/HEVC at advertised formats; hide RAW-video and Camera2 DNG
- 🔲 `[ADB]` Device capture of JPEG + ProRAW + a short movie; pull files
- **Parity:** PARITY_CHECKLIST §D still/video rows for iOS
- **Design fidelity:** n/a (pipeline)
- **Gate:** ProRAW opens in Photos; no crash on denied mic (video mute)

### MIG.4 — iOS shooting chrome (IA)

Inventory: §1.3–1.4, C2, D1–D23, Q4, preview style guide.

- 🔲 `[AGENT]` Eight-band stack: island/safe-area slot, 3:4 finder, readout (photo vs video chips), 7×3 flow (Settings r2c6, empty hidden slots), tray (center shutter, mode toggle left of dial)
- 🔲 `[ADB]` Device screenshots portrait + landscape vs DESIGN_BRIEF (flow, not pixels)
- **Parity:** C2, D1–D23 present or honestly omitted
- **Design fidelity:** J + K handheld list in PARITY_CHECKLIST
- **Gate:** side-by-side Android/iOS screenshots in `hfr-runs/`; HUMAN visual OK or Deviation

### MIG.5 — iOS controls, dial, overlays

Inventory: §1.4.4–1.4.6, §1.3.5 overlays, Q6, Q7.

- 🔲 `[AGENT]` Dial A/M/H/S/BKT/Macro/QR/Dual-if-available; Night/Bokeh Fallback or hide; MONO hide unless hardware; ISO/SS/WB/AF; Vision eye-AF; histogram/peaking/zebras/grids/horizon
- 🔲 `[ADB]` Exercise dial + overlay toggles on device
- **Parity:** PARITY_CHECKLIST §C and §E iOS column
- **Design fidelity:** overlays clipped to finder band; root-blue absent
- **Gate:** hide-unavailable behavior (`FleetUiVisibilityGate` equivalent); Vision overlay on a face scene

### MIG.6 — iOS gallery + library desk

Inventory: C3, C4, §1.6–1.8, Q11.

- 🔲 `[AGENT]` PhotoKit album `PointAndShoot`; gallery pager; library desk tabs; no journal UI (PARTIAL)
- **Parity:** §F iOS
- **Design fidelity:** 3:4 review tiles; charcoal/orange chrome language
- **Gate:** capture → appears in album; share/delete; DNG/ProRAW review of imported files

### MIG.7 — iOS settings, recipes, connectivity

Inventory: §1.9, D28–D29, Concert/Museum/Airplane, tether §1.13, Q12.

- 🔲 `[AGENT]` Settings search; HUD flags; Concert/Museum/Airplane; **not** Astro/Document/motion trip; SettingsExportBundle import/export; tether client to Android `:28765`/`:28770`
- **Parity:** §G, recipes except PARTIAL
- **Design fidelity:** settings style guide colors
- **Gate:** export JSON from Android imports on iOS (and reverse); airplane-safe suppresses donate prompt

### MIG.8 — Desktop companion shell (CMP)

Inventory: Q1, §1.18, TECH_DECISION desktop column.

- 🔲 `[AGENT]` Compose Multiplatform window: Tether / Library / Camera(UVC) / Presets / About — **not** 7×3 home
- **Parity:** PARITY_CHECKLIST K
- **Design fidelity:** charcoal/orange, not a phone frame
- **Gate:** host launch on Linux or Windows CI/agent; screenshot of nav

### MIG.9 — Desktop tether + MJPEG + UVC host

Inventory: §1.13 ports, Q1, Q8, TETHER_API.

- 🔲 `[AGENT]` Tether client `:28765` capture/flash/status; MJPEG `:28770` live view; UVC host picker + non-DNG record
- 🔲 `[ADB]` Phone running preview + desktop tether session (one serial; force-stop after)
- 🔲 `[HUMAN]` Provide a USB webcam or phone-as-gadget when UVC host is proven
- **Parity:** §H UVC host Durable; gadget Omit; files not labeled DNG
- **Design fidelity:** live view is a pane, not the eight-band phone stack as home
- **Gate:** `POST /capture` produces a phone still; UVC clip plays in OS player

### MIG.10 — Desktop library + preset sync

Inventory: C4, Q12, §1.6.

- 🔲 `[AGENT]` Library desk on desktop; SettingsExportBundle round-trip with Android and iOS
- **Parity:** §F desktop, §G
- **Design fidelity:** desk tabs match C4 names
- **Gate:** import Android export; cull/rate a DNG from disk

### MIG.11 — Honesty gates (Omit / Fallback / PARTIAL) on all targets

Inventory: §1.19, DESIGN_BRIEF tables, Q7, Q10, Q16–Q18.

- 🔲 `[AGENT]` Single source of hide/Fallback labels consumed by iOS + desktop + Android chrome; PARTIAL items remain non-features everywhere; Planned catalog not on iOS/desktop
- **Parity:** no greyed-out theater; no journal/motion/Astro/Document shipping
- **Design fidelity:** empty 7×3 cells when hidden on handheld
- **Gate:** PARITY_CHECKLIST walkthrough; JVM tests for hide policy

### MIG.12 — Final parity sprint

Inventory: entire checklist.

- 🔲 `[AGENT]` Tick [`docs/migration/PARITY_CHECKLIST.md`](docs/migration/PARITY_CHECKLIST.md) end-to-end; file Deviations for anything waived
- 🔲 `[ADB]` Android: `pns_capture_pipeline_verify` **then** `pns_chrome_ux_gate` (not parallel) to prove freeze
- 🔲 `[ADB]` iOS device: still ProRAW + video + chrome screenshots
- 🔲 `[ADB]` Desktop: tether + UVC smoke
- 🔲 `[HUMAN]` Visual IA sign-off (not pixel-identical); distribution channels Q9 when cutting builds
- **Parity:** every checklist row D/F/O/P or n/a
- **Design fidelity:** DESIGN_BRIEF match-the-brief statement
- **Gate:** this HUMAN list + filled Sign-off table in PARITY_CHECKLIST. **Then** (not before) consider ADR-0011 from TECH_DECISION

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

- **Version:** 2026-11-16 — Multiplatform migration Phases 0–3 docs; MIG.* Phase 4 not started. HUMAN consolidated + AUTO promotions remain.
- **Owner:** Maintainer closes Milestone H after HUMAN list + release sign-off.

## Build plan (Point & Shoot)

**Purpose:** Active milestones and open tasks. Shipped work → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

| SoT | Path |
|-----|------|
| Settings / pipeline | `docs/PNS_TECHNICAL_SETTINGS.md` |
| Regression locks | `docs/AGENT_REGRESSION_MEMORY.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8 |
| Fleet / DNG | `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `AGENTS.md` CRITICAL sections |
| Code review intake | `docs/CODE_REVIEW_PLANNING_INTAKE.json` · [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md) |
| Parity debt | `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` |
| Peer benchmark + M28 program | `docs/CAMERA_APP_PIPELINE_BENCHMARK.md` (Sprint **28.0**) · plan `.cursor/plans/camera_pipeline_benchmark_ba492901.plan.md` |

**Primary device:** OnePlus 12 **CPH2583** · wireless ADB mDNS `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` (`scripts/pns_adb_device.env` — refresh `PNS_ADB_SERIAL` when IP changes).

---

### How agents must execute

1. **One milestone at a time** — **Milestone T** + **H.CRI-5** + **H-RESTORE** + **Milestone 28** closed (agent). **Active:** **Milestone H** (human + residual agent). Serialize **HUMAN** blockers (dual-video framing, secure-camera PRIVACY review, **CRI-033** subjective DCG) per sprint tags below.
2. **Local-first:** Tier 0 `pns_local_dev_parallel.ps1` · Tier 1 `pns_prerelease_gate.ps1 -SkipGradle` · Tier 2 `pns_verify_toolchain.ps1 -RunTests` · Tier 3 USB (one serial) · Tier 4 prerelease + `-IncludeUsb` before ship. See **`docs/LOCAL_FIRST_DEV_LOOP.md`**.
3. **Never ✅ without Appendix A** — host Tier 2 + `ReadLints`; device = `hfr-runs/` on **CPH2583** unless a named lane (e.g. H.7-OP13).
4. **USB hygiene:** capture **then** chrome **sequentially**; `force-stop` after every session (**`AGENTS.md`**).
5. **Hard rules:** No §4a `streamHints` or §2 RAW10-first without USB proof; DNG/chrome/fleet locks in **`.cursor/rules/`**; parity sweep requires **`-Mode Full|Delta`**.

Full rules (multi-agent, git, changelog): prior BUILD_PLAN §How agents must execute items 8–16 — unchanged policy, omitted here for brevity.

---

### Global toolkit

| Gate | Script |
|------|--------|
| Host + tests | `pns_verify_toolchain.ps1 -RunTests` |
| RAW still | `pns_capture_pipeline_verify.ps1` |
| Chrome UX | `pns_chrome_ux_gate.ps1` (`-FocalMmSlot` tele proof) |
| Highlight (H) metering | `pns_highlight_meter_verify.ps1` |
| Aux DNG openability | `pns_aux_dng_capture_analyze.ps1` |
| Milestone H host | `pns_milestone_h_host_gate.ps1` |
| Pre-release | `pns_prerelease_gate.ps1` (`-IncludeUsb`) |
| Fleet matrix | `pns_fleet_matrix_scan.ps1` · `pns_fleet_parity_sweep.ps1 -Mode Delta` |
| M28 peer benchmark | `pns_camera_app_pipeline_scan.ps1` (Sprint **28.0**) |
| Still export closure | `pns_still_export_verify.ps1` · `pns_independent_tonal_verify.ps1` · `pns_mono_capture_verify.ps1` |
| Video format closure | `pns_video_format_test.ps1` · `pns_av1_parity_verify.ps1` · `pns_4k_regular_verify.ps1` |
| HDR10 DCG | `pns_video_hdr10_metadata_verify.ps1` |
| Release | `pns_github_release.ps1` — **`.cursor/skills/github-release/SKILL.md`** |

Full index: **`AGENTS.md`**.

---

## Sprint AUDIT-2026-06-21 — Post-M28 hygiene ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT-2026-06-21](BUILD_PLAN_COMPLETED.md#sprint-audit-2026-06-21--post-m28-hygiene-agent-closed).

---

## Sprint H-RESTORE-2026-06-19 — Highlight (H) metering + pure-HAL DNG ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint H-RESTORE](BUILD_PLAN_COMPLETED.md#sprint-h-restore-2026-06-19--highlight-h-metering--pure-hal-dng-agent-closed).

---

## Sprint AUDIT3-2026-06-18 — CI green + overlay wiring ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT3](BUILD_PLAN_COMPLETED.md#sprint-audit3-2026-06-18--ci-green--overlay-wiring-agent-closed).

---

## Sprint AUDIT2-2026-06-18 — Post-ship hygiene ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT2](BUILD_PLAN_COMPLETED.md#sprint-audit2-2026-06-18--post-ship-hygiene-agent-closed).

---

## Sprint AUDIT-2026-06-18 — Weekly triage ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint AUDIT-2026-06-18](BUILD_PLAN_COMPLETED.md#sprint-audit-2026-06-18--ci--tm-ship-agent-closed).

---

## Milestone T — Template alignment ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Milestone T](BUILD_PLAN_COMPLETED.md#milestone-t--template-alignment).

---

## Sprint TM — Template Migration (bootstrap + modularization) ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Sprint TM](BUILD_PLAN_COMPLETED.md#sprint-tm--template-migration).

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment; agent hygiene + release when owner approves.

### Active at a glance

| Lane | Open work |
|------|-----------|
| **Agent** | **H.6** CRI-032 — ADB overlay seed wired; pixel gate needs **face in frame** (see AUDIT3) |
| **Human** | **H.2–H.5** calibration / accounts / store copy · **H.6/H.8** subjective UX · **H.7-OP13** ACR · **H.9** PRIVACY / signing |

**CRI program:** **H.CRI-0…6** + **H.CRI-5** + **H-RESTORE** archived → [COMPLETED](BUILD_PLAN_COMPLETED.md#milestone-h--completed-sprints). **H.CRI-7** = human (**CRI-032/033/034/035**).

**Last CPH2583 USB (2026-06-21):** Milestone **28** closure + AUDIT — `photo_capture_verify_20260621_043524` · `chrome_ux_gate_20260621_043540` · `parity_sweep_20260621_043558` PASS (0 ship blockers) · M28 Wave C gates (`dual_video`, `spatial_audio`, `extension_handoff_spike`) PASS.

---

### Code review recommendations (2026-06-21)

Full audit: [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md). Host evidence: bootstrap **PASS** · detekt **PASS** (post AUDIT fix) · Tier 0 **FAIL** (`python` missing) · Tier 2 re-run after `pwsh` fallback fix.

| Priority | Item | Owner | Notes |
|----------|------|-------|-------|
| **P1** | Human Milestone H closure | Human | **CRI-032** eye-AF (face in frame) · **CRI-033** H.265 DCG @4K · **CRI-034** store/PRIVACY · **CRI-035** OP13 ACR |
| **P1** | Release cut | Human + agent | **H.9** — `0.14.0-beta.15` shipped in repo; GitHub release + Tier 4 `pns_prerelease_gate.ps1 -IncludeUsb` after human sign-off |
| **P2** | Host toolchain PATH | Agent / maintainer | Install **python** + **ffprobe** on PATH; re-run Tier 0 + VF mediacodec gate |
| **P2** | `dng_aesthetic_gate` self-test | Host | Milestone H host gate — blocked without Python |
| **P2** | Lint baseline ~170 | Hygiene | `updateLintBaseline` when triaging warnings |
| **P2** | `PBI-video.hfr.120-DeliveryHonesty` | M24 | `blocked_unstable` on CPH2583 — matrix truth |
| — | DNG / capture locks | Hard rule | USB proof before §4a / §2 changes |
| — | M28 deferred features | Post-M28 | Panorama, preview shots, comp HDR — see `docs/spikes/` |

**Prior audit (2026-06-17):** Tier 0 **8/8** when Python on PATH · Tier 2 PASS — superseded by host PATH gaps above.

**H.HYGIENE closed** · **H-RESTORE closed** → [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md#milestone-h--completed-sprints).

---

### Sprint H.CRI-7 — Human + release (cross-tagged)

| CRI | Home | Status |
|-----|------|--------|
| **032** | H.6, H.8.1 | Agent — HUD seed fix shipped (AUDIT3); pixel gate **FAIL** without face in frame (`mlFaceHud boxes=0`) |
| **033** | H.8.3 | Human — H.265 DCG @4K colors (fail 2026-05-26) |
| **034** | H.5, H.9 | Human — store copy + PRIVACY/metadata |
| **035** | H.7-OP13 | Human — ACR on OP13 aux DNGs (integrity PASS; scene parity FAIL) |
| Release | H.9 | ❌ Blocked on owner sign-off |

---

### Sprint H.2 — Physical calibration

- 🔲 **[HUMAN]** ColorChecker under controlled illuminant
- ❌ **[AGENT]** `pns_colorchecker_de2000_gate.py` — **blocked on HUMAN**

### Sprint H.3 — Account ownership

- 🔲 **[HUMAN]** Owner GitLab login confirmed

### Sprint H.4 — Signing authority

- 🔲 **[HUMAN]** Keystore custody confirmed

### Sprint H.5 — Publication & community

- 🔲 **[HUMAN] CRI-034** Store listing — `metadata/en-US/` + Play listing
- 🔲 **[HUMAN]** Community announcements

### Sprint H.6 — Subjective UX

- 🔲 **[AGENT] CRI-032** `pns_eye_af_pixel_gate.ps1` — **FAIL** without face in frame (`eye_af_pixel_gate_20260618_183819`; ADB `eyeAfOverlay` seed fixed in AUDIT3)
- 🔲 **[HUMAN]** HUD / LUT aesthetics · immersive mode feel

### Sprint H.7-OP13 — Optional OP13 regression lane

> Legacy device only. Agent automation closed → [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-h7-op13--optional-op13-regression-lane-agent-closed-2026-06-13).

- 🔲 **[HUMAN] CRI-035** ACR sign-off on `hfr-runs/aux_dng_capture_analyze_20260613_014424`

### Sprint H.8 — M14 + M15 subjective sign-off

- 🔲 **[HUMAN] H.8.1** Eye/face overlay on glass
- 🔲 **[HUMAN] H.8.2** Dual-video stacked framing
- 🔲 **[HUMAN] H.8.3 CRI-033** All codecs/scenes — **H.265 DCG @4K** colors fail
- 🔲 **[HUMAN] H.8.5** False color on grey card + highlight scene

### Sprint H.9 — Publication / signing handoff

- 🔲 **[HUMAN] CRI-034** `PRIVACY.md`, `NOTICE`, F-Droid `metadata/` creative review
- 🔲 **[HUMAN]** `leaderboard-ingest/config/signing_pins.json` release cert SHA-256
- ✅ **[AGENT]** GitHub Pages smoke after deploy — `pns_github_pages_smoke.ps1` + `gh run list` **PASS** (2026-06-18)
- ✅ **[AGENT]** Release cut — `v0.14.0-beta.12` minified release APK (~48 MB) on GitHub (2026-06-18; debug-key signed)

**Milestone H gate:** Human checklist **H.2–H.9** + `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583.

---

## Milestone 28 — Feature richness + pipeline parity ✅ CLOSED

Archive: [BUILD_PLAN_COMPLETED.md — Milestone 28](BUILD_PLAN_COMPLETED.md#milestone-28--feature-richness-waves-a-d) (Waves A–D · **beta.13–beta.15** · catalog **v6** · USB CPH2583 `b5214fc6` 2026-06-21).

---

Parity intake refresh: `pns_fleet_parity_sweep.ps1 -Mode Delta` · promote `PBI-*` from `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`.

<!-- AUTO_HAL_HONESTY_GAPS_START -->
- Generated: 2026-06-21T04:37:53Z
- Source: `hfr-runs/parity_sweep_20260621_043558`
- Open honesty gaps: **0**
<!-- AUTO_HAL_HONESTY_GAPS_END -->

---

## Appendix A — Verification protocol

1. Tier 2 `pns_verify_toolchain.ps1 -RunTests` (or sprint-specific gate)
2. `ReadLints` on touched Kotlin
3. USB: artifact under `hfr-runs/` on CPH2583; `force-stop` after session
4. User-visible ship: `CHANGELOG.md` + `changelog_coverage.v1.json` (`pns_changelog_gate.ps1`)

## Appendix B — Shipped baseline (summary)

Template alignment (T), CRI program (0–6), H.CRI-5 monolith extraction, **H-RESTORE** (H metering + pure-HAL DNG), fleet parity (M18–M27), M14 chrome/video, **Milestone 28** (Waves A–D), DNG loadability locks — **BUILD_PLAN_COMPLETED.md** · **KNOWLEDGE_BASE.md**. **Active:** Milestone H (human).

## Appendix C — Quick grep

| Need | Pattern |
|------|---------|
| Open human | `^- 🔲 \[HUMAN\]` |
| Open agent | `^- 🔲 \[AGENT\]` |
| Blocked | `^- ❌` |
| Done | `^- ✅` |
| Sprint headers | `^### Sprint` |

---

## Document control

- **Version:** 2026-06-21 — **Milestone 28** archived (Waves A–D); **Milestone H** remains active.
- **Owner:** Maintainer closes Milestone H after human checklist + release sign-off.

---

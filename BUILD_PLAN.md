## Build plan (Point & Shoot)

**Purpose:** Active milestones and open tasks. Shipped work → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

| SoT | Path |
|-----|------|
| Settings / pipeline | `docs/PNS_TECHNICAL_SETTINGS.md` |
| Regression locks | `docs/AGENT_REGRESSION_MEMORY.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` §8 |
| Fleet / DNG | `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `AGENTS.md` CRITICAL sections |
| Code review intake | `docs/CODE_REVIEW_PLANNING_INTAKE.json` · [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md) |
| Parity debt | `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` |

**Primary device:** OnePlus 12 **CPH2583** · `scripts/pns_adb_device.env` → `PNS_ADB_SERIAL` (Wi‑Fi: `adb-b5214fc6-D4ZwCF._adb-tls-connect._tcp` as of 2026-06-18).

---

### How agents must execute

1. **One milestone at a time** — **Milestone T** + **H.CRI-5** closed (agent); active = **Milestone H** (human + residual agent).
2. **Local-first:** Tier 0 `pns_local_dev_parallel.ps1` · Tier 1 `pns_prerelease_gate.ps1 -SkipGradle` · Tier 2 `pns_verify_toolchain.ps1 -RunTests` · Tier 3 USB (one serial) · Tier 4 prerelease + `-IncludeUsb` before ship. See **`docs/LOCAL_FIRST_DEV_LOOP.md`**.
3. **Never `[x]` without Appendix A** — host Tier 2 + `ReadLints`; device = `hfr-runs/` on **CPH2583** unless a named lane (e.g. H.7-OP13).
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
| Milestone H host | `pns_milestone_h_host_gate.ps1` |
| Pre-release | `pns_prerelease_gate.ps1` (`-IncludeUsb`) |
| Fleet matrix | `pns_fleet_matrix_scan.ps1` · `pns_fleet_parity_sweep.ps1 -Mode Delta` |
| Release | `pns_github_release.ps1` — **`.cursor/skills/github-release/SKILL.md`** |

Full index: **`AGENTS.md`**.

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

**Objective:** Bootstrap template parity + phased Gradle module split. **Gate:** [`scripts/pns_milestone_tm_gate.ps1`](scripts/pns_milestone_tm_gate.ps1) · ADR: [`docs/adr/0009-modular-boundaries.md`](docs/adr/0009-modular-boundaries.md).

Archive: [BUILD_PLAN_COMPLETED.md — Sprint TM](BUILD_PLAN_COMPLETED.md#sprint-tm--template-migration).

**Shipped (agent lane, 2026-06-17):** TM.0 bootstrap shims · TM.1 `modules/*/MODULE.md` + `examples/golden-path/` · TM.2 ADR-0009 · TM.3–TM.7 acyclic library slice (`:pns-core`, `:pns-fleet`, `:pns-capture`, `:pns-preview`) with hub UI + capture session glue in `:app` · TM.8–TM.9 docs + closure gate.

**Deferred (post-TM):** Full `PreviewEngineScreen` / `RawCaptureSupport` / fleet builder extraction — requires `:pns-core` policy interfaces (ADR-0009).

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment; agent hygiene + release when owner approves.

### Active at a glance

| Lane | Open work |
|------|-----------|
| **Agent** | **H.6** eye-AF (face); **H.9** release (owner) |
| **Human** | **H.2–H.5** calibration / accounts / store · **H.6/H.8** subjective UX · **H.7-OP13** ACR · **H.9** PRIVACY / signing / Pages |

**CRI program:** **H.CRI-0…6** + **H.CRI-5** archived → [COMPLETED](BUILD_PLAN_COMPLETED.md#milestone-h--completed-sprints). **H.CRI-7** = human (**CRI-032/033/034/035**).

**Last CPH2583 USB (2026-06-18):** AUDIT2.3 — capture `photo_capture_verify_20260618_110420` + chrome `chrome_ux_gate_20260618_110452` **PASS** (wireless ADB, Gradle 9.5 / AGP 9.1 stack).

---

### Code review recommendations (2026-06-17)

Full audit: [`docs/CODE_REVIEW.md`](docs/CODE_REVIEW.md). Host evidence: Tier 0 **7/7** · Tier 2 **`pns_verify_toolchain.ps1 -RunTests` PASS** (2044 JVM tests).

| Priority | Item | Owner | Notes |
|----------|------|-------|-------|
| **P1** | Human Milestone H closure | Human | **CRI-032** eye-AF (face in frame) · **CRI-033** H.265 DCG @4K colors · **CRI-034** store/PRIVACY · **CRI-035** OP13 ACR |
| **P1** | Release cut | Human + agent | **H.9** — owner sign-off then `pns_github_release.ps1` + Tier 4 `pns_prerelease_gate.ps1 -IncludeUsb` |
| **P2** | `dng_aesthetic_gate` self-test | Host | Milestone H host gate: scene exposure delta vs ReferenceCam fixtures — **not** loadability |
| **P2** | `still.independent_tonal` | M27.1 | Parity `not_proven` — verify script or fleet hide |
| **P2** | Lint baseline ~170 | Hygiene | `updateLintBaseline` when triaging warnings |
| **P2** | `PBI-video.hfr.120-DeliveryHonesty` | M24 | `blocked_unstable` on CPH2583 — matrix truth, not a code bug |
| — | DNG / capture locks | Hard rule | USB proof before §4a / §2 changes |

**H.HYGIENE closed** → [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md#sprint-hhygiene--host-gates-agent-closed-2026-06-17).

---

### Sprint H.CRI-7 — Human + release (cross-tagged)

| CRI | Home | Status |
|-----|------|--------|
| **032** | H.6, H.8.1 | Human — face in frame for eye-AF pixel gate |
| **033** | H.8.3 | Human — H.265 DCG @4K colors (fail 2026-05-26) |
| **034** | H.5, H.9 | Human — store copy + PRIVACY/metadata |
| **035** | H.7-OP13 | Human — ACR on OP13 aux DNGs (integrity PASS; scene parity FAIL) |
| Release | H.9 | Blocked on owner sign-off |

---

### Sprint H.2 — Physical calibration

- [ ] **[HUMAN]** ColorChecker under controlled illuminant
- [ ] **[AGENT]** `pns_colorchecker_de2000_gate.py` — **blocked on HUMAN**

### Sprint H.3 — Account ownership

- [ ] **[HUMAN]** Owner GitLab login confirmed

### Sprint H.4 — Signing authority

- [ ] **[HUMAN]** Keystore custody confirmed

### Sprint H.5 — Publication & community

- [ ] **[HUMAN] CRI-034** Store listing — `metadata/en-US/` + Play listing
- [ ] **[HUMAN]** Community announcements

### Sprint H.6 — Subjective UX

- [ ] **[AGENT] CRI-032** `pns_eye_af_pixel_gate.ps1` — needs **face in frame** (last fail: no face, 2026-06-13)
- [ ] **[HUMAN]** HUD / LUT aesthetics · immersive mode feel

### Sprint H.7-OP13 — Optional OP13 regression lane

> Legacy device only. Agent automation closed → [COMPLETED](BUILD_PLAN_COMPLETED.md#sprint-h7-op13--optional-op13-regression-lane-agent-closed-2026-06-13).

- [ ] **[HUMAN] CRI-035** ACR sign-off on `hfr-runs/aux_dng_capture_analyze_20260613_014424`

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing
- [ ] **[HUMAN] H.8.3 CRI-033** All codecs/scenes — **H.265 DCG @4K** colors fail
- [ ] **[HUMAN] H.8.5** False color on grey card + highlight scene

### Sprint H.9 — Publication / signing handoff

- [ ] **[HUMAN] CRI-034** `PRIVACY.md`, `NOTICE`, F-Droid `metadata/` creative review
- [ ] **[HUMAN]** `leaderboard-ingest/config/signing_pins.json` release cert SHA-256
- [ ] **[HUMAN]** GitHub Pages smoke after deploy
- [ ] **[AGENT]** Release cut when owner approves — `pns_github_release.ps1` · Tier 4 `pns_prerelease_gate.ps1`

**Milestone H gate:** Human checklist **H.2–H.9** + `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583.

---

Parity intake refresh: `pns_fleet_parity_sweep.ps1 -Mode Delta` · promote `PBI-*` from `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`.

<!-- AUTO_HAL_HONESTY_GAPS_START -->
- Generated: 2026-06-13T01:17:06.8380173Z
- Source: `hfr-runs/parity_sweep_20260613_011027`
- Open honesty gaps: **0**
<!-- AUTO_HAL_HONESTY_GAPS_END -->

---

## Appendix A — Verification protocol

1. Tier 2 `pns_verify_toolchain.ps1 -RunTests` (or sprint-specific gate)
2. `ReadLints` on touched Kotlin
3. USB: artifact under `hfr-runs/` on CPH2583; `force-stop` after session
4. User-visible ship: `CHANGELOG.md` + `changelog_coverage.v1.json` (`pns_changelog_gate.ps1`)

## Appendix B — Shipped baseline (summary)

Template alignment (T), CRI program (0–6), H.CRI-5 monolith extraction + T.14 mock mode, fleet parity (M18–M27), M14 chrome/video, DNG loadability locks — **BUILD_PLAN_COMPLETED.md** · **KNOWLEDGE_BASE.md**.

## Appendix C — Quick grep

| Need | Pattern |
|------|---------|
| Open human | `^- \[ \] \[HUMAN\]` |
| Open agent | `^- \[ \] \[AGENT\]` |
| Sprint headers | `^### Sprint` |

---

## Document control

- **Version:** 2026-06-18 — AUDIT2 + Dependabot Gradle stack closed; human Milestone H remains.
- **Owner:** Maintainer closes Milestone H after human checklist + release sign-off.

---

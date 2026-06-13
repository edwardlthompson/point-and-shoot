# Code review planning intake (full project)

**Generated:** 2026-06-13 · **Schema:** `docs/CODE_REVIEW_PLANNING_INTAKE.json` (machine-readable)  
**Audience:** Planning agent — promote `CRI-*` rows into `BUILD_PLAN.md` sprints (same pattern as `FLEET_PARITY_BUILD_PLAN_INTAKE.json`).

**Scope:** Entire repository (app, fleet, scripts, CI, docs, tests) — not limited to Milestone T/H delta.

---

## Runtime evidence (host, 2026-06-13)

| Gate | Result |
|------|--------|
| `:app:detekt` | **PASS** (post H.CRI-4 burn-down) |
| `:app:lintDebug` | **PASS** (with ~170 baselined issues) |
| `pns_milestone_h_host_gate.ps1 -SkipGradle` | **PASS** (2026-06-13 post H.CRI-0) |
| CPH2583 USB | RAW video **PASS** (`raw_video_verify_20260612_210437`); Full parity **PASS** (`parity_sweep_20260613_011027`, shipBlockers=0) |
| OP13 USB session | Aux DNG **PASS**; parity color **FAIL** (scene); eye-AF **FAIL** (no face) — **CRI-009/035/032 remain open** |

---

## Workstreams (planning order)

| ID | Title | Priority | Suggested milestone |
|----|-------|----------|---------------------|
| **WS-DNG-DOCS** | DNG locks + agent doc alignment | P1 | Immediate / H.1a |
| **WS-FIXTURES** | ReferenceApp fixture SoT | P1 | H.7-OP13 / 13.3f |
| **WS-GATES** | Automation SKIP/exit + host gates | P1 | H / T.12 |
| **WS-CAPTURE** | video.raw* parity honesty | P1 | **H.1a** (active) |
| **WS-MONOLITH** | PreviewEngineScreen extraction | P2 | **T.13** (deferred) |
| **WS-TEST-DEBT** | JVM tests + detekt/lint | P2 | T.7 / M27 |
| **WS-FLEET** | Matrix builder, OP13 pack, catalog tests | P2 | M16–M18 |
| **WS-CI-SEC** | CI filters, PS7 split, security | P3 | T.2 / T.11 |
| **WS-HUMAN** | Human sign-off backlog | P3 | H.5–H.9 |

---

## Critical findings (fix first)

| ID | Issue | Evidence |
|----|-------|----------|
| **CRI-001** | `AGENTS.md` says `allowPhysicalTotalResultPairing=true`; code has **6× false**; lock rule says false | grep + agent mis-ship risk |
| **CRI-006** | `video.raw_picker` **SHIP_BLOCKER** `session_failed` on OP13 Full sweep | BUILD_PLAN H.1a auto block |
| **CRI-008** | `referenceapp_legacy_sku/` **not in repo**; scripts assume it | Only `referenceapp_cph2655/` checked in |
| **CRI-010** | `pns_release_asset_check` **FAIL** instead of SKIP when no release APK | Milestone H host gate blocked |
| **CRI-011** | `pns_legacy_regression_pack.ps1` **missing**; docs reference it | Glob 0 files |
| **CRI-015** | `PreviewEngineScreen.kt` **~22.5k lines** — highest regression blast radius | Line count / T.13 deferral |

---

## Full item index

See **`CODE_REVIEW_PLANNING_INTAKE.json`** → `items[]` (35 rows, `CRI-001` … `CRI-035`).

Each item includes: `workstream`, `severity`, `workType`, `paths`, `issue`, `evidence`, `suggestedFix`, `suggestedMilestone`, `status`.

### workType triage (for sprint assignment)

| workType | Count | Planning hint |
|----------|------:|---------------|
| DocSync | 4 | Same-commit doc fixes; no USB |
| AppFeature | 4 | USB proof on CPH2583 or named lane |
| AutomationProof | 10 | Script/orchestrator; host rerunnable |
| TestCoverage | 8 | JVM/golden; pairs with T.7 |
| Refactor | 2 | T.13; do not mix with capture hotfixes |
| Policy | 4 | Constants + regression tests |
| Security | 3 | CI / secrets hygiene |
| Human | 4 | Irreducible; stay in Milestone H |

---

## Promotion workflow (planning agent)

1. Read `docs/CODE_REVIEW_PLANNING_INTAKE.json` and active **`BUILD_PLAN.md`**.
2. For each **P1 workstream**, create or extend sprints with `CRI-*` IDs in row text.
3. **Do not** close `CRI-*` in JSON until Appendix A evidence exists (artifact path or gate PASS).
4. After promotion, run `pns_template_doc_link_check.ps1` if docs touched.
5. Parity-affecting items → refresh `FLEET_PARITY_BUILD_PLAN_INTAKE` after USB proof.

**Promoted:** 2026-06-13 → BUILD_PLAN sprints **H.CRI-0…7** + cross-tags on H.1a, H.6, H.7-OP13, H.5, H.8, H.9.

**Do not** set `status: closed` in JSON until matching BUILD_PLAN row is `[x]` with Appendix A evidence.

---

## BUILD_PLAN sprint map

| Sprint | CRI IDs | USB | BUILD_PLAN section |
|--------|---------|-----|-------------------|
| **H.CRI-0** | 001, 008, 010, 028, 029 | Host only | Host unblockers |
| **H.1a + H.CRI-1** | 006, 007, 009, 035 | CPH2583 → OP13 | H.1a auto HAL + dual USB proof |
| **H.CRI-2** | 002, 003, 004, 005, 019 | CPH2583 if session touched | DNG pipeline hardening |
| **H.CRI-3** | 011, 012, 013, 014, 031 | OP13 pack | Automation hygiene |
| **H.CRI-4** | 017, 018, 021, 022, 023, 024 | Host optional | Fleet quality + detekt/lint |
| **H.CRI-5** | 015, 016, 030 (+ T.13–T.14) | Per slice | T.13 monolith extraction |
| **H.CRI-6** | 020, 025, 026, 027 | Host / CI | CI, security, Paparazzi |
| **H.CRI-7** | 032, 033, 034 | Human | Cross-tagged H.5–H.9 |

Cross-tagged only (no separate sprint rows): **CRI-032** → H.6/H.8.1 · **CRI-033** → H.8.3 · **CRI-034** → H.5/H.9 · **CRI-035** → H.7-OP13 + H.CRI-1.

---

## Cross-links

| Doc | Role |
|-----|------|
| `BUILD_PLAN.md` | Active sprints |
| `docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json` | Catalog parity debt |
| `docs/AGENT_REGRESSION_MEMORY.md` | USB-proven regressions |
| `docs/REVERTED_FEATURES_RESTORE_LIST.md` | Capture bisect locks |
| `KNOWLEDGE_BASE.md` | SoT index (refresh after promotion) |

---

*This intake is additive — it does not replace parity debt or BUILD_PLAN human rows.*

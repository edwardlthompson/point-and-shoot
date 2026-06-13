## Build plan (Point & Shoot)

**Purpose:** Milestones → sprints → gates. Active work here; shipped bodies in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)**.

- **Settings truth:** `docs/PNS_TECHNICAL_SETTINGS.md` — update on every settings/pipeline change
- **Audit log:** `PROBE_BUILD_PLAN.md` §5/§6 · `CHANGELOG.md` · `CLI_BUILD_AND_SIDELOAD.md` · `docs/REVERTED_FEATURES_RESTORE_LIST.md` (bisect locks §8) · **`docs/AGENT_REGRESSION_MEMORY.md`** (append on proven fixes) · **`KNOWLEDGE_BASE.md`** · **`AGENT_MEMORY.md`** · **`PROMPT_LIBRARY.md`** · **`docs/adr/`** · **`docs/LOCAL_FIRST_DEV_LOOP.md`** · **`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`** · **`PRIVACY.md`**
- **Fleet/DNG:** `docs/FLEET_DEVICE_CAPABILITY_MATRIX.md` · `docs/FLEET_DEVICE_VERIFY_MATRIX.md` · `docs/FLEET_ONEPLUS13_RAW_POLICY.md` (legacy plugin) · `docs/DNG_OPENABILITY_REGRESSIONS.md` · `docs/RAW_REFERENCE_APP_MATRIX.md` · `docs/M13_7_GATE.md` · `docs/M14_12_DUAL_VIDEO.md`

---

### How agents must execute

1. **One milestone at a time.** **Milestone T** is **complete (agent lane)** — active agent work is **Milestone H** including the **H.CRI-0…7** code-review fix program ([`docs/CODE_REVIEW_PLANNING_INTAKE.json`](docs/CODE_REVIEW_PLANNING_INTAKE.json)); human rows H.2–H.9 run in parallel. **T.13–T.14** are active only under **H.CRI-5** (not deferred). Finish every sprint before starting the next.
2. **Tasks in order within a sprint.** Blockers → log in `PROBE_BUILD_PLAN.md` §5.
3. **Local-first tiers** (see **`docs/LOCAL_FIRST_DEV_LOOP.md`**): **Tier 0** `pns_local_dev_parallel.ps1` while editing · **Tier 1** `pns_prerelease_gate.ps1 -SkipGradle` before doc/metadata commits · **Tier 2** `pns_verify_toolchain.ps1 -RunTests` when Kotlin/Gradle touched · **Tier 3** USB on one serial (matrix below) · **Tier 4** `pns_prerelease_gate.ps1` (+ `-IncludeUsb` when device online) before ship.
4. **After each sprint:** run the sprint gate listed under that sprint (or Tier 0–2 minimum for host-only rows). On failure, stop and fix.
5. **After all sprints:** run the Milestone gate before proceeding.
6. **Tick rules:** Never `[x]` without Appendix A. Host: Tier 2 + `ReadLints`. Device: §5 evidence on **CPH2583** unless an optional lane is explicitly named (e.g. H.7-OP13).
7. **Device truth:** Do not mark **`[AGENT]`** / **`[ADB]`** complete without USB proof. If no device is online, leave the row open and state that verification was not run.
8. **UI gate:** Visible chrome changes → **`assembleDebug`**, sideload, `pns_device_screencap.ps1` proof. Preview chrome layout is **locked** (`.cursor/rules/preview-chrome-ui-lock.mdc`) — behavioral fixes only unless the user explicitly requests a layout change.
9. **USB hygiene:** One **`PNS_ADB_SERIAL`** at a time; never run **`pns_capture_pipeline_verify`** and **`pns_chrome_ux_gate`** in parallel on the same serial; prefer **`pns_usb_gate_mutex.ps1`** when orchestrating. **`adb shell am force-stop dev.pointandshoot`** after every USB session (battery rule — **`AGENTS.md`**).
10. **Multi-agent parallel:** Before **2+ concurrent agents**, read **`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`** and **`.cursor/rules/multi-agent-parallel.mdc`**. Use **`pns_agent_worktree_bootstrap.ps1`** (`feature/agent-<slug>`); no overlapping file paths; one integrator merges shared schema files before parallel feature work.
11. **Parity sweep:** **`pns_fleet_parity_sweep.ps1 -Mode Full|Delta`** — **`-Mode` is required**; ask maintainer if not specified (**`AGENTS.md`** CRITICAL — Fleet Parity Sweep).
12. **JAVA_HOME / ADB:** Android Studio JBR; `platform-tools` first; `scripts/pns_adb_device.env` for `PNS_ADB_SERIAL`.
13. **Git:** commit + push after each numbered milestone gate passes (agent lane). Human-only Milestone H closure does not require agent commits.
14. **Hard rules — do not regress:** Read **`docs/AGENT_REGRESSION_MEMORY.md`** before capture/DNG/preview/fleet edits; append a **`REG-*`** row after USB-proven fixes. No `automationSuppressFacePipeline` for sequential RAW alone; no §4a `streamHints` or §2 RAW10-first `Default` without USB proof; capture/session/DNG changes → `pns_capture_pipeline_verify.ps1` on **CPH2583**; settings changes → update `docs/PNS_TECHNICAL_SETTINGS.md` same commit; user-visible ship → update **`CHANGELOG.md`** + **`scripts/changelog_coverage.v1.json`** same commit (gate: **`pns_changelog_gate.ps1`**). Fleet/DNG/chrome locks: **`AGENTS.md`**, **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8, **`.cursor/rules/`** (`dng-*`, `dodge-tele-focal-routing`, `fleet-generic-policy`, `fleet-ui-visibility`, `preview-chrome-ui-lock`, `preview-readout-video-mode-lock`, `changelog-coverage`, `multi-agent-parallel`).
15. **Archive:** Completed agent tasks → summarize under the matching **feature category** in `BUILD_PLAN_COMPLETED.md` (Milestone T → §29). Human rows stay in Milestone H.
16. **Session checkpoint:** At milestone start, read `.cursor-session-state` if present (schema: `.cursor-session-state.example`; rule: **`.cursor/rules/session-checkpoint.mdc`**). At milestone end or handoff, write current milestone, sprint, last gates, device serial, open blockers; refresh **`AGENT_MEMORY.md`**; instruct fresh chat to read checkpoint, then delete the file. Bisect history stays in `docs/AGENT_REGRESSION_MEMORY.md` only.

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
| `scripts/pns_template_doc_link_check.ps1` | Validate KNOWLEDGE_BASE + DECISION_LOG relative links (Milestone T) |
| `scripts/pns_prerelease_gate.ps1` | Pre-release orchestrator — toolchain, changelog, SBOM, DNG fixtures, metadata (Milestone T) |
| `scripts/pns_repro_build_verify.ps1` | Reproducible build host smoke (Milestone T) |
| `scripts/pns_perf_budget_host_gate.ps1` | PERFORMANCE_BUDGETS.md ↔ PerfBudget.kt drift (Milestone T) |
| `scripts/pns_fdroid_metadata_validate.ps1` | F-Droid metadata + en-US store assets (Milestone T) |
| `scripts/pns_local_dev_parallel.ps1` | Tier 0 parallel host gates (Milestone T.15) |
| `scripts/pns_agent_worktree_bootstrap.ps1` | Parallel agent worktree + `feature/agent-*` branch |
| `scripts/pns_changelog_gate.ps1` | CHANGELOG ↔ `changelog_coverage.v1.json` ↔ `versionCode` |
| `scripts/pns_usb_gate_mutex.ps1` | Serialize USB gates on one ADB serial |
| `scripts/pns_github_release.ps1` | Release prep/publish — **`.cursor/skills/github-release/SKILL.md`** |
| `scripts/pns_milestone_h_host_gate.ps1` | Milestone **H** agent host lane (fixtures, aesthetic, keystore verify) |
| `scripts/pns_milestone_t_gate.ps1` | Milestone **T** closure — Tier 0 + prerelease host (`-SkipGradle`) |

Full script index: **`AGENTS.md`**. Local-first tier matrix: **`docs/LOCAL_FIRST_DEV_LOOP.md`**. **Primary fleet USB device:** OnePlus 12 **CPH2583** (not CPH2655 unless OP13 regression lane).

### Parity intake queue (auto-generated)

Every parity sweep / regression pack tier-2 refreshes:

- [`docs/FLEET_PARITY_DEBT_LEDGER.json`](docs/FLEET_PARITY_DEBT_LEDGER.json) — deduplicated debt with `workType` triage
- [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) — actionable `PBI-{catalogId}-{workType}` rows for sprint promotion

Scripts: `pns_parity_debt_ledger_refresh.ps1` · `pns_parity_build_plan_intake.ps1` (wired at end of `pns_fleet_parity_sweep.ps1`).

**Promotion rule:** move scoped `PBI-*` rows into **Milestone H** (agent or human rows) or **Future features (deferred)**; mark `status=closed` in intake JSON when USB proof passes. **Code review intake:** promote `CRI-*` from [`docs/CODE_REVIEW_PLANNING_INTAKE.json`](docs/CODE_REVIEW_PLANNING_INTAKE.json) into sprints when scheduling engineering work (full-project audit 2026-06-13). **CRI program:** all promoted `CRI-*` rows live under **H.CRI-0…7** or are cross-tagged on existing H sprints; set `status=closed` in intake JSON only with Appendix A artifact path. Full mapping: [`docs/CODE_REVIEW_PLANNING_INTAKE.md`](docs/CODE_REVIEW_PLANNING_INTAKE.md) § BUILD_PLAN sprint map.

### Future features (deferred — unscheduled)

Add new requests here only when explicitly scheduled.

**Milestone T backlog (deferred, not blockers):**

- **T.13–T.14** — promoted to **Sprint H.CRI-5** (2026-06-13 CRI intake). **T.14** mock/demo implementation remains gated on H.CRI-5 slice 1 + ADR-0008 (**CRI-030**).

Promote other deferred items to active BUILD_PLAN when explicitly scheduled.

---

## Milestone T — Project template alignment ✅ CLOSED (agent lane)

**Closed:** 2026-06-12 · **Gate:** `scripts/pns_milestone_t_gate.ps1` PASS (host) · **Archive:** [BUILD_PLAN_COMPLETED.md — Milestone T](BUILD_PLAN_COMPLETED.md#milestone-t--template-alignment)

| Scope | Status |
|-------|--------|
| Sprints **T.1–T.12**, **T.15** (agent) | ✅ Complete |
| Sprints **T.13–T.14** | Deferred → Future features above |
| **T.10** store copy creative review | → **[HUMAN] H.5** |
| Owner **PRIVACY** + metadata sign-off | → **[HUMAN] H.9** |

**Closure commands:**

```powershell
.\scripts\pns_milestone_t_gate.ps1          # milestone sign-off (host)
.\scripts\pns_local_dev_parallel.ps1        # Tier 0 while editing
.\scripts\pns_prerelease_gate.ps1 -SkipGradle
```

Full sprint checklist and deliverables: **BUILD_PLAN_COMPLETED.md** (Milestone T section).

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment: creative, security, perceptual. Agent rows follow **local-first tiers** (§ How agents must execute) and **`.cursor/rules/`** locks — no preview chrome layout edits without explicit user request.

**Agent sprint gates (host):** Tier 0 → Tier 1 after doc/metadata edits · Tier 2 when Kotlin touched · **`pns_milestone_h_host_gate.ps1`** before claiming agent lane complete (use **`-SkipGradle`** only when Detekt baseline is known-red).

**Agent sprint gates (USB, CPH2583):** One serial; capture then chrome **sequentially**; **`force-stop`** after session. Optional USB subset: **`pns_prerelease_gate.ps1 -IncludeUsb`**.

### CRI program overview (35 items — audit 2026-06-13)

**Intake:** [`docs/CODE_REVIEW_PLANNING_INTAKE.json`](docs/CODE_REVIEW_PLANNING_INTAKE.json) · **USB order:** CPH2583 first, OP13 (`8bf09993`) second · **Locks:** DNG loadability, metadata pairing (`false`), dodge tele 73/85/150, preview chrome (behavioral only).

| Sprint | CRI IDs | USB |
|--------|---------|-----|
| **H.CRI-0** | 001, 008, 010, 028, 029 | Host only |
| **H.1a + H.CRI-1** | 006, 007, 009, 035 | CPH2583 → OP13 sequential |
| **H.CRI-2** | 002, 003, 004, 005, 019 | CPH2583 if session touched |
| **H.CRI-3** | 011, 012, 013, 014, 031 | OP13 pack + Tier 0 PS5.1/PS7 |
| **H.CRI-4** | 017, 018, 021, 022, 023, 024 | Host; matrix USB optional |
| **H.CRI-5** | 015, 016, 030 (+ T.13–T.14) | Per extraction slice |
| **H.CRI-6** | 020, 025, 026, 027 | Host / CI |
| **H.CRI-7** | 032, 033, 034 | Human + release lane |

**Program gate (agent):** all H.CRI-0…6 archived → `pns_milestone_h_host_gate.ps1` **full** `-RunTests` PASS → `pns_prerelease_gate.ps1 -IncludeUsb` on CPH2583 → owner H checklist.

---

### Sprint H.CRI-0 — Host unblockers ✅ archived 2026-06-13

→ [BUILD_PLAN_COMPLETED.md — H.CRI-0](BUILD_PLAN_COMPLETED.md#sprint-hcri-0--host-unblockers-agent-closed-2026-06-13)

---

### Sprint H.CRI-2 — DNG pipeline hardening ✅ archived 2026-06-13

→ [BUILD_PLAN_COMPLETED.md — H.CRI-2](BUILD_PLAN_COMPLETED.md#sprint-hcri-2--dng-pipeline-hardening-agent-closed-2026-06-13)

---

### Sprint H.CRI-3 — Automation & OP13 regression hygiene ✅ archived 2026-06-13

→ [BUILD_PLAN_COMPLETED.md — H.CRI-3](BUILD_PLAN_COMPLETED.md#sprint-hcri-3--automation-hygiene-agent-closed-2026-06-13) · **OP13 USB re-verify PASS** 2026-06-13 (`8bf09993`: matrix quick + aux DNG openability + PiP + multicam melt; splat fix in `pns_op13_regression_pack.ps1`)

---

### Sprint H.CRI-4 — Fleet quality + static analysis debt ✅ archived 2026-06-13

→ [BUILD_PLAN_COMPLETED.md — H.CRI-4](BUILD_PLAN_COMPLETED.md#sprint-hcri-4--fleet-quality--detekt-agent-closed-2026-06-13)

---

### Sprint H.CRI-5 — T.13 monolith extraction (slice 1 archived; carryover open)

→ [BUILD_PLAN_COMPLETED.md — H.CRI-5 slice 1](BUILD_PLAN_COMPLETED.md#sprint-hcri-5--t13-slice-1-agent-closed-2026-06-13)

- [ ] **[AGENT] T.13 carryover** — SharedPreferences schema migration, fleet JSON validation, prefs round-trip test; further `PreviewEngineScreen` extraction slices
- [ ] **[AGENT] T.14** — Unified mock/demo mode — **deferred until carryover lands**

---

### Sprint H.CRI-6 — CI, security, visual regression ✅ archived 2026-06-13

→ [BUILD_PLAN_COMPLETED.md — H.CRI-6](BUILD_PLAN_COMPLETED.md#sprint-hcri-6--ci--security-agent-closed-2026-06-13)

---

### Sprint H.CRI-7 — Human + release closure (cross-tagged)

Agent support wired; irreducible human judgment stays on existing H rows (**CRI-032/033/034**):

| CRI | BUILD_PLAN home | Agent support | Status |
|-----|-----------------|---------------|--------|
| **CRI-032** | H.6, H.8.1 | Re-run `pns_eye_af_pixel_gate.ps1` when face in frame | **Human** — face required |
| **CRI-033** | H.8.3 | `pns_video_hdr10_metadata_verify.ps1` artifacts | **Human** — DCG @4K colors |
| **CRI-034** | H.5, H.9 | `pns_fdroid_metadata_validate.ps1` after copy edit | **Human** — store/PRIVACY |
| **Release** | H.9 agent row | `pns_github_release.ps1` + **`pns_release_asset_check.ps1 -RequireRelease`** | **Blocked on H.9** |

**Sprint gate:** Owner checklist + Tier 4 **`pns_prerelease_gate.ps1 -IncludeUsb`**

---

### Sprint H.1a — Auto-synced HAL honesty gap fixes (latest Full run)

<!-- AUTO_HAL_HONESTY_GAPS_START -->
- Generated: 2026-06-13T01:17:06.8380173Z
- Source run: C:\Users\edwar\AndroidStudioProjects\point-and-shoot\hfr-runs\parity_sweep_20260613_011027
- Open honesty gaps: 0
- [x] **[AGENT]** No advertised-vs-proven honesty gaps in latest Full run (CPH2583 `hfr-runs/parity_sweep_20260613_011027`).
<!-- AUTO_HAL_HONESTY_GAPS_END -->

#### H.CRI-1 — dual USB proof

- [x] **[AGENT] CRI-006/007** — CPH2583: `pns_raw_video_verify.ps1` PASS (`hfr-runs/raw_video_verify_20260612_210437`); Full parity PASS (`parity_sweep_20260613_011027`)
- [x] **[AGENT] CRI-009** — OP13 `8bf09993`: ProShot forensics `hfr-runs/referenceapp_live_forensics_20260613_013404` → `pns_referenceapp_reference_sync.ps1 -FixtureProfile Cph2655` refreshed `tests/fixtures/referenceapp_cph2655/` + `manifest.json` (CPH2655 serial, DCIM remotes)
- [x] **[AGENT] CRI-035** — Same-session back-to-back capture on OP13: fixtures refreshed then `hfr-runs/aux_dng_capture_analyze_20260613_014424` — **integrity + desktop open PASS**; automated color parity **FAIL** (P&S exposure ~5–8× darker bayer means vs ProShot refs; not a loadability bug) → **human ACR path** (H.7-OP13 below)

**Sprint gate (agent):** Tier 2 if Kotlin touched · refresh parity intake · CPH2583: **`pns_raw_video_verify.ps1`** → **`pns_fleet_parity_sweep.ps1 -Mode Delta`** → **`force-stop`** · OP13: repeat with `-LegacyOp13FleetPolicy` if matrix touched · **`pns_build_plan_honesty_gap_sync.ps1`** · **`pns_capability_catalog_gate.ps1`** for catalog-only engineering rows.

**Still blocked without human:** ColorChecker (H.2), keystore custody (H.4), store copy (H.5), face-in-frame eye-AF (H.6/H.8.1), subjective HUD/codec (H.8).

### Sprint H.2 — Physical calibration capture

- [ ] **[HUMAN]** Set up ColorChecker under controlled illuminant (irreducible — physical setup)
- [ ] **[AGENT]** Trigger ADB capture; run `pns_colorchecker_de2000_gate.py`; assert all Macbeth patches dE2000 < threshold — **blocked on HUMAN** (host self-test archived **2026-06-13** → [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md#sprint-h--host-validators--publication-prep-agent-closed-2026-06-13))

**Sprint gate (agent):** `pns_colorchecker_de2000_gate.py` (host self-test until HUMAN capture exists) · USB row requires **`force-stop`** after capture session.

### Sprint H.3 — Account ownership

- [ ] **[HUMAN]** Confirm you are logged into the owner GitLab account (irreducible — identity custody)

### Sprint H.4 — Signing authority

- [ ] **[HUMAN]** Confirm you hold custody of the keystore file (irreducible — security)

### Sprint H.5 — Publication & community

- [ ] **[HUMAN] CRI-034** Store listing copy — **`metadata/en-US/`** (`title.txt`, `short_description.txt`, `full_description.txt`) + Play listing (carryover from Milestone **T.10**)
- [ ] **[HUMAN]** Community announcements — irreducible public communication

### Sprint H.6 — Subjective UX sign-off

- [ ] **[AGENT] CRI-032** `pns_eye_af_pixel_gate.ps1` — **FAIL** 2026-06-13 OP13 unattended (`hfr-runs/eye_af_pixel_gate_20260612_201445`: no face in frame for overlay markers); prior fail 20260529
- [ ] **[HUMAN]** HUD / LUT default aesthetics — irreducible perceptual
- [ ] **[HUMAN]** Immersive mode feel — irreducible perceptual

**Sprint gate (agent):** Run **alone** on serial (not parallel with capture/chrome gates) · requires **face in frame** for unattended PASS · overlay changes are **behavioral only** (`.cursor/rules/preview-chrome-ui-lock.mdc`) · **`force-stop`** after run.

### Sprint H.7-OP13 — Optional OP13 regression lane

> **`.cursor/rules/fleet-generic-policy.mdc`:** LegacyDevice / OP13 only — not the CPH2583 default fleet program. Enable via **`FleetPolicyPreferences`** or legacy plugin; DNG loadability + metadata pairing locks still apply.
>
> **Agent closed 2026-06-13** (aux DNG, M13.3g-2, matrix scan, Full parity): [BUILD_PLAN_COMPLETED.md — Sprint H.7-OP13](BUILD_PLAN_COMPLETED.md#sprint-h7-op13--optional-op13-regression-lane-agent-closed-2026-06-13)

- [x] **[AGENT] CRI-035** (+ **CRI-008** resolver) `dng_referenceapp_parity_gate.py` — back-to-back OP13 session 2026-06-13: fixtures from ProShot forensics; P&S `aux_dng_capture_analyze_20260613_014424` — **integrity PASS**, color **FAIL** on exposure delta (see `referenceapp_parity_gate.json`); automated agent lane → human ACR
- [ ] **[ADB]** OP13 Lineage/custom Full sweep second variant — **skipped** (device on stock ROM; no custom variant connected)
- [ ] **[HUMAN] CRI-035** ACR on OP13 aux DNGs (`hfr-runs/aux_dng_capture_analyze_20260613_014424`) — optional regression sign-off; **required** for automated color parity closure

**Sprint gate (agent):** Do not overlap with chrome/capture gates on one serial · attach **`pns_fleet_matrix_diff.ps1`** when promoting matrix JSON to repo

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass (14.5 + 15.1) — pixel gate passes; on-face rubber-stamp
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing usability (14.12 + 15.5)
- [ ] **[HUMAN] H.8.3 CRI-033** Owner visual: all codecs/scenes good — **fail:** H.265 **DCG @4K** bad colors (2026-05-26); re-open 15.2 human row
- [ ] **[HUMAN] H.8.5** False color correct on grey card + highlight scene (15.21)

### Sprint H.9 — M25 publication/signing handoff

- [ ] **[HUMAN] CRI-034** Owner sign-off: **`PRIVACY.md`**, **`NOTICE`**, F-Droid **`metadata/`** (carryover from Milestone **T.11**)
- [ ] **[HUMAN]** Populate `leaderboard-ingest/config/signing_pins.json` with release APK cert SHA-256
- [ ] **[HUMAN]** GitHub Pages smoke after deploy (buyer-facing copy, disclosure banner, GSMArena untested labeling)
- [ ] **[AGENT]** Release cut (when owner approves): **`.cursor/skills/github-release/SKILL.md`** → **`pns_github_release.ps1`** · **`pns_release_asset_check.ps1 -RequireRelease`** · Tier 4 **`pns_prerelease_gate.ps1`**

**Agent host pre-handoff archived 2026-06-13:** [BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md#sprint-h--host-validators--publication-prep-agent-closed-2026-06-13)

**Milestone H gate:**

| Lane | Requirement |
|------|-------------|
| **Agent host** | `pns_milestone_h_host_gate.ps1` PASS (or `-SkipGradle` with documented Detekt debt) |
| **Agent USB** | `pns_prerelease_gate.ps1 -IncludeUsb` on **CPH2583** when agent rows claimed |
| **CRI agent program** | H.CRI-0…6 archived in COMPLETED; intake JSON `CRI-*` closed with artifact paths |
| **Human** | Owner-approved checklist (H.2–H.9 human rows) |

---

## Appendix A — Verification protocol (abbreviated)

**Local-first (see `docs/LOCAL_FIRST_DEV_LOOP.md`):** Tier 0/1 for doc-only · Tier 2 below for Kotlin · Tier 3 USB evidence · Tier 4 before release.

1. `pns_verify_toolchain.ps1 -RunTests` → PASSED (Tier 2)
2. `ReadLints` clean on touched Kotlin  
3. Claimed paths/symbols exist  
4. Unit tests: `failures="0" errors="0"`  
5. `CHANGELOG.md` + `scripts/changelog_coverage.v1.json` for user-visible changes (`pns_changelog_gate.ps1`); artifact paths under **`hfr-runs/`** for gates  
6. **[ADB]/[ROOT]:** device evidence on **CPH2583** (or named optional lane); **`force-stop`** after session  
7. **[MIXED]:** parent stays `[ ]` until every child venue is satisfied  
8. **Parallel agents:** asymmetric file scoping + worktree per **`.cursor/rules/multi-agent-parallel.mdc`**

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
| Template alignment (Milestone T) | **Shipped (agent lane 2026-06-12)** — human deferrals → Milestone H |

---

## Appendix D — Template gap matrix

| Template § | Gap count closed by Milestone T | Remaining intentional divergence |
|------------|--------------------------------|----------------------------------|
| §1 Dimensions | 2 | Apache-2.0 not MIT |
| §2 CI/CD | 18 | androidTest → USB scripts; file size global → new-code-only |
| §3 Agent memory | 7 | `.cursorrules` → `.cursor/rules/` |
| Module A F-Droid | 9 | ML Kit exception documented |
| §5 Protocols | 4 | — |
| §6 Directives | 4 deferred (T.13–T.14) | Compose monolith until extraction |
| §7 Pre-release | 6 | USB lanes optional in orchestrator |
| §8 Startup | 5 | — |

---

## Appendix C — Agent quick grep

| Need | Pattern |
|------|---------|
| Open human | `^- \[ \] \[HUMAN\]` |
| Open mixed | `^- \[ \] \[MIXED\]` |
| Sprint headers | `^### Sprint` |
| Open Milestone T | `^## Milestone T` (closed — see BUILD_PLAN_COMPLETED) |

---

## Document control

- **Version:** Active plan **2026-06-13** — CRI fix program (**H.CRI-0…7**) integrated; T.13–T.14 promoted to H.CRI-5; open: Milestone H human + residual CRI agent rows.
- **Owner:** Project maintainer approves Milestone H closure; Milestone T human rows live under H.5 / H.9.

---

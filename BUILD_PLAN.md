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
| `scripts/pns_fleet_matrix_scan.ps1` | USB full matrix pull → `hfr-runs/fleet_matrix_*` (M16) |
| `scripts/pns_fleet_matrix_diff.ps1` | Host diff two matrix JSONs (M16) |
| `scripts/pns_fleet_parity_sweep.ps1` | **Fleet Parity Sweep** — `-Mode Full\|Delta` required (M18.6) |
| `scripts/pns_fleet_regression_pack.ps1` | Tiered matrix + parity Delta + catalog gate (M18.4) |
| `scripts/pns_m18_gate.ps1` | Milestone 18 one-shot host + USB gate |
| `scripts/pns_fleet_macro_export.ps1` | Cross-device macro benchmark CSV (M18.4) |
| `scripts/pns_m25_gate.ps1` | Milestone 25 leaderboard host gate (M25) |
| `scripts/pns_m26_gate.ps1` | Milestone 26 parity closure gate (M26) |
| `scripts/pns_leaderboard_host_smoke.ps1` | Leaderboard JSON/CSV/RSS host smoke (M25) |
| `scripts/pns_capability_catalog_gate.ps1` | Host catalog row / descriptor gate (M18.5) |
| `scripts/pns_leaderboard_site_publish.ps1` | Leaderboard site data build (M25) |
| `scripts/pns_leaderboard_pages_push.ps1` | Publish + push `docs/leaderboard` to GitHub Pages (M25) |
| `scripts/pns_fleet_parity_leaderboard_refresh.ps1` | Host leaderboard JSON/MD from parity sweeps (M25) |

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
| **Milestone 20** | **Archived** — concurrent capture (dual video + Multicam Melt + PiP) |
| **Milestone 22** | **Archived** — parity proof-pack closure + capability provider map |
| **Milestone 23** | **Archived** — fleet hardening + resilience closeout (2026-06-03) |
| **Milestone 24** | **Active** — 4K120 stability and truthfulness (strict route + endurance + parity truth class) |
| **Milestone 25** | **Active** — Camera2 source-of-truth leaderboard (host scaffold landed 2026-06-04; USB pair + ingest + proof gates open) |
| **Milestone 26** | **Active** — Parity closure (auto-intake from sweeps; promote `PBI-*` rows to sprints) |
| **Milestone H** | **Active** — residual **[HUMAN]** work; **H.7** closed **CPH2583** (owner 2026-05-29); OP13 lane optional |
| **Bespoke Gallery (BG.1–BG.3)** | **Archived** — integration + device verify + UX polish (**maintainer sign-off 2026-05-22**) |
| **Audio & Sound (AS.1–AS.3)** | **Archived** — agent + human sign-off **2026-05-22** |
| **User Interface & Experience (UX.1–UX.3)** | **Archived** — theme, nav, workflow, cloud backup **2026-05-25** |

**Chrome lock:** **`docs/preview-chrome-layout-style-guide.md`** — behavioral fixes only unless user requests UI changes.

### Parity intake queue (auto-generated)

Every parity sweep / regression pack tier-2 refreshes:

- [`docs/FLEET_PARITY_DEBT_LEDGER.json`](docs/FLEET_PARITY_DEBT_LEDGER.json) — deduplicated debt with `workType` triage
- [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) — actionable `PBI-{catalogId}-{workType}` rows for sprint promotion

Scripts: `pns_parity_debt_ledger_refresh.ps1` · `pns_parity_build_plan_intake.ps1` (wired at end of `pns_fleet_parity_sweep.ps1`).

**Promotion rule:** move scoped `PBI-*` rows into Milestone 26 sprints below; mark `status=closed` in intake JSON when USB proof passes. Do not auto-append unchecked bullets here (avoids churn).

### Future features (deferred — unscheduled)

Leaderboard remaining work is tracked under **Milestone 25** below. This list stays empty until new product requests land.

---

## Archived milestones (M15–M23)

Milestones **15–23** are complete. Sprint bodies and gate outcomes live in **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** under **Archived milestone sprints (M15–M23)**.

| Milestone | Archived |
|-----------|----------|
| **M15** — Pro Camera Polish | Feature categories + archived sprints |
| **M16** — Fleet Device Capability Matrix | Feature categories + archived sprints |
| **M18** — Fleet max-out framework | 2026-05-30 |
| **M19** — Feature max-out | 2026-05-30 |
| **M20** — Concurrent capture | 2026-05-30 |
| **M21** — Fleet parity honesty | 2026-05-30 |
| **M22** — Proof-pack closure | 2026-06-02 |
| **M23** — Fleet hardening + resilience | 2026-06-03 |

---

## Milestone 24 — 4K120 stability and truthfulness

**Objective:** Give 4K120 the best chance to succeed with a fleet-generic route ladder, strict start truthfulness, endurance evidence, and parity classification that does not over-claim.

**Docs:** `docs/PNS_TECHNICAL_SETTINGS.md` · `docs/AGENT_REGRESSION_MEMORY.md` · `docs/FLEET_PARITY_DEVICE_LEADERBOARD.md`

**Host gate:** `scripts/pns_m24_gate.ps1 -HostOnly` — toolchain + M24 host checks (strict-start and truth-class script paths).

**Device gate (active USB):** capability-class probe (`S0/S1/S2`) → strict `pns_4k120_verify.ps1` truth-aware retries → `pns_4k120_endurance.ps1` (S2 path) → `pns_fleet_parity_sweep.ps1 -Mode Full` with explicit 4K120 truth source/serial metadata.

### Sprint 24.0 — Baseline instrumentation + rubric

- [x] **[AGENT]** Add canonical HFR route telemetry (`hfrWarmupAttempt`, `hfrRoute`, `hfrHealthWindowMs`, `hfrBlockReason`) in preview/video logs and ADB validation lines — extended with `interleaved_sub4k`, `hsCaptureWxH`, `encodePrefWxH`, `mcPrepared`, `strictHfrWarmupHealthy`
- [x] **[AGENT]** Document strict 4K120 success rubric in technical settings (start gate, sustained gate, classification outcomes) — `docs/PNS_TECHNICAL_SETTINGS.md` M24 lane
- [ ] **[ADB]** Smoke run confirms telemetry needles exist in `PNS.Cam` / `PNS.AdbValidation` — **blocked 2026-06-05:** CPH2583 Wi‑Fi ADB offline after reboot; reconnect via `pns_adb_wifi_connect.ps1`

### Sprint 24.1 — HFR route ladder (interleaved ↔ encoder-priority)

- [x] **[AGENT]** Implement bounded route ladder for constrained HS bring-up: interleaved first, encoder-priority fallback on configure instability — `handleCaptureSessionConfigureFailed` + `StrictHfrPolicy.nextConfigureFailAction`
- [x] **[AGENT]** Keep strict behavior: recording start blocked if no route reaches 120-ready health state — `strictHfrWarmupHealthy` / `inAppVideo120StrictBlocked`
- [ ] **[ADB]** `pns_mediacodec_hfr_verify.ps1 -OnlyTest 4K_120fps_MediaCodec -RequireFfprobeAv` with attempt/route telemetry present

### Sprint 24.2 — Strict warmup + retry budget

- [x] **[AGENT]** Add warmup health window before strict 120 start is allowed
- [x] **[AGENT]** Add bounded retry budget before session is marked unstable for runtime window
- [x] **[AGENT]** ADB automation defers `isRecording` until `adbStrictHfrWarmupReady()` — `PreviewEngineScreen.kt`
- [x] **[AGENT]** Add deterministic host coverage for strict pass-after-retry and strict block after budget exhaustion — `StrictHfrPolicyTest`

### Sprint 24.3 — Mid-record resilience

- [x] **[AGENT]** Add one bounded same-fps re-acquire on mid-record HS collapse, then deterministic fail/stop — `fallbackFromUnstableHfr` + `hfrMidRecordOutcome` ADB log
- [x] **[AGENT]** Ensure cleanup prevents zombie recorder/session states — existing `closeCamera` / `deferMcStop` paths unchanged; mid-record sets `hfrMidRecordRecoveryUsed`
- [ ] **[ADB]** Robustness run captures outcome class (`sustained`, `recovered_once`, `blocked_unstable`)

### Sprint 24.4 — Delivery truth gates

- [x] **[AGENT]** Tighten 4K120 gate output with truth classes: `true_4k120`, `hs120_sub4k`, `blocked_unstable` — `pns_mediacodec_hfr_verify.ps1` `TruthClass` + `summary.json`
- [x] **[AGENT]** Wire truth class into parity merge/reporting so fleet scoring reflects delivered class — `pns_fleet_parity_sweep.ps1` `video4k120TruthClass` (see `PNS_TECHNICAL_SETTINGS.md` M24)
- [ ] **[ADB]** Updated `pns_4k120_verify.ps1` / `pns_mediacodec_hfr_verify.ps1` emit truth class in JSON + markdown summary — host wired; **CPH2583** run `hfr-runs/m24_gate_20260605_023318/` blocked_unstable (do not overlap capture/parity on one serial)

### Sprint 24.5 — Fleet policy alignment

- [x] **[AGENT]** Map runtime 4K120 truth to catalog/parity semantics without device-specific hardcoding — parity sweep env handoff + catalog `video.hfr.120`
- [x] **[AGENT]** Ensure no false “ship-ready 4K120” parity state when strict criteria are unmet — strict verify only passes `true_4k120`
- [x] **[AGENT]** Format picker / readout honesty for sub-4K HS @ 4K encode (`VideoDeliveryHonesty`, `video.delivery_honesty` parity row)
- [ ] **[ADB]** `pns_fleet_parity_sweep.ps1 -Mode Full` shows truthful 4K120 classification and gap accounting — rerun alone on CPH2583 after M24 gate

### Sprint 24.6 — Endurance evidence pack

- [x] **[AGENT]** Add `pns_4k120_endurance.ps1` to measure longest sustained 4K120 run and classify terminal bottleneck (`thermal`, `session_disconnect`, `encoder_stall`, `fps_collapse`)
- [x] **[AGENT]** Export timeline + fps trend + terminal reason under `hfr-runs/4k120_endurance_*`
- [ ] **[ADB]** Verify 30s minimum pass path + longest-duration measured artifact — **FAIL** `m24_gate_20260605_023318/endurance/` (`bestPassSec=0`, `session_disconnect_or_encoder_stall`; likely concurrent gate contention)

### Sprint 24.7 — Milestone orchestration + closeout

- [x] **[AGENT]** Add `scripts/pns_m24_gate.ps1` orchestration (host preflight → 4K120 strict → endurance → parity full)
- [x] **[AGENT]** Include mandatory app cleanup (`adb shell am force-stop dev.pointandshoot`) in every path
- [x] **[AGENT]** Update technical settings + regression memory + changelog coverage in same lane — `docs/PNS_TECHNICAL_SETTINGS.md` §Strict 4K120 (2026-06-04)

**M24 gate:** `scripts/pns_m24_gate.ps1` — host pass + strict 4K120 + endurance + parity Full truth-class evidence.

---

## Milestone 25 — Camera2 source-of-truth leaderboard

**Objective:** Public leaderboard + in-app probes so buyers can compare **USB-tested Camera2** capability, resolution withholding, ROM pairing (stock vs custom), and OEM accountability — without treating GSMArena or OEM camera apps as Camera2 truth.

**Agent scaffold (2026-06-04):** App probes (`ResolutionBetrayal`, hub readiness, submit payload), publish pipeline (`product_groups.json`, `oem_accountability.json`, CSV/RSS/catalog export), GitHub Pages UI (`#/product/{groupId}`, OEM page, buyer presets), `docs/CAMERA2_OEM_DISPARITY.md`, `ResolutionBetrayalTest` — see [`docs/leaderboard/README.md`](docs/leaderboard/README.md). **Do not tick rows below without Appendix A + sprint gate evidence.**

**Docs:** [`docs/CAMERA2_OEM_DISPARITY.md`](docs/CAMERA2_OEM_DISPARITY.md) · [`docs/leaderboard/README.md`](docs/leaderboard/README.md) · [`docs/FLEET_PARITY_SWEEP.md`](docs/FLEET_PARITY_SWEEP.md) · [`docs/FLEET_DEVICE_VERIFY_MATRIX.md`](docs/FLEET_DEVICE_VERIFY_MATRIX.md)

**Host gate:** `scripts/pns_leaderboard_site_publish.ps1 -SkipGsmarenaScrape` exit 0 + `scripts/pns_leaderboard_export_catalog.ps1` exit 0 + `ResolutionBetrayalTest` pass in `pns_verify_toolchain.ps1 -RunTests`.

**Device gate:** `pns_fleet_parity_sweep.ps1 -Mode Full` on **CPH2583** (baseline) + **CPH2649** stock **and** Lineage pair when hardware available; attach `hfr-runs/parity_sweep_*` + published `docs/leaderboard/data/`.

### Sprint 25.0 — App probe closeout

- [x] **[AGENT]** Full-tier matrix: rear `lensInfo` gate + hub **Leaderboard readiness** blocks contribute unless full tier + all rear lensInfo + Full sweep (`LeaderboardReadiness.kt`, `FleetMatrixHubScreen.kt`) — JVM `LeaderboardReadinessTest` in `pns_m25_gate.ps1 -HostOnly` **PASS** `hfr-runs/m25_gate_*`
- [x] **[AGENT]** Full parity sweep: `still.resolution_maximum_map` / `still.hidden_highres` sessionOk from `max_resolution_map_jpeg` session probe — **CPH2583** Full sweep `hfr-runs/parity_sweep_20260605_021644/` (rows `not_advertised` on OP12 HAL; evaluators wired; session probe active when advertised)
- [x] **[AGENT]** Promote resolution catalog rows from **INFORMATIONAL** → **SHIP_BLOCKER** after USB proof on ≥2 SKUs (`CameraCapabilityCatalog.kt`; `docs/AGENT_REGRESSION_MEMORY.md` `REG-20260605-003`; Full sweeps `parity_sweep_20260605_105238/` + `parity_sweep_20260605_105702/`)
- [x] **[AGENT]** Hub contribute: `romReported` self-tag + validation; public `#/device/{slug}` URL + copy (`LeaderboardRomReport.kt`, `LeaderboardDeviceSlug.kt`, `FleetLeaderboardSubmit.kt`) — JVM tests pass
- [x] **[ADB]** `PNS.FleetMatrix lensInfo rear=` log after full rescan on **CPH2583**; readiness card green for full tier + Full sweep — `hfr-runs/fleet_matrix_20260605_021552/` (`lensInfo rear=id=2 WIDE; id=3 UW; id=4 TELE`)

### Sprint 25.1 — Priority USB fleet pair (OP12 baseline + OP13 collapse)

- [x] **[ADB]** **CPH2583** Full sweep: confirm high parity + low `resolutionBetrayalIndex` on published profile — `hfr-runs/parity_sweep_20260605_021644/` (`resolutionBetrayalIndex=0`, `718a8115ff142454`, parity 74.2% / 5 ship blockers)
- [ ] **[ADB]** **CPH2649** stock ROM Full sweep → `testedVariants[]` with `romFlavor: stock` — **interim:** CPH2655 stock `parity_sweep_20260605_022500/` + product group entry; confirm SKU alias vs CPH2649 before ship tick
- [ ] **[ADB]** **CPH2649** Lineage (or custom) Full sweep → second `testedVariants[]` entry; product group `#/product/oneplus-13` shows custom vs stock delta
- [x] **[AGENT]** Republish after pair; verify `docs/FLEET_PARITY_DEVICE_LEADERBOARD.json` ranks OP12 vs OP13 honestly — host publish 2026-06-05 (`leaderboard.csv` OP12 rank data present)
- [x] **[AGENT]** Update `docs/FLEET_DEVICE_VERIFY_MATRIX.md` M25 rows with artifact paths

### Sprint 25.2 — GSMArena advertised spec + HAL sensor path

- [x] **[AGENT]** Live `gsmarena_device_specs_scrape.py` success (photo + video table); reduce reliance on `fromSensorCache` fallback after 429 backoff — retry hardening landed (`gsmarena_sensor_scrape.py` + `gsmarena_device_specs_scrape.py`); latest scrape populated live photo/video fields for 4 devices with only CPH2649 still blocked by GSMArena title mismatch (`gsmarena_device_specs.json` errors[])
- [x] **[AGENT]** Prefer HAL `lensInfo` / matrix sensor sum over GSMArena when full-tier matrix present (`Get-MergedSensorSpecs` in `pns_leaderboard_common.ps1`)
- [x] **[AGENT]** Expand `Map-GsmarenaAdvertisedClaims` to `lens.ois`, `lens.tele`, per-lens MP, MSRP/launch when scraped
- [x] **[HOST]** Weekly CI stale flag on site (`site.json` `gsmarenaSpecsStale` / footer note in `app.js`) — host publish emits flags; `pns_m25_gate.ps1 -HostOnly` **PASS**

### Sprint 25.3 — Community ingest + submission merge

- [x] **[AGENT]** Deploy `leaderboard-ingest/` to Render; set `LEADERBOARD_INGEST_URL` in hub / env example — live service `https://pns-leaderboard-ingest-live.onrender.com`; app/env wired in `app/build.gradle.kts` + `scripts/pns_adb_device.env.example`
- [ ] **[HUMAN]** Populate `leaderboard-ingest/config/signing_pins.json` with release APK cert SHA-256 (see ingest README)
- [x] **[AGENT]** End-to-end: app Full sweep submit → ingest approve → `pns_leaderboard_site_publish.ps1 -MergeSubmissions` → device appears on site — live ingest accepted `ede8899d-ad56-4fc0-8654-a653d5571135` (`approved/auto_pass`), merged via `-MergeSubmissions`, submission artifacts now in `docs/leaderboard/submissions/`
- [x] **[AGENT]** Ingest validates `resolutionBetrayalIndex`, `measurementContext`, `buildDisplay` in submission schema (`leaderboard-ingest/main.py`)

### Sprint 25.5 — Site proof, Pages deploy, exports

- [x] **[AGENT]** Add `scripts/pns_m25_gate.ps1` + `pns_leaderboard_host_smoke.ps1` — host **PASS** `hfr-runs/m25_gate_*`
- [x] **[AGENT]** Host smoke: device JSON has `resolutionBetrayal`, `oemLossSummary`, `measurementContext`; product groups + CSV/RSS/glossary present
- [x] **[AGENT]** `pns_leaderboard_pages_push.ps1` → GitHub Pages live; verify RSS + `leaderboard.csv` links in footer — pushed commit `9fcc97d` and workflow trigger logged
- [x] **[AGENT]** Verification checklist (plan Phase 5): resolution panel on known-bad SKU, OEM OnePlus withheld-feature aggregates, community payload fields in pulled submission JSON — host checklist in `docs/leaderboard/README.md` §Phase 5; USB/community rows pending ingest
- [ ] **[HUMAN]** GitHub Pages smoke after deploy (buyer-facing copy, disclosure banner, GSMArena untested labeling)

**M25 gate:** `pns_m25_gate.ps1` **PASS** host lane (`hfr-runs/m25_gate_20260605_105056/`) + USB Full sweeps (`parity_sweep_20260605_105238/`, `parity_sweep_20260605_105702/`). Full matrix refresh evidence captured in both sweep matrix artifacts. Ingest live + merged submission proof + Pages push complete. Still open: CPH2649 stock/Lineage pair and signing pins.

**Out of scope (locked):** Automated OEM camera app scoring; GSMArena as Camera2 rank input; DXOMark as parity score (external links only).

---

## Milestone 26 — Parity closure (intake-driven)

**Objective:** Close recurring parity debt surfaced by sweeps. Source of truth: [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) (refreshed after every parity sweep).

**Docs:** [`docs/FLEET_PARITY_SWEEP.md`](docs/FLEET_PARITY_SWEEP.md) · [`docs/FLEET_PARITY_DEBT_LEDGER.md`](docs/FLEET_PARITY_DEBT_LEDGER.md)

**Host gate:** `pns_parity_debt_ledger_refresh.ps1` + `pns_parity_build_plan_intake.ps1` exit 0 after sweep.

### Sprint 26.1 — ShipNow AppFeature (from intake)

- [ ] **[AGENT]** `video.delivery_honesty` — readout/picker labels match encode + HS capture sizes (ties M24); gate: parity `video.delivery_honesty` proven — **agent:** `VideoDeliveryHonesty.kt` + parity `proveOk`; **ADB pending**
- [ ] **[AGENT]** `still.heic` / `still.motion_photo` / `still.tiff16` / `still.jxl` — complete export paths; gate: `pns_still_export_verify.ps1` — **agent:** `still.heic` parity `proveOk` when API 30+ + sessionOk; export paths open
- [ ] **[AGENT]** `audio.spatial` — surface + record path; gate: `pns_spatial_audio_verify.ps1`
- [ ] **[AGENT]** `still.independent_tonal` — wire pipeline; gate: `pns_independent_tonal_verify.ps1`

### Sprint 26.2 — AutomationProof (proof-pack closure)

- [ ] **[AGENT]** Enable `-IncludeProofPack` on regression pack Full tier (not Quick CI) — **wired:** `-ParityMode Full -IncludeProofPack` on `pns_fleet_regression_pack.ps1`
- [ ] **[AGENT]** Close top `AutomationProof` intake rows where `parityProofScript` exists but sweep reports `unautomated`

### Sprint 26.3 — Ownership cleanup

- [ ] **[AGENT]** Assign `UNASSIGNED` catalog rows in `docs/M22_PROVIDER_OWNERSHIP.json` (e.g. `still.proshot_leaf`, `product.hardware_camera_key`)

**M26 gate:** Top 3 open `AppFeature` rows closed on CPH2583 + intake `status=closed` + parity Delta pass.

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | Shipped work index **by app feature** (22 categories); not milestone/sprint layout |

**Open in this file:** **Milestone 24** + **Milestone 25** + **Milestone 26** + **Milestone H** (M15–M23 + completed M25 sprints archived in `BUILD_PLAN_COMPLETED.md`)

### Archiving completed work — procedure

1. When a sprint closes, add its completed tasks under the right **feature category** in **`BUILD_PLAN_COMPLETED.md`** (not as a new milestone section).
2. Keep **`BUILD_PLAN.md`** as pointers + open milestone rows only (currently **M24**, **M25**, **M26**, **Milestone H**).
3. Update **`CHANGELOG.md`** for user-visible changes and sync **`scripts/changelog_coverage.v1.json`** (release tag, date, `versionCode`, `requiredMentions`). Run **`pns_changelog_gate.ps1`** before milestone gates.

---

## Milestone H — Human & publication

**Objective:** Irreducible human judgment: creative, security, perceptual.

**Completed sprints:** H.1, H.3 (agent), H.4 (agent), H.5 (agent), H.6 (agent/a11y), H.7 (CPH2583), H.8 closed rows → **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone H — completed sprints*).

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
- [ ] **[HUMAN]** ACR on OP13 aux DNGs (optional regression sign-off)

### Sprint H.8 — M14 + M15 subjective sign-off

- [ ] **[HUMAN] H.8.1** Eye/face overlay on glass (14.5 + 15.1) — pixel gate passes; on-face rubber-stamp
- [ ] **[HUMAN] H.8.2** Dual-video stacked framing usability (14.12 + 15.5)
- [ ] **[HUMAN] H.8.3** Owner visual: all codecs/scenes good — **fail:** H.265 **DCG @4K** bad colors (2026-05-26); re-open 15.2 human row
- [ ] **[HUMAN] H.8.5** False color correct on grey card + highlight scene (15.21)

**Milestone H gate:** Owner-approved checklist; **H.7** closed for **CPH2583** (2026-05-29); **H.8** closes M14/M15 subjective claims.

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

- **Version:** Active plan **2026-06-05** — **M15–M23** archived; active: **Milestone 24** + **Milestone 25** + **Milestone 26** + **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

---

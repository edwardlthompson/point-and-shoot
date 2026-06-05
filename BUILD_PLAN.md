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
| `scripts/pns_m27_gate.ps1` | Milestone 27 parity debt burn-down gate (M27; sprint 27.6) |
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
| **Milestone 26** | **Archived** — parity closure intake-driven (2026-06-05 USB gate **PASS** `hfr-runs/m26_gate_20260605_113034/`) |
| **Milestone 27** | **Active** — remaining parity debt burn-down (113 actionable ledger rows; 94 open intake as of 2026-06-05) |
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

**Promotion rule:** move scoped `PBI-*` rows into **Milestone 27** sprints below; mark `status=closed` in intake JSON when USB proof passes. Do not auto-append unchecked bullets here (avoids churn).

**Baseline (post-M26):** [`docs/FLEET_PARITY_DEBT_LEDGER.json`](docs/FLEET_PARITY_DEBT_LEDGER.json) — **113** actionable rows · [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) — **94** open / 97 total. By fail reason: `not_proven` 33 · `session_failed` 18 · `advertised_not_surfaced` 36 · `unautomated` 25 · `4k120_truth_blocked_unstable` 1 (owned by **M24**).

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
| **M26** — Parity closure | 2026-06-05 |

---

## Milestone 24 — 4K120 stability and truthfulness

**Objective:** Give 4K120 the best chance to succeed with a fleet-generic route ladder, strict start truthfulness, endurance evidence, and parity classification that does not over-claim.

**Docs:** `docs/PNS_TECHNICAL_SETTINGS.md` · `docs/AGENT_REGRESSION_MEMORY.md` · `docs/FLEET_PARITY_DEVICE_LEADERBOARD.md`

**Host gate:** `scripts/pns_m24_gate.ps1 -HostOnly` — toolchain + M24 host checks (strict-start and truth-class script paths).

**Device gate (active USB):** capability-class probe (`S0/S1/S2`) → strict `pns_4k120_verify.ps1` truth-aware retries → `pns_4k120_endurance.ps1` (S2 path) → `pns_fleet_parity_sweep.ps1 -Mode Full` with explicit 4K120 truth source/serial metadata.

### Sprint 24.0 — Baseline instrumentation + rubric

- [ ] **[ADB]** Smoke run confirms telemetry needles exist in `PNS.Cam` / `PNS.AdbValidation` — **blocked 2026-06-05:** CPH2583 Wi‑Fi ADB offline after reboot; reconnect via `pns_adb_wifi_connect.ps1`

### Sprint 24.1 — HFR route ladder (interleaved ↔ encoder-priority)

- [ ] **[ADB]** `pns_mediacodec_hfr_verify.ps1 -OnlyTest 4K_120fps_MediaCodec -RequireFfprobeAv` with attempt/route telemetry present

### Sprint 24.2 — Strict warmup + retry budget

- Completed rows moved to `BUILD_PLAN_COMPLETED.md`.

### Sprint 24.3 — Mid-record resilience

- [ ] **[ADB]** Robustness run captures outcome class (`sustained`, `recovered_once`, `blocked_unstable`)

### Sprint 24.4 — Delivery truth gates

- [ ] **[ADB]** Updated `pns_4k120_verify.ps1` / `pns_mediacodec_hfr_verify.ps1` emit truth class in JSON + markdown summary — host wired; **CPH2583** run `hfr-runs/m24_gate_20260605_023318/` blocked_unstable (do not overlap capture/parity on one serial)

### Sprint 24.5 — Fleet policy alignment

- [ ] **[ADB]** `pns_fleet_parity_sweep.ps1 -Mode Full` shows truthful 4K120 classification and gap accounting — rerun alone on CPH2583 after M24 gate

### Sprint 24.6 — Endurance evidence pack

- [ ] **[ADB]** Verify 30s minimum pass path + longest-duration measured artifact — **FAIL** `m24_gate_20260605_023318/endurance/` (`bestPassSec=0`, `session_disconnect_or_encoder_stall`; likely concurrent gate contention)

### Sprint 24.7 — Milestone orchestration + closeout

- Completed rows moved to `BUILD_PLAN_COMPLETED.md`.

**M24 gate:** `scripts/pns_m24_gate.ps1` — host pass + strict 4K120 + endurance + parity Full truth-class evidence.

---

## Milestone 25 — Camera2 source-of-truth leaderboard

**Objective:** Public leaderboard + in-app probes so buyers can compare **USB-tested Camera2** capability, resolution withholding, ROM pairing (stock vs custom), and OEM accountability — without treating GSMArena or OEM camera apps as Camera2 truth.

**Agent scaffold (2026-06-04):** App probes (`ResolutionBetrayal`, hub readiness, submit payload), publish pipeline (`product_groups.json`, `oem_accountability.json`, CSV/RSS/catalog export), GitHub Pages UI (`#/product/{groupId}`, OEM page, buyer presets), `docs/CAMERA2_OEM_DISPARITY.md`, `ResolutionBetrayalTest` — see [`docs/leaderboard/README.md`](docs/leaderboard/README.md). **Do not tick rows below without Appendix A + sprint gate evidence.**

**Docs:** [`docs/CAMERA2_OEM_DISPARITY.md`](docs/CAMERA2_OEM_DISPARITY.md) · [`docs/leaderboard/README.md`](docs/leaderboard/README.md) · [`docs/FLEET_PARITY_SWEEP.md`](docs/FLEET_PARITY_SWEEP.md) · [`docs/FLEET_DEVICE_VERIFY_MATRIX.md`](docs/FLEET_DEVICE_VERIFY_MATRIX.md)

**Host gate:** `scripts/pns_leaderboard_site_publish.ps1 -SkipGsmarenaScrape` exit 0 + `scripts/pns_leaderboard_export_catalog.ps1` exit 0 + `ResolutionBetrayalTest` pass in `pns_verify_toolchain.ps1 -RunTests`.

**Device gate:** `pns_fleet_parity_sweep.ps1 -Mode Full` on **CPH2583** (baseline) + **CPH2649** stock **and** Lineage pair when hardware available; attach `hfr-runs/parity_sweep_*` + published `docs/leaderboard/data/`.

### Sprint 25.0 — App probe closeout

- Completed rows moved to `BUILD_PLAN_COMPLETED.md`.

### Sprint 25.1 — Priority USB fleet pair (OP12 baseline + OP13 collapse)

- [ ] **[ADB]** **CPH2649** stock ROM Full sweep → `testedVariants[]` with `romFlavor: stock` — **interim:** CPH2655 stock `parity_sweep_20260605_022500/` + product group entry; confirm SKU alias vs CPH2649 before ship tick
- [ ] **[ADB]** **CPH2649** Lineage (or custom) Full sweep → second `testedVariants[]` entry; product group `#/product/oneplus-13` shows custom vs stock delta

### Sprint 25.2 — GSMArena advertised spec + HAL sensor path

- Completed rows moved to `BUILD_PLAN_COMPLETED.md`.

### Sprint 25.3 — Community ingest + submission merge

- Completed agent rows moved to `BUILD_PLAN_COMPLETED.md`.

### Sprint 25.5 — Site proof, Pages deploy, exports

- Completed agent rows moved to `BUILD_PLAN_COMPLETED.md`.

**M25 gate:** `pns_m25_gate.ps1` **PASS** host lane (`hfr-runs/m25_gate_20260605_105056/`) + USB Full sweeps (`parity_sweep_20260605_105238/`, `parity_sweep_20260605_105702/`). Full matrix refresh evidence captured in both sweep matrix artifacts. Ingest live + merged submission proof + Pages push complete. Still open: CPH2649 stock/Lineage pair (ADB) and Milestone H publication/signing rows.

**Out of scope (locked):** Automated OEM camera app scoring; GSMArena as Camera2 rank input; DXOMark as parity score (external links only).

---

## Milestone 26 — Parity closure (intake-driven) — archived

Sprint bodies and gate outcome: **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** (*Milestone 26 — completed sprints*). USB gate **PASS** `hfr-runs/m26_gate_20260605_113034/` on **CPH2583** (`b5214fc6`).

**M26 gate:** `scripts/pns_m26_gate.ps1` — top-3 AppFeature proven + intake closed + parity Delta pass.

---

## Milestone 27 — Remaining parity debt burn-down

**Objective:** Close the parity debt backlog surfaced after M26 — prove or honestly reclassify catalog rows on **CPH2583**, wire automation proof for manifest scripts, and drive **`actionableRowCount`** down with each Full sweep + proof pack.

**Source of truth:** [`docs/FLEET_PARITY_DEBT_LEDGER.json`](docs/FLEET_PARITY_DEBT_LEDGER.json) · [`docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json`](docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json) (refreshed after every parity sweep).

**Docs:** [`docs/FLEET_PARITY_SWEEP.md`](docs/FLEET_PARITY_SWEEP.md) · [`docs/FLEET_PARITY_DEBT_LEDGER.md`](docs/FLEET_PARITY_DEBT_LEDGER.md) · [`scripts/parity_proof_manifest.json`](scripts/parity_proof_manifest.json)

**Host gate:** `pns_parity_debt_ledger_refresh.ps1` + `pns_parity_build_plan_intake.ps1` exit 0 · `pns_parity_proof_pack.ps1 -HostOnly`.

**Device gate:** `pns_fleet_parity_sweep.ps1 -Mode Full -IncludeProofPack` on **CPH2583** alone (do not overlap with capture/M24 gates on one serial).

**Out of scope / other milestones:** `video.hfr.120` **DeliveryHonesty** (`4k120_truth_blocked_unstable`) stays under **Milestone 24** until strict 4K120 passes. **`advertised_not_surfaced`** HUD/lens/codec picker rows are **informational** unless promoted to ship blockers with maintainer sign-off.

### Sprint 27.1 — ShipNow AppFeature (not proven)

Top recurrence from intake — prove on device or fix export/automation path:

- [ ] **[AGENT]** Still export remainder: `still.heic`, `still.tiff16`, `still.independent_tonal` — fix `pns_still_export_verify.ps1` composed-smoke logcat flake; gate all four formats on **CPH2583**
- [ ] **[AGENT]** `video.delivery_honesty` — Full sweep `provenOk` (pairs with M24 readout/picker tiers); add proof-manifest row if missing
- [ ] **[AGENT]** Video color profiles: `video.color.bt709`, `video.color.flat`, `video.color.hdr10`, `video.color.hlg10`, `video.color.pq` — gate: `pns_video_color_profile_verify.ps1` per profile
- [ ] **[AGENT]** `audio.unprocessed` — gate: `pns_audio_unprocessed_verify.ps1`
- [ ] **[AGENT]** `video.vp9` + `video.vp9.*` — export/WebM path or catalog downgrade to ProbeOnly with USB proof

### Sprint 27.2 — MatrixGate AppFeature (codec / HFR / RAW / perf)

Session or matrix-gated rows — prove after matrix full tier + session probes:

- [ ] **[AGENT]** AV1 family: `video.av1`, `video.av1.*` — resolve `session_failed` on **CPH2583** or document matrix `sessionOk=false`; gate: `pns_av1_parity_verify.ps1`
- [ ] **[AGENT]** RAW video: `video.raw`, `video.raw_picker` — gate: `pns_raw_video_verify.ps1`
- [ ] **[AGENT]** HFR ladder: `video.hfr`, `video.hfr.24/30/60/240`, `video.hfr.120` (AppFeature lane only — delivery truth stays **M24**) — gate: `pns_hfr_fps_parity_verify.ps1` / `pns_4k120_verify.ps1`
- [ ] **[AGENT]** Regular / UHD: `video.4k_regular`, `video.uhd60` — gate: `pns_4k_regular_verify.ps1`, `pns_mediacodec_hfr_verify.ps1`
- [ ] **[AGENT]** Perf probes: `perf.capture_latency`, `perf.cold_preview_ms`, `perf.first_frame_ms`, `perf.thermal_adaptive` — gate: `pns_memory_profiler.ps1`, `pns_battery_life_test.ps1`
- [ ] **[AGENT]** `raw.dng`, `root.max_res_unlock_cph2583`, `still.monochrome_capture` — matrix/session proof or catalog honesty update

### Sprint 27.3 — AutomationProof (Full proof pack)

- [ ] **[AGENT]** Run `pns_fleet_regression_pack.ps1 -Tier 2 -ParityMode Full -IncludeProofPack` on **CPH2583** (not Quick CI)
- [ ] **[AGENT]** Close `GAP_UNAUTOMATED` intake rows where `parity_proof_manifest.json` has a script — merge via `-IncludeProofPack` on Full sweep
- [ ] **[AGENT]** Extend recent-proof merge for any manifest script missing from `Get-RecentParityProofByCatalog` (follow `audio.spatial` / still-export pattern)

### Sprint 27.4 — Surfacing honesty (advertised_not_surfaced)

36 ledger rows — catalog advertises features already shipped but not listed in catalog `surfacing` slices (HUD, lens row, picker codecs):

- [ ] **[AGENT]** Audit `CameraCapabilityCatalog` surfacing vs `FleetUiVisibilityGate` — align catalog `surfacing` tags or downgrade to **INFORMATIONAL** where consumer chrome correctly hides controls
- [ ] **[AGENT]** Batch-close intake `PBI-*-AppFeature` rows with `failReason=advertised_not_surfaced` after catalog fix + Delta sweep shows `OK` or excluded gap class

### Sprint 27.5 — ProbeOnly / deferred rows

- [ ] **[AGENT]** `still.proshot_leaf`, `product.hardware_camera_key` — keep **ProbeOnly**; optional `pns_hardware_key_probe.ps1` when device key present; do not promote to ship blocker without USB proof

### Sprint 27.6 — Milestone gate + intake closeout

- [ ] **[AGENT]** Add `scripts/pns_m27_gate.ps1` — host preflight → Full sweep + proof pack → debt ledger delta vs M27 baseline → intake `openCount` regression check
- [ ] **[ADB]** **CPH2583** Full + proof pack: `GAP_ADVERTISED_NOT_PROVEN` + `GAP_UNAUTOMATED` reduced vs baseline; `shipBlockerGapCount=0`
- [ ] **[AGENT]** Mark closed `PBI-*` rows in intake JSON; refresh ledger + intake markdown

**M27 gate:** `pns_m27_gate.ps1` — Full sweep + proof pack on **CPH2583**; **`actionableRowCount` ≤ 80** (≥25% burn from 113 baseline) **or** maintainer accepts residual surfacing debt documented in ledger appendix; intake `openCount` strictly lower than post-M26 baseline (94).

---

## Completed milestones & sprints (archive)

| Archive | Contents |
|---------|----------|
| **[BUILD_PLAN_COMPLETED.md](BUILD_PLAN_COMPLETED.md)** | Shipped work index **by app feature** (22 categories); not milestone/sprint layout |

**Open in this file:** **Milestone 24** + **Milestone 25** + **Milestone 27** + **Milestone H** (M15–M23 + M26 archived in `BUILD_PLAN_COMPLETED.md`)

### Archiving completed work — procedure

1. When a sprint closes, add its completed tasks under the right **feature category** in **`BUILD_PLAN_COMPLETED.md`** (not as a new milestone section).
2. Keep **`BUILD_PLAN.md`** as pointers + open milestone rows only (currently **M24**, **M25**, **M27**, **Milestone H**).
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

### Sprint H.9 — M25 publication/signing handoff

- [ ] **[HUMAN]** Populate `leaderboard-ingest/config/signing_pins.json` with release APK cert SHA-256 (moved from M25.3)
- [ ] **[HUMAN]** GitHub Pages smoke after deploy (buyer-facing copy, disclosure banner, GSMArena untested labeling) (moved from M25.5)

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

- **Version:** Active plan **2026-06-05** — **M15–M23** + **M26** archived; active: **Milestone 24** + **Milestone 25** + **Milestone 27** + **Milestone H**.
- **Owner:** Project maintainer approves Milestone H closures.

---

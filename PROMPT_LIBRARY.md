# Prompt library (Point & Shoot)

Reusable agent prompts for this repo. Each entry: **When** · **Read first** · **Run** · **Pass** · **Also test** · **Critique**.

---

## 1 — Capture pipeline change

**When:** Editing `PreviewEngineScreen.kt`, `RawCaptureSupport.kt`, session create, or RAW still paths.

**Read first:** [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) §1 · [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) (grep target files) · [`docs/REVERTED_FEATURES_RESTORE_LIST.md`](docs/REVERTED_FEATURES_RESTORE_LIST.md) §8 · [ADR-0002](docs/adr/0002-dng-save-no-exif-rewrite.md)

**Run:** Minimal diff → `pns_verify_toolchain.ps1 -RunTests` → USB `pns_capture_pipeline_verify.ps1` on **CPH2583** → append `REG-*` if USB-proven

**Pass:** `captureRawStill 1/1 ok=true`; no §4a `streamHints` / §2 RAW10-first regression

**Also test:** If readout/chrome touched → `pns_chrome_ux_gate.ps1` (not parallel on same device)

**Critique:** After **3 consecutive** failed USB gates on the same hypothesis, stop and escalate — distinguish device flake (heat, mutex) from code regression.

---

## 2 — Fleet parity sweep (Full vs Delta)

**When:** User asks for parity sweep, FPS, or catalog honesty without naming mode → **AskQuestion** Full / Delta first.

**Read first:** [`AGENTS.md`](AGENTS.md) CRITICAL Fleet Parity · [`docs/FLEET_PARITY_SWEEP.md`](docs/FLEET_PARITY_SWEEP.md) · [ADR-0004](docs/adr/0004-fleet-matrix-sot.md)

**Run:**
```powershell
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Delta
# or
.\scripts\pns_fleet_parity_sweep.ps1 -Mode Full -IncludeRecord
```

**Pass:** `parity_report.json` in `hfr-runs/`; intake refreshed; no overlapping capture/chrome gates on same serial

**Also test:** `pns_fleet_regression_pack.ps1 -Tier all` after matrix-affecting code

**Critique:** Delta on stale matrix produces false green — refresh matrix scan first if `fingerprintSha256Prefix` or `versionCode` changed.

---

## 3 — Preview chrome behavioral fix

**When:** Bug in tap mapping, readout wiring, or session behavior — **not** spacing/colors/tiles.

**Read first:** [`.cursor/rules/preview-chrome-ui-lock.mdc`](.cursor/rules/preview-chrome-ui-lock.mdc) · [`docs/preview-chrome-layout-style-guide.md`](docs/preview-chrome-layout-style-guide.md)

**Run:** Smallest behavioral diff → `ReadLints` → sideload → `pns_chrome_ux_gate.ps1` · visible proof → `pns_device_screencap.ps1`

**Pass:** Gate green; layout unchanged vs style guide; no flex weight / slot position edits

**Also test:** `-FocalMmSlot 150` if tele/crop wiring touched ([ADR-0003](docs/adr/0003-dodge-tele-focal-routing.md))

**Critique:** Do not “fix UX” by unlocking chrome geometry — request explicit user approval for layout changes.

---

## 4 — GitHub release cut

**When:** User asks to ship, publish, cut release, or bump for Obtainium.

**Read first:** [`.cursor/skills/github-release/SKILL.md`](.cursor/skills/github-release/SKILL.md) · [`CHANGELOG.md`](CHANGELOG.md) · [`scripts/changelog_coverage.v1.json`](scripts/changelog_coverage.v1.json)

**Run:** `pns_github_release.ps1 -PrepareOnly` then review → `-Publish` when signing ready · post T.12: `pns_prerelease_gate.ps1`

**Pass:** `gh release` with APK + CHANGELOG asset; coverage manifest synced; `versionCode` bump matches changelog section

**Also test:** `zipalign -c` via `pns_release_packaging.ps1`

**Critique:** Do not publish without user-visible CHANGELOG bullets unless `-AllowEmptyUnreleased` explicitly requested.

---

## 5 — DNG loadability triage

**When:** ACR/Lightroom reject DNGs, or `dng_tiff_integrity_check.py` fails.

**Read first:** [`docs/DNG_OPENABILITY_REGRESSIONS.md`](docs/DNG_OPENABILITY_REGRESSIONS.md) · [ADR-0002](docs/adr/0002-dng-save-no-exif-rewrite.md) · [`.cursor/rules/dng-save-pipeline-lock.mdc`](.cursor/rules/dng-save-pipeline-lock.mdc)

**Run:** `pns_aux_dng_capture_analyze.ps1` → grep `PNS.CaptureStill` `dng save diag` → `dng_desktop_open_gate.py` on pulled dir

**Pass:** `DNG INTEGRITY: PASS`; desktop open gate PASS; no `ExifInterface.saveAttributes` in save path

**Also test:** `pns_capture_pipeline_verify.ps1` if session/metadata pairing changed

**Critique:** Dark/green cast may be calibration not loadability — check `rawFmt` and metadata pairing before re-enabling physical `TotalCaptureResult` pairing.

---

## 6 — New fleet SKU onboarding

**When:** Adding verify-matrix row or onboarding new device class.

**Read first:** [`docs/FLEET_DEVICE_VERIFY_MATRIX.md`](docs/FLEET_DEVICE_VERIFY_MATRIX.md) · [`docs/FLEET_DEVICE_CAPABILITY_MATRIX.md`](docs/FLEET_DEVICE_CAPABILITY_MATRIX.md) · [ADR-0004](docs/adr/0004-fleet-matrix-sot.md)

**Run:** USB `pns_fleet_matrix_scan.ps1 -ScanTier full` → `pns_fleet_matrix_diff.ps1` → parity `-Mode Full` on device

**Pass:** Valid `fleet_device_matrix.json`; catalog gate green; no new legacy-only gates without `FleetDevicePolicy` plugin

**Also test:** Shallow hub validate if probe export changed

**Critique:** One USB serial is not global truth — one row per **SKU** in verify matrix.

---

## 7 — Fleet matrix rescan + diff

**When:** After fleet cap, catalog, or HAL-affecting changes.

**Read first:** [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) §2 · [`AGENTS.md`](AGENTS.md) CRITICAL Fleet matrix

**Run:**
```powershell
.\scripts\pns_fleet_matrix_scan.ps1 -ScanTier full
.\scripts\pns_fleet_matrix_diff.ps1 -Before <old.json> -After hfr-runs/fleet_matrix_*/fleet_device_matrix.json
```

**Pass:** `PNS.FleetMatrix scanTier=full`; schema validate PASS; attach diff in PR notes

**Also test:** `pns_fleet_parity_sweep.ps1 -Mode Delta`

**Critique:** Quick-tier matrix alone is insufficient for ship blockers — use full tier before claiming parity closure.

---

## 8 — Chrome UX gate with tele focal proof

**When:** Tele routing, focal row, or crop math changes.

**Read first:** [ADR-0003](docs/adr/0003-dodge-tele-focal-routing.md) · [`DODGE_PROFILE.md`](DODGE_PROFILE.md)

**Run:** `pns_chrome_ux_gate.ps1 -SkipGradle -FocalMmSlot 150` (alone on device — not parallel with capture verify)

**Pass:** `teleFocalSlotOk=true`; log `focalSlotTap=` with physical tele `cameraIdAfter=` and `focalCrop=LongTele150`

**Also test:** `-FocalMmSlot 85` and `73` when touching shared tele sensor path

**Critique:** At FPS ≥120 digital crops may not apply — do not “fix” tele UX by forcing fleet lens switch.

---

## 9 — Active sprint execution (Milestone H)

**When:** Executing open sprints from [`BUILD_PLAN.md`](BUILD_PLAN.md) (active: **Milestone H**; Milestone **T** closed — archive only).

**Read first:** [`AGENT_MEMORY.md`](AGENT_MEMORY.md) · [`docs/LOCAL_FIRST_DEV_LOOP.md`](docs/LOCAL_FIRST_DEV_LOOP.md) · relevant **`.cursor/rules/*-lock.mdc`**

**Run:** One sprint at a time → sprint gate in BUILD_PLAN → Tier 0/1/2 as appropriate → tick `[x]` only with Appendix A evidence

**Pass:** Sprint gate listed under each H sprint; **`pns_milestone_h_host_gate.ps1`** before claiming agent lane complete

**Also test:** USB rows on **CPH2583** only; **`force-stop`** after ADB; never parallel capture + chrome gates

**Critique:** Human rows (H.2–H.9) are irreducible — do not `[x]` without owner sign-off. Preview chrome layout is locked.

---

## 9b — Template alignment sprint execution (Milestone T — archived)

**When:** Resuming deferred **T.13–T.14** or auditing closed Milestone **T** deliverables.

**Read first:** [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md#milestone-t--template-alignment) · [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md)

**Run:** One sprint at a time → sprint gate → archive to BUILD_PLAN_COMPLETED §29

**Pass:** `pns_milestone_t_gate.ps1` for closure sign-off; `pns_template_doc_link_check.ps1` for doc sprints

---

## 10 — Milestone gate and archive

**When:** All sprints in a milestone complete.

**Read first:** [`BUILD_PLAN.md`](BUILD_PLAN.md) milestone gate · Appendix A

**Run:** Milestone gate script (e.g. `pns_milestone_h_host_gate.ps1` + human checklist for **H**, `pns_milestone_t_gate.ps1` for closed **T**, `pns_m18_gate.ps1` for fleet max-out) → summarize shipped work → [`BUILD_PLAN_COMPLETED.md`](BUILD_PLAN_COMPLETED.md) → refresh [`AGENT_MEMORY.md`](AGENT_MEMORY.md)

**Pass:** Owner sign-off for human gates; host `-RunTests` green; device evidence paths in `hfr-runs/`

**Also test:** `pns_changelog_gate.ps1` if user-visible

**Critique:** Do not `[x]` parent **[MIXED]** rows until every child venue (host + USB + human) is satisfied.

---

## 11 — Settings / technical constants change

**When:** Adding or changing prefs, dial modes, readout constants, or pipeline flags.

**Read first:** [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) · [`.cursor/rules/pns-technical-settings.mdc`](.cursor/rules/pns-technical-settings.mdc)

**Run:** Code change + **same commit** update to `PNS_TECHNICAL_SETTINGS.md` → unit tests if pure helpers touched

**Pass:** Doc section matches code; `ReadLints` clean

**Also test:** USB if behavior affects capture session or readout

**Critique:** Undocumented constants become fleet regressions — grep settings doc before closing task.

---

## 12 — Local-first dev loop (Tier 0–2)

**When:** Any multi-file edit session; before commit or PR.

**Read first:** [`docs/LOCAL_FIRST_DEV_LOOP.md`](docs/LOCAL_FIRST_DEV_LOOP.md)

**Run:**
```powershell
.\scripts\pns_local_dev_parallel.ps1              # Tier 0 ~5–15s, parallel host
.\scripts\pns_prerelease_gate.ps1 -SkipGradle     # Tier 1 ~10–20s
.\scripts\pns_verify_toolchain.ps1 -RunTests      # Tier 2 before push (Kotlin touched)
```

**Pass:** Tier 0/1 exit 0; Tier 2 when shipping Kotlin

**Also test:** USB Tier 3 when capture/chrome/fleet touched — sequential on one serial

**Critique:** Do not wait for GitHub Actions to discover doc/fixture drift — Tier 0 is cheap.

---

## 13 — Multi-agent parallel milestone

**When:** Splitting a large milestone across up to 8 Cursor agents / Cloud VMs.

**Read first:** [`docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md`](docs/MULTI_AGENT_PARALLEL_ORCHESTRATION.md) · [`.cursor/rules/multi-agent-parallel.mdc`](.cursor/rules/multi-agent-parallel.mdc)

**Run:**
```powershell
# Phase 0 — one agent, schema lock files only, merge first
# Phase 1 — per parallel task:
.\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug <kebab-name> -Create
# Each agent: scoped paths only; Tier 0 in its worktree
# Phase 2 — integrator on main/integration branch:
.\scripts\pns_local_dev_parallel.ps1
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

**Pass:** No file-path overlap between concurrent agents; schema lock merged before parallel phase; USB gates sequential

**Also test:** `pns_prerelease_gate.ps1` before release cut

**Critique:** Two agents editing `PreviewEngineScreen.kt` or `libs.versions.toml` in parallel will waste merge time — decouple or serialize.

---

*Milestone T Sprint T.15 — local-first + multi-agent orchestration.*

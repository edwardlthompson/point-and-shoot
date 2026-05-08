# Point & Shoot -- Probe build plan

**Audience:** Engineers and **automated coding agents** maintaining `point-and-shoot`.
**Primary orchestration:** `scripts/pns_hfr_autorun.ps1` (ADB, `PNS.SWEEP_SIGNAL`, pulled JSON).

---

## 1. Rules for agents (read first)

These rules reduce drift ("slipping") between documentation and reality.

1. **Never change `- [ ]` to `- [x]` without completing Section 3 *Verification-before-tick* for that row.** Unchecked means "not proven in-session" or "scope still partial."
2. **After any Kotlin/Java or `scripts/*.ps1` change**, run the toolchain gate (`.\scripts\pns_verify_toolchain.ps1`). Exit code **0** is required before claiming the repo builds.
3. **Docs-only edits** (this file or comments): run `.\scripts\pns_verify_toolchain.ps1 -SkipGradle` before ticking documentation-only items.
4. **Do not mark a phase `[x]` while Section 9 *Status* still says *Partial*** unless you first rewrite the goal/status to match full completion **and** record rationale in **Section 5 Progress log**.
5. **Append** to **Section 5 Progress log** when you verify work -- do not delete historical rows.
6. **Prefer UTF-8** for `.kt` and `.ps1` on Windows; UTF-16 breaks Gradle/PowerShell (see **Section 11 Notes**).
7. **Start-of-list audit:** Before trusting existing `- [x]` rows (after merging branches, long sessions, or when progress feels inconsistent), walk **Section 6 from PH0 down through Infrastructure** in order. For each `- [x]`, confirm **Section 9** *Status* matches the claimed scope, **Section 5** contains matching evidence (or add an audit row), and if Kotlin or `scripts/*.ps1` changed since that evidence, re-run the gate from **Section 2** before building on that assumption.

---

## 2. Verification gates

| Gate | When | Command |
|------|------|---------|
| **Full toolchain** | Default after code or script changes | `.\scripts\pns_verify_toolchain.ps1` |
| **Equivalent wrapper** | Same checks | `.\scripts\pns_hfr_autorun.ps1 -VerifyToolchain` |
| **Docs / markdown only** | No Kotlin or PowerShell touched | `.\scripts\pns_verify_toolchain.ps1 -SkipGradle` |
| **Quick Kotlin** | Narrow edits (optional shortcut) | `.\gradlew.bat compileDebugKotlin` |
| **CI (GitHub)** | Push or PR to main/master matching **`paths`** | `.github/workflows/toolchain-verify.yml` (full gate on Ubuntu); **`workflow_dispatch`** anytime |
| **Plan doc (GitHub)** | Push/PR touching `PROBE_BUILD_PLAN.md` only (see workflow paths) | `.github/workflows/plan-doc-verify.yml` (`-SkipGradle`) |

Optional: `-ReportDir .\hfr-runs` writes `toolchain_verify_*.txt`. **Exit code 0 = pass, 1 = fail.**

---

## 3. Verification-before-tick (mandatory protocol)

Before setting any checkbox in **Section 6 Master checklist** to `[x]`:

| Step | Action |
|------|--------|
| **A** | Confirm **acceptance criteria** for that row (Section 9 Phases or Section 6 Infrastructure) are satisfied. |
| **B** | Run the appropriate gate from **Section 2** and capture **exit code 0**. |
| **C** | For **device-dependent** behavior, confirm the implementation exists (named screens/probes in Section 9). On-device JSON pulls are optional for "implemented" but required if you claim a specific bugfix. |
| **D** | Add a row to **Section 5 Progress log**: date, item id, evidence summary (`toolchain PASSED`, commit SHA optional). |
| **E** | Only then edit `- [ ]` to `- [x]` in **Section 6**. |

If any step fails, **leave the box unchecked** and describe the gap under **Section 9 Status** for that phase.

---

## 4. Checkbox semantics

| Symbol | Meaning |
|--------|---------|
| `- [ ]` | Not complete, not verified this session, or scope intentionally partial. |
| `- [x]` | Complete **for the stated scope**, verified per Section 3, and logged in Section 5. |

**Infrastructure** rows track host automation quality, not HAL behavior on a specific phone.

---

## 5. Progress log (audit trail)

Append-only. Each verification that supports a checkbox change gets a row.

| Date (UTC) | Item | Evidence |
|------------|------|----------|
| 2026-05-07 | **INFRA-1, INFRA-2**, build health | `pns_verify_toolchain.ps1` -> **PASSED** (`assembleDebug`, UTF-8 + AST parse for `pns_hfr_autorun.ps1`, `pns_probe_watch.ps1`, `pns_verify_toolchain.ps1`). |
| 2026-05-07 | **PH0-PH4, PH8** implementation audit | Sources present: `ExhaustiveMediaProbeScreen.kt`, `EncoderProbeCore.kt`; `PipelineAccessProbe.kt`; `SessionMatrixProbeScreen.kt`; `HdrDcgRuntimeProbeScreen.kt`, `SessionConfigurationCompat.kt`; `LogicalPhysicalProbeScreen.kt` (lensFacing / hardwareLevel). Toolchain PASSED. |
| 2026-05-07 | **DOC** | Full plan rewrite (Sections 1-11). `pns_verify_toolchain.ps1 -SkipGradle` PASSED. |
| 2026-05-07 | **DOC** (audit protocol) | UTF-8 on disk verified (`Format-Hex`); Start-of-list audit rule (Section 1); `pns_verify_toolchain.ps1 -SkipGradle` PASSED. |
| 2026-05-07 | **PH5** | Rescoped Section 9 PH5 to shipped probeScope; `CaptureLatencyProbeScreen.kt` adds root `probeScope`; `RawHdrExclusivityProbeScreen.kt` adds `sessionSupportSummary`; `BurstProbeScreen.kt` adds `burstWallMsPerShot` / `approxBurstFps`. Full toolchain gate after Kotlin edits. |
| 2026-05-07 | **INFRA-2** | `pns_verify_toolchain.ps1`: scan `app/src/**/*.kt` for UTF-16 LE (fail gate); generalized UTF-16 heuristic for `.ps1` / `.kt`. Full toolchain PASSED after change. |
| 2026-05-07 | **PH6, PH7, PH9** | Rescoped Section 9 to shipped scopes; added root `probeScope` to RawHdr + Burst JSON; ticked PH6/PH7/PH9. Full toolchain after Kotlin UTF-8 conversion. |
| 2026-05-07 | **PH6/PH7/PH9 verify** | `pns_verify_toolchain.ps1` **PASSED** (assembleDebug, scripts, Kotlin UTF-8 scan); Section 9 PH6-PH9 bodies synced.|
| 2026-05-07 | **INFRA-2 + INFRA-3** | Cross-platform Gradle in `pns_verify_toolchain.ps1`; added `.github/workflows/toolchain-verify.yml` (Ubuntu, SDK 36). Local `pns_verify_toolchain.ps1` **PASSED**. |
| 2026-05-07 | **INFRA-4** | `dependabot.yml` + workflow: `gradle/actions/setup-gradle@v4`, concurrency, `workflow_dispatch`. Full local `pns_verify_toolchain.ps1` **PASSED**. |
| 2026-05-07 | **INFRA-5** | CI `paths` filters on `toolchain-verify.yml`; manual `workflow_dispatch` for exceptions. Local `pns_verify_toolchain.ps1` **PASSED**. |
| 2026-05-07 | **INFRA-5 (sync)** | Restored `paths` filters in `toolchain-verify.yml` (push/PR); included `native/**` for future NDK wiring. Local `pns_verify_toolchain.ps1` **PASSED** (full gate after workflow edit). |
| 2026-05-07 | **INFRA-6** | Added `plan-doc-verify.yml`; toolchain `paths` incl. `.github/workflows/**`. `pns_verify_toolchain.ps1 -SkipGradle` **PASSED** after workflow/plan edits. |
| 2026-05-07 | **Device / sideload (pre-commit)** | Moved Grant-CameraPermission above Install-PnsDebugApk in pns_hfr_autorun.ps1; UTF-8 save; full pns_verify_toolchain.ps1 **PASSED**; -SideloadOnly -SkipGradleBuild installed on adb device 8bf09993; pm path dev.pointandshoot OK. |
| 2026-05-07 | **Smoke (device)** | `-RunProbeSmoke -SkipSideload -SkipGradleBuild`; pulled `deep_caps_*.json` + `session_matrix_*.json` with done signals; `suite_run_summary_smoke_*.txt` in `hfr-runs/`. Full `pns_verify_toolchain.ps1` **PASSED** same session. |
| 2026-05-07 | **Release APK** | `-AssembleReleaseOnly` exit 0 (internal signing). Preceded by full `pns_verify_toolchain.ps1` **PASSED**. |
| 2026-05-07 | **Core suite (device)** | `-RunCoreProbePlan -ExhaustiveHfrOnly -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 60` on adb `8bf09993`; `suite_run_summary_core_*.txt`, `phase9_thermal_core_*.txt`, `exhaustive_probe_*.json` under `hfr-runs/`. **Note:** `CAPTURE_LATENCY_DONE ok=false` (no JSON pulled); `ENC_PROBE_DONE ok=false` (no enc JSON). |
| 2026-05-07 | **PH5 device validation**, SessionConfiguration API 35+ reprocess path | **CPH2655** (adb `8bf09993`), Android 16: after sideload, `-RunCaptureLatency -MaxRuns 0 -SkipSideload -SkipGradleBuild` yields `CAPTURE_LATENCY_DONE ok=true`, pulled `capture_latency_20260507_171448.json`; reprocess probe `reprocessInputToJpegSessionSupported=true`. `-MaxRuns 1` encoder run: `ENC_PROBE_DONE ok=true`, pulled `hfr_run1_20260507_171549_enc_probe.json`. Code: API 35+ uses `SessionConfiguration(sessionType, outputs)` + `setInputConfiguration` (Builder not visible to reflection on this build); UTF-8 fixes for `SessionConfigurationCompat.kt` / `CaptureLatencyProbeScreen.kt`; defensive JSON on session-config build failure. `pns_verify_toolchain.ps1` **PASSED** same session. |
| 2026-05-07 | **PH6 + PH7 device validation** (piecemeal) | adb `8bf09993` (CPH2655): `-Sideload -RunRawHdrExcl -RunBurstProbe -MaxRuns 0 -SkipGradleBuild` after `pns_verify_toolchain.ps1` **PASSED**. `RAW_HDR_EXCL_DONE … ok=true`, pulled `raw_hdr_excl_20260507_172155.json` (root `probeScope`). `BURST_PROBE_DONE … ok=true`, pulled `burst_probe_20260507_172156.json` (root `probeScope`). |
| 2026-05-07 | **PH9 thermal snapshot** (host) | `-ThermalSnapshotOnly -OutDir .\hfr-runs` on adb `8bf09993`; wrote `phase9_thermal_standalone_20260507_172317*.txt`. Preceded by `pns_verify_toolchain.ps1 -SkipGradle` **PASSED** after §5 doc append. |
| 2026-05-07 | **Core suite (device) refresh** | adb `8bf09993` (CPH2655 / Android 16): full `pns_verify_toolchain.ps1` **PASSED**, then `-RunCoreProbePlan -ExhaustiveHfrOnly -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 45 -ProgressIntervalSeconds 90`. `CAPTURE_LATENCY_DONE … ok=true`, pulled `capture_latency_20260507_172511.json`; `ENC_PROBE_DONE … ok=true`, pulled `hfr_run1_20260507_173322_enc_probe.json`; exhaustive `exhaustive_20260507_172642.json`; `suite_run_summary_core_20260507_173538.txt`; `phase9_thermal_core_20260507_173537*.txt`. |
| 2026-05-07 | **Full suite (device)** | adb `8bf09993` (CPH2655): full `pns_verify_toolchain.ps1` **PASSED**, then `-RunFullSuite -ExhaustiveHfrOnly -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 45 -ProgressIntervalSeconds 90`. All phases through exhaustive + encoder completed (exit 0). `CAPTURE_LATENCY_DONE … ok=true` (`capture_latency_20260507_173803.json`); `ENC_PROBE_DONE … ok=true` (`hfr_run1_20260507_174618_enc_probe.json`); exhaustive `exhaustive_20260507_173937.json`; `suite_run_summary_full_20260507_174820.txt`; `phase9_thermal_full_20260507_174819*.txt`. **Legacy Camera1:** `LEGACY_CAM1_DONE … ok=false`; host reported no `legacy_camera1_*.json` pulled (investigate app/logcat if needed). |
| 2026-05-07 | **Full suite + FULL exhaustive matrix** (device) | adb `8bf09993`: `-RunFullSuiteFullMatrix -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 45 -ProgressIntervalSeconds 90` (~49m wall). **Exhaustive:** host timeout before `EXHAUSTIVE_PROBE_DONE` (no `exhaustive_probe_*.json` pulled; log `exhaustive_20260507_175051_*`). Encoder + Phase 9 + summary still ran: `ENC_PROBE_DONE … ok=true`, `hfr_run1_20260507_183554_enc_probe.json`, `phase9_thermal_full_matrix_20260507_183806*.txt`, `suite_run_summary_full_matrix_20260507_183807.txt`. **Retry guidance:** raise `-ExhaustiveTimeoutMinutes` (e.g. 90–120) for full matrix on this hardware. |
| 2026-05-08 | **Legacy Camera1 (composition) + full_matrix exhaustive** | **Fix:** `LegacyCamera1ProbeScreen` runs `runLegacyCamera1Probe` on `LegacyCam1WorkScope` (SupervisorJob + IO), `DisposableEffect` cancels job; avoids `LeftCompositionCancellationException` when host navigates away during headless run (same pattern as encoder probe). **Piecemeal:** `-Sideload -RunLegacyCamera1 -MaxRuns 0 -SkipGradleBuild` on adb `8bf09993` → `LEGACY_CAM1_DONE … ok=true`, pulled `legacy1_20260507_184218.json`. **Full suite full matrix:** `-RunFullSuiteFullMatrix -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 120 -ProgressIntervalSeconds 120` (~79m wall) → `EXHAUSTIVE_PROBE_DONE … ok=true`, `exhaustive_20260507_184430.json`; in-suite legacy `legacy1_20260507_184426` ok=true + JSON; `hfr_run1_20260507_195938_enc_probe.json`; `phase9_thermal_full_matrix_20260507_200138*.txt`; `suite_run_summary_full_matrix_20260507_200139.txt`. `pns_verify_toolchain.ps1` **PASSED** after Kotlin change. |
| 2026-05-08 | **Chained regression (device)** | adb `8bf09993`: `pns_verify_toolchain.ps1` **PASSED**, then **core** `-RunCoreProbePlan -ExhaustiveHfrOnly -MaxRuns 1 -SkipGradleBuild -ExhaustiveTimeoutMinutes 45` → `CAPTURE_LATENCY_DONE … ok=true` (`capture_latency_20260507_200420.json`), `ENC_PROBE_DONE … ok=true` (`hfr_run1_20260507_201233_enc_probe.json`), `suite_run_summary_core_20260507_201442.txt`, `phase9_thermal_core_20260507_201442*.txt`. Immediately **full** `-RunFullSuite … -SkipSideload -SkipGradleBuild` (same timeouts) → `LEGACY_CAM1_DONE … ok=true` + JSON (`legacy1_20260507_201630.json`), `ENC_PROBE_DONE … ok=true` (`hfr_run1_20260507_202315_enc_probe.json`), `suite_run_summary_full_20260507_202520.txt`, `phase9_thermal_full_20260507_202519*.txt`. Combined wall ~21m; exit 0. |
| 2026-05-08 | **Smoke + thermal (device)** | adb `8bf09993`: `-RunProbeSmoke -SmokeIncludeThermal -SkipSideload -SkipGradleBuild` after chained regression; `deep_caps_20260507_202555.json`, `session_matrix_20260507_202600.json`, `suite_run_summary_smoke_20260507_202601.txt`, `phase9_thermal_smoke_20260507_202602*.txt`. Exit 0. |
| | *(append next verification here)* | |

---

## 6. Master checklist

**Do not edit checkboxes without Section 3.** Keep aligned with Section 9 *Status* lines.

### Phases (product scope)

- [x] **PH0** -- HFR / constrained high-speed encoder matrix (exhaustive + encoder orchestration)
- [x] **PH1** -- Static pipeline access (`pipelineAccess` in deep caps / exhaustive)
- [x] **PH2** -- Session configuration matrix (`SessionMatrixProbeScreen`)
- [x] **PH3** -- HDR / DCG runtime session toggles (`HdrDcgRuntimeProbeScreen`)
- [x] **PH4** -- 10-bit / DR profile spots (compat + HDR runtime JSON / YUV spots)
- [x] **PH5** -- ZSL / reprocess latency *(shipped scope: Section 9 PH5; `probeScope` in `capture_latency_*.json`)*
- [x] **PH6** -- RAW / preview + DR session matrix *(shipped scope: Section 9 PH6; `probeScope` in `raw_hdr_exclusivity_*.json`)*
- [x] **PH7** -- Burst / AE bracket probe *(shipped scope: Section 9 PH7; `probeScope` in `burst_probe_*.json`)*
- [x] **PH8** -- Logical vs physical comparison (`LogicalPhysicalProbeScreen`, `-RunLogicalPhysical`)
- [x] **PH9** -- Thermal / sustained *(host shipped scope: Section 9 PH9; `phase9_thermal_*.txt`)*

### Infrastructure (host automation)

- [x] **INFRA-1** -- `pns_hfr_autorun.ps1`: suite modes, JSON clears, Phase 9 thermal labels, suite summary fields, `-RunProbeSmoke`, `-SmokeIncludeThermal`
- [x] **INFRA-2** -- `pns_verify_toolchain.ps1` + `-VerifyToolchain`; UTF-8/parse checks for scripts **and** `app/src/**/*.kt` (reject UTF-16 LE); Gradle via `gradlew` (Unix/macOS CI) or `gradlew.bat` (Windows)
- [x] **INFRA-3** -- GitHub Actions `.github/workflows/toolchain-verify.yml`: Ubuntu + Android SDK + `./scripts/pns_verify_toolchain.ps1` on push/PR to `main`/`master`
- [x] **INFRA-4** -- Dependabot `.github/dependabot.yml` (weekly GitHub Actions); CI workflow: `gradle/actions/setup-gradle@v4`, concurrency cancel-in-progress, `workflow_dispatch`
- [x] **INFRA-5** -- CI `paths` filters on push/PR (Kotlin/scripts/Gradle/wrapper/workflow); docs-only changes use **`plan-doc-verify`** or **`workflow_dispatch`**
- [x] **INFRA-6** -- `.github/workflows/plan-doc-verify.yml`: `pns_verify_toolchain.ps1 -SkipGradle` when push/PR affects only `PROBE_BUILD_PLAN.md`

---

## 7. Automation (host)

- **Sideload:** `-RunCoreProbePlan` / `-RunFullSuite` run `gradlew assembleDebug` then `adb install -r -t` unless `-SkipSideload`. `-SkipGradleBuild` installs newest debug APK. **`.\scripts\pns_hfr_autorun.ps1 -SideloadOnly`** -- install only.
- **Project root:** `-ProjectRoot <repo>` if script is not under `scripts/` relative to repo.
- **Camera:** `pm grant dev.pointandshoot android.permission.CAMERA` after device wait / root.

**Common commands**

```text
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunCoreProbePlan -ExhaustiveHfrOnly -MaxRuns 1
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunCoreProbePlanFullMatrix -Sideload -MaxRuns 1
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunFullSuite -ExhaustiveHfrOnly -MaxRuns 1
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunFullSuiteFullMatrix -Sideload -MaxRuns 1
.\scripts\pns_hfr_autorun.ps1 -AssembleReleaseOnly
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunProbeSmoke -Sideload
.\scripts\pns_hfr_autorun.ps1 -OutDir .\hfr-runs -RunProbeSmoke -SmokeIncludeThermal -Sideload
```

**Piecemeal flags:** `-RunDeepCaps`, `-RunSessionMatrix`, `-RunHdrDcgRuntime`, `-RunCaptureLatency`, `-RunRawHdrExcl`, `-RunBurstProbe`, `-RunLogicalPhysical`, `-RunExhaustive`, `-RunLegacyCamera1`, `-ExhaustiveHfrOnly`, `-ExhaustiveIncludeLogical`, `-Sideload`, `-ThermalSnapshotOnly`.

**Phase 9:** `-EncoderPauseSeconds`; thermal artifacts after core/full suites (`phase9_thermal_<core|core_matrix|full|full_matrix>_*.txt`); `-ThermalSnapshotOnly`; smoke thermal via `-SmokeIncludeThermal`. Host **clears** stale JSON on device before phases (see Section 8).

**Suite summaries:** `suite_run_summary_<suite>_*.txt` (git + paths + `adbSerial`, `deviceModel`, `androidRelease`, `deviceManufacturer`, `cpuAbi` when adb works).

- **CI full:** `.github/workflows/toolchain-verify.yml` runs full `pns_verify_toolchain.ps1` when **`paths`** match (see YAML). **`workflow_dispatch`** always runs the full gate.
- **CI plan-only:** `.github/workflows/plan-doc-verify.yml` runs `pns_verify_toolchain.ps1 -SkipGradle` when only `PROBE_BUILD_PLAN.md` changes (scripts + Kotlin UTF-8 scan, no Gradle).
- **Dependency updates:** Dependabot opens weekly PRs for GitHub Actions versions (`.github/dependabot.yml`).

---

## 8. Artifacts (signals)

| Artifact | Done signal / notes |
|----------|---------------------|
| `deep_caps_*.json` | `DEEP_CAPS_DONE` |
| `session_matrix_*.json` | `SESSION_MATRIX_DONE` |
| `hdr_dcg_session_*.json` | `HDR_DCG_SESSION_JSON`, `HDR_DCG_SESSION_DONE` |
| `capture_latency_*.json` | `CAPTURE_LATENCY_DONE`; `probeScope`; optional `CAP_LAT reprocess_session` |
| `raw_hdr_exclusivity_*.json` | `RAW_HDR_EXCL_DONE`; `probeScope`; `RAW_HDR_DR_ROW` per DR profile; per-camera `sessionSupportSummary` |
| `burst_probe_*.json` | `BURST_PROBE_DONE`; `probeScope`; `burstWallMsPerShot`, `approxBurstFps` |
| `phase9_thermal_<suite>_*.txt` | Host (`Write-Phase9ThermalArtifacts`); suite in filename |
| `logical_physical_*.json` | `LOGICAL_PHYSICAL_DONE` |
| `exhaustive_probe_*.json` | `EXHAUSTIVE_PROBE_DONE runId=` |
| `enc_probe_*.json` | `ENC_PROBE_DONE` |
| `legacy_camera1_*.json` | `LEGACY_CAM1_DONE` |

Logcat tag: **`PNS.SWEEP_SIGNAL`**.

---

## 9. Suite order and phase detail

### Suite order (automated)

| Suite | Order |
|-------|--------|
| **Core** | deep_caps -> session_matrix -> hdr_dcg -> capture_latency -> raw_hdr -> burst -> logical_physical -> exhaustive -> encoder |
| **Full** | Same through logical_physical -> **legacy Camera1** -> exhaustive -> encoder |
| **Smoke** | deep_caps -> session_matrix only |

### Phase 0 -- HFR / encoder matrix

- **Goal:** Map allowed HFR / encoder combinations.
- **Status:** Exhaustive + encoder probes + script orchestration.
- **Acceptance for [x]:** `ExhaustiveMediaProbeScreen` / encoder path + `Run-Once` / suite wiring; toolchain passes.

### Phase 1 -- Static pipeline access

- **Goal:** Per-camera pipeline flags in JSON.
- **Status:** Implemented (`PipelineAccessProbe.kt`, deep caps / exhaustive).
- **Acceptance:** `pipelineAccess` emitted in artifacts.

### Phase 2 -- Session configuration matrix

- **Goal:** `isSessionConfigurationSupported` REGULAR + HIGH_SPEED sizes.
- **Status:** Implemented (`SessionMatrixProbeScreen.kt`).
- **Acceptance:** `session_matrix_*.json` + `SESSION_MATRIX_DONE`.

### Phase 3 -- HDR / DCG runtime

- **Goal:** DR profiles x preview `OutputConfiguration` with `setDynamicRangeProfile`, incl. recommended 10-bit spot.
- **Status:** Implemented (`HdrDcgRuntimeProbeScreen.kt`, `SessionConfigurationCompat.kt`).

### Phase 4 -- 10-bit / DR spots

- **Goal:** Targeted YUV / DR checks beyond baseline HDR runtime.
- **Status:** Implemented (HDR JSON + `isSessionSupportedWithDynamicRangeImageReader`).

### Phase 5 -- ZSL / reprocess latency

- **Goal (shipped scope):** Measure still JPEG latency to first `TotalCaptureResult`, compare ZSL request-key on/off when available, measure `TEMPLATE_ZERO_SHUTTER_LAG` latency, record static YUV/private reprocessing capability flags, and query whether an input-to-JPEG reprocess session configuration is supported (no actual reprocess capture loop).
- **Status:** **Complete for shipped scope** -- `CaptureLatencyProbeScreen.kt` emits `probeScope` plus latency rows and `reprocess_input_to_jpeg_session` in `capture_latency_*.json`. End-to-end reprocess pipeline latency is explicitly **out of scope** (`probeScope.fullReprocessPipelineLatency_endToEnd` is false).
- **Acceptance for [x]:** JSON includes `probeScope`, latency tests, reprocess session probe where applicable; toolchain passes.

### Phase 6 -- RAW vs HDR exclusivity

- **Goal (shipped scope):** Session-configuration matrix per camera: preview (SurfaceTexture or YUV) + RAW output, plain and with each enumerated dynamic range profile on preview when API 33+. Emit per-camera tests and `sessionSupportSummary`.
- **Status:** **Complete for shipped scope** -- `RawHdrExclusivityProbeScreen.kt`; root JSON **`probeScope`** records scope; API below 28 writes `probeScope.reason=requiresApi28`.
- **Acceptance for [x]:** `raw_hdr_exclusivity_*.json` includes `probeScope`, matrix rows, and summary where applicable; toolchain passes.

### Phase 7 -- Burst / bracket

- **Goal (shipped scope):** Fixed-count JPEG `captureBurst` wall timing (per-shot and approximate FPS), AE compensation bracket at min/mid/max indices when the range is non-flat, `BURST_CAPTURE` capability, and AE/burst-related metadata key names (bounded lists).
- **Status:** **Complete for shipped scope** -- `BurstProbeScreen.kt`; root **`probeScope`** marks vendor-specific burst-limit enumeration as out of scope (use deep caps for that).
- **Acceptance for [x]:** `burst_probe_*.json` includes `probeScope`, burst timing, bracket entries when available; toolchain passes.

### Phase 8 -- Logical vs physical

- **Goal:** Session spot checks on logical and listed physical ids.
- **Status:** Implemented (`LogicalPhysicalProbeScreen.kt`; JSON includes `lensFacing`, `hardwareLevel`).

### Phase 9 -- Thermal / sustained

- **Goal (shipped scope):** Host-side thermal/sustained-load observability: snapshot dumps (`Write-Phase9ThermalArtifacts`), pause spacing between encoder iterations (`-EncoderPauseSeconds`), standalone `-ThermalSnapshotOnly`, smoke thermal (`-SmokeIncludeThermal`), labeled `phase9_thermal_<suite>_*.txt` outputs after core/full flows.
- **Status:** **Complete for shipped scope** -- implemented in `pns_hfr_autorun.ps1` (no dedicated in-app thermal stress probe required for this checklist).
- **Out of scope:** Exotic vendor thermal nodes not reachable via standard dumpsys/sysfs patterns from adb; continuous in-camera thermal throttling experiments.
- **Acceptance for [x]:** Section 7 flags and artifact naming verified; host scripts pass toolchain UTF-8/parse checks alongside the app build.


---

## 10. Suggested validation on hardware

- **CI first:** merge probe-affecting work only after **`toolchain-verify`** is green (Ubuntu parity with full `pns_verify_toolchain.ps1`). Plan-only edits to `PROBE_BUILD_PLAN.md` should pass **`plan-doc-verify`** (`-SkipGradle`).
- **Local gate:** before pushing Kotlin or `scripts/*.ps1` changes, run `.\scripts\pns_verify_toolchain.ps1` on Windows, or `./scripts/pns_verify_toolchain.ps1` on Unix/macOS.
- Long exhaustive: `-RunFullSuiteFullMatrix` or `-RunExhaustive` without `-ExhaustiveHfrOnly`; tune `-ExhaustiveTimeoutMinutes`.
- Release-type APK: `-AssembleReleaseOnly` (internal signing -- replace for production).
- Optional device smoke: `-RunProbeSmoke -Sideload` after installing the verified APK.
## 11. Notes

- Heavy probes: use an application/work **CoroutineScope** for background work to avoid `LeftCompositionCancellationException`.
- After `adb root`, script re-grants camera when needed.
- Reinstall before long suites so on-device JSON matches the build.
- **Windows:** save `.kt` and `.ps1` as **UTF-8** (not UTF-16 LE).

---

## Document control

| Field | Value |
|-------|-------|
| **Canonical checklist** | Section 6 |
| **Evidence trail** | Section 5 |
| **Last structural review** | 2026-05-07 -- INFRA-6 plan-doc CI + toolchain paths restored |


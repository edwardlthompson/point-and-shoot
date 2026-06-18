# Local-first dev loop (Point & Shoot)

**Objective:** Run quality gates on **local hardware** before pushing or waiting on GitHub Actions. This repo is **Kotlin + Gradle + PowerShell + selective Python** — not a Python app; there is no project-wide `ruff` / `mypy` / `pyright` lane. Host Python is used only where scripts already invoke it (DNG gates, leaderboard publish, matrix validate).

**Related:** [Multi-agent parallel orchestration](MULTI_AGENT_PARALLEL_ORCHESTRATION.md) · [`AGENTS.md`](../AGENTS.md) · [`CLI_BUILD_AND_SIDELOAD.md`](../CLI_BUILD_AND_SIDELOAD.md)

---

## Tier model (run bottom-up during edits)

| Tier | Target latency | Command | What it proves |
|------|----------------|---------|----------------|
| **0 — parallel host** | ~5–15s (PS7) / ~30–60s (PS5.1 sequential) | `.\scripts\pns_local_dev_parallel.ps1` | Independent doc/fixture/metadata gates (no Gradle); PS7 uses `-Parallel`, PS5.1 runs sequentially |
| **1 — prerelease host** | ~10–20s | `.\scripts\pns_prerelease_gate.ps1 -SkipGradle` | Full prerelease host lane (toolchain host + DNG fixtures + F-Droid + repro + security config) |
| **2 — Kotlin host** | ~2–8 min | `.\scripts\pns_verify_toolchain.ps1 -RunTests` | `assembleDebug`, Detekt, lint, unit tests, Kover floor, changelog/license/SBOM |
| **3 — USB (one serial)** | ~5–20 min | See USB matrix below | Device truth; **never** overlap capture + chrome on one serial |
| **4 — ship** | Tier 2 + 1 + optional 3 | `.\scripts\pns_prerelease_gate.ps1` then `-IncludeUsb` when device online | Release-ready host + optional USB subset |

**Rule:** Feed stdout back into the agent session and fix failures **locally** before declaring a milestone done. CI mirrors Tier 2 (`.github/workflows/toolchain-verify.yml`) and doc verify (`plan-doc-verify.yml`) — it does not replace USB Tier 3.

---

## Parallel vs sequential (host)

### Safe to run in parallel (no Gradle daemon contention)

These are wired in `pns_local_dev_parallel.ps1` (PowerShell **7+** runs them concurrently; **5.1** falls back to sequential with the same pass/fail contract):

| Script | Role |
|--------|------|
| `pns_changelog_gate.ps1` | CHANGELOG ↔ coverage manifest |
| `pns_template_doc_link_check.ps1` | KNOWLEDGE_BASE / DECISION_LOG links |
| `pns_perf_budget_host_gate.ps1` | `PerfBudget.kt` ↔ `PERFORMANCE_BUDGETS.md` |
| `pns_license_inventory.ps1` | `LICENSES.md` ↔ `libs.versions.toml` |
| `pns_fdroid_metadata_validate.ps1` | F-Droid metadata |
| `pns_repro_build_verify.ps1` | Lockfile + legal + SBOM fingerprint |
| `pns_fixture_dng_gates.ps1` | ReferenceApp fixture DNG openability (Python) |

### Must stay sequential

| Constraint | Reason |
|------------|--------|
| **One Gradle graph at a time** | `assembleDebug`, `detekt`, `lintDebug`, `testDebugUnitTest`, `koverVerify` share daemon + file locks |
| **USB on one `PNS_ADB_SERIAL`** | Camera mutex; heat; false `ERROR_CAMERA_DEVICE` when gates overlap |
| **`pns_capture_pipeline_verify` then `pns_chrome_ux_gate`** | Documented fleet rule — same serial, sequential only |
| **Shared schema files** | See [Multi-agent doc](MULTI_AGENT_PARALLEL_ORCHESTRATION.md) § Shared schema lock |

---

## USB gate matrix (Tier 3)

| Change area | Minimum USB | Notes |
|-------------|-------------|-------|
| RAW / session / DNG | `pns_capture_pipeline_verify.ps1` | Alone on serial |
| Preview chrome / focal / readout | `pns_chrome_ux_gate.ps1 -FocalMmSlot 85` (or `150` for tele) | Alone on serial |
| Both capture + chrome | Run **sequentially** | Never parallel |
| Fleet matrix | `pns_fleet_matrix_scan.ps1` | Cold probe hub |
| Parity | `pns_fleet_parity_sweep.ps1 -Mode Delta` | Ask Full vs Delta first |
| Pre-release USB subset | `pns_prerelease_gate.ps1 -IncludeUsb` | Orchestrates capture → chrome → eye-AF |

Always **`adb shell am force-stop dev.pointandshoot`** after a USB session (battery rule in `AGENTS.md`).

---

## IDE integration (VS Code / Cursor)

Tasks live in [`.vscode/tasks.json`](../.vscode/tasks.json):

| Task | Maps to |
|------|---------|
| **P&S: local parallel host (Tier 0)** | `pns_local_dev_parallel.ps1` |
| **P&S: prerelease host (Tier 1)** | `pns_prerelease_gate.ps1 -SkipGradle` |
| **P&S: toolchain + tests (Tier 2)** | `pns_verify_toolchain.ps1 -RunTests` |
| **P&S: prerelease full (Tier 4 host)** | `pns_prerelease_gate.ps1` |

**Watch mode:** Gradle/Detekt do not ship a repo-wide file watcher. Practical loop:

1. On save batch → run **Tier 0** (parallel host).
2. Before commit → **Tier 1** or **Tier 2** depending on Kotlin touched.
3. Before PR / release → **Tier 4** + USB when device available.

Optional: `pre-commit run --all-files` (gitleaks + toolchain `-SkipGradle`) on commit.

---

## CI mapping

| GitHub workflow | Local equivalent |
|---------------|------------------|
| `toolchain-verify.yml` | `pns_verify_toolchain.ps1 -RunTests` |
| `plan-doc-verify.yml` | `pns_verify_toolchain.ps1 -SkipGradle` |
| `security-scan.yml` (gitleaks) | `pre-commit` gitleaks hook or local `gitleaks detect` |
| `codeql-analysis.yml` | No local substitute — CI only; prerelease gate checks workflow **presence** |
| `build-signed.yml` | Local `assembleRelease` + `pns_repro_build_verify.ps1 -ApkPath …` |

---

## Out of scope (this doc)

- Replacing Android Studio’s incremental compile / Compose preview
- Cloud VM farm for parallel USB (one physical serial per device gate)
- Auto-fix Detekt findings (baseline exists; fix incrementally)

---

## Implementation status

| Item | Status |
|------|--------|
| Tier 0 `pns_local_dev_parallel.ps1` | **Shipped** |
| Tier 1–4 via existing `pns_prerelease_gate.ps1` / `pns_verify_toolchain.ps1` | **Shipped** (Milestone T.12) |
| VS Code tasks | **Shipped** |
| Gradle watch daemon | **Not planned** — use Tier 0 on save, Tier 2 before push |
| Full CI replacement | **Not planned** — local proves faster feedback; CI remains merge gate |

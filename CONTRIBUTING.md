# Contributing to Point & Shoot

Thank you for helping improve a FOSS pro camera app. This project prioritizes **device truth**, **minimal diffs**, and **locked subsystems** (DNG, preview chrome, fleet matrix).

## Before you start

1. Read [`AGENTS.md`](AGENTS.md) if you use Cursor or automation scripts.
2. Read [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) for canonical docs → code → gates.
3. For capture/DNG/fleet/chrome changes: grep [`docs/AGENT_REGRESSION_MEMORY.md`](docs/AGENT_REGRESSION_MEMORY.md) first.

## Development setup

| Requirement | Notes |
|-------------|--------|
| **JDK 21** | Android Studio JBR or [`scripts/pns_java_home.ps1`](scripts/pns_java_home.ps1) |
| **Android SDK** | `compileSdk 36`; NDK **26.3.11579264** for native code |
| **Python 3** | DNG/tiff gate scripts under `scripts/*.py` |
| **USB device** | Optional but required for capture/chrome proof — copy [`scripts/pns_adb_device.env.example`](scripts/pns_adb_device.env.example) → `pns_adb_device.env` (gitignored) |
| **pre-commit** | Recommended — see below |

Host gate (full):

```powershell
.\scripts\pns_verify_toolchain.ps1 -RunTests
```

Docs-only fast path:

```powershell
.\scripts\pns_verify_toolchain.ps1 -SkipGradle
```

## Pre-commit hooks

```powershell
pip install pre-commit
pre-commit install
pre-commit run --all-files
```

Hooks mirror CI subset: **gitleaks** + **`pns_verify_toolchain.ps1 -SkipGradle`** (UTF-8, PowerShell parse, FOSS dep-audit, changelog, SBOM).

## Branch and commit flow

- Branch from **`main`** with a short descriptive name (`fix/dng-metadata`, `docs/contributing`).
- **Conventional Commits** (soft): prefer `feat:`, `fix:`, `docs:`, `chore:`, `ci:`, `refactor:`, `test:`, `build:` in PR titles — [`conventional-pr-title.yml`](.github/workflows/conventional-pr-title.yml) warns on mismatch.
- One logical change per PR when possible.
- **Do not** commit secrets, keystores, or `scripts/pns_adb_device.env`.

## Required CI checks (GitHub)

| Workflow | When |
|----------|------|
| [`toolchain-verify.yml`](.github/workflows/toolchain-verify.yml) | Kotlin / scripts / gradle changes |
| [`plan-doc-verify.yml`](.github/workflows/plan-doc-verify.yml) | Plan / memory / ADR docs |
| [`security-scan.yml`](.github/workflows/security-scan.yml) | Gitleaks + Trivy |
| [`codeql-analysis.yml`](.github/workflows/codeql-analysis.yml) | App / native / gradle changes |

See [GitLab CI mirror](#gitlab-ci-mirror) below.

## Pull request checklist

See [`.github/pull_request_template.md`](.github/pull_request_template.md). Minimum:

- [ ] `pns_verify_toolchain.ps1 -RunTests` green (or equivalent CI)
- [ ] Capture/session/DNG changes → USB proof on **CPH2583** when applicable
- [ ] User-visible changes → [`CHANGELOG.md`](CHANGELOG.md) + [`scripts/changelog_coverage.v1.json`](scripts/changelog_coverage.v1.json)
- [ ] Settings/constants → [`docs/PNS_TECHNICAL_SETTINGS.md`](docs/PNS_TECHNICAL_SETTINGS.md) same commit

## Agent vs human paths

| Contributor | Guidance |
|-------------|----------|
| **Human** | Normal PR flow; subjective UX / store copy stays in Milestone **H** human rows |
| **Agent** | Follow [`BUILD_PLAN.md`](BUILD_PLAN.md) sprints; use [`PROMPT_LIBRARY.md`](PROMPT_LIBRARY.md); never tick `[x]` without Appendix A evidence |
| **Both** | Preview chrome layout is **locked** — behavioral fixes only unless explicitly requested |

## Dev container

[`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json) supports host-only gates (Gradle unit tests, Python scripts). **USB ADB and device gates stay on the host** — mount the repo on Windows/macOS/Linux with platform-tools for real hardware proof.

## Code style and coverage (Sprint T.7)

| Layer | Tool | Gate |
|-------|------|------|
| Kotlin static analysis | Detekt | `:app:detekt` |
| Android / Compose | Lint | `:app:lintDebug` |
| JVM unit coverage (scoped) | Kover | `:app:koverVerify` — **40% line floor** on fleet helpers + DNG metadata/bracket schedulers (excludes Compose UI + USB byte pipelines — [`ADR-0007`](docs/adr/0007-code-style-gate.md)) |
| Formatting | **Detekt-only** | Spotless/ktfmt **not** adopted (baseline churn) — see ADR-0007 |

### New file size (soft policy)

Applies to **new** files only — do not split legacy monoliths opportunistically.

| Kind | Target | Exempt |
|------|--------|--------|
| Compose UI | ≤250 lines | `PreviewEngineScreen.kt` until M23 extraction |
| Pure logic / helpers | ≤150 lines | — |

Reviewers may request a split when a new file exceeds targets without justification.

### Integration tests (`androidTest`)

There is **no checked-in `androidTest/` tree**. Device truth uses USB ADB scripts instead:

| Need | Script |
|------|--------|
| RAW still / DNG | `pns_photo_capture_verify.ps1`, `pns_aux_dng_capture_analyze.ps1` |
| Preview chrome | `pns_chrome_ux_gate.ps1` |
| Fleet matrix | `pns_fleet_matrix_scan.ps1` |

See [`AGENTS.md`](AGENTS.md) automation table for the full catalog.

### Preview chrome accessibility (USB only)

Preview chrome layout is **locked** — do not add Paparazzi or refactor for a11y without an explicit unlock request.

| Scope | Gate | Notes |
|-------|------|-------|
| **Host JVM** | `AboutScreenA11yTest`, `ChromeSettingsSearchA11yTest` | Semantics strings + Settings search index |
| **USB / device** | [`scripts/pns_a11y_dump_gate.ps1`](scripts/pns_a11y_dump_gate.ps1) | `uiautomator dump` on preview foreground; asserts focusable buttons have `content-desc` |

Run USB gate on **CPH2583** during release prep (not CI — requires device + preview session):

```powershell
.\scripts\pns_a11y_dump_gate.ps1
```

### Performance budgets (host)

[`scripts/pns_perf_budget_host_gate.ps1`](scripts/pns_perf_budget_host_gate.ps1) asserts [`PERFORMANCE_BUDGETS.md`](PERFORMANCE_BUDGETS.md) stays aligned with [`PerfBudget.kt`](app/src/main/java/dev/pointandshoot/PerfBudget.kt) and [`PerfBudgetTest.kt`](app/src/test/java/dev/pointandshoot/PerfBudgetTest.kt). Wired in `pns_verify_toolchain.ps1`.

### Visual regression (preview chrome locked)

Full policy: [`docs/VISUAL_REGRESSION_POLICY.md`](docs/VISUAL_REGRESSION_POLICY.md).

| Proof type | When | Tool |
|------------|------|------|
| **Manual screencap** | Any visible UI / settings change | [`pns_device_screencap.ps1`](scripts/pns_device_screencap.ps1) → `hfr-runs/` or PR attachment |
| **USB pixel gate** | Eye-AF overlay alignment (release prep) | [`pns_eye_af_pixel_gate.ps1`](scripts/pns_eye_af_pixel_gate.ps1) via [`pns_prerelease_gate.ps1 -IncludeUsb`](scripts/pns_prerelease_gate.ps1) |
| **Paparazzi** | **Deferred** — Settings/About scaffold only | `@Ignore` in `*PaparazziTest.kt`; **no** snapshots on locked preview chrome |

**Do not** enable Paparazzi on `PreviewEngineScreen` or change chrome layout for snapshot tests without an explicit maintainer unlock request.

## GitLab CI mirror

## Security

Report vulnerabilities privately — [`SECURITY.md`](SECURITY.md).

## License

By contributing, you agree your contributions are licensed under the project [**Apache-2.0**](LICENSE) license.

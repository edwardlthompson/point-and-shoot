# Security Triage

Weekly CVE triage playbook for Dependabot alerts and release security gates (Point & Shoot).

Vulnerability reporting: [`SECURITY.md`](../SECURITY.md). Alignment: [`BOOTSTRAP_ALIGNMENT.md`](BOOTSTRAP_ALIGNMENT.md).

## Setup (one-time, [HUMAN])

1. GitHub → **Settings** → **Code security and analysis**
2. Enable **Dependabot alerts** and **Dependabot security updates**
3. Enable **Private vulnerability reporting**
4. Confirm [`.github/dependabot.yml`](../.github/dependabot.yml) covers Gradle / GitHub Actions
5. Branch protection on `main` — recommended required checks (P&S names):
   - **Toolchain verify**
   - **Security scan**
   - **CodeQL**
   - **Dependency Review** (PRs)
   - **OpenSSF Scorecard** — optional until maintainer enables as required (workflow is non-blocking by default)

## Weekly triage pass

Recommended cadence: **Monday** (aligned with Scorecard schedule).

| Step | Owner | Action |
|------|-------|--------|
| 1 | HUMAN | Open **Security → Dependabot alerts**; Critical/High first |
| 2 | HUMAN | Review Dependabot version-update PRs |
| 3 | AGENT | Apply bumps, run `pns_verify_toolchain.ps1 -RunTests`, open/merge PRs |
| 4 | AUTO | Toolchain verify, Security scan, CodeQL validate merges |
| 5 | HUMAN | Merge or defer with ADR/DECISION_LOG note |
| 6 | AUTO | Review latest **OpenSSF Scorecard** run (SARIF → Code scanning) |
| 7 | AGENT | `/dependabot` or `/triage` batch command |

## OpenSSF Scorecard

- Workflow: [`.github/workflows/scorecard.yml`](../.github/workflows/scorecard.yml)
- Triage SARIF findings into BUILD_PLAN `[AGENT]` rows or dismiss with rationale in `DECISION_LOG.md`
- Do **not** treat Scorecard as merge-blocking until `[HUMAN]` confirms branch protection

## Dependency Review

- Workflow: [`.github/workflows/dependency-review.yml`](../.github/workflows/dependency-review.yml)
- Runs on PRs to `main`; fails on new High/Critical vulnerabilities

## Triage decisions

| Decision | When | Action |
|----------|------|--------|
| **Fix** | Patch available, low risk | Merge Dependabot PR or `[AGENT]` applies bump |
| **Defer** | No fix yet, acceptable risk | Issue + expiry; log in `DECISION_LOG.md` |
| **Dismiss** | False positive / N/A | Document rationale |

## Release gate (before tag / Obtainium ship)

- 🔲 Weekly triage completed within last **7 days**
- 🔲 Zero open **Critical/High** Dependabot alerts (or documented `[HUMAN]` exception)
- 🔲 `[AUTO]` Security scan + CodeQL green on `main`
- 🔲 Changelog coverage: `pns_changelog_gate.ps1` / `pns_github_release.ps1` skill

Camera/HAL complexity may require USB proof for security-relevant capture changes — see `AGENTS.md`.

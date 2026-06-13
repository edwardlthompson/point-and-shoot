# Security policy

## Supported versions

Security fixes are applied to the **latest release** published on [GitHub Releases](https://github.com/edwardlthompson/point-and-shoot/releases). Pre-release / sideload builds from `main` receive best-effort fixes but are not a long-term support line.

| Version | Supported |
|---------|-----------|
| Latest tagged release (`v*`) | Yes |
| Older tagged releases | No |
| Debug APK from `main` | Best effort only |

Check [`CHANGELOG.md`](CHANGELOG.md) and [`app/build.gradle.kts`](app/build.gradle.kts) `versionName` / `versionCode` for the current release.

## Reporting a vulnerability

**Please do not open public GitHub issues for undisclosed security problems.**

1. Use [GitHub Private Vulnerability Reporting](https://github.com/edwardlthompson/point-and-shoot/security/advisories/new) (Security → Advisories → **Report a vulnerability**), or
2. Contact the maintainer through a private channel you already use for this project.

Include:

- Affected version / commit
- Steps to reproduce
- Impact assessment (data exposure, privilege escalation, etc.)
- Optional proof-of-concept

## Response expectations

| Stage | Target |
|-------|--------|
| Initial acknowledgement | **7 days** |
| Severity triage + planned fix window | **14 days** |
| Fix or documented mitigation on `main` | **90 days** for high/critical (best effort) |

Complex camera/HAL issues may require USB proof on fleet hardware; we will keep reporters updated on status.

## Automated scanning (CI)

| Tool | Workflow | Scope |
|------|----------|--------|
| **CodeQL** | [`.github/workflows/codeql-analysis.yml`](.github/workflows/codeql-analysis.yml) | Java/Kotlin (`:app:assembleDebug`) |
| **Gitleaks** | [`.github/workflows/security-scan.yml`](.github/workflows/security-scan.yml) | Secret detection (`.gitleaks.toml`) |
| **Trivy** | [`.github/workflows/security-scan.yml`](.github/workflows/security-scan.yml) | FS vuln scan (`gradle/`, `app/build.gradle.kts`; `.trivyignore`) |
| **Dependabot** | [`.github/dependabot.yml`](.github/dependabot.yml) | GitHub Actions + Gradle (grouped) |
| **FOSS dep-audit** | [`scripts/pns_verify_toolchain.ps1`](scripts/pns_verify_toolchain.ps1) | Blocks Play Services / Firebase / ads SDKs |

## Scope notes

- **ML Kit face detection** is an intentional on-device exception — see [ADR-0006](docs/adr/0006-mlkit-face-detection-exception.md).
- Release signing keys and `keystore.properties` must **never** be committed (see [`.gitignore`](.gitignore)).
- Device serials belong in gitignored [`scripts/pns_adb_device.env`](scripts/pns_adb_device.env) only.

## Security-related development

Before changing auth, network, or storage paths: read [`KNOWLEDGE_BASE.md`](KNOWLEDGE_BASE.md) and [`PROMPT_LIBRARY.md`](PROMPT_LIBRARY.md). Host gates: `pns_verify_toolchain.ps1`, `pns_prerelease_gate.ps1` (Milestone T.12).

---
name: github-release
description: >-
  Cut a Point & Shoot GitHub release: bump versionCode/versionName, move CHANGELOG
  Unreleased to a dated section, sync changelog_coverage.v1.json, build APK, create
  gh release with changelog body + assets. Use when the user asks to ship, publish,
  or cut a GitHub release, release notes, or version bump for Obtainium.
---

# GitHub release (Point & Shoot)

## When to use

Apply this skill when the user asks to **make a release**, **publish to GitHub**, **cut a beta**, **bump version for Obtainium**, or similar.

## Prerequisites

- **Unreleased content** in `CHANGELOG.md` under `## Unreleased` (user-visible bullets). If empty, use `-AllowEmptyUnreleased` only when the user explicitly wants a packaging-only drop.
- **`gh` CLI** authenticated (`gh auth status`) for publish.
- **Release signing** configured (`keystore.properties` or env vars) for `assembleRelease`.
- Working tree clean enough to commit version/changelog files before publish (recommended).

## One-command flow (maintainer)

```powershell
# 1) Prepare — review diff before commit
.\scripts\pns_github_release.ps1 -PrepareOnly

# 2) Commit CHANGELOG.md, changelog_coverage.v1.json, app/build.gradle.kts, PnsExternalUrl.kt

# 3) Publish — build APK, tag, GitHub release
.\scripts\pns_github_release.ps1 -Publish -SkipPrepare -Prerelease

# 4) Push branch + tag
git push origin main
git push origin v0.14.0-beta.7   # example; tag from script output
```

Optional overrides:

```powershell
.\scripts\pns_github_release.ps1 -PrepareOnly -Tag 0.15.0-beta.1 -Summary "Pre-release for Milestone 23."
.\scripts\pns_github_release.ps1 -Publish -SkipPrepare -Draft
.\scripts\pns_github_release.ps1 -DryRun -PrepareOnly
```

## What `-PrepareOnly` updates (same commit)

| File | Change |
|------|--------|
| `CHANGELOG.md` | Moves `## Unreleased` → `## [x.y.z] - date`; resets Unreleased placeholder |
| `scripts/changelog_coverage.v1.json` | `latestRelease.tag`, `.date`, `.versionCode` |
| `app/build.gradle.kts` | `versionCode`, `versionName` (semver, no leading `v`) |
| `app/.../PnsExternalUrl.kt` | `PNS_GITHUB_LATEST_RELEASE_TAG` |

Then runs **`pns_changelog_gate.ps1`** (must pass).

## Versioning (Android + semver)

| Field | Convention | Example |
|-------|------------|---------|
| **versionName** | Semver in `app/build.gradle.kts` (no leading `v`) | `0.14.0-beta.6` |
| **Git tag** | `v` + versionName | `v0.14.0-beta.6` |
| **versionCode** | Monotonic integer (`incrementOnly` +1 per release); semver lives in **versionName** | `22000` → `22001` |
| **APK filename** | `{appDisplayName}-{versionName}.apk` | `Point-and-Shoot-0.14.0-beta.6.apk` |

Helpers: **`scripts/pns_release_naming.ps1`** (dot-sourced by packaging + github release).

## What `-Publish` does

1. `pns_release_packaging.ps1` → `dist/Point-and-Shoot-{versionName}.apk`
2. Annotated git tag `v<semver>` (unless `-SkipGitTag`)
3. `gh release create` with:
   - **Body:** changelog section for that version + link to full `CHANGELOG.md`
   - **Assets:** renamed APK + **`CHANGELOG.md`** attached
4. Artifact JSON under `hfr-runs/github_release_*`

Config: `scripts/release_config.v1.json` (`appDisplayName`, GitHub owner/repo, tag prefix, `versionCodePolicy`).

## App About section

Settings → **About & heritage** includes:

- **What's new (GitHub release notes)** → `/releases/latest`
- **Full changelog (GitHub)** → `CHANGELOG.md` on `main`

Constants live in `PnsExternalUrl.kt`. Gate: `pns_about_links_verify.ps1`.

## Agent checklist

1. Read `## Unreleased` — if empty, ask user to add bullets or confirm `-AllowEmptyUnreleased`.
2. Run `-PrepareOnly`; show summary of new tag, versionCode, and changelog header.
3. **Do not commit** unless the user asked to commit.
4. For publish: confirm `gh auth`, signing, and that prepare changes are committed.
5. Run `-Publish -SkipPrepare`; report release URL and remind to `git push` tag.
6. Update `requiredMentions` in `changelog_coverage.v1.json` when a new milestone must stay in CHANGELOG forever.

## Do not

- Bump `versionCode` without updating coverage manifest (toolchain gate fails).
- Skip `CHANGELOG.md` on the GitHub release (script attaches it by default).
- Tell the user the release is done until `gh release create` succeeds or they confirm manual upload.

## Legacy script

`pns_release_automation.ps1` remains for tag-only uploads; prefer **`pns_github_release.ps1`** for the full prepare + publish path.

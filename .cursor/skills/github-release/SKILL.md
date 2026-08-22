---
name: github-release
description: >-
  Cut a Point & Shoot GitHub release: bump versionCode/versionName, move CHANGELOG
  Unreleased to a dated section, sync changelog_coverage.v1.json and F-Droid
  metadata/changelog excerpt, build APK, create gh release with changelog body +
  APK + `{apk}.sha256` sidecar. Use when the user asks to ship, publish, or cut a GitHub release,
  release notes, or version bump for Obtainium.
---

# GitHub release (Point & Shoot)

## When to use

Apply this skill when the user asks to **make a release**, **publish to GitHub**, **cut a version**, **bump version for Obtainium**, or similar. `/ship` always uses this path and must upload a **production-signed** APK.

## Prerequisites

- **Unreleased content** in `CHANGELOG.md` under `## Unreleased` (user-visible bullets). If empty, use `-AllowEmptyUnreleased` only when the user explicitly wants a packaging-only drop.
- **`gh` CLI** authenticated (`gh auth status`) for publish.
- **Release signing** configured (`keystore.properties` or env vars) for `assembleRelease`.
- Working tree clean enough to commit version/changelog files before publish (recommended).

## One-command flow (maintainer)

```powershell
# 1) Prepare — review diff before commit
.\scripts\pns_github_release.ps1 -PrepareOnly

# 2) Commit CHANGELOG.md, changelog_coverage.v1.json, app/build.gradle.kts,
#    PnsExternalUrl.kt, metadata/metadata.yml, metadata/en-US/changelogs/{versionCode}.txt

# 3) Publish — build APK, tag, GitHub release
# Always pass -Tag when using -SkipPrepare (or omit -Tag to reuse gradle versionName).
.\scripts\pns_github_release.ps1 -Publish -SkipPrepare

# 4) Push branch + tag
git push origin main
git push origin v0.14.0   # example; tag from script output
```

Optional overrides:

```powershell
.\scripts\pns_github_release.ps1 -PrepareOnly -Tag 0.15.0 -Summary "Milestone 23."
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

Then runs **`pns_changelog_gate.ps1`** (must pass). Before any release cut, run **`pns_prerelease_gate.ps1`** (see agent checklist).

## Versioning (Android + semver)

| Field | Convention | Example |
|-------|------------|---------|
| **versionName** | Stable semver in `app/build.gradle.kts` (no leading `v`, no `-beta.N`) | `0.14.0` |
| **GitHub title / About** | `{appTitle} {versionName}` | `Point & Shoot 0.14.0` |
| **Git tag** | `v` + versionName | `v0.14.0` |
| **versionCode** | Monotonic integer (`incrementOnly` +1 per release); semver lives in **versionName** | `22016` → `22017` |
| **APK filename** | `{appDisplayName}-{versionName}.apk` | `Point-and-Shoot-0.14.0.apk` |

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
2. Run **`.\scripts\pns_prerelease_gate.ps1`** (host lane) before `-PrepareOnly` / `-Publish`. Use **`-SkipGradle`** for a fast docs/fixture pass; full ship requires default (runs **`pns_verify_toolchain.ps1 -RunTests`**). USB: **`-IncludeUsb -Serial <adb>`** runs capture then chrome sequentially on one device.
3. Run `-PrepareOnly`; show summary of new tag, versionCode, and changelog header.
4. **Do not commit** unless the user asked to commit.
5. For publish: confirm `gh auth`, **production signing** (`keystore.properties` or `ANDROID_KEYSTORE_*`), and that prepare changes are committed. Halt if signing is missing — do not upload a debug-key APK.
6. Run `-Publish -SkipPrepare` (no `-Prerelease` unless the user asked). Report release URL and remind to `git push` tag.
7. Update `requiredMentions` in `changelog_coverage.v1.json` when a new milestone must stay in CHANGELOG forever.

## Do not

- Bump `versionCode` without updating coverage manifest (toolchain gate fails).
- Skip `CHANGELOG.md` on the GitHub release (script attaches it by default).
- Tell the user the release is done until `gh release create` succeeds or they confirm manual upload.
- Upload a debug-key or unsigned APK on `/ship`.

## Legacy script

`pns_release_automation.ps1` remains for tag-only uploads; prefer **`pns_github_release.ps1`** for the full prepare + publish path.

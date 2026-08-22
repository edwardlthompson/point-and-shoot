# Release: commit, push, and update docs

**User invoked `/push` — explicit approval for `git push`.**

## Version + signing (mandatory)

- **Name:** GitHub title and About line are `Point & Shoot {MAJOR.MINOR.PATCH}`. Do **not** auto-cut `-beta.N` names. Next prepare after `0.14.0-beta.22` is **`0.14.0`**.
- **Signed APK:** `-Publish` must upload a **production-signed** `assembleRelease` APK. Halt if `keystore.properties` / `ANDROID_KEYSTORE_*` is missing. Never pass `-AllowDebugKey`. Never pass `-Prerelease` unless the user explicitly asks for a GitHub pre-release.

## Step 1 — Pre-release validation

```powershell
.\scripts\pns_prerelease_gate.ps1 -SkipGradle
.\scripts\pns_changelog_gate.ps1
```

Update `CHANGELOG.md` Unreleased and `scripts/changelog_coverage.v1.json` when bumping `versionCode`.

## Step 2 — Release notes

Draft from `RELEASE_NOTES.md.example` / dated `CHANGELOG.md` section.

## Step 3 — Commit and push

- Stage **explicit paths only** (never `git add .`)
- Commit with conventional message; body lists key changes
- `git push`
- `.\scripts\pns_check_github_ci.ps1 -WaitSeconds 600`

## Step 4 — Release

Follow `.cursor/skills/github-release/SKILL.md`:

```powershell
.\scripts\pns_github_release.ps1 -PrepareOnly
# commit version/changelog files, then:
.\scripts\pns_github_release.ps1 -Publish -SkipPrepare
git push origin HEAD
git push origin v<semver>
```

`-Publish` runs `pns_release_packaging.ps1` (signed APK + zipalign + apksigner) and uploads `{apk}` + `{apk}.sha256` + `CHANGELOG.md`.

## Step 5 — Cleanup

Mark BUILD_PLAN ✅; archive to `BUILD_PLAN_COMPLETED.md` when closing a sprint.

Do not force-push or skip hooks. Halt and escalate [HUMAN] on failure.

Begin now.

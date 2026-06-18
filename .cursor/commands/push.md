# Release: commit, push, and update docs

**User invoked `/push` — explicit approval for `git push`.**

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

Prefer `.cursor/skills/github-release/SKILL.md` → `scripts/pns_github_release.ps1` for Obtainium-ready GitHub releases.

## Step 5 — Cleanup

Mark BUILD_PLAN ✅; archive to `BUILD_PLAN_COMPLETED.md` when closing a sprint.

Do not force-push or skip hooks. Halt and escalate [HUMAN] on failure.

Begin now.

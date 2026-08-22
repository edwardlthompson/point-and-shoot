# Publish release super workflow

Invoking this command grants explicit approval for `git push` per destructive-ops rules.

**Stable name:** cut `Point & Shoot MAJOR.MINOR.PATCH` (no `-beta.N`). Next auto-prepare after `0.14.0-beta.22` is **`0.14.0`**.

**Signed APK:** `/push` step 4 must run `pns_github_release.ps1 -Publish` with a production keystore (`keystore.properties` or `ANDROID_KEYSTORE_*`). Do not upload a debug-key APK. Halt if signing is missing.

Read and execute each sub-command in order. After each step, summarize pass/fail.

1. Read @.cursor/commands/prerelease.md — execute fully
2. Read @.cursor/commands/push.md — execute fully
3. Read @.cursor/commands/regress.md — execute fully

Begin now.

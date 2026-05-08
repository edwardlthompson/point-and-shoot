## Release notes template

Copy this file to `RELEASE_NOTES.md` when preparing a release.

### Summary
- (1-3 bullets: what changed and why it matters)

### Highlights
- (Feature / fix / perf)

### Compatibility
- **Target device**: OnePlus 13 (dodge)
- **OS**: LineageOS 23 (Android 16 / API 36)
- **Notes**: (any known limitations)

### Changes
#### Added
- (bullets)

#### Changed
- (bullets)

#### Fixed
- (bullets)

### Verification
- [ ] [HOST] `.\gradlew.bat :app:assembleRelease` (or debug if release not set up yet)
- [ ] [ADB] `adb install -r ...apk`
- [ ] [ADB] Smoke run (no crash)
- [ ] [ADB] Logcat monitored with PID filter

### Upgrade notes
- (breaking changes, migration steps)

## Release notes template

Copy this file to `RELEASE_NOTES.md` when preparing a release.

### Summary
- (1–3 bullets: what changed and why it matters)

### Highlights
- (Feature / fix / perf)

### Compatibility
- **Target device**: OnePlus 13 (dodge)
- **OS**: LineageOS 23 (Android 16 / API 36)
- **Notes**: (any known limitations)

### Changes
#### Added
- (bullets)

#### Changed
- (bullets)

#### Fixed
- (bullets)

### Verification
- [ ] [HOST] `.\gradlew.bat :app:assembleRelease` (or debug if release not set up yet)
- [ ] [ADB] `adb install -r ...apk`
- [ ] [ADB] Smoke run (no crash)
- [ ] [ADB] Logcat monitored with PID filter

### Upgrade notes
- (breaking changes, migration steps)


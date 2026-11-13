# News Inbox

Auto-collected inbox of news, upstream updates, security fixes, and ideas relevant to this repo, appended daily by News Bot.
A Cursor planning agent should review these items and fold the useful ones into BUILD_PLAN; entries are append-only, newest at the bottom.


## 2026-11-13

**News and updates**

- Pillow heap overflow when saving TIFF via libtiff (CVE-2026-128067, high); affects <= 12.3.0, fixed in 13.0.0. [source](https://github.com/advisories/GHSA-xcvg-w56q-r89m)
- Android Developers case study: SmileID used CameraX to handle OEM orientation quirks, aspect stretching and OOM across 18,000+ devices. [source](https://developer.android.com/blog/posts/smileid-camerax-android-identity-verification)
- Kotlin 2.5.0-RC released (fixes a reified Enum<T> inference regression); Gradle 9.9.1 / 8.14.7 patch releases. [source](https://github.com/JetBrains/kotlin/releases/tag/v2.5.0-RC)

**Tasks for the planning agent**

- [ ] Pin pillow>=13.0.0 for JPEG/DNG compare scripts — The scripts import Pillow and the TIFF-save overflow affects all versions through 12.3.0. Label: `[AGENT]` · Priority: P1 · https://github.com/advisories/GHSA-xcvg-w56q-r89m
- [ ] Review CameraX device-quirk and OOM patterns from SmileID — Proven fixes for OEM orientation, stretching and low-RAM crashes apply to a pro camera pipeline. Label: `[AGENT]` · Priority: P3 · https://developer.android.com/blog/posts/smileid-camerax-android-identity-verification
- [ ] Smoke-test Kotlin 2.5.0-RC and bump Gradle wrapper — Catches compiler regressions before 2.5.0 stable. Label: `[AGENT]` · Priority: P3 · https://github.com/JetBrains/kotlin/releases/tag/v2.5.0-RC

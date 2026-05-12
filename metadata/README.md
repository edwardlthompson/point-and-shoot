# F-Droid metadata

This folder holds **placeholders and future** fastlane-style / YAML metadata for [F-Droid](https://f-droid.org/) inclusion.

**User-facing distribution** (GitHub Releases, Obtainium, and the roadmap for the main F-Droid repo) is documented in the root [**README.md**](../README.md) under **Documentation → Releases**.

When preparing an [F-Droid submission](https://f-droid.org/docs/Including_an_App/), typical expectations include:

- Application ID **`dev.pointandshoot`** (see `app/build.gradle.kts`).
- Source at a **tagged release** with reproducible or documented builds.
- Screenshots, summary/description, and license aligned with repo **`LICENSE`** (Apache-2.0).
- No proprietary blobs / no Play Services (already project constraints).

Phase 0 placeholders enforced:

- Apache-2.0 licensing
- No proprietary blobs
- No Google Play Services dependencies

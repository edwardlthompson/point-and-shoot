# LICENSES.md - Third-party license inventory

Source-of-truth for the **first-party (declared)** runtime, test, and build-time
dependencies of Point & Shoot. Closes BUILD_PLAN.md §9 "Security/F-Droid
hygiene" requirement: "Dependency/license scan gate (FOSS-only); SBOM
generation (optional, but recommended for transparency)".

This file is kept in sync with `gradle/libs.versions.toml` by
`scripts/pns_license_inventory.ps1`, which is run by
`scripts/pns_verify_toolchain.ps1` on every gate invocation. If the script
reports drift (new dep, removed dep, version mismatch), update **both**
this file and re-run the gate.

> Transitive dependencies are not enumerated here - the FOSS dep-audit in
> `pns_verify_toolchain.ps1` rejects any Play Services / Firebase / ML Kit /
> Play Billing / Ads references in any Gradle file or version catalog, which
> covers the proprietary-blob risk for transitive pulls. A full
> CycloneDX-style SBOM with transitive resolution is a Phase-1 follow-up.

## Apache-2.0 statement

Point & Shoot is licensed Apache-2.0 (see `LICENSE`). Every dependency below
is verified to be license-compatible with Apache-2.0 redistribution.

## Runtime dependencies (shipped in the APK)

| Coordinate | Version | SPDX | Origin |
|---|---|---|---|
| androidx.core:core-ktx | 1.16.0 | Apache-2.0 | Jetpack |
| androidx.lifecycle:lifecycle-runtime-ktx | 2.9.0 | Apache-2.0 | Jetpack |
| androidx.activity:activity-compose | 1.10.1 | Apache-2.0 | Jetpack |
| androidx.compose:compose-bom | 2026.04.01 | Apache-2.0 | Jetpack (BOM) |
| androidx.compose.ui:ui | (BOM) | Apache-2.0 | Jetpack |
| androidx.compose.ui:ui-graphics | (BOM) | Apache-2.0 | Jetpack |
| androidx.compose.ui:ui-tooling-preview | (BOM) | Apache-2.0 | Jetpack |
| androidx.compose.material3:material3 | (BOM) | Apache-2.0 | Jetpack |
| androidx.camera:camera-camera2 | 1.4.1 | Apache-2.0 | Jetpack (CameraX) |
| androidx.graphics:graphics-core | 1.0.0-alpha05 | Apache-2.0 | Jetpack |

## Debug-only dependencies (debug APK only, not in release)

| Coordinate | Version | SPDX | Origin |
|---|---|---|---|
| androidx.compose.ui:ui-tooling | (BOM) | Apache-2.0 | Jetpack |

## Test dependencies (not shipped in any APK)

| Coordinate | Version | SPDX | Origin | Notes |
|---|---|---|---|---|
| junit:junit | 4.13.2 | EPL-1.0 | https://junit.org/junit4/ | EPL-1.0 is FOSS-compatible. Used only as `testImplementation`; never linked into the APK. |
| org.json:json | 20240303 | JSON-LICENSE (\u2248MIT, "good not evil") | https://github.com/stleary/JSON-java | Real `org.json.JSONObject` for unit-testing `EncoderAttemptJsonAdapter.decode` (the Android stub on the unit-test classpath throws "Stub!"). Used only as `testImplementation`; the runtime APK uses Android's bundled `org.json` instead. The "good not evil" clause is widely treated as functionally MIT for redistribution; we never modify or redistribute the library. |

## Build-time plugins (host toolchain only)

| Plugin id | Version | SPDX | Origin |
|---|---|---|---|
| com.android.application | 8.7.3 | Apache-2.0 | Android Gradle Plugin |
| org.jetbrains.kotlin.android | 2.1.21 | Apache-2.0 | Kotlin |
| org.jetbrains.kotlin.plugin.compose | 2.1.21 | Apache-2.0 | Kotlin |

## Build / verification tooling (not a Gradle dep, listed for completeness)

| Tool | Version pin | SPDX | Origin |
|---|---|---|---|
| Gradle | 8.9 (`gradle/wrapper/gradle-wrapper.properties`) | Apache-2.0 | https://gradle.org |
| OpenJDK / JBR | 17 (Android Studio bundled `jbr`) | GPL-2.0-with-Classpath-Exception | https://openjdk.org |
| PowerShell | pwsh 7+ | MIT | https://github.com/PowerShell/PowerShell |
| Android SDK / build-tools | (per CI / local install) | Various Android SDK License | https://developer.android.com |

## License compatibility summary

* **Apache-2.0** (everything we ship) - ALLOWS redistribution under Apache-2.0.
* **EPL-1.0** (JUnit 4) - ALLOWED for `testImplementation` because we do not
  modify JUnit and we do not redistribute it (tests run on the host / CI).
* **JSON-LICENSE / "good not evil"** (`org.json:json`) - ALLOWED for
  `testImplementation` because we do not modify or redistribute the library;
  the unmodified MIT-style permission is sufficient for our use, and the
  runtime APK does not link against this artifact (it uses Android's bundled
  `org.json` instead).
* **GPL-2.0-with-Classpath-Exception** (OpenJDK / JBR) - ALLOWED because the
  Classpath Exception explicitly permits linking application code without
  GPL contagion.
* **MIT** (PowerShell tooling) - ALLOWED.

No copyleft license appears in any **shipped** artifact.

## Pending / planned dependencies (NDK pipeline)

When the native pipeline lands (see `NDK_PLAN.md`), the table will gain:

| Coordinate / source | Pinned tag | SPDX | Distribution |
|---|---|---|---|
| github.com/AOMediaCodec/libavif | TBD | BSD-2-Clause | source-build via FetchContent |
| github.com/libjxl/libjxl | TBD | BSD-3-Clause | source-build via FetchContent |
| github.com/google/highway | TBD | Apache-2.0 | transitive of libjxl |
| github.com/google/brotli | TBD | MIT | transitive of libjxl |
| AOMedia aom (libavif dep) | TBD | BSD-2-Clause + Alliance for Open Media Patent License 1.0 | transitive of libavif |

All five are verified Apache-2.0-compatible upstream. The pinned SHA-256 of
each source archive will be captured in `native/THIRD_PARTY.md` when the
fetch step is wired into CMake (`NDK_PLAN.md` §"Source-of-truth strategy").

## Bundled LUTs (Phase 4 — partial: code-generated entries shipped)

Every entry must be FOSS-licensed under an Apache-2.0-compatible SPDX (no
proprietary "free" LUTs from Lightroom, DaVinci, FilmConvert, etc.). The
runtime catalog ([`app/src/main/java/dev/pointandshoot/LutCatalog.kt`](app/src/main/java/dev/pointandshoot/LutCatalog.kt))
enforces the SPDX whitelist at JVM-test time via
[`LutCatalogTest`](app/src/test/java/dev/pointandshoot/LutCatalogTest.kt).
Asset-backed entries (ACES, Filmic) will live under `app/src/main/assets/luts/`
when the Gradle `downloadBundledLuts` task lands; each leaf folder will
contain `LICENSE.txt`, `SOURCE.txt`, and `SHA256.txt` siblings, and
`scripts/pns_license_inventory.ps1` will gain a LUT walker that validates the
on-disk layout against this table.

| LUT name | Source | Pinned tag / commit | SPDX | Scope | Status | SHA-256 |
|---|---|---|---|---|---|---|
| ACES sRGB → ACEScct | https://github.com/AcademySoftwareFoundation/OpenColorIO-Configs (`aces_1.2`) | TBD | Apache-2.0 | still + video | planned | TBD |
| ACEScct → sRGB Display | same as above | TBD | Apache-2.0 | still + video | planned | TBD |
| Filmic (Blender) sRGB → log | https://github.com/sobotka/filmic-blender | TBD | Apache-2.0 | still + video | planned | TBD |
| Rec.709 identity | code-generated by `BuiltInLuts.rec709Identity` (no upstream) | n/a | public-domain | still + video | **shipped** (catalog: `LutCatalog.None`) | n/a (code-generated) |
| B&W BT.601 | code-generated by `BuiltInLuts.bwBt601` from `Y = 0.299R + 0.587G + 0.114B` (BT.601 luma weights) | n/a | public-domain | still + video | **shipped** (catalog: `LutCatalog.BwBt601`) | n/a (code-generated) |
| B&W BT.709 | code-generated by `BuiltInLuts.bwBt709` from `Y = 0.2126R + 0.7152G + 0.0722B` (BT.709 luma weights) | n/a | public-domain | still + video | **shipped** (catalog: `LutCatalog.BwBt709`) | n/a (code-generated) |
| Point & Shoot Cinematic (teal-orange) | original to this repo (`BuiltInLuts.pnsCinematic`); shadow tint (0.30, 0.55, 0.70), highlight tint (1.00, 0.65, 0.35), 30 % strength, smoothstep-blended on BT.709 luma | n/a | Apache-2.0 | still + video | **shipped** (catalog: `LutCatalog.PnsCinematic`) | n/a (code-generated) |

**Sourcing rules** (enforced by `LutCatalogTest`):

* SPDX MUST be one of `{Apache-2.0, BSD-2-Clause, BSD-3-Clause, MIT, CC0-1.0, public-domain}` per `LutCatalog.ALLOWED_SPDX`.
* No LUT may be derived from a proprietary preset — the licensing chain must
  be fully auditable back to a FOSS upstream OR generated by us from
  public-domain math (encoding identities, BT.601 / BT.709 luma weights,
  Hermite smoothstep, etc.). The shipped Cinematic LUT is built from these
  primitives only; no proprietary look (Lightroom, DaVinci, FilmConvert) was
  reverse-engineered as source material.
* The chart images for X-Rite ColorChecker variants are intentionally NOT
  bundled (trademark / image rights). Reference Lab values (which are facts,
  not copyrightable) will be bundled in `assets/calibration/targets/*.json`
  when the calibration UI lands so the calibration mode can solve against
  them when the user has their own physical chart.
* User-imported `.cube` files via SAF skip this whitelist (the user owns
  their license compliance for imported content). The app neither mirrors
  nor redistributes user-imported LUTs.
* Build-time download via Gradle `downloadBundledLuts` task (planned): each
  upstream fetch is SHA-256-pinned; the LUT files themselves are NOT
  committed to git (build artifact); the `LICENSE.txt` / `SOURCE.txt` /
  `SHA256.txt` sidecars ARE committed and tracked here.

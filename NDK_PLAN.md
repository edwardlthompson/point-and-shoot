# NDK_PLAN.md - Native pipeline (libavif / libjxl / focus peaking)

Concrete plan for fleshing out `native/` from "stubs only" (Phase 0) to a
working JNI surface that the Kotlin capture engine can call. This document
has no device dependency and no upstream binaries are committed - it
describes how Phase 1+ will source and link them.

> Status: **scaffolding shipped (Phase 0)**. The Kotlin facade
> (`NativeEncoders`), the routing layer (`EncoderRoute`), the JNI stubs
> (`native/pns_native.cpp`), the CMake skeleton with commented
> `FetchContent` blocks (`native/CMakeLists.txt`), and the
> `NativeDiagnosticsScreen` for on-device validation are all in place.
> What is NOT yet wired: the `externalNativeBuild` block in
> `app/build.gradle.kts` and the libavif / libjxl `FetchContent` URL +
> SHA-256 pins. The fallback path (`isAvailable = false` -> JPEG output)
> is JUnit-tested and ADB-validated. See `BUILD_PLAN.md` §4 / §9 for the
> surrounding task context.

## Goals

1. **AVIF encoder** for the `Standard Pro` imaging profile (10-bit HDR /
   Display P3) that accepts a 10-bit YUV plane (or 16-bit interleaved RGB)
   and writes a `.avif` byte stream.
2. **JPEG XL encoder** for the `Ultra-Max` imaging profile (12-bit / Rec.
   2020) that accepts a 12-bit RGB / YUV plane and writes a `.jxl` byte
   stream.
3. **Focus-peaking shader** (preview-only, not capture-path) that runs on
   the GPU via Vulkan / GLES 3.x and surfaces high-frequency edges as a
   color overlay. Implemented as a Compose-side fragment shader, not as a
   classical NDK module - listed here for completeness.
4. **Stable JNI surface** that exposes the encoders to Kotlin via plain
   functions (no global state, single allocation per call).

## Non-goals

- Ship our own encoder. Both `libavif` and `libjxl` are mature; we
  consume them, we do not fork them.
- AVIF / JXL **decode** support. The app is a camera, not a viewer.
- Run-time format detection or automatic transcoding. The Kotlin engine
  picks the encoder per `ImagingProfile`.

## Library choice + licensing

| Library  | Upstream                                             | License    | Status              |
|----------|------------------------------------------------------|------------|---------------------|
| libavif  | https://github.com/AOMediaCodec/libavif              | BSD-2      | FOSS-compatible     |
| aom      | https://aomedia.googlesource.com/aom (libavif dep)   | BSD-2 + AL | FOSS-compatible     |
| libjxl   | https://github.com/libjxl/libjxl                     | BSD-3      | FOSS-compatible     |
| highway  | https://github.com/google/highway (libjxl dep)       | Apache-2.0 | FOSS-compatible     |
| brotli   | https://github.com/google/brotli (libjxl dep)        | MIT        | FOSS-compatible     |

All five are explicitly compatible with the project's Apache-2.0 license
and our `pns_verify_toolchain.ps1` FOSS audit (which only forbids Play
Services / Firebase / ML Kit / Play Billing / Ads).

Pinned versions will be recorded in `native/THIRD_PARTY.md` once we add
`fetchcontent`-style downloads to the CMake build.

## Source-of-truth strategy

We will **not** vendor the source trees in git. Instead, the CMake build
will use `FetchContent_Declare` to download tagged source archives during
the first build and cache them under `~/.gradle/caches/pns-native/`. This
keeps the repo small and lets Dependabot-style PRs bump the pinned tags.

Acceptance criteria for the fetch step:

- Pinned to a specific upstream tag + SHA-256.
- Build fails (with a clear message) if the SHA mismatches.
- Sources are extracted into `<build>/_deps/` and never copied into
  `native/` (which stays as JNI glue + headers only).

## JNI surface (shipped Phase 0)

The actual shape lives in
`app/src/main/java/dev/pointandshoot/NativeEncoders.kt`. The defensive
loader returns `Result.NotAvailable` whenever the .so is absent, so the
capture engine never sees an `UnsatisfiedLinkError`:

```kotlin
object NativeEncoders {
    sealed class Result {
        data class Success(val bytes: ByteArray) : Result()
        data object NotAvailable : Result()
        data class NativeError(val code: Int, val message: String? = null) : Result()
    }

    val isAvailable: Boolean
    val lastLoadError: String?
    fun version(): Int

    fun encodeAvif10Hdr(
        planeY: ByteArray, planeU: ByteArray, planeV: ByteArray,
        width: Int, height: Int, strideY: Int, strideUV: Int,
    ): Result

    fun encodeJxl12Rec2020(
        planeRgb: ByteArray, width: Int, height: Int, stride: Int,
    ): Result

    @JvmStatic private external fun nativeVersion(): Int
    @JvmStatic private external fun nativeEncodeAvif10Hdr(...): ByteArray?
    @JvmStatic private external fun nativeEncodeJxl12Rec2020(...): ByteArray?
}
```

Why `ByteArray?` instead of `OutputStream` (versus the original draft
above): streams across the JNI boundary trigger one upcall per chunk and
are easy to misuse. Returning the encoded bytes lets the encode-lane
executor pick the on-disk write strategy (atomic-rename-on-temp, MediaStore
pending entry, etc.). The capture pipeline calls these from the IO/encode
lane (`CAPTURE_ARCHITECTURE.md`) with bounded queue depth so the preview
lane never stalls.

The companion router lives at
`app/src/main/java/dev/pointandshoot/EncoderRoute.kt`:

```kotlin
object EncoderRoute {
    data class Decision(
        val profile: ImagingProfile,
        val rawWritten: RawMode,
        val tonalWritten: TonalContainer?,  // null when fallbackJpeg is true
        val fallbackJpeg: Boolean,
        val downgradeReason: String?,
    )

    fun decide(profile: ImagingProfile, nativeAvailable: Boolean): Decision
    fun downgradedProfiles(nativeAvailable: Boolean): List<ImagingProfile>
}
```

`EncoderRoute` is pure-data and JUnit-tested in `EncoderRouteTest` (9
tests). The Kotlin facade is JUnit-tested in `NativeEncodersFallbackTest`
(10 tests) covering the no-.so fallback path that the JVM unit-test
classpath always exercises.

## Gradle / CMake wiring (target)

`app/build.gradle.kts` additions (NOT yet present - tracked here):

```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += setOf("arm64-v8a")  // OnePlus 13 is 64-bit only
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++23", "-fno-rtti", "-fno-exceptions")
                arguments += "-DPNS_USE_LIBAVIF=ON"
                arguments += "-DPNS_USE_LIBJXL=ON"
            }
        }
    }
    externalNativeBuild {
        cmake {
            path = file("../native/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
```

`native/CMakeLists.txt` will gain:

- `FetchContent_Declare(libavif URL <pinned-tag-tarball> URL_HASH SHA256=<pinned>)`
- `FetchContent_Declare(libjxl  URL <pinned-tag-tarball> URL_HASH SHA256=<pinned>)`
- `add_subdirectory(${libavif_SOURCE_DIR})` + `target_link_libraries(pns_native PRIVATE avif)`
- Same for libjxl + highway + brotli.

## Build & verification gates (host-side)

When the NDK pipeline lands, the toolchain gate (`pns_verify_toolchain.ps1`)
will be extended to:

1. Run `:app:assembleDebug` (already present) - ensures CMake fetches +
   compiles cleanly.
2. Run `:app:externalNativeBuildDebug` explicitly so a CMake-only break
   is reported up-front.
3. Confirm the `arm64-v8a` shared object is packaged in the APK
   (`unzip -l app-debug.apk | grep libpns_native.so`).
4. Forbid any new transitive proprietary dependencies via the existing
   FOSS dep-audit (already in place).

CI (`toolchain-verify.yml`) inherits all of the above for free since it
already runs `assembleDebug`.

## Fallback strategy (encoder unavailable)

If `System.loadLibrary("pns_native")` fails at runtime (e.g., the .so
didn't ship for the target ABI - shouldn't happen on OnePlus 13 but is
defensive against unexpected hosts), the Kotlin engine falls back per
`FAILURE_MATRIX.md`:

- AVIF / JXL writes are replaced by JPEG (Camera2 native).
- DNG (lossless) still ships - it's pure Camera2 `DngCreator`, no NDK.
- The HUD shows a one-shot "Native encoders unavailable; output downgraded
  to JPEG" message and writes the same to `PNS.Diagnostics`.

## Schedule

This work is **not on the critical path** for Phase 0 / Phase 1 of the
capture engine. It blocks the **Standard Pro** and **Ultra-Max** imaging
profiles' tonal-container outputs (AVIF / JXL) but not the DNG path. The
recommended order is:

1. **DONE (Phase 0)**. Ship the Kotlin facade + JNI stubs + CMake
   skeleton + diagnostics screen + JVM tests + install script. The
   capture engine can already call `EncoderRoute.decide(...)` and degrade
   to JPEG without the .so.
2. Land Phase 1 capture engine + DNG path (no NDK needed). The capture
   engine drives `EncoderRoute` per-shot.
3. Then land NDK pipeline behind the `pns.nativeEncoders` Gradle property
   so we can ship JPEG-only release builds in the meantime.
4. Validate AVIF / JXL outputs by pulling files and opening in desktop
   tooling (Phase 1 V&V gate in `BUILD_PLAN.md`).

## Human action required (before Phase 1)

Two things need a human decision before the encoder bodies can land:

1. **Install the NDK + CMake on dev hosts and CI runners.** Run
   `scripts/pns_install_ndk.ps1` to do this non-interactively once
   Android SDK Command-line Tools are present (the script prints exact
   instructions if they are not). On CI, use the `setup-android` action
   with the `ndk-version` and `cmake-version` inputs.
2. **Pin the libavif / libjxl tags + SHA-256s** in
   `native/CMakeLists.txt` and update the table in `native/THIRD_PARTY.md`.
   Both upstreams release frequently enough that the bake-in of a tag
   should be a deliberate review action, not an automated bump.

Everything else - the Kotlin facade, the route, the diagnostics screen,
the JNI symbol layout, and the fallback contract - is already in tree.

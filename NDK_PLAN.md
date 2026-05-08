# NDK_PLAN.md - Native pipeline (libavif / libjxl / focus peaking)

Concrete plan for fleshing out `native/` from "stubs only" (Phase 0) to a
working JNI surface that the Kotlin capture engine can call. This document
has no device dependency and no upstream binaries are committed - it
describes how Phase 1+ will source and link them.

> Status: **planning**. The on-device engine wiring depends on Phase 0
> probe results (RAW + dynamic-range exclusivity, 10-bit pipeline support).
> See `BUILD_PLAN.md` §4 / §9 for the surrounding task context.

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

## JNI surface (target)

```kotlin
// app/src/main/java/dev/pointandshoot/NativeEncoders.kt (planned)
object NativeEncoders {
    /**
     * Encode a 10-bit HDR plane to AVIF (Display P3) and write to [output].
     * Returns the number of bytes written.
     */
    external fun encodeAvif10Hdr(
        planeY: ByteArray, planeU: ByteArray, planeV: ByteArray,
        width: Int, height: Int, strideY: Int, strideUV: Int,
        output: java.io.OutputStream,
    ): Long

    /**
     * Encode a 12-bit RGB plane to JPEG XL (Rec. 2020). Single planar buffer.
     */
    external fun encodeJxl12Rec2020(
        planeRGB: ByteArray, width: Int, height: Int, stride: Int,
        output: java.io.OutputStream,
    ): Long

    init { System.loadLibrary("pns_native") }
}
```

The capture pipeline will call these from the IO/encode lane (see
`CAPTURE_ARCHITECTURE.md`), with bounded queue depth so the preview lane
never stalls.

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

1. Land Phase 1 capture engine + DNG path (no NDK needed).
2. Then land NDK pipeline (this doc) behind a `BuildConfig`-style flag so
   we can ship JPEG-only release builds in the meantime.
3. Validate AVIF / JXL outputs by pulling files and opening in desktop
   tooling (Phase 1 V&V gate in `BUILD_PLAN.md`).

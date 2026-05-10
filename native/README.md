# native/

NDK / JNI layer for Point & Shoot:

- `libavif` (10-bit AVIF encode for `Standard Pro`)
- `libjxl` (12-bit JPEG XL encode for `Ultra-Max`)
- focus peaking / edge overlays (preview-only; tracked here for completeness;
  the actual implementation will live in a Compose-side fragment shader, not
  in C++)

## Phase 0 / Phase 1 prep (current) — JNI stubs in the APK

Shipped:

- `pns_native.cpp` — JNI stubs matching `dev.pointandshoot.NativeEncoders`.
  Encoder entry points return `nullptr`; Kotlin maps that to
  `NativeError` / retries when real libavif/libjxl bodies land.
- `CMakeLists.txt` — builds `libpns_native.so` from `pns_native.cpp`;
  commented `FetchContent_Declare` blocks await libavif/libjxl pins.
- `THIRD_PARTY.md` — license matrix for planned upstream dependencies.

**Gradle:** `app/build.gradle.kts` wires **`externalNativeBuild`** (pinned
**`ndkVersion`**, CMake **3.22.1**, ABI filters **arm64-v8a** + **x86_64**).
Debug/release APKs include **`lib/*/libpns_native.so`**; on device,
`NativeEncoders.isAvailable` is **`true`** when `loadLibrary` succeeds.

JUnit (`:app:testDebugUnitTest`) still has **no** `.so` on the classpath —
`NativeEncodersFallbackTest` exercises the unload / fallback contract.

**Next:** Uncomment FetchContent + link libavif/libjxl per `NDK_PLAN.md`.

## Layout

| Path | Purpose |
|------|---------|
| `pns_native.cpp` | JNI stubs (`Java_dev_pointandshoot_NativeEncoders_*` symbols). |
| `CMakeLists.txt` | Single-target build script for `libpns_native.so`. |
| `THIRD_PARTY.md` | License matrix for libavif / libjxl / aom / highway / brotli. |

## How to verify the .so behavior on the JVM (no NDK needed)

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests dev.pointandshoot.NativeEncodersFallbackTest
.\gradlew.bat :app:testDebugUnitTest --tests dev.pointandshoot.EncoderRouteTest
```

Both suites pass green on every commit; they prove the capture engine will
gracefully degrade to JPEG when the .so is missing per
`FAILURE_MATRIX.md`.

## How to verify the screen on a device

```powershell
adb shell am start -n dev.pointandshoot/.MainActivity --es pns_screen native
```

The "Native diagnostics" screen reports the runtime status of the .so and
the per-profile encoder route. Normal debug APKs show **LOADED** (stub
version **0**); JVM tests still exercise the absent-.so path.

## How to install the NDK + CMake (Phase 1 prep)

```powershell
.\scripts\pns_install_ndk.ps1
```

The script is non-interactive: it locates the Android SDK, finds
`sdkmanager.bat`, accepts the SDK licenses, and installs `ndk;26.x` plus
`cmake;3.22.1`. Pre-requisite: Android SDK Command-line Tools must be
installed first (the script prints exact instructions if they are missing).
After the install, set `$env:ANDROID_NDK_HOME` and uncomment the
`externalNativeBuild` block in `app/build.gradle.kts` per `NDK_PLAN.md`.

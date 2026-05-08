# native/

NDK / JNI layer for Point & Shoot:

- `libavif` (10-bit AVIF encode for `Standard Pro`)
- `libjxl` (12-bit JPEG XL encode for `Ultra-Max`)
- focus peaking / edge overlays (preview-only; tracked here for completeness;
  the actual implementation will live in a Compose-side fragment shader, not
  in C++)

## Phase 0 (current) - JNI stubs only

Phase 0 ships:

- `pns_native.cpp` - JNI stubs matching the
  `dev.pointandshoot.NativeEncoders` Kotlin facade. Every encoder entry
  point returns `nullptr` (and the Kotlin facade reports
  `NativeEncoders.Result.NotAvailable`). Phase 1 replaces these bodies with
  real `libavif` / `libjxl` calls.
- `CMakeLists.txt` - builds `libpns_native.so` from `pns_native.cpp` and
  carries the commented-out `FetchContent_Declare` blocks for the upstream
  encoder sources; turning Phase 1 on is a comment-removal + a SHA-256 pin
  + a `-DPNS_USE_LIBAVIF=ON` CMake arg.
- `THIRD_PARTY.md` - license matrix for the planned upstream dependencies;
  pre-populated so license review can happen before any source is fetched.

`app/build.gradle.kts` deliberately does NOT wire `externalNativeBuild` in
Phase 0. The Kotlin facade lives entirely on the JVM classpath, so the
debug + release builds produce the same APK whether or not the NDK is
installed locally. JUnit unit tests (`:app:testDebugUnitTest`) exercise
the no-native fallback path because the JVM never has a
`libpns_native.so`.

When Phase 1 lands the `externalNativeBuild` block flips on and AGP starts
invoking `cmake` against this directory, so the .so ships in the APK and
`NativeEncoders.isAvailable` reports `true` at runtime.

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
the per-profile encoder route. Phase 0 builds always show "NOT LOADED" +
"DOWNGRADED to JPEG"; Phase 1 builds will show "LOADED" with version 1.

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

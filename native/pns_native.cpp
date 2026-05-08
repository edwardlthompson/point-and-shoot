// Phase 0 JNI stubs for `dev.pointandshoot.NativeEncoders`. The Kotlin facade
// at `app/src/main/java/dev/pointandshoot/NativeEncoders.kt` wraps each call
// with a defensive try/catch + `isAvailable` short-circuit, so when this .so
// isn't built (the developer host case) the app simply returns
// `Result.NotAvailable` and the capture engine falls back to JPEG per
// `FAILURE_MATRIX.md`.
//
// The stubs below match the JNI symbols produced by `@JvmStatic external fun`
// inside the `NativeEncoders` Kotlin object. See `NDK_PLAN.md` for the full
// roadmap (libavif / libjxl FetchContent + Gradle externalNativeBuild wiring
// behind the `pns.nativeEncoders` build switch).

#include <jni.h>

namespace {

// PNS_NATIVE_VERSION is bumped when the native ABI changes in a way that
// requires the Kotlin facade to gate behavior. Kept at 0 in Phase 0 because
// no real encoders ship yet.
constexpr jint PNS_NATIVE_VERSION = 0;

}  // namespace

extern "C" {

// Legacy symbol kept so `Java_dev_pointandshoot_Native_version` callers (none
// in tree today) still link if the .so happens to ship to a stale build. Safe
// to delete once the codebase moves to `NativeEncoders` exclusively.
JNIEXPORT jint JNICALL
Java_dev_pointandshoot_Native_version(JNIEnv* /*env*/, jclass /*clazz*/) {
    return PNS_NATIVE_VERSION;
}

JNIEXPORT jint JNICALL
Java_dev_pointandshoot_NativeEncoders_nativeVersion(JNIEnv* /*env*/, jclass /*clazz*/) {
    return PNS_NATIVE_VERSION;
}

// Phase 0 stub. Returns `nullptr` so the Kotlin facade reports
// `Result.NativeError(code = -1, message = "native returned null")`.
// Phase 1 will replace this with a `libavif`-backed implementation.
JNIEXPORT jbyteArray JNICALL
Java_dev_pointandshoot_NativeEncoders_nativeEncodeAvif10Hdr(
        JNIEnv* /*env*/,
        jclass /*clazz*/,
        jbyteArray /*planeY*/,
        jbyteArray /*planeU*/,
        jbyteArray /*planeV*/,
        jint /*width*/,
        jint /*height*/,
        jint /*strideY*/,
        jint /*strideUV*/) {
    return nullptr;
}

// Phase 0 stub. Returns `nullptr` for the same reason as the AVIF stub.
// Phase 1 will replace this with a `libjxl`-backed implementation.
JNIEXPORT jbyteArray JNICALL
Java_dev_pointandshoot_NativeEncoders_nativeEncodeJxl12Rec2020(
        JNIEnv* /*env*/,
        jclass /*clazz*/,
        jbyteArray /*planeRgb*/,
        jint /*width*/,
        jint /*height*/,
        jint /*stride*/) {
    return nullptr;
}

}  // extern "C"

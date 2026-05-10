package dev.pointandshoot

/**
 * NativeEncoders - Kotlin facade over the planned `libpns_native.so` JNI surface
 * (see `NDK_PLAN.md` and `BUILD_PLAN.md` §4 / §7).
 *
 * Phase 0 contract (this file): load the shared library defensively and surface
 * a stable, fallback-aware Kotlin API to the rest of the app. The actual native
 * code (libavif / libjxl / focus peaking) lands later behind the
 * `pns.nativeEncoders` build-time switch. Until then the .so is absent on
 * developer hosts and the methods below return [Result.NotAvailable] (or for
 * version-style probes: a sentinel `0`).
 *
 * Design decisions worth pinning here so future contributors don't relitigate
 * them:
 *
 * 1.  **Static initializer never crashes.** `System.loadLibrary("pns_native")`
 *     is wrapped in `try { ... } catch (Throwable)`. If the .so is missing,
 *     malformed, or built for the wrong ABI, [isAvailable] flips to `false`
 *     and [lastLoadError] captures the message for diagnostics. A library
 *     that crashes its consumer at first access is unacceptable for a camera
 *     app where startup latency is measured in milliseconds.
 * 2.  **Every public method short-circuits on [isAvailable] = `false`.** A
 *     missing .so does NOT propagate as `UnsatisfiedLinkError` to callers.
 *     The capture engine queries [isAvailable] (or relies on
 *     [Result.NotAvailable]) to fall back to JPEG per `FAILURE_MATRIX.md`.
 * 3.  **JVM unit tests work.** This file is engine-agnostic and pure-data on
 *     the JVM classpath: `:app:testDebugUnitTest` exercises the fallback path
 *     because the JVM never has a `pns_native.so`. See
 *     `NativeEncodersFallbackTest`.
 * 4.  **JNI symbol names match `native/pns_native.cpp`.** Functions are marked
 *     `@JvmStatic` so the linker looks for `Java_dev_pointandshoot_NativeEncoders_<methodName>`
 *     with a `(JNIEnv*, jclass, ...)` signature. This keeps the C++ side
 *     consistent with the original `Java_dev_pointandshoot_Native_version`
 *     stub style.
 *
 * The Kotlin signatures here intentionally do NOT match the speculative
 * `OutputStream`-based shapes shown in `NDK_PLAN.md` "JNI surface (target)".
 * Streams across the JNI boundary are slow (one upcall per chunk) and easy
 * to misuse; the implemented surface uses `ByteArray` / `ByteBuffer` for
 * input and returns the encoded bytes as a `ByteArray` so the caller (the
 * encode-lane executor) controls the on-disk write. `NDK_PLAN.md` will be
 * updated to reflect the implemented shape when the encoder bodies land.
 */
object NativeEncoders {
    /**
     * Result of a native encode call. The capture engine maps
     * [NotAvailable] / [NativeError] onto its existing fallback ladder
     * (DNG always survives; tonal container downgrades to JPEG).
     */
    sealed class Result {
        /** The native encoder produced [bytes]. `bytes.size` is the on-disk size. */
        data class Success(val bytes: ByteArray) : Result() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Success) return false
                return bytes.contentEquals(other.bytes)
            }

            override fun hashCode(): Int = bytes.contentHashCode()
        }

        /** `pns_native.so` is not loaded (missing, wrong ABI, or load failure). */
        data object NotAvailable : Result()

        /** The native encoder loaded but failed at call time. [code] is opaque. */
        data class NativeError(val code: Int, val message: String? = null) : Result()
    }

    /** Sentinel returned when [version] is queried but [isAvailable] is `false`. */
    const val VERSION_UNAVAILABLE: Int = 0

    private const val LIBRARY_NAME: String = "pns_native"

    private val loadOutcome: LoadOutcome by lazy { tryLoad() }

    /**
     * `true` iff `libpns_native.so` was successfully loaded for the current
     * process. Always `false` on JVM unit tests (no .so on the classpath).
     * On device builds, `externalNativeBuild` packages the `.so` per ABI.
     */
    val isAvailable: Boolean
        get() = loadOutcome.loaded

    /**
     * Diagnostic message captured from the failed `loadLibrary` call (if any).
     * `null` when the library is loaded successfully OR when no load was
     * attempted yet. Surfaced by `NativeDiagnosticsScreen` and the
     * `DiagnosticsMode.dump` report.
     */
    val lastLoadError: String?
        get() = loadOutcome.error

    /**
     * Native library version, or [VERSION_UNAVAILABLE] when the .so is absent.
     * Returns `1` when libavif + libjxl are linked (Android NDK build); stub/host builds use `0`.
     */
    fun version(): Int {
        if (!isAvailable) return VERSION_UNAVAILABLE
        return try {
            nativeVersion()
        } catch (t: Throwable) {
            VERSION_UNAVAILABLE
        }
    }

    /**
     * Encode a 10-bit YUV plane to AVIF (Display P3, BT.2020 transfer for HDR).
     *
     * Phase 0: always returns [Result.NotAvailable] because the .so isn't built.
     * Phase 1: encodes via `libavif` and returns a [Result.Success] with the
     * AVIF byte stream.
     *
     * @param planeY luma plane, packed 10-bit-in-16-bit little endian.
     * @param planeU chroma-U plane, same packing.
     * @param planeV chroma-V plane, same packing.
     * @param width  pixel width.
     * @param height pixel height.
     * @param strideY bytes per row of [planeY]; must be `>= width * 2`.
     * @param strideUV bytes per row of [planeU] / [planeV]; assumes 4:2:0
     *                 chroma subsampling (caller supplies half-height planes).
     */
    fun encodeAvif10Hdr(
        planeY: ByteArray,
        planeU: ByteArray,
        planeV: ByteArray,
        width: Int,
        height: Int,
        strideY: Int,
        strideUV: Int,
    ): Result {
        if (!isAvailable) return Result.NotAvailable
        return try {
            val bytes = nativeEncodeAvif10Hdr(planeY, planeU, planeV, width, height, strideY, strideUV)
                ?: return Result.NativeError(code = -1, message = "native returned null")
            Result.Success(bytes)
        } catch (t: Throwable) {
            Result.NativeError(code = -1, message = t.message)
        }
    }

    /**
     * Encode a 12-bit interleaved RGB plane to JPEG XL (Rec. 2020 primaries).
     *
     * Phase 0: always returns [Result.NotAvailable]. Phase 1: encodes via
     * `libjxl` and returns a [Result.Success] with the JXL byte stream.
     *
     * @param planeRgb 12-bit-in-16-bit little-endian, RGB interleaved.
     * @param width    pixel width.
     * @param height   pixel height.
     * @param stride   bytes per row of [planeRgb]; must be `>= width * 6`.
     */
    fun encodeJxl12Rec2020(
        planeRgb: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
    ): Result {
        if (!isAvailable) return Result.NotAvailable
        return try {
            val bytes = nativeEncodeJxl12Rec2020(planeRgb, width, height, stride)
                ?: return Result.NativeError(code = -1, message = "native returned null")
            Result.Success(bytes)
        } catch (t: Throwable) {
            Result.NativeError(code = -1, message = t.message)
        }
    }

    private fun tryLoad(): LoadOutcome {
        return try {
            System.loadLibrary(LIBRARY_NAME)
            LoadOutcome(loaded = true, error = null)
        } catch (t: Throwable) {
            LoadOutcome(loaded = false, error = t.message ?: t::class.java.simpleName)
        }
    }

    private data class LoadOutcome(val loaded: Boolean, val error: String?)

    @JvmStatic
    private external fun nativeVersion(): Int

    @JvmStatic
    private external fun nativeEncodeAvif10Hdr(
        planeY: ByteArray,
        planeU: ByteArray,
        planeV: ByteArray,
        width: Int,
        height: Int,
        strideY: Int,
        strideUV: Int,
    ): ByteArray?

    @JvmStatic
    private external fun nativeEncodeJxl12Rec2020(
        planeRgb: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
    ): ByteArray?
}

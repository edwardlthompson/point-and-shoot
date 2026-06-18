package dev.pointandshoot

import dev.pointandshoot.StillDngBackend
import dev.pointandshoot.fleet.StillDngBackendPolicy

/**
 * Future AltReferenceApp-class native DNG encode ([StillDngBackend.ALTREFERENCEAPP_NATIVE]).
 *
 * AltReferenceApp ships encode in `libnative-camera-host.so` (Adobe DNG SDK). P&S will route here
 * when JNI bodies land in `libpns_native.so`; until then [encodeDngFromRaw] returns [Result.NotAvailable]
 * and [StillDngBackendPolicy] uses [StillDngBackend.ALTREFERENCEAPP_INSPIRED] + [DngCreator].
 */
object AltReferenceAppNativeStill {

    sealed class Result {
        data class Success(val bytes: ByteArray) : Result()
        data object NotAvailable : Result()
        data class NativeError(val code: Int, val message: String? = null) : Result()
    }

    fun isRequested(): Boolean = StillDngBackendPolicy.active() == StillDngBackend.ALTREFERENCEAPP_NATIVE

    fun nativeStubVersion(): Int =
        if (!NativeEncoders.isAvailable) {
            0
        } else {
            runCatching { nativeAltReferenceAppDngEncodeVersion() }.getOrDefault(0)
        }

  /**
   * Placeholder for native `RawEncoder::encode_DNG12` path.
   */
    fun encodeDngFromRaw(
        @Suppress("UNUSED_PARAMETER") rawBytes: ByteArray,
        @Suppress("UNUSED_PARAMETER") width: Int,
        @Suppress("UNUSED_PARAMETER") height: Int,
        @Suppress("UNUSED_PARAMETER") rawFormat: Int,
    ): Result = Result.NotAvailable

    @JvmStatic
    private external fun nativeAltReferenceAppDngEncodeVersion(): Int
}

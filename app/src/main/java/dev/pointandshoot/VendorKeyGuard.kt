package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * Centralized feature-detection + safe-fallback helpers for **vendor Camera2
 * keys** per BUILD_PLAN §9 ("Vendor tag safety gate"):
 *
 *   * Every vendor tag use is feature-detected and guarded.
 *   * Fallback behavior verified when the tag is unavailable / ignored.
 *
 * The probe (`CameraCapabilitiesProbe.kt`) discovers vendor tag *names* on
 * each camera. Any production code that wants to *use* one of those tags
 * MUST go through this helper so the gate is enforced exactly once and the
 * absence path is observable in logcat.
 *
 * Example:
 * ```
 * VendorKeyGuard.useIfAvailable(
 *     characteristics = cc,
 *     vendorKeyName = "com.oplus.lbmf.EnableMFHDR",
 *     ifMissing = { Log.d(TAG, "MFHDR not advertised; standard pipeline") },
 * ) { keyName ->
 *     // build a request that sets `keyName` on a vendor CaptureRequest.Key<Byte>
 *     // (the helper proves the key is enumerable; the actual setter still has
 *     //  to use Camera2's vendor-key APIs).
 * }
 * ```
 */
object VendorKeyGuard {

    private const val TAG = "PNS.VendorKey"

    /**
     * Returns true if [characteristics] advertises a *characteristic* key whose
     * name matches [vendorKeyName] (case-sensitive).
     */
    fun isCharacteristicKeyAvailable(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): Boolean {
        return characteristics.keys.any { it.name == vendorKeyName }
    }

    /**
     * Returns true if [characteristics] advertises a *capture-request* key
     * (i.e., something settable on a `CaptureRequest.Builder`) whose name
     * matches [vendorKeyName].
     */
    fun isRequestKeyAvailable(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): Boolean {
        val keys = runCatching { characteristics.availableCaptureRequestKeys }.getOrNull()
            ?: return false
        return keys.any { it.name == vendorKeyName }
    }

    /**
     * Returns true if [characteristics] advertises a *session* key (settable on
     * a `SessionConfiguration`) whose name matches [vendorKeyName].
     */
    fun isSessionKeyAvailable(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): Boolean {
        val keys = runCatching { characteristics.availableSessionKeys }.getOrNull()
            ?: return false
        return keys.any { it.name == vendorKeyName }
    }

    /**
     * Convenience guard for an opt-in vendor key. If the tag is advertised,
     * [block] is invoked with the key name (so the caller can apply it via
     * Camera2's vendor-key APIs). If not, [ifMissing] runs and the call
     * returns false. Either way the gate is logged at DEBUG so missing
     * vendor paths are visible in logcat.
     */
    inline fun useIfAvailable(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
        scope: KeyScope = KeyScope.Request,
        ifMissing: () -> Unit = {},
        block: (String) -> Unit,
    ): Boolean {
        val present = when (scope) {
            KeyScope.Characteristic -> isCharacteristicKeyAvailable(characteristics, vendorKeyName)
            KeyScope.Request -> isRequestKeyAvailable(characteristics, vendorKeyName)
            KeyScope.Session -> isSessionKeyAvailable(characteristics, vendorKeyName)
        }
        if (present) {
            log("present", vendorKeyName, scope)
            block(vendorKeyName)
        } else {
            log("absent", vendorKeyName, scope)
            ifMissing()
        }
        return present
    }

    /**
     * Look up a typed `CaptureRequest.Key<*>` by name. Camera2 exposes vendor
     * keys via name on API 30+, but only after the camera characteristics have
     * been queried at least once. Returns null if the key is not present.
     */
    fun captureRequestKey(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): CaptureRequest.Key<*>? {
        val keys = runCatching { characteristics.availableCaptureRequestKeys }.getOrNull().orEmpty()
        return keys.firstOrNull { it.name == vendorKeyName }
    }

    enum class KeyScope { Characteristic, Request, Session }

    @PublishedApi
    internal fun log(state: String, name: String, scope: KeyScope) {
        Log.d(TAG, "guard $state scope=$scope name=$name")
    }
}

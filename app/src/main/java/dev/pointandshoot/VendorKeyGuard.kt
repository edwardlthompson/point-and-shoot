package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Rational

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

    /**
     * Look up a vendor session key by name (from [CameraCharacteristics.availableSessionKeys]).
     */
    fun captureSessionKey(
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): CaptureRequest.Key<*>? {
        val keys = runCatching { characteristics.availableSessionKeys }.getOrNull().orEmpty()
        return keys.firstOrNull { it.name == vendorKeyName }
    }

    /**
     * Best-effort set for vendor boolean-ish enable keys whose Java type is not known at compile time.
     * Returns a short tag (`byte`, `int`, …) when [builder.set] succeeds, or null when the key is missing
     * or no overload matched.
     */
    fun trySetVendorRequestEnable(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): String? {
        val key = captureRequestKey(characteristics, vendorKeyName) ?: return null
        return trySetVendorEnableOnBuilder(builder, key, vendorKeyName)
    }

    /**
     * Same as [trySetVendorRequestEnable] but resolves [vendorKeyName] from [availableSessionKeys]
     * for use with [android.hardware.camera2.params.SessionConfiguration.setSessionParameters].
     */
    fun trySetVendorSessionEnable(
        builder: CaptureRequest.Builder,
        characteristics: CameraCharacteristics,
        vendorKeyName: String,
    ): String? {
        val key = captureSessionKey(characteristics, vendorKeyName) ?: return null
        return trySetVendorEnableOnBuilder(builder, key, vendorKeyName)
    }

    private fun trySetVendorEnableOnBuilder(
        builder: CaptureRequest.Builder,
        key: CaptureRequest.Key<*>,
        vendorKeyName: String,
    ): String? {
        val valueClass = vendorKeyValueClass(key)
        if (valueClass == null) {
            Log.d(
                TAG,
                "vendorKey getType missing for $vendorKeyName; trying legacy set(Key,*) probes",
            )
            return trySetVendorEnableByLegacyObjectProbe(builder, key)
        }
        Log.d(TAG, "vendorKey $vendorKeyName valueType=${valueClass.name}")
        val tag =
            runCatching {
                @Suppress("UNCHECKED_CAST")
                when {
                valueClass == java.lang.Byte::class.java || valueClass == java.lang.Byte.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Byte>, 1.toByte())
                    "byte"
                }
                valueClass == java.lang.Short::class.java || valueClass == java.lang.Short.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Short>, 1.toShort())
                    "short"
                }
                valueClass == java.lang.Integer::class.java || valueClass == java.lang.Integer.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Int>, 1)
                    "int"
                }
                valueClass == java.lang.Long::class.java || valueClass == java.lang.Long.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Long>, 1L)
                    "long"
                }
                valueClass == java.lang.Boolean::class.java || valueClass == java.lang.Boolean.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Boolean>, true)
                    "boolean"
                }
                valueClass == java.lang.Float::class.java || valueClass == java.lang.Float.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Float>, 1f)
                    "float"
                }
                valueClass == java.lang.Double::class.java || valueClass == java.lang.Double.TYPE -> {
                    builder.set(key as CaptureRequest.Key<Double>, 1.0)
                    "double"
                }
                valueClass == Rational::class.java -> {
                    builder.set(key as CaptureRequest.Key<Rational>, Rational(1, 1))
                    "Rational"
                }
                valueClass == java.lang.String::class.java -> {
                    builder.set(key as CaptureRequest.Key<String>, "1")
                    "String"
                }
                else -> {
                    Log.d(TAG, "vendorTrySet unsupported valueClass=${valueClass.name} key=$vendorKeyName")
                    null
                }
            }
            }.fold(
                onSuccess = { it },
                onFailure = { e ->
                    Log.d(
                        TAG,
                        "vendorTrySet threw valueClass=${valueClass.name} ${e.javaClass.simpleName}: ${e.message}",
                    )
                    null
                },
            )
        return tag
    }

    /**
     * `CaptureRequest.Key` on some OEM builds does not declare [CameraMetadata.Key.getType];
     * walk concrete classes up to [Any] and invoke the first `getType()` found.
     */
    private fun vendorKeyValueClass(key: CaptureRequest.Key<*>): Class<*>? {
        var clazz: Class<*>? = key.javaClass
        while (clazz != null) {
            val m =
                runCatching {
                    clazz.getDeclaredMethod("getType").apply { isAccessible = true }
                }.getOrNull()
            if (m != null) {
                val type = runCatching { m.invoke(key) as Class<*> }.getOrNull()
                if (type != null) return type
            }
            clazz = clazz.superclass
        }
        return null
    }

    /** OEM/vendor keys sometimes omit [CameraMetadata.Key.getType]; brute-force every `set(Key,*)` overload. */
    private fun trySetVendorEnableByLegacyObjectProbe(
        builder: CaptureRequest.Builder,
        key: CaptureRequest.Key<*>,
    ): String? {
        val candidates =
            CaptureRequest.Builder::class.java.methods.filter { m ->
                m.name == "set" &&
                    m.parameterCount == 2 &&
                    m.parameterTypes[0] == CaptureRequest.Key::class.java
            }
        if (candidates.isEmpty()) return null
        // OEM/vendor enable tags omit typed metadata.getType(); HAL value domains vary (e.g. enum int > 1).
        val attempts =
            listOf(
                java.lang.Byte.decode("1") to "byte",
                java.lang.Byte.decode("0") to "byte0",
                java.lang.Byte.decode("2") to "byte2",
                java.lang.Short.decode("1") to "short",
                java.lang.Short.decode("2") to "short2",
                java.lang.Integer.decode("1") to "Integer",
                java.lang.Integer.valueOf(0) to "int0",
                java.lang.Integer.valueOf(2) to "int2",
                java.lang.Integer.valueOf(3) to "int3",
                java.lang.Integer.valueOf(-1) to "int-1",
                java.lang.Boolean.TRUE to "Boolean",
                java.lang.Boolean.FALSE to "BooleanFalse",
                java.lang.Long.decode("1") to "long",
                java.lang.Long.decode("0") to "long0",
                java.lang.Float.valueOf(1f) to "float",
                java.lang.Float.valueOf(0f) to "float0",
                java.lang.Double.valueOf(1.0) to "double",
                Rational(1, 1) to "Rational",
                Rational(0, 1) to "Rational0",
                "1" to "String",
                "true" to "StringTrue",
                intArrayOf(1) to "intArr1",
                intArrayOf(1, 0) to "intArr2",
                byteArrayOf(1) to "byteArr1",
            )
        for (setMethod in candidates) {
            for ((value, tag) in attempts) {
                val ok =
                    runCatching {
                        setMethod.invoke(builder, key, value)
                        true
                    }.getOrDefault(false)
                if (ok) return tag
            }
        }
        return null
    }

    enum class KeyScope { Characteristic, Request, Session }

    @PublishedApi
    internal fun log(state: String, name: String, scope: KeyScope) {
        Log.d(TAG, "guard $state scope=$scope name=$name")
    }
}

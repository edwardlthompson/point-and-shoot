package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.util.Log

/**
 * Freezes autofocus on single still [CaptureRequest]s so the shutter does not implicitly restart an
 * AF sweep when the HAL transitions from preview repeating to still capture.
 *
 * **Reference (read-only homework):** upstream *External Camera App* on SourceForge (`externalcameraapp` project)
 * implements a Camera2 **state machine** around still capture (precapture / AF+AE wait / capture).
 * Relevant search terms in their tree: `CameraController2`, `ready_for_capture`,
 * `CONTROL_AF_LOCK`, `CONTROL_AF_TRIGGER`, `CONTROL_AE_PRECAPTURE_TRIGGER`. This helper applies
 * only the **minimal** subset we need today: **AF trigger idle** + **AF lock** when the device
 * reports lock support and preview AF already looked stable — not a full precapture port.
 *
 * **Key resolution:** `CONTROL_AF_LOCK` / `CONTROL_AF_LOCK_AVAILABLE` are resolved from
 * [CameraCharacteristics.getAvailableCaptureRequestKeys] / [CameraCharacteristics.getKeys] by stable
 * metadata **names** (e.g. `android.control.afLock`), with reflection on `CaptureRequest` /
 * `CameraCharacteristics` as a fallback when OEMs omit keys from those lists.
 *
 * **Flash:** when [PreviewFlashPolicy.stillFlashSkipsAfFreeze] is true, skip **both** idle and lock
 * so we do not fight OEM flash-metering / precapture sequences that expect AF to run.
 */
object StillCaptureAfFreeze {
    private const val TAG = "PNS.StillAfFreeze"

    private val lastBoundaryDiag = java.util.concurrent.atomic.AtomicReference<String?>(null)

    /** Consumed once by [StillCaptureBoundaryDiag.logBoundary] for fleet triage when `req.keys` omits AF lock. */
    internal fun consumeLastBoundaryDiag(): String? = lastBoundaryDiag.getAndSet(null)

    private fun recordBoundaryDiag(line: String) {
        lastBoundaryDiag.set(line)
    }

    /** [android.hardware.camera2.CaptureRequest.CONTROL_AF_LOCK] metadata name. */
    internal const val ANDROID_CONTROL_AF_LOCK = "android.control.afLock"

    /** [android.hardware.camera2.CameraCharacteristics.CONTROL_AF_LOCK_AVAILABLE] metadata name. */
    internal const val ANDROID_CONTROL_AF_LOCK_AVAILABLE = "android.control.afLockAvailable"

    @Suppress("UNCHECKED_CAST")
    internal fun findRequestAfLockKey(keys: Collection<CaptureRequest.Key<*>>): CaptureRequest.Key<Boolean>? {
        keys.firstOrNull { it.name == ANDROID_CONTROL_AF_LOCK }?.let {
            return it as? CaptureRequest.Key<Boolean>
        }
        return keys.firstOrNull { key ->
            val n = key.name
            n.equals(ANDROID_CONTROL_AF_LOCK, ignoreCase = true) ||
                (n.contains("afLock", ignoreCase = true) && n.contains("control", ignoreCase = true))
        } as? CaptureRequest.Key<Boolean>
    }

    @Suppress("UNCHECKED_CAST")
    internal fun findCharacteristicsAfLockAvailableKey(
        chars: CameraCharacteristics,
    ): CameraCharacteristics.Key<Boolean>? {
        for (k in chars.keys) {
            if (k.name == ANDROID_CONTROL_AF_LOCK_AVAILABLE) {
                return k as? CameraCharacteristics.Key<Boolean>
            }
        }
        for (k in chars.keys) {
            val n = k.name
            if (n.equals(ANDROID_CONTROL_AF_LOCK_AVAILABLE, ignoreCase = true) ||
                (n.contains("afLock", ignoreCase = true) && n.contains("Available", ignoreCase = true))
            ) {
                return k as? CameraCharacteristics.Key<Boolean>
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private val captureRequestAfLockKeyReflect: CaptureRequest.Key<Boolean>? by lazy {
        runCatching {
            val f = CaptureRequest::class.java.getDeclaredField("CONTROL_AF_LOCK")
            f.isAccessible = true
            f.get(null) as CaptureRequest.Key<Boolean>
        }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private val characteristicsAfLockAvailableKeyReflect: CameraCharacteristics.Key<Boolean>? by lazy {
        runCatching {
            val f = CameraCharacteristics::class.java.getDeclaredField("CONTROL_AF_LOCK_AVAILABLE")
            f.isAccessible = true
            f.get(null) as CameraCharacteristics.Key<Boolean>
        }.getOrNull()
    }

    private fun resolveRequestAfLockKey(keys: Collection<CaptureRequest.Key<*>>): CaptureRequest.Key<Boolean>? =
        findRequestAfLockKey(keys) ?: captureRequestAfLockKeyReflect

    private fun resolveAfLockAvailableKey(chars: CameraCharacteristics): CameraCharacteristics.Key<Boolean>? =
        findCharacteristicsAfLockAvailableKey(chars) ?: characteristicsAfLockAvailableKeyReflect

    private fun afLockAvailable(chars: CameraCharacteristics): Boolean {
        val k = resolveAfLockAvailableKey(chars) ?: return false
        return runCatching { chars.get(k) == true }.getOrDefault(false)
    }

    private fun requestKeysContainAfLock(keys: Collection<CaptureRequest.Key<*>>): Boolean {
        val k = resolveRequestAfLockKey(keys) ?: return false
        return keys.contains(k)
    }

    /**
     * @param previewAfState last repeating [CaptureResult.CONTROL_AF_STATE], or null if unknown.
     * @return true when any key was written (for optional debug).
     */
    fun applyToStillRequestIfAllowed(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        previewAfState: Int?,
        skipEntireAfFreeze: Boolean,
    ): Boolean {
        if (skipEntireAfFreeze) {
            Log.i(TAG, "skipAfFreeze flashOrPrecapturePath=true")
            recordBoundaryDiag("skipFlashOrPrecapture")
            return false
        }
        val keys =
            chars.availableCaptureRequestKeys
                ?: run {
                    recordBoundaryDiag("noAvailRequestKeys")
                    return false
                }
        var wrote = false
        if (keys.contains(CaptureRequest.CONTROL_AF_TRIGGER)) {
            runCatching {
                req.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                wrote = true
            }.onFailure { Log.w(TAG, "CONTROL_AF_TRIGGER IDLE: ${it.message}") }
        }
        val lockAvail = afLockAvailable(chars)
        val lockKey = resolveRequestAfLockKey(keys)
        if (!lockAvail) {
            Log.i(
                TAG,
                "afLockSkipped reason=notAdvertised previewAfState=$previewAfState idleApplied=$wrote",
            )
            recordBoundaryDiag("lockAvail=false")
            return wrote
        }
        if (lockKey == null || !requestKeysContainAfLock(keys)) {
            Log.i(
                TAG,
                "afLockSkipped reason=noRequestKey previewAfState=$previewAfState idleApplied=$wrote",
            )
            recordBoundaryDiag("noRequestKey")
            return wrote
        }
        val stable =
            previewAfState == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                previewAfState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
        if (!stable) {
            Log.i(TAG, "afLockSkipped reason=previewAfNotStable previewAfState=$previewAfState idleApplied=$wrote")
            recordBoundaryDiag("previewAfNotStable=$previewAfState")
            return wrote
        }
        runCatching {
            req.set(lockKey, true)
            wrote = true
            Log.i(TAG, "afLockApplied previewAfState=$previewAfState")
            recordBoundaryDiag("lockSet=true")
        }.onFailure {
            Log.w(TAG, "CONTROL_AF_LOCK set failed: ${it.message}")
            recordBoundaryDiag("lockSetFailed=${it.message?.take(32)}")
        }
        if (lastBoundaryDiag.get() == null) {
            recordBoundaryDiag(if (wrote) "idleOnly" else "noop")
        }
        return wrote
    }

    /**
     * For [StillCaptureBoundaryDiag] / fleet triage: read `CONTROL_AF_LOCK` from the built still
     * [CaptureRequest] using the same name-based resolution as [applyToStillRequestIfAllowed].
     */
    fun readRequestAfLockForDiag(req: CaptureRequest): String {
        val keys = req.keys
        val k =
            findRequestAfLockKey(keys)
                ?: captureRequestAfLockKeyReflect?.takeIf { keys.contains(it) }
                ?: captureRequestAfLockKeyReflect
                ?: keys.firstOrNull { it.name.contains("afLock", ignoreCase = true) }
                    as? CaptureRequest.Key<Boolean>
        if (k == null) return "?"
        return runCatching { req.get(k).let { v -> v?.toString() ?: "null" } }.getOrElse { "err" }
    }
}

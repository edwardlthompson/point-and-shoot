package dev.pointandshoot.fleet

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.RggbChannelVector
import android.media.Image
import android.os.Build
import android.util.Log
import android.util.Rational
import dev.pointandshoot.DngBayerAsShotNeutral
import dev.pointandshoot.DngForwardMatrixFix
import dev.pointandshoot.DngSaveBisectState
import dev.pointandshoot.TiffDngColorMatrixPatch

/**
 * CPH2655 leaf DNG post-process after [android.hardware.camera2.DngCreator].
 *
 * **ProShot / MotionCam gap (May 2026):** Play Store apps do not run Bayer/gain AsShotNeutral surgery.
 * MotionCam encodes in native code with per-model profiles; ProShot relies on
 * `DngCreator(openedCameraCharacteristics, stillResult)` plus still IQ (lens shading, etc.).
 *
 * Shipped OP13 path: force IFD0 + raw-IFD **ColorMatrix / ForwardMatrix** from the **opened leaf**
 * [CameraCharacteristics], optional per-id FM override, and **AsShotNeutral** from
 * [CaptureResult.SENSOR_NEUTRAL_COLOR_POINT] on the still result — not inverted gains or Bayer means.
 */
object LeafDngHalReconcile {
    private const val TAG = "PNS.LeafDng"

    fun shouldReconcileLeafDngMetadata(sessionCameraId: String): Boolean =
        shouldReconcileLeafDngMetadataWhen(
            deviceApplies = OnePlus13FleetPolicy.appliesToDevice(),
            backend = StillDngBackendPolicy.active(),
            sessionCameraId = sessionCameraId,
        )

    fun usesHalColorCalibration(sessionCameraId: String?): Boolean =
        useHalColorCalibrationPath(sessionCameraId)

    fun usesAsnOnlyReconcile(sessionCameraId: String?): Boolean =
        useAsnOnlyPath(sessionCameraId)

    /**
     * Legacy ASN-only reconcile (bisect). Default OP13 aux uses [applyHalColorCalibrationReconcile].
     */
    internal fun shouldReconcileLeafDngMetadataWhen(
        deviceApplies: Boolean,
        backend: StillDngBackend,
        sessionCameraId: String,
        proShotPureDngSave: Boolean = OnePlus13FleetPolicy.useProShotPureDngSave(),
        wideLeafCalibrationForAuxDng: Boolean = OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng(),
    ): Boolean {
        DngSaveBisectState.forceLeafHalReconcile?.let { forced ->
            return forced &&
                deviceApplies &&
                sessionCameraId in leafRearIds
        }
        if (
            deviceApplies &&
            wideLeafCalibrationForAuxDng &&
            (sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
                sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        if (deviceApplies && proShotPureDngSave) {
            return false
        }
        if (sessionCameraId == OnePlus13FleetPolicy.CANONICAL_WIDE) {
            return false
        }
        if (
            OnePlus13FleetPolicy.useOp13AsnReconcileOnly() &&
            (sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
                sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        return deviceApplies &&
            backend == StillDngBackend.FRAMEWORK_PROSHOT &&
            sessionCameraId in leafRearIds
    }

    private val leafRearIds: Set<String> =
        setOf(
            OnePlus13FleetPolicy.CANONICAL_UW,
            OnePlus13FleetPolicy.CANONICAL_WIDE,
            OnePlus13FleetPolicy.CANONICAL_TELE,
        )

    fun usesWideLeafCalibrationReconcile(sessionCameraId: String?): Boolean =
        useWideLeafCalibrationPath(sessionCameraId)

    fun applyPostDngCreatorPatches(
        original: ByteArray,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        rawImage: Image,
        sessionCameraId: String?,
        preWriteBayerAsn: FloatArray? = null,
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
    ): ByteArray {
        if (useWideLeafCalibrationPath(sessionCameraId, wideCalibrationCharacteristics)) {
            return applyWideLeafCalibrationReconcile(
                original,
                wideCalibrationCharacteristics!!,
                captureResult,
                sessionCameraId,
            )
        }
        if (useHalColorCalibrationPath(sessionCameraId)) {
            return applyHalColorCalibrationReconcile(
                original,
                characteristics,
                captureResult,
                sessionCameraId,
            )
        }
        if (useAsnOnlyPath(sessionCameraId)) {
            return applyOp13AsnOnlyReconcile(original, captureResult, sessionCameraId)
        }
        return applyLegacyAsnReconcile(
            original,
            characteristics,
            captureResult,
            rawImage,
            sessionCameraId,
            preWriteBayerAsn,
        )
    }

    private fun useAsnOnlyPath(sessionCameraId: String?): Boolean {
        if (OnePlus13FleetPolicy.useProShotPureDngSave()) return false
        if (DngSaveBisectState.forceLegacyAsnReconcile) return false
        if (sessionCameraId == null) return false
        if (!OnePlus13FleetPolicy.useOp13AsnReconcileOnly()) return false
        return sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
            sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE
    }

    private fun useWideLeafCalibrationPath(
        sessionCameraId: String?,
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
    ): Boolean {
        if (wideCalibrationCharacteristics == null || sessionCameraId == null) return false
        if (!OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng()) return false
        return sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
            sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE
    }

    /**
     * Aux sensors keep their RAW pixels; **color profile** (CM/FM) comes from wide cam **2** HAL tables.
     * AsShotNeutral stays on the aux still AWB ([COLOR_CORRECTION_GAINS] / Op13 correction).
     */
    private fun applyWideLeafCalibrationReconcile(
        original: ByteArray,
        wideCharacteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        sessionCameraId: String?,
    ): ByteArray {
        val cm2Before = TiffDngColorMatrixPatch.readMatrixElement00(original, TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2)
        var bytes = TiffDngColorMatrixPatch.patchCalibrationTagsIfd0(original, wideCharacteristics)
        bytes = TiffDngColorMatrixPatch.patch(bytes, wideCharacteristics)
        val correctedStillGains =
            sessionCameraId?.let { Op13LeafStillColorCorrection.takePendingCorrectedGains(it) }
        when {
            correctedStillGains != null -> {
                bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, correctedStillGains)
                Log.i(
                    TAG,
                    "wide-cal AsShotNeutral from Op13 still-request gains auxCam=$sessionCameraId " +
                        "profileCam=${OnePlus13FleetPolicy.CANONICAL_WIDE}",
                )
            }
            else -> {
                val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
                if (gains != null) {
                    val forPatch =
                        if (DngSaveBisectState.skipHalWbGainCorrection) {
                            gains
                        } else {
                            applyHalWbGainCorrection(gains, sessionCameraId)
                        }
                    bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, forPatch)
                    Log.i(
                        TAG,
                        "wide-cal AsShotNeutral from COLOR_CORRECTION_GAINS auxCam=$sessionCameraId",
                    )
                } else {
                    Log.w(TAG, "wide-cal AsShotNeutral missing auxCam=$sessionCameraId")
                }
            }
        }
        val cm2After = TiffDngColorMatrixPatch.readMatrixElement00(bytes, TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2)
        val cm2Wide = matrixElement00(wideCharacteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2))
        Log.i(
            TAG,
            "wide-cal reconcile auxCam=$sessionCameraId cm2_before=$cm2Before cm2_after=$cm2After " +
                "cm2_wide_chars=$cm2Wide",
        )
        return bytes
    }

    private fun useHalColorCalibrationPath(sessionCameraId: String?): Boolean {
        if (OnePlus13FleetPolicy.useWideLeafCalibrationForAuxDng()) return false
        if (OnePlus13FleetPolicy.useProShotPureDngSave()) return false
        if (DngSaveBisectState.forceLegacyAsnReconcile) return false
        if (sessionCameraId == null) return false
        if (!OnePlus13FleetPolicy.useHalColorCalibrationReconcile()) return false
        return sessionCameraId == OnePlus13FleetPolicy.CANONICAL_UW ||
            sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE
    }

    private fun applyOp13AsnOnlyReconcile(
        original: ByteArray,
        captureResult: TotalCaptureResult,
        sessionCameraId: String?,
    ): ByteArray {
        val correctedStillGains =
            sessionCameraId?.let { Op13LeafStillColorCorrection.takePendingCorrectedGains(it) }
        if (correctedStillGains != null) {
            val bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(original, correctedStillGains)
            Log.i(
                TAG,
                "asn-only AsShotNeutral from Op13 corrected still-request gains cam=$sessionCameraId",
            )
            return bytes
        }
        val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
        if (gains != null) {
            val forPatch =
                if (DngSaveBisectState.skipHalWbGainCorrection) {
                    gains
                } else {
                    applyHalWbGainCorrection(gains, sessionCameraId)
                }
            Log.i(TAG, "asn-only AsShotNeutral from COLOR_CORRECTION_GAINS cam=$sessionCameraId")
            return TiffDngColorMatrixPatch.patchAsShotNeutral(original, forPatch)
        }
        Log.w(TAG, "asn-only skipped: no Op13 gains or COLOR_CORRECTION_GAINS cam=$sessionCameraId")
        return original
    }

    private fun applyHalColorCalibrationReconcile(
        original: ByteArray,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        sessionCameraId: String?,
    ): ByteArray {
        val cm2Before = TiffDngColorMatrixPatch.readMatrixElement00(original, TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2)
        var bytes = TiffDngColorMatrixPatch.patchCalibrationTagsIfd0(original, characteristics)
        bytes = TiffDngColorMatrixPatch.patch(bytes, characteristics)
        val model = Build.MODEL?.lowercase().orEmpty()
        DngForwardMatrixFix.get(model, sessionCameraId)?.let { fmOverride ->
            bytes = TiffDngColorMatrixPatch.patchForwardMatrix(bytes, fmOverride)
            Log.i(TAG, "hal cal ForwardMatrix override cam=$sessionCameraId")
        }
        val correctedStillGains =
            sessionCameraId?.let { Op13LeafStillColorCorrection.takePendingCorrectedGains(it) }
        when {
            correctedStillGains != null -> {
                bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, correctedStillGains)
                Log.i(
                    TAG,
                    "hal cal AsShotNeutral from Op13 corrected still-request gains cam=$sessionCameraId",
                )
            }
            else -> {
                val ncp = captureResult.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)
                if (ncp != null && ncp.size >= 3) {
                    bytes =
                        TiffDngColorMatrixPatch.patchAsShotNeutralFromFloats(
                            bytes,
                            asnFromNeutralRationals(ncp),
                        )
                    Log.i(TAG, "hal cal AsShotNeutral from SENSOR_NEUTRAL_COLOR_POINT cam=$sessionCameraId")
                } else {
                    Log.w(TAG, "hal cal AsShotNeutral missing (no Op13 gains or NCP) cam=$sessionCameraId")
                }
            }
        }
        val cm2After = TiffDngColorMatrixPatch.readMatrixElement00(bytes, TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2)
        val cm2Chars = matrixElement00(characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2))
        Log.i(
            TAG,
            "hal cal reconcile cam=$sessionCameraId cm2_file_before=$cm2Before cm2_file_after=$cm2After " +
                "cm2_chars=$cm2Chars",
        )
        return bytes
    }

    /** DNG AsShotNeutral from [CaptureResult.SENSOR_NEUTRAL_COLOR_POINT] (R,G,B rationals). */
    internal fun asnFromNeutralRationals(ncp: Array<Rational>): FloatArray =
        normalizeAsShotNeutralTriplet(
            rationalToFloat(ncp[0]),
            rationalToFloat(ncp[1]),
            rationalToFloat(ncp[2]),
        )

    /** Max-normalized DNG AsShotNeutral RGB (largest channel = 1). */
    internal fun normalizeAsShotNeutralTriplet(r: Float, g: Float, b: Float): FloatArray {
        val rN = r.coerceAtLeast(1e-6f)
        val gN = g.coerceAtLeast(1e-6f)
        val bN = b.coerceAtLeast(1e-6f)
        val max = maxOf(rN, gN, bN)
        return floatArrayOf(rN / max, gN / max, bN / max)
    }

    private fun rationalToFloat(r: Rational): Float =
        r.numerator.toFloat() / r.denominator.coerceAtLeast(1).toFloat()

    private fun matrixElement00(
        transform: android.hardware.camera2.params.ColorSpaceTransform?,
    ): String {
        if (transform == null) return "null"
        return runCatching {
            val e = transform.getElement(0, 0)
            "%.4f".format(e.numerator.toFloat() / e.denominator.coerceAtLeast(1))
        }.getOrElse { "?" }
    }

    // --- Legacy ASN path (bisect / forceLegacyAsnReconcile) ---

    private fun applyLegacyAsnReconcile(
        original: ByteArray,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        rawImage: Image,
        sessionCameraId: String?,
        preWriteBayerAsn: FloatArray?,
    ): ByteArray {
        var bytes = original
        val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
        if (preferBayerAsnForSession(sessionCameraId)) {
            return patchAsnFromBayer(bytes, characteristics, rawImage, captureResult, preWriteBayerAsn)
        }
        if (gains != null) {
            val forPatch =
                if (DngSaveBisectState.skipHalWbGainCorrection) {
                    gains
                } else {
                    applyHalWbGainCorrection(gains, sessionCameraId)
                }
            bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, forPatch)
            Log.i(
                TAG,
                "AsShotNeutral patched from COLOR_CORRECTION_GAINS" +
                    if (forPatch === gains) "" else " (HAL WB-corrected)" +
                    " cam=$sessionCameraId",
            )
            return bytes
        }
        return patchAsnFromBayer(bytes, characteristics, rawImage, captureResult, preWriteBayerAsn)
    }

    private fun preferBayerAsnForSession(sessionCameraId: String?): Boolean {
        if (DngSaveBisectState.forceBayerAsnOnLeafReconcile) return true
        if (!OnePlus13FleetPolicy.appliesToDevice()) return false
        return sessionCameraId == OnePlus13FleetPolicy.CANONICAL_TELE
    }

    fun applyHalWbGainCorrection(
        gains: RggbChannelVector,
        sessionCameraId: String?,
    ): RggbChannelVector =
        applyHalWbGainCorrectionWhen(
            gains,
            sessionCameraId,
            Build.MODEL?.lowercase().orEmpty(),
        )

    internal fun applyHalWbGainCorrectionWhen(
        gains: RggbChannelVector,
        sessionCameraId: String?,
        modelLower: String,
    ): RggbChannelVector {
        val wb = DngForwardMatrixFix.getWbCorrection(modelLower, sessionCameraId) ?: return gains
        return RggbChannelVector(
            gains.red * wb.scaleR,
            gains.greenEven,
            gains.greenOdd,
            gains.blue * wb.scaleB,
        )
    }

    private fun patchAsnFromBayer(
        bytes: ByteArray,
        characteristics: CameraCharacteristics,
        rawImage: Image,
        captureResult: TotalCaptureResult,
        preWriteBayerAsn: FloatArray?,
    ): ByteArray {
        var out = bytes
        val asnFromBayer =
            preWriteBayerAsn
                ?: DngBayerAsShotNeutral.estimateFromDngBytes(out, characteristics)
                ?: DngBayerAsShotNeutral.estimate(characteristics, rawImage, captureResult)
        if (asnFromBayer != null) {
            out = TiffDngColorMatrixPatch.patchAsShotNeutralFromFloats(out, asnFromBayer)
            Log.i(
                TAG,
                when {
                    preWriteBayerAsn != null -> "AsShotNeutral patched from Bayer (pre-write)"
                    else -> "AsShotNeutral patched from Bayer means"
                },
            )
        } else {
            val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
            if (gains != null) {
                val forPatch = applyHalWbGainCorrection(gains, null)
                out = TiffDngColorMatrixPatch.patchAsShotNeutral(out, forPatch)
                Log.i(TAG, "AsShotNeutral patched from COLOR_CORRECTION_GAINS (Bayer fallback)")
            } else {
                Log.w(TAG, "AsShotNeutral patch skipped: no gains or Bayer estimate")
            }
        }
        return out
    }
}

package dev.pointandshoot.fleet

import android.content.Context
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
 * LegacySku leaf DNG post-process after [android.hardware.camera2.DngCreator].
 *
 * **ReferenceCam / MotionCam gap (May 2026):** Play Store apps do not run Bayer/gain AsShotNeutral surgery.
 * MotionCam encodes in native code with per-model profiles; ReferenceCam relies on
 * `DngCreator(openedCameraCharacteristics, stillResult)` plus still IQ (lens shading, etc.).
 *
 * Shipped LegacyDevice path: force IFD0 + raw-IFD **ColorMatrix / ForwardMatrix** from the **opened leaf**
 * [CameraCharacteristics], optional per-id FM override, and **AsShotNeutral** from
 * [CaptureResult.SENSOR_NEUTRAL_COLOR_POINT] on the still result — not inverted gains or Bayer means.
 */
object LeafDngHalReconcile {
    private const val TAG = "PNS.LeafDng"

    fun shouldReconcileLeafDngMetadata(sessionCameraId: String): Boolean =
        shouldReconcileLeafDngMetadataWhen(
            deviceApplies = LegacyFleetPolicy.appliesToDevice(),
            backend = StillDngBackendPolicy.active(),
            sessionCameraId = sessionCameraId,
        )

    fun usesHalColorCalibration(sessionCameraId: String?): Boolean =
        useHalColorCalibrationPath(sessionCameraId)

    fun usesAsnOnlyReconcile(sessionCameraId: String?): Boolean =
        useAsnOnlyPath(sessionCameraId)

    fun usesProShotReferenceCalibration(sessionCameraId: String?): Boolean =
        useProShotReferenceCalibrationPath(sessionCameraId)

    /** Live [Image] Bayer stats — DngCreator row-strip layout mis-reads CFA on LegacySku. */
    fun shouldEstimateBayerBeforeWrite(sessionCameraId: String?): Boolean {
        if (sessionCameraId == null) return false
        if (useProShotReferenceCalibrationPath(sessionCameraId)) return true
        if (useAsnOnlyPath(sessionCameraId)) return true
        return shouldReconcileLeafDngMetadata(sessionCameraId) &&
            preferBayerAsnForSession(sessionCameraId)
    }

    /**
     * Legacy ASN-only reconcile (bisect). Default LegacyDevice aux uses [applyHalColorCalibrationReconcile].
     */
    internal fun shouldReconcileLeafDngMetadataWhen(
        deviceApplies: Boolean,
        backend: StillDngBackend,
        sessionCameraId: String,
        proShotPureDngSave: Boolean = LegacyFleetPolicy.useProShotPureDngSave(),
        wideLeafCalibrationForAuxDng: Boolean = LegacyFleetPolicy.useWideLeafCalibrationForAuxDng(),
        uwProShotAsnReconcile: Boolean = LegacyFleetPolicy.useLegacyLeafAuxColorReconcile(),
        proShotReferenceCalibration: Boolean = LegacyFleetPolicy.useProShotReferenceCalibration(),
    ): Boolean {
        DngSaveBisectState.forceLeafHalReconcile?.let { forced ->
            return forced &&
                deviceApplies &&
                sessionCameraId in leafRearIds
        }
        if (
            deviceApplies &&
            proShotReferenceCalibration &&
            proShotPureDngSave &&
            (sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
                sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        if (
            deviceApplies &&
            wideLeafCalibrationForAuxDng &&
            (sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
                sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        if (
            deviceApplies &&
            proShotPureDngSave &&
            uwProShotAsnReconcile &&
            (sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
                sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        if (deviceApplies && proShotPureDngSave && !proShotReferenceCalibration) {
            return false
        }
        if (sessionCameraId == LegacyFleetPolicy.CANONICAL_WIDE) {
            return false
        }
        if (
            LegacyFleetPolicy.useLegacyAsnReconcileOnly() &&
            (sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
                sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        return deviceApplies &&
            backend == StillDngBackend.FRAMEWORK_PROSHOT &&
            sessionCameraId in leafRearIds
    }

    private val leafRearIds: Set<String> =
        setOf(
            LegacyFleetPolicy.CANONICAL_UW,
            LegacyFleetPolicy.CANONICAL_WIDE,
            LegacyFleetPolicy.CANONICAL_TELE,
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
        preWriteBayerEstimate: DngBayerAsShotNeutral.BayerAsnEstimate? = null,
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
        assetContext: Context? = null,
    ): ByteArray {
        if (useProShotReferenceCalibrationPath(sessionCameraId)) {
            return applyProShotReferenceCalibrationReconcile(
                original,
                sessionCameraId!!,
                assetContext,
                characteristics,
                captureResult,
                preWriteBayerEstimate,
            )
        }
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
            val bayerAsn =
                if (sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE) {
                    preWriteBayerAsn
                        ?: DngBayerAsShotNeutral.estimateFromDngBytes(original, characteristics)
                } else {
                    preWriteBayerAsn
                }
            val saneBayerAsn = bayerAsn?.takeIf { isSaneAsnForDesktopOpenGate(it) }
            return applyOp13AsnOnlyReconcile(
                original,
                captureResult,
                sessionCameraId,
                preWriteBayerAsn = saneBayerAsn,
            )
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

    private fun isSaneAsnForDesktopOpenGate(asn: FloatArray): Boolean {
        if (asn.size < 3) return false
        val r = asn[0].coerceAtLeast(1e-6f)
        val b = asn[2].coerceAtLeast(1e-6f)
        val wbR = 1f / r
        val wbB = 1f / b
        // Match desktop-open gate bounds (see scripts/dng_desktop_open_gate.py).
        return wbR in 0.45f..2.8f && wbB in 0.45f..2.8f
    }

    private fun useProShotReferenceCalibrationPath(sessionCameraId: String?): Boolean {
        if (!LegacyFleetPolicy.useProShotReferenceCalibration()) return false
        if (sessionCameraId == null) return false
        // Wide (LYT-808): HAL CM/FM/ASN are trustworthy — aux only needs ReferenceCam profile + Bayer ASN.
        return sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
            sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE
    }

    private fun applyProShotReferenceCalibrationReconcile(
        original: ByteArray,
        sessionCameraId: String,
        assetContext: Context?,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        preWriteBayerEstimate: DngBayerAsShotNeutral.BayerAsnEstimate? = null,
    ): ByteArray {
        val ctx = assetContext?.applicationContext
        if (ctx == null) {
            Log.w(TAG, "referencecam-ref cal skipped: no assetContext cam=$sessionCameraId")
            return original
        }
        val slot = ProShotReferenceCalibration.forCameraId(ctx, sessionCameraId)
        if (slot == null) {
            Log.w(TAG, "referencecam-ref cal skipped: no slot for cam=$sessionCameraId")
            return original
        }
        val cm2Before =
            TiffDngColorMatrixPatch.readMatrixElement00(
                original,
                TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2,
            )
        var bytes =
            TiffDngColorMatrixPatch.patchCalibrationFromProShotReference(
                original,
                slot.colorMatrix1(),
                slot.colorMatrix2(),
                slot.forwardMatrix1(),
                slot.forwardMatrix2(),
            )
        val model = Build.MODEL?.lowercase().orEmpty()
        DngForwardMatrixFix.get(model, sessionCameraId)?.let { fmOverride ->
            bytes = TiffDngColorMatrixPatch.patchForwardMatrix(bytes, fmOverride)
            Log.i(TAG, "referencecam-ref ForwardMatrix override cam=$sessionCameraId")
        }
        bytes = patchProShotReferenceAsShotNeutral(
            bytes,
            characteristics,
            captureResult,
            sessionCameraId,
            slot,
            preWriteBayerEstimate,
        )
        val cm2After =
            TiffDngColorMatrixPatch.readMatrixElement00(
                bytes,
                TiffDngColorMatrixPatch.TAG_COLOR_MATRIX2,
            )
        Log.i(
            TAG,
            "referencecam-ref cal patched cam=$sessionCameraId cm2_before=$cm2Before cm2_after=$cm2After",
        )
        return bytes
    }

    /**
     * ReferenceCam CM/FM tags are copied from reference DNGs; ASN must track **this** capture's Bayer
     * (static ReferenceCam ASN on different RAW causes green cast in ACR).
     */
    private fun patchProShotReferenceAsShotNeutral(
        bytes: ByteArray,
        characteristics: CameraCharacteristics,
        captureResult: TotalCaptureResult,
        sessionCameraId: String,
        slot: ProShotReferenceCalibration.Slot,
        @Suppress("UNUSED_PARAMETER") preWriteBayerEstimate: DngBayerAsShotNeutral.BayerAsnEstimate? = null,
    ): ByteArray {
        // Same gray-card scene as bundled refs: ReferenceCam ASN + per-id FM override (above) is the
        // ACR/Lightroom path. Bayer strip/readback on LegacySku mis-phases CFA; HAL gains stay on
        // the still request for UW raw pixels only.
        return patchAsnFromProShotReferenceRationals(bytes, slot, sessionCameraId)
    }

    private fun patchAsnFromProShotReferenceRationals(
        bytes: ByteArray,
        slot: ProShotReferenceCalibration.Slot,
        sessionCameraId: String,
    ): ByteArray {
        val nd = slot.asnRationalNd
        if (nd.size != 6) return bytes
        val r = nd[0].toFloat() / nd[1].coerceAtLeast(1L)
        val g = nd[2].toFloat() / nd[3].coerceAtLeast(1L)
        val b = nd[4].toFloat() / nd[5].coerceAtLeast(1L)
        val asn = normalizeAsShotNeutralTriplet(r, g, b)
        Log.w(TAG, "referencecam-ref ASN fallback bundled ReferenceCam cam=$sessionCameraId")
        return TiffDngColorMatrixPatch.patchAsShotNeutralFromFloats(bytes, asn)
    }

    private fun useAsnOnlyPath(sessionCameraId: String?): Boolean {
        if (useProShotReferenceCalibrationPath(sessionCameraId)) return false
        if (
            LegacyFleetPolicy.useProShotPureDngSave() &&
            LegacyFleetPolicy.useLegacyLeafAuxColorReconcile() &&
            (sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
                sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE)
        ) {
            return true
        }
        if (LegacyFleetPolicy.useProShotPureDngSave()) return false
        if (DngSaveBisectState.forceLegacyAsnReconcile) return false
        if (sessionCameraId == null) return false
        if (!LegacyFleetPolicy.useLegacyAsnReconcileOnly()) return false
        return sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
            sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE
    }

    private fun useWideLeafCalibrationPath(
        sessionCameraId: String?,
        wideCalibrationCharacteristics: CameraCharacteristics? = null,
    ): Boolean {
        if (wideCalibrationCharacteristics == null || sessionCameraId == null) return false
        if (!LegacyFleetPolicy.useWideLeafCalibrationForAuxDng()) return false
        return sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
            sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE
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
            sessionCameraId?.let { LegacyLeafStillColorCorrection.takePendingCorrectedGains(it) }
        when {
            correctedStillGains != null -> {
                bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, correctedStillGains)
                Log.i(
                    TAG,
                    "wide-cal AsShotNeutral from Op13 still-request gains auxCam=$sessionCameraId " +
                        "profileCam=${LegacyFleetPolicy.CANONICAL_WIDE}",
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
        if (LegacyFleetPolicy.useWideLeafCalibrationForAuxDng()) return false
        if (LegacyFleetPolicy.useProShotPureDngSave()) return false
        if (DngSaveBisectState.forceLegacyAsnReconcile) return false
        if (sessionCameraId == null) return false
        if (!LegacyFleetPolicy.useHalColorCalibrationReconcile()) return false
        return sessionCameraId == LegacyFleetPolicy.CANONICAL_UW ||
            sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE
    }

    private fun applyOp13AsnOnlyReconcile(
        original: ByteArray,
        captureResult: TotalCaptureResult,
        sessionCameraId: String?,
        preWriteBayerAsn: FloatArray? = null,
    ): ByteArray {
        var bytes = original
        if (sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE && preWriteBayerAsn != null) {
            bytes =
                TiffDngColorMatrixPatch.patchAsShotNeutralFromFloats(
                    bytes,
                    preWriteBayerAsn,
                )
            Log.i(TAG, "asn-only AsShotNeutral from Bayer (pre-write) cam=$sessionCameraId")
        } else {
        val correctedStillGains =
            sessionCameraId?.let { LegacyLeafStillColorCorrection.takePendingCorrectedGains(it) }
        if (correctedStillGains != null) {
            bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, correctedStillGains)
            Log.i(
                TAG,
                "asn-only AsShotNeutral from Op13 corrected still-request gains cam=$sessionCameraId",
            )
        } else {
            val gains = captureResult.get(CaptureResult.COLOR_CORRECTION_GAINS)
            if (gains != null) {
                val forPatch =
                    if (DngSaveBisectState.skipHalWbGainCorrection) {
                        gains
                    } else if (sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE) {
                        // LegacySku tele: static WB scale table over-corrects; keep raw HAL gains.
                        gains
                    } else {
                        applyHalWbGainCorrection(gains, sessionCameraId)
                    }
                Log.i(TAG, "asn-only AsShotNeutral from COLOR_CORRECTION_GAINS cam=$sessionCameraId")
                bytes = TiffDngColorMatrixPatch.patchAsShotNeutral(bytes, forPatch)
            } else {
                Log.w(TAG, "asn-only skipped: no Op13 gains or COLOR_CORRECTION_GAINS cam=$sessionCameraId")
            }
        }
        }
        if (LegacyFleetPolicy.useLegacyLeafAuxColorReconcile()) {
            val model = Build.MODEL?.lowercase().orEmpty()
            DngForwardMatrixFix.get(model, sessionCameraId)?.let { fmOverride ->
                bytes = TiffDngColorMatrixPatch.patchForwardMatrix(bytes, fmOverride)
                Log.i(TAG, "asn+fm ForwardMatrix override cam=$sessionCameraId")
            }
        }
        return bytes
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
            sessionCameraId?.let { LegacyLeafStillColorCorrection.takePendingCorrectedGains(it) }
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
        if (!LegacyFleetPolicy.appliesToDevice()) return false
        return sessionCameraId == LegacyFleetPolicy.CANONICAL_TELE
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

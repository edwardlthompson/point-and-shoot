package dev.pointandshoot

import androidx.compose.ui.geometry.Offset
import kotlin.math.ln
import kotlin.math.pow

/**
 * Chart-driven calibration helpers: solve WB/CCM from a reference target, apply
 * natural capture defaults (minimal ISP sharpening, no creative LUT), and align
 * exposure to a neutral patch.
 */
object CalibrationWorkflow {

    /** [PreviewJpegProcessingHints] bias for minimal edge/NR/tonemap sharpening (natural). */
    const val NATURAL_HARDWARE_JPEG_ISP_BIAS: Int = -2

    /**
     * ColorChecker Classic **Neutral 5** linear-light luma (row 3, col 3 in
     * [BundledReferenceTargets.ColorCheckerClassic24]).
     */
    const val NEUTRAL5_REC709_LUMA: Float = 0.396f

    data class ComputeResult(
        val profile: CalibrationProfile,
        /** EV offset toward chart neutral (positive = brighten). */
        val exposureStops: Double,
        val acceptedPatchCount: Int,
    )

    fun hudNaturalDefaults(current: HudSettings): HudSettings =
        current.copy(
            hardwareJpegIspBias = NATURAL_HARDWARE_JPEG_ISP_BIAS,
            selectedLutForStills = LutCatalog.None.name,
        )

    fun previewShaderWbFromProfile(profile: CalibrationProfile): FloatArray =
        floatArrayOf(profile.wbGains.r, profile.wbGains.g, profile.wbGains.b)

    /**
     * Map UI corner taps (layout px) to [RgbPlane] pixel coordinates after
     * [BitmapRgbPlane.fromBitmap] downsampling.
     */
    fun chartCornersForPlane(
        corners: List<Offset>,
        layoutWidth: Int,
        layoutHeight: Int,
        planeWidth: Int,
        planeHeight: Int,
    ): ChartCorners {
        require(corners.size == 4) { "Need 4 corners (was ${corners.size})" }
        require(layoutWidth > 0 && layoutHeight > 0) { "layout size must be positive" }
        val sx = planeWidth.toFloat() / layoutWidth.toFloat()
        val sy = planeHeight.toFloat() / layoutHeight.toFloat()
        return ChartCorners(
            tl = Point2(corners[0].x * sx, corners[0].y * sy),
            tr = Point2(corners[1].x * sx, corners[1].y * sy),
            br = Point2(corners[2].x * sx, corners[2].y * sy),
            bl = Point2(corners[3].x * sx, corners[3].y * sy),
        )
    }

    /**
     * Solve profile from a display-referred preview bitmap and chart corners.
     */
    fun computeFromBitmap(
        bitmap: android.graphics.Bitmap,
        target: ReferenceTarget,
        corners: ChartCorners,
        cameraId: String,
        maxVariance: Float = CalibrationSampler.DEFAULT_MAX_VARIANCE,
    ): ComputeResult {
        val plane = BitmapRgbPlane.fromBitmap(bitmap)
        return computeFromPlane(plane, target, corners, cameraId, maxVariance)
    }

    fun computeFromPlane(
        plane: RgbPlane,
        target: ReferenceTarget,
        corners: ChartCorners,
        cameraId: String,
        maxVariance: Float = CalibrationSampler.DEFAULT_MAX_VARIANCE,
    ): ComputeResult {
        val samples = CalibrationSampler.sample(plane, target, corners, maxVariance)
        val accepted = samples.filter { !it.rejected && it.patchRef != null }
        require(accepted.size >= 6) {
            "Only ${accepted.size} of ${samples.size} patches passed; reframe or retap corners."
        }
        val neutralSamples =
            accepted
                .filter { it.patchRef!!.role == ReferenceTarget.PatchRole.Neutral }
                .map { it.mean }
        val wb =
            CalibrationMath.computeWbGains(
                neutralPatches = neutralSamples.ifEmpty { accepted.map { it.mean } },
            )
        val measuredAfterWb =
            accepted.map { sample ->
                floatArrayOf(
                    (sample.mean[0] * wb.r).coerceIn(0f, 1f),
                    (sample.mean[1] * wb.g).coerceIn(0f, 1f),
                    (sample.mean[2] * wb.b).coerceIn(0f, 1f),
                )
            }
        val targetRgb = accepted.map { checkNotNull(it.patchRef).referenceRgb }
        val ccm = CalibrationMath.computeCcm(measuredAfterWb, targetRgb)
        val profile =
            CalibrationProfile(
                wbGains = wb,
                ccm = ccm,
                bias = CalibrationProfile.Bias.Zero,
                mtf50Lpph = null,
                illuminant = target.illuminant,
                capturedAtMs = System.currentTimeMillis(),
                cameraId = cameraId,
                targetId = target.id,
            )
        val neutral5 =
            accepted.firstOrNull { patch ->
                val ref = patch.patchRef
                ref != null && ref.row == 3 && ref.col == 3
            } ?: accepted.firstOrNull { it.patchRef?.role == ReferenceTarget.PatchRole.Neutral }
        val exposureStops =
            if (neutral5 != null) {
                suggestExposureStopsFromNeutralMean(neutral5.mean)
            } else {
                0.0
            }
        return ComputeResult(
            profile = profile,
            exposureStops = exposureStops,
            acceptedPatchCount = accepted.size,
        )
    }

    /**
     * EV adjustment so chart neutral patch approaches [NEUTRAL5_REC709_LUMA].
     */
    fun suggestExposureStopsFromNeutralMean(meanRgb: FloatArray): Double {
        require(meanRgb.size == 3)
        val measured =
            (0.2126f * meanRgb[0] + 0.7152f * meanRgb[1] + 0.0722f * meanRgb[2])
                .coerceAtLeast(1e-4f)
        val ratio = NEUTRAL5_REC709_LUMA / measured
        return (ln(ratio.toDouble()) / ln(2.0)).coerceIn(-1.25, 1.25)
    }
}

package dev.pointandshoot

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared FOSS face-geometry helpers for the `foss` flavor YUV fallback
 * ([android.media.FaceDetector] + Camera2 [STATISTICS_FACES] already in main).
 *
 * [android.media.FaceDetector.Face] reports the midpoint between the eyes and
 * inter-eye distance, not a bounding box. This maps those to HUD boxes, pupil
 * marks, and a mouth band for the FOSS smile estimator.
 *
 * Pure JVM — unit-tested in [FaceDetectorGeometryTest].
 */
object FaceDetectorGeometry {

    private const val MIN_EVEN_WIDTH = 2
    private const val DEGREES_CIRCLE = 360
    private const val ROTATE_90 = 90
    private const val ROTATE_180 = 180
    private const val ROTATE_270 = 270
    private const val CROP_TOLERANCE_PX = 1
    /** Half-width of the HUD box relative to inter-eye distance. */
    private const val BOX_HALF_WIDTH_EYES = 1.2f
    /** Distance from eye midpoint up to the top of the HUD box. */
    private const val BOX_TOP_EYES = 1.2f
    /** Distance from eye midpoint down to the bottom of the HUD box. */
    private const val BOX_BOTTOM_EYES = 1.8f
    /** Mouth-band half-width relative to inter-eye distance. */
    private const val MOUTH_HALF_WIDTH_EYES = 0.7f
    /** Mouth-band top, below the eyes. */
    private const val MOUTH_TOP_EYES = 0.55f
    /** Mouth-band bottom. */
    private const val MOUTH_BOTTOM_EYES = 1.35f

    data class FaceHint(
        val boundsLeft: Int,
        val boundsTop: Int,
        val boundsRight: Int,
        val boundsBottom: Int,
        val leftEyeX: Float,
        val leftEyeY: Float,
        val rightEyeX: Float,
        val rightEyeY: Float,
        val confidence: Float,
        val mouthLeft: Int,
        val mouthTop: Int,
        val mouthRight: Int,
        val mouthBottom: Int,
    ) {
        val boundsWidth: Int get() = boundsRight - boundsLeft
        val boundsHeight: Int get() = boundsBottom - boundsTop
    }

    /**
     * Inter-ocular distance is typically ~1/3 of face width; eyes sit ~40% from
     * the top of the face. Mouth band is below the eyes, ~one eye-distance down.
     */
    fun fromEyesMidpoint(
        midX: Float,
        midY: Float,
        eyesDistance: Float,
        imageWidth: Int,
        imageHeight: Int,
        confidence: Float,
    ): FaceHint? {
        if (imageWidth <= 0 || imageHeight <= 0) return null
        val dist = eyesDistance.coerceAtLeast(1f)
        val halfW = dist * BOX_HALF_WIDTH_EYES
        val left = (midX - halfW).roundToInt().coerceIn(0, imageWidth - 1)
        val right = (midX + halfW).roundToInt().coerceIn(left + 1, imageWidth)
        val top = (midY - dist * BOX_TOP_EYES).roundToInt().coerceIn(0, imageHeight - 1)
        val bottom = (midY + dist * BOX_BOTTOM_EYES).roundToInt().coerceIn(top + 1, imageHeight)
        val leftEyeX = (midX - dist / 2f).coerceIn(0f, (imageWidth - 1).toFloat())
        val rightEyeX = (midX + dist / 2f).coerceIn(0f, (imageWidth - 1).toFloat())
        val eyeY = midY.coerceIn(0f, (imageHeight - 1).toFloat())
        val mouthCx = midX
        val mouthHalfW = dist * MOUTH_HALF_WIDTH_EYES
        val mouthTop = (midY + dist * MOUTH_TOP_EYES).roundToInt().coerceIn(0, imageHeight - 1)
        val mouthBottom = (midY + dist * MOUTH_BOTTOM_EYES).roundToInt().coerceIn(mouthTop + 1, imageHeight)
        val mouthLeft = (mouthCx - mouthHalfW).roundToInt().coerceIn(0, imageWidth - 1)
        val mouthRight = (mouthCx + mouthHalfW).roundToInt().coerceIn(mouthLeft + 1, imageWidth)
        return FaceHint(
            boundsLeft = left,
            boundsTop = top,
            boundsRight = right,
            boundsBottom = bottom,
            leftEyeX = leftEyeX,
            leftEyeY = eyeY,
            rightEyeX = rightEyeX,
            rightEyeY = eyeY,
            confidence = confidence.coerceIn(0f, 1f),
            mouthLeft = mouthLeft,
            mouthTop = mouthTop,
            mouthRight = mouthRight,
            mouthBottom = mouthBottom,
        )
    }

    /**
     * Maps a point in the **upright** FaceDetector bitmap back to YUV pixel space.
     *
     * Pipeline: YUV → uniformly downscaled RGB_565 ([preRotateW]×[preRotateH]) →
     * clockwise rotation by [rotationDegrees] → detector bitmap ([detW]×[detH]).
     */
    fun mapUprightPointToYuv(
        detX: Float,
        detY: Float,
        detW: Int,
        detH: Int,
        rotationDegrees: Int,
        preRotateW: Int,
        preRotateH: Int,
        yuvW: Int,
        yuvH: Int,
    ): Pair<Float, Float>? {
        if (!positiveSize(detW, detH)) return null
        if (!positiveSize(preRotateW, preRotateH)) return null
        if (!positiveSize(yuvW, yuvH)) return null
        val rot = normalizeDegrees(rotationDegrees)
        val (sx, sy) = inverseRotatePoint(detX, detY, detW, detH, preRotateW, preRotateH, rot)
            ?: return null
        val yuvX = sx * yuvW / preRotateW.toFloat()
        val yuvY = sy * yuvH / preRotateH.toFloat()
        return yuvX.coerceIn(0f, (yuvW - 1).toFloat()) to yuvY.coerceIn(0f, (yuvH - 1).toFloat())
    }

    /**
     * Inverse of clockwise rotation used by [android.graphics.Matrix.setRotate].
     *
     * 90 CW: source (sx, sy) in WxH → dest (h-1-sy, sx) in HxW
     * 180: source (sx, sy) → dest (w-1-sx, h-1-sy)
     * 270 CW: source (sx, sy) → dest (sy, w-1-sx) in HxW
     */
    fun inverseRotatePoint(
        dx: Float,
        dy: Float,
        detW: Int,
        detH: Int,
        srcW: Int,
        srcH: Int,
        rotationDegrees: Int,
    ): Pair<Float, Float>? {
        val rot = normalizeDegrees(rotationDegrees)
        return when (rot) {
            0 -> dx to dy
            ROTATE_90 -> {
                if (!nearSwapped(detW, detH, srcW, srcH)) return null
                val sx = dy
                val sy = (srcH - 1) - dx
                sx to sy
            }
            ROTATE_180 -> {
                val sx = (srcW - 1) - dx
                val sy = (srcH - 1) - dy
                sx to sy
            }
            ROTATE_270 -> {
                if (!nearSwapped(detW, detH, srcW, srcH)) return null
                val sx = (srcW - 1) - dy
                val sy = dx
                sx to sy
            }
            else -> null
        }?.let { (sx, sy) ->
            sx.coerceIn(0f, (srcW - 1).toFloat()) to sy.coerceIn(0f, (srcH - 1).toFloat())
        }
    }

    fun evenFloor(value: Int): Int = max(MIN_EVEN_WIDTH, value and 1.inv())

    private fun positiveSize(width: Int, height: Int): Boolean = width > 0 && height > 0

    private fun normalizeDegrees(degrees: Int): Int =
        ((degrees % DEGREES_CIRCLE) + DEGREES_CIRCLE) % DEGREES_CIRCLE

    private fun nearSwapped(detW: Int, detH: Int, srcW: Int, srcH: Int): Boolean {
        val widthDelta = abs(detW - srcH)
        val heightDelta = abs(detH - srcW)
        return widthDelta <= CROP_TOLERANCE_PX && heightDelta <= CROP_TOLERANCE_PX
    }
}

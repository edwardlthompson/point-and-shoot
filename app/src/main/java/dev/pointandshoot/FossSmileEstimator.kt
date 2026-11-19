package dev.pointandshoot

/**
 * Best-effort FOSS smile probability from a grayscale mouth band.
 *
 * ML Kit [Face.smilingProbability] is classification. AOSP
 * [android.media.FaceDetector] has no smile class, so the foss flavor uses a
 * conservative teeth-vs-corners luma heuristic on the mouth rectangle from
 * [FaceDetectorGeometry]. Returns null when the band is too small.
 *
 * Smile-triggered still stays **off** by default ([HudSettings.enableSmileTriggeredStill]).
 */
object FossSmileEstimator {

    private const val MIN_MOUTH_WIDTH = 6
    private const val MIN_MOUTH_HEIGHT = 3
    private const val MIN_THIRD_COLUMNS = 2
    private const val MOUTH_THIRDS = 3
    private const val BYTE_MASK = 0xFF
    /** Mouth-center luma minus lip-corner luma that maps to probability 1. */
    private const val SMILE_DELTA_SCALE = 48f
    /** Offset so a small center-bright delta is already a mid probability. */
    private const val SMILE_BIAS = 0.42f

    /**
     * @param luma Row-major 0..255 samples of the mouth band ([width]×[height]).
     * @return Probability in 0..1, or null if the band cannot be scored.
     */
    fun probabilityFromMouthLuma(
        luma: ByteArray,
        width: Int,
        height: Int,
    ): Float? {
        if (width < MIN_MOUTH_WIDTH || height < MIN_MOUTH_HEIGHT) return null
        if (luma.size < width * height) return null
        val third = width / MOUTH_THIRDS
        if (third < MIN_THIRD_COLUMNS) return null
        var leftSum = 0L
        var leftN = 0
        var centerSum = 0L
        var centerN = 0
        var rightSum = 0L
        var rightN = 0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val v = luma[row + x].toInt() and BYTE_MASK
                when {
                    x < third -> {
                        leftSum += v
                        leftN++
                    }
                    x >= width - third -> {
                        rightSum += v
                        rightN++
                    }
                    else -> {
                        centerSum += v
                        centerN++
                    }
                }
            }
        }
        if (leftN == 0 || centerN == 0 || rightN == 0) return null
        val leftMean = leftSum.toFloat() / leftN
        val rightMean = rightSum.toFloat() / rightN
        val centerMean = centerSum.toFloat() / centerN
        val outerMean = (leftMean + rightMean) / 2f
        // Teeth (or an open mouth) raise center luma vs lip corners.
        val delta = centerMean - outerMean
        return ((delta / SMILE_DELTA_SCALE) + SMILE_BIAS).coerceIn(0f, 1f)
    }
}

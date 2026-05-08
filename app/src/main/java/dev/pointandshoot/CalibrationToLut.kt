package dev.pointandshoot

/**
 * Bridge from a [CalibrationProfile] (WB gains + 3x3 CCM + bias) to a [Lut3D]
 * and a serialized `.cube` text per BUILD_PLAN §7 ("Phase 4 - calibration
 * mode -> .cube exporter").
 *
 * The point of doing this conversion eagerly (instead of applying WB + CCM at
 * encode time) is so non-RAW outputs (AVIF / JXL / JPEG) inherit the same
 * color math as the live preview / video lane via the standard LUT pipeline.
 * The exported `.cube` is also what users hand to color-grading software for
 * post (DaVinci, Resolve, Premiere) so their post-production grade aligns with
 * what they shot to.
 *
 * Pure function; no Android dependencies.
 */
object CalibrationToLut {

    /**
     * Generate a 3D LUT that applies (WB -> CCM -> bias) to a uniformly-sampled
     * normalized RGB cube. Default size is 33 (the same grid the GLES shader
     * samples), which gives ~ 1 LSB error on 8-bit displays for smooth
     * matrices.
     */
    fun toLut3D(profile: CalibrationProfile, size: Int = BuiltInLuts.DEFAULT_SIZE): Lut3D {
        require(size in Lut3D.SUPPORTED_SIZES) { "size must be in ${Lut3D.SUPPORTED_SIZES} (was $size)" }
        val out = FloatArray(size * size * size * 3)
        val denom = (size - 1).toFloat()
        val rgbBuf = FloatArray(3)
        for (b in 0 until size) {
            val bf = b / denom
            for (g in 0 until size) {
                val gf = g / denom
                for (r in 0 until size) {
                    val rf = r / denom
                    rgbBuf[0] = rf; rgbBuf[1] = gf; rgbBuf[2] = bf
                    val transformed = profile.apply(rgbBuf)
                    val idx = ((b * size + g) * size + r) * 3
                    out[idx] = transformed[0]
                    out[idx + 1] = transformed[1]
                    out[idx + 2] = transformed[2]
                }
            }
        }
        return Lut3D(size, out)
    }

    /**
     * Generate the `.cube` text for [profile]. The TITLE line records the
     * calibration provenance (illuminant + camera + chart) so a user inspecting
     * the file weeks later knows what shot it came from.
     */
    fun toCube(
        profile: CalibrationProfile,
        size: Int = BuiltInLuts.DEFAULT_SIZE,
        title: String? = null,
    ): String {
        val effectiveTitle = title ?: defaultTitle(profile)
        return LutPipeline.serializeCube(toLut3D(profile, size), title = effectiveTitle)
    }

    private fun defaultTitle(profile: CalibrationProfile): String =
        "Point & Shoot calibration ${profile.illuminant}" +
            " cam=${profile.cameraId} target=${profile.targetId}"
}

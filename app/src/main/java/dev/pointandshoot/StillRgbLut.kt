package dev.pointandshoot

/**
 * CPU LUT application for **encoded still** RGB planes per BUILD_PLAN §7 ("Stills
 * (post-encode)"): linear-light sRGB → [LutPipeline.applyTrilinear] → sRGB OETF,
 * on interleaved **RGB888** (`width × height × 3` bytes, row-major).
 *
 * Higher bit depths should be expanded to linear floats first; this helper covers
 * the 8-bit path the JPEG / preview-resolution fallback uses today.
 */
object StillRgbLut {

    /**
     * Applies [lut] in-place to [rgb888]. No-op when [lut] is identity.
     *
     * @param rgb888 at least `width * height * 3` bytes.
     */
    /**
     * Applies a saved chart [CalibrationProfile] (WB + CCM) as a corrective 3D LUT.
     * Used when [LutCatalog.None] so JPEG companions stay natural, not stylized.
     */
    fun applyCalibrationProfileInPlace(
        rgb888: ByteArray,
        width: Int,
        height: Int,
        profile: CalibrationProfile,
    ) {
        val lut = CalibrationToLut.toLut3D(profile, BuiltInLuts.DEFAULT_SIZE)
        applyToRgb888InPlace(rgb888, width, height, lut)
    }

    fun applyToRgb888InPlace(rgb888: ByteArray, width: Int, height: Int, lut: Lut3D) {
        require(width > 0 && height > 0) { "width and height must be positive" }
        val need = width * height * 3
        require(rgb888.size >= need) { "rgb888.size ${rgb888.size} < required $need" }
        if (lut.isIdentity()) return

        val tmp = FloatArray(3)
        var i = 0
        while (i < need) {
            tmp[0] = BitmapRgbPlane.srgbByteToLinear(rgb888[i].toInt() and 0xFF)
            tmp[1] = BitmapRgbPlane.srgbByteToLinear(rgb888[i + 1].toInt() and 0xFF)
            tmp[2] = BitmapRgbPlane.srgbByteToLinear(rgb888[i + 2].toInt() and 0xFF)
            LutPipeline.applyTrilinearInto(tmp[0], tmp[1], tmp[2], lut, tmp, offset = 0)
            rgb888[i] = u8(BitmapRgbPlane.linearToSrgbByte(tmp[0]))
            rgb888[i + 1] = u8(BitmapRgbPlane.linearToSrgbByte(tmp[1]))
            rgb888[i + 2] = u8(BitmapRgbPlane.linearToSrgbByte(tmp[2]))
            i += 3
        }
    }

    private fun u8(v: Int): Byte = (v and 0xFF).toByte()
}

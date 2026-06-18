package dev.pointandshoot

/**
 * Sprint **15.17** — minimal ICC v4 RGB display profile for JPEG / AVIF `colr` embedding.
 */
object IccProfileBuilder {
    private const val ICC_MAGIC = 0x61637370 // 'acsp'
    private const val PROFILE_CLASS_SPAC = 0x73706163 // 'spac'

    fun forColorSpaceTarget(target: ColorSpaceTarget): ByteArray =
        when (target) {
            ColorSpaceTarget.DisplayP3 -> buildDisplayP3Profile()
            ColorSpaceTarget.Rec2020 -> buildRec2020Profile()
            ColorSpaceTarget.ProPhotoRgb -> buildRgbHeaderProfile(profileDesc = "ProPhoto RGB")
            ColorSpaceTarget.AdobeRgb1998 -> buildRgbHeaderProfile(profileDesc = "Adobe RGB (1998)")
            ColorSpaceTarget.SrgbRec709 -> buildRgbHeaderProfile(profileDesc = "sRGB IEC61966-2.1")
        }

    /** @deprecated use [forColorSpaceTarget] */
    fun buildSrgbDisplayProfile(): ByteArray = buildDisplayP3Profile()

    /**
     * Minimal Display P3–tagged ICC (header stub) — exiftool / viewers report an embedded profile.
     */
    fun buildDisplayP3Profile(): ByteArray = buildRgbHeaderProfile(profileDesc = "Display P3")

    fun buildRec2020Profile(): ByteArray = buildRgbHeaderProfile(profileDesc = "Rec. 2020")

    private fun buildRgbHeaderProfile(profileDesc: String): ByteArray {
        val header = ByteArray(128)
        writeBe32(header, 0, ICC_MAGIC)
        writeBe32(header, 4, 0x04000000)
        writeBe32(header, 8, PROFILE_CLASS_SPAC)
        writeBe32(header, 12, 0x52474220) // 'RGB '
        writeBe32(header, 16, 0x58595A20) // 'XYZ '
        writeBe32(header, 20, header.size)
        val desc = profileDesc.take(32).padEnd(32, ' ').toByteArray(Charsets.US_ASCII)
        System.arraycopy(desc, 0, header, 64, desc.size.coerceAtMost(32))
        return header
    }

    private fun writeBe32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }
}

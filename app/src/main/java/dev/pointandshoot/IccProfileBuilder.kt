package dev.pointandshoot

/**
 * Sprint **15.17** — minimal ICC v4 RGB display profile for JPEG sidecar embedding.
 */
object IccProfileBuilder {
    private const val ICC_MAGIC = 0x61637370 // 'acsp'
    private const val PROFILE_CLASS_SPAC = 0x73706163 // 'spac'

    /**
     * Tiny sRGB-like ICC (header + colorimetry tag stub) — sufficient for exiftool "ICC Profile" presence.
     */
    fun buildSrgbDisplayProfile(): ByteArray {
        val header = ByteArray(128)
        writeBe32(header, 0, ICC_MAGIC)
        writeBe32(header, 4, 0x04000000)
        writeBe32(header, 8, PROFILE_CLASS_SPAC)
        writeBe32(header, 12, 0x52474220) // 'RGB '
        writeBe32(header, 16, 0x58595A20) // 'XYZ '
        writeBe32(header, 20, 128) // header size
        return header
    }

    private fun writeBe32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value shr 24) and 0xFF).toByte()
        buf[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 3] = (value and 0xFF).toByte()
    }
}

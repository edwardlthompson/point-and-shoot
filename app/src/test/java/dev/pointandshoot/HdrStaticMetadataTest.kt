package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class HdrStaticMetadataTest {

    private fun expectClose(expected: Float, actual: Float, tol: Float = 1e-4f, label: String = "") {
        assertTrue("$label expected=$expected actual=$actual tol=$tol", abs(expected - actual) <= tol)
    }

    @Test
    fun `MasteringDisplayMetadata REC2020_1000_NITS has the published Rec 2020 primaries`() {
        val m = MasteringDisplayMetadata.REC2020_1000_NITS
        expectClose(0.708f, m.xR, label = "xR")
        expectClose(0.292f, m.yR, label = "yR")
        expectClose(0.170f, m.xG, label = "xG")
        expectClose(0.797f, m.yG, label = "yG")
        expectClose(0.131f, m.xB, label = "xB")
        expectClose(0.046f, m.yB, label = "yB")
        expectClose(0.3127f, m.xWhite, label = "xW")
        expectClose(0.3290f, m.yWhite, label = "yW")
        assertEquals(1000f, m.maxLuminanceNits, 0f)
        assertEquals(0.005f, m.minLuminanceNits, 0f)
    }

    @Test
    fun `MasteringDisplayMetadata DISPLAY_P3_1000_NITS has the published P3 primaries`() {
        val m = MasteringDisplayMetadata.DISPLAY_P3_1000_NITS
        expectClose(0.680f, m.xR, label = "xR")
        expectClose(0.320f, m.yR, label = "yR")
        expectClose(0.265f, m.xG, label = "xG")
        expectClose(0.690f, m.yG, label = "yG")
        expectClose(0.150f, m.xB, label = "xB")
        expectClose(0.060f, m.yB, label = "yB")
        expectClose(0.3127f, m.xWhite, label = "xW")
        expectClose(0.3290f, m.yWhite, label = "yW")
    }

    @Test
    fun `primariesGbr returns G then B then R per ITU-T H 265 D 2 27`() {
        val m = MasteringDisplayMetadata.REC2020_1000_NITS
        val ordered = m.primariesGbr()
        assertEquals(3, ordered.size)
        assertEquals(m.xG to m.yG, ordered[0])
        assertEquals(m.xB to m.yB, ordered[1])
        assertEquals(m.xR to m.yR, ordered[2])
    }

    @Test
    fun `MasteringDisplayMetadata rejects out-of-range chromaticities`() {
        assertThrows(IllegalArgumentException::class.java) {
            MasteringDisplayMetadata(
                xR = 2f, yR = 0.292f, xG = 0.170f, yG = 0.797f, xB = 0.131f, yB = 0.046f,
                xWhite = 0.3127f, yWhite = 0.3290f, maxLuminanceNits = 1000f, minLuminanceNits = 0.005f,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            MasteringDisplayMetadata(
                xR = 0.708f, yR = 0f, xG = 0.170f, yG = 0.797f, xB = 0.131f, yB = 0.046f,
                xWhite = 0.3127f, yWhite = 0.3290f, maxLuminanceNits = 1000f, minLuminanceNits = 0.005f,
            )
        }
    }

    @Test
    fun `MasteringDisplayMetadata rejects max less than min luminance`() {
        assertThrows(IllegalArgumentException::class.java) {
            MasteringDisplayMetadata(
                xR = 0.708f, yR = 0.292f, xG = 0.170f, yG = 0.797f, xB = 0.131f, yB = 0.046f,
                xWhite = 0.3127f, yWhite = 0.3290f, maxLuminanceNits = 0.001f, minLuminanceNits = 0.005f,
            )
        }
    }

    @Test
    fun `MasteringDisplayMetadata rejects negative luminance`() {
        assertThrows(IllegalArgumentException::class.java) {
            MasteringDisplayMetadata(
                xR = 0.708f, yR = 0.292f, xG = 0.170f, yG = 0.797f, xB = 0.131f, yB = 0.046f,
                xWhite = 0.3127f, yWhite = 0.3290f, maxLuminanceNits = 1000f, minLuminanceNits = -0.001f,
            )
        }
    }

    @Test
    fun `encodeMdcvPayload produces exactly 24 bytes`() {
        val payload = HdrStaticMetadata.encodeMdcvPayload(MasteringDisplayMetadata.REC2020_1000_NITS)
        assertEquals(24, payload.size)
    }

    @Test
    fun `encodeMdcvPayload writes Rec 2020 primaries in GBR order with 50000 chromaticity scale`() {
        val payload = HdrStaticMetadata.encodeMdcvPayload(MasteringDisplayMetadata.REC2020_1000_NITS)
        // First entry must be Green (0.170, 0.797) → (8500, 39850)
        val xG = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val yG = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        assertEquals(8500, xG)
        assertEquals(39850, yG)
        // Second entry must be Blue (0.131, 0.046) → (6550, 2300)
        val xB = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        val yB = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
        assertEquals(6550, xB)
        assertEquals(2300, yB)
        // Third entry must be Red (0.708, 0.292) → (35400, 14600)
        val xR = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
        val yR = ((payload[10].toInt() and 0xFF) shl 8) or (payload[11].toInt() and 0xFF)
        assertEquals(35400, xR)
        assertEquals(14600, yR)
    }

    @Test
    fun `encodeMdcvPayload writes whitepoint and luminance with the correct scale`() {
        val payload = HdrStaticMetadata.encodeMdcvPayload(MasteringDisplayMetadata.REC2020_1000_NITS)
        val xW = ((payload[12].toInt() and 0xFF) shl 8) or (payload[13].toInt() and 0xFF)
        val yW = ((payload[14].toInt() and 0xFF) shl 8) or (payload[15].toInt() and 0xFF)
        assertEquals(15635, xW) // 0.3127 * 50_000 ≈ 15635
        assertEquals(16450, yW) // 0.3290 * 50_000 ≈ 16450
        // maxLuminance = 1000 cd/m^2 -> 10_000_000
        val maxLum = ((payload[16].toLong() and 0xFF) shl 24) or
            ((payload[17].toLong() and 0xFF) shl 16) or
            ((payload[18].toLong() and 0xFF) shl 8) or
            (payload[19].toLong() and 0xFF)
        assertEquals(10_000_000L, maxLum)
        // minLuminance = 0.005 cd/m^2 -> 50
        val minLum = ((payload[20].toLong() and 0xFF) shl 24) or
            ((payload[21].toLong() and 0xFF) shl 16) or
            ((payload[22].toLong() and 0xFF) shl 8) or
            (payload[23].toLong() and 0xFF)
        assertEquals(50L, minLum)
    }

    @Test
    fun `encodeMdcvPayload then decodeMdcvPayload round-trips Rec 2020 1000 nits within scale tolerance`() {
        val payload = HdrStaticMetadata.encodeMdcvPayload(MasteringDisplayMetadata.REC2020_1000_NITS)
        val round = HdrStaticMetadata.decodeMdcvPayload(payload)
        val src = MasteringDisplayMetadata.REC2020_1000_NITS
        val tol = 2.0f / 50_000f
        expectClose(src.xR, round.xR, tol = tol, label = "xR")
        expectClose(src.yR, round.yR, tol = tol, label = "yR")
        expectClose(src.xG, round.xG, tol = tol, label = "xG")
        expectClose(src.yG, round.yG, tol = tol, label = "yG")
        expectClose(src.xB, round.xB, tol = tol, label = "xB")
        expectClose(src.yB, round.yB, tol = tol, label = "yB")
        expectClose(src.xWhite, round.xWhite, tol = tol, label = "xW")
        expectClose(src.yWhite, round.yWhite, tol = tol, label = "yW")
        // Luminances are stored at 0.0001 cd/m^2 ULP, so float precision reigns.
        expectClose(src.maxLuminanceNits, round.maxLuminanceNits, tol = 1e-3f, label = "maxLum")
        expectClose(src.minLuminanceNits, round.minLuminanceNits, tol = 1e-3f, label = "minLum")
    }

    @Test
    fun `decodeMdcvPayload throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            HdrStaticMetadata.decodeMdcvPayload(ByteArray(23))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HdrStaticMetadata.decodeMdcvPayload(ByteArray(25))
        }
    }

    @Test
    fun `MDCV constants are pinned to the documented values`() {
        assertEquals(1, MasteringDisplayMetadata.SCHEMA_VERSION)
        assertEquals(24, MasteringDisplayMetadata.MDCV_PAYLOAD_LENGTH)
        assertEquals(50_000, MasteringDisplayMetadata.CHROMATICITY_SCALE)
        assertEquals(10_000, MasteringDisplayMetadata.LUMINANCE_SCALE)
    }

    @Test
    fun `ContentLightLevel rejects out-of-range maxCll and maxFall`() {
        assertThrows(IllegalArgumentException::class.java) { ContentLightLevel(maxCll = -1, maxFall = 0) }
        assertThrows(IllegalArgumentException::class.java) { ContentLightLevel(maxCll = 0x10000, maxFall = 0) }
        assertThrows(IllegalArgumentException::class.java) { ContentLightLevel(maxCll = 0, maxFall = -1) }
        assertThrows(IllegalArgumentException::class.java) { ContentLightLevel(maxCll = 0, maxFall = 0x10000) }
    }

    @Test
    fun `encodeClliPayload produces exactly 4 big-endian bytes`() {
        val payload = HdrStaticMetadata.encodeClliPayload(ContentLightLevel(maxCll = 1000, maxFall = 400))
        assertEquals(4, payload.size)
        val maxCll = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        val maxFall = ((payload[2].toInt() and 0xFF) shl 8) or (payload[3].toInt() and 0xFF)
        assertEquals(1000, maxCll)
        assertEquals(400, maxFall)
    }

    @Test
    fun `encodeClliPayload then decodeClliPayload round-trips`() {
        val src = ContentLightLevel(maxCll = 4000, maxFall = 1500)
        val payload = HdrStaticMetadata.encodeClliPayload(src)
        val round = HdrStaticMetadata.decodeClliPayload(payload)
        assertEquals(src, round)
    }

    @Test
    fun `decodeClliPayload throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            HdrStaticMetadata.decodeClliPayload(ByteArray(3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HdrStaticMetadata.decodeClliPayload(ByteArray(5))
        }
    }

    @Test
    fun `encodeClliPayload caps maxCll and maxFall at 65535 (clamped during construction)`() {
        val src = ContentLightLevel(maxCll = 0xFFFF, maxFall = 0xFFFF)
        val payload = HdrStaticMetadata.encodeClliPayload(src)
        assertEquals(0xFF.toByte(), payload[0])
        assertEquals(0xFF.toByte(), payload[1])
        assertEquals(0xFF.toByte(), payload[2])
        assertEquals(0xFF.toByte(), payload[3])
    }

    @Test
    fun `CLLI constants are pinned`() {
        assertEquals(1, ContentLightLevel.SCHEMA_VERSION)
        assertEquals(4, ContentLightLevel.CLLI_PAYLOAD_LENGTH)
    }

    @Test
    fun `scaleChromaticity rounds to nearest and clamps to uint16`() {
        assertEquals(35400, HdrStaticMetadata.scaleChromaticity(0.708f))
        assertEquals(35400, HdrStaticMetadata.scaleChromaticity(0.7080001f))
        assertEquals(0, HdrStaticMetadata.scaleChromaticity(-1f))
        assertEquals(0xFFFF, HdrStaticMetadata.scaleChromaticity(2f))
    }

    @Test
    fun `scaleLuminance rounds to nearest and clamps to uint32`() {
        assertEquals(10_000_000L, HdrStaticMetadata.scaleLuminance(1000f))
        assertEquals(50L, HdrStaticMetadata.scaleLuminance(0.005f))
        assertEquals(0L, HdrStaticMetadata.scaleLuminance(0f))
        assertEquals(0xFFFFFFFFL, HdrStaticMetadata.scaleLuminance(1e9f))
    }

    @Test
    fun `unscaleChromaticity inverts scaleChromaticity within ULP`() {
        for (raw in floatArrayOf(0.708f, 0.292f, 0.131f, 0.046f, 0.3127f, 0.3290f)) {
            val round = HdrStaticMetadata.unscaleChromaticity(HdrStaticMetadata.scaleChromaticity(raw))
            expectClose(raw, round, tol = 1f / 50_000f, label = "chromaticity round-trip")
        }
    }
}

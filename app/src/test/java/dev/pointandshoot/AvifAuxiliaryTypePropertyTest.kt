package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * JUnit tests for [AvifAuxiliaryTypeProperty] (`auxC` FullBox).
 *
 * Tests cover:
 *
 *  * Constant pins (BOX_TYPE / VERSION / FLAGS / SCHEMA_VERSION /
 *    AUX_TYPE_ALPHA / AUX_TYPE_DEPTH).
 *  * `Payload` validation rejects empty auxType + embedded NUL.
 *  * `Payload.equals` / `hashCode` compare auxSubtype by content.
 *  * Pre-computed `ALPHA` / `DEPTH` companions match constants.
 *  * `encodePayload` byte-layout pins for AVIF alpha (canonical
 *    44-byte payload), HEIF depth, custom auxType + subtype.
 *  * `decodePayload` round-trips every preset + a custom payload
 *    with non-empty auxSubtype.
 *  * `decodePayload` rejections (empty payload, missing NUL,
 *    NUL at offset 0).
 *  * `encodeBox` 56-byte canonical AVIF alpha envelope with
 *    every field offset pinned.
 *  * `encodeAlphaBox` byte-exact matches `encodeBox(Payload.ALPHA)`.
 */
class AvifAuxiliaryTypePropertyTest {

    @Test
    fun `SCHEMA_VERSION pin`() {
        assertEquals(1, AvifAuxiliaryTypeProperty.SCHEMA_VERSION)
    }

    @Test
    fun `BOX_TYPE pin`() {
        assertEquals("auxC", AvifAuxiliaryTypeProperty.BOX_TYPE)
    }

    @Test
    fun `VERSION pin`() {
        assertEquals(0, AvifAuxiliaryTypeProperty.VERSION)
    }

    @Test
    fun `FLAGS pin`() {
        assertEquals(0, AvifAuxiliaryTypeProperty.FLAGS)
    }

    @Test
    fun `AUX_TYPE_ALPHA pin`() {
        assertEquals(
            "urn:mpeg:mpegB:cicp:systems:auxiliary:alpha",
            AvifAuxiliaryTypeProperty.AUX_TYPE_ALPHA,
        )
    }

    @Test
    fun `AUX_TYPE_DEPTH pin`() {
        assertEquals(
            "urn:mpeg:mpegB:cicp:systems:auxiliary:depth",
            AvifAuxiliaryTypeProperty.AUX_TYPE_DEPTH,
        )
    }

    @Test
    fun `EMPTY_AUX_SUBTYPE pin`() {
        assertEquals(0, AvifAuxiliaryTypeProperty.EMPTY_AUX_SUBTYPE.size)
    }

    @Test
    fun `Payload rejects empty auxType`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryTypeProperty.Payload(auxType = "")
        }
    }

    @Test
    fun `Payload rejects auxType with embedded NUL`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryTypeProperty.Payload(auxType = "alpha\u0000inner")
        }
    }

    @Test
    fun `Payload equals compares auxSubtype by content`() {
        val a = AvifAuxiliaryTypeProperty.Payload(
            auxType = "urn:test",
            auxSubtype = byteArrayOf(0x01, 0x02, 0x03),
        )
        val b = AvifAuxiliaryTypeProperty.Payload(
            auxType = "urn:test",
            auxSubtype = byteArrayOf(0x01, 0x02, 0x03),
        )
        val c = AvifAuxiliaryTypeProperty.Payload(
            auxType = "urn:test",
            auxSubtype = byteArrayOf(0x01, 0x02, 0x04),
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `Payload ALPHA companion matches the AUX_TYPE_ALPHA constant`() {
        assertEquals(
            AvifAuxiliaryTypeProperty.AUX_TYPE_ALPHA,
            AvifAuxiliaryTypeProperty.Payload.ALPHA.auxType,
        )
        assertEquals(0, AvifAuxiliaryTypeProperty.Payload.ALPHA.auxSubtype.size)
    }

    @Test
    fun `Payload DEPTH companion matches the AUX_TYPE_DEPTH constant`() {
        assertEquals(
            AvifAuxiliaryTypeProperty.AUX_TYPE_DEPTH,
            AvifAuxiliaryTypeProperty.Payload.DEPTH.auxType,
        )
        assertEquals(0, AvifAuxiliaryTypeProperty.Payload.DEPTH.auxSubtype.size)
    }

    @Test
    fun `encodePayload AVIF alpha is auxType bytes plus single NUL`() {
        val payload = AvifAuxiliaryTypeProperty.encodePayload(
            AvifAuxiliaryTypeProperty.Payload.ALPHA,
        )
        // The canonical AVIF alpha URN is 43 ASCII chars; payload
        // is therefore 43 + 1 (NUL) = 44 bytes.
        assertEquals(44, payload.size)
        val expected = (AvifAuxiliaryTypeProperty.AUX_TYPE_ALPHA.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x00))
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload HEIF depth produces depth URN bytes plus NUL`() {
        val payload = AvifAuxiliaryTypeProperty.encodePayload(
            AvifAuxiliaryTypeProperty.Payload.DEPTH,
        )
        val expected = (AvifAuxiliaryTypeProperty.AUX_TYPE_DEPTH.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x00))
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodePayload appends auxSubtype after the NUL`() {
        val payload = AvifAuxiliaryTypeProperty.encodePayload(
            AvifAuxiliaryTypeProperty.Payload(
                auxType = "urn:test",
                auxSubtype = byteArrayOf(0x10, 0x20, 0x30),
            ),
        )
        // urn:test = 8 ASCII bytes + 1 NUL + 3 subtype bytes = 12.
        assertEquals(12, payload.size)
        assertEquals(0x00, payload[8].toInt())
        assertEquals(0x10, payload[9].toInt())
        assertEquals(0x20, payload[10].toInt())
        assertEquals(0x30, payload[11].toInt())
    }

    @Test
    fun `decodePayload AVIF alpha round-trips back to the input`() {
        val encoded = AvifAuxiliaryTypeProperty.encodePayload(
            AvifAuxiliaryTypeProperty.Payload.ALPHA,
        )
        val decoded = AvifAuxiliaryTypeProperty.decodePayload(encoded)
        assertEquals(AvifAuxiliaryTypeProperty.Payload.ALPHA, decoded)
    }

    @Test
    fun `decodePayload HEIF depth round-trips back to the input`() {
        val encoded = AvifAuxiliaryTypeProperty.encodePayload(
            AvifAuxiliaryTypeProperty.Payload.DEPTH,
        )
        val decoded = AvifAuxiliaryTypeProperty.decodePayload(encoded)
        assertEquals(AvifAuxiliaryTypeProperty.Payload.DEPTH, decoded)
    }

    @Test
    fun `decodePayload preserves auxSubtype on round-trip`() {
        val original = AvifAuxiliaryTypeProperty.Payload(
            auxType = "urn:test",
            auxSubtype = byteArrayOf(0x01, 0x02, 0x03, 0x04),
        )
        val encoded = AvifAuxiliaryTypeProperty.encodePayload(original)
        val decoded = AvifAuxiliaryTypeProperty.decodePayload(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodePayload rejects empty payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryTypeProperty.decodePayload(ByteArray(0))
        }
    }

    @Test
    fun `decodePayload rejects payload with no NUL terminator`() {
        // 43-byte ASCII URN with no trailing NUL.
        val noNul = AvifAuxiliaryTypeProperty.AUX_TYPE_ALPHA.toByteArray(Charsets.UTF_8)
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryTypeProperty.decodePayload(noNul)
        }
    }

    @Test
    fun `decodePayload rejects NUL at offset 0`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryTypeProperty.decodePayload(byteArrayOf(0x00))
        }
    }

    @Test
    fun `encodeBox AVIF alpha produces 56-byte canonical FullBox envelope`() {
        val box = AvifAuxiliaryTypeProperty.encodeAlphaBox()
        // Total = 8 header + 4 (version+flags) + 44 (44-byte payload) = 56.
        assertEquals(56, box.size)
        // Size BE
        assertEquals(0x00, box[0].toInt() and 0xFF)
        assertEquals(0x00, box[1].toInt() and 0xFF)
        assertEquals(0x00, box[2].toInt() and 0xFF)
        assertEquals(56, box[3].toInt() and 0xFF)
        // Type "auxC"
        assertEquals('a', box[4].toInt().toChar())
        assertEquals('u', box[5].toInt().toChar())
        assertEquals('x', box[6].toInt().toChar())
        assertEquals('C', box[7].toInt().toChar())
        // Version + flags = 0
        assertEquals(0x00, box[8].toInt() and 0xFF)
        assertEquals(0x00, box[9].toInt() and 0xFF)
        assertEquals(0x00, box[10].toInt() and 0xFF)
        assertEquals(0x00, box[11].toInt() and 0xFF)
        // Payload (44 bytes): URN ASCII + trailing NUL.
        val payload = box.copyOfRange(12, 56)
        val expected = (AvifAuxiliaryTypeProperty.AUX_TYPE_ALPHA.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x00))
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodeAlphaBox is byte-exact equal to encodeBox of Payload ALPHA`() {
        val a = AvifAuxiliaryTypeProperty.encodeAlphaBox()
        val b = AvifAuxiliaryTypeProperty.encodeBox(AvifAuxiliaryTypeProperty.Payload.ALPHA)
        assertArrayEquals(a, b)
    }

    @Test
    fun `encodeBox HEIF depth produces canonical envelope`() {
        val box = AvifAuxiliaryTypeProperty.encodeBox(AvifAuxiliaryTypeProperty.Payload.DEPTH)
        // 8 header + 4 (v+f) + 43 URN + 1 NUL = 56.
        assertEquals(56, box.size)
        val typeBytes = box.copyOfRange(4, 8)
        assertEquals("auxC", String(typeBytes, Charsets.US_ASCII))
        val payload = box.copyOfRange(12, box.size)
        val expected = (AvifAuxiliaryTypeProperty.AUX_TYPE_DEPTH.toByteArray(Charsets.UTF_8) +
            byteArrayOf(0x00))
        assertArrayEquals(expected, payload)
    }

    @Test
    fun `encodeBox custom auxType + subtype produces correct total size`() {
        val box = AvifAuxiliaryTypeProperty.encodeBox(
            AvifAuxiliaryTypeProperty.Payload(
                auxType = "urn:test",
                auxSubtype = byteArrayOf(0x01, 0x02),
            ),
        )
        // 8 header + 4 (v+f) + 8 URN + 1 NUL + 2 subtype = 23.
        assertEquals(23, box.size)
    }
}

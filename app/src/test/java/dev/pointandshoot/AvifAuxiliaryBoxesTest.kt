package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AvifAuxiliaryBoxesTest {

    @Test
    fun `Rotation enum maps degrees and wireValue`() {
        assertEquals(0, AvifAuxiliaryBoxes.Rotation.Rot0.degrees)
        assertEquals(0, AvifAuxiliaryBoxes.Rotation.Rot0.wireValue)
        assertEquals(90, AvifAuxiliaryBoxes.Rotation.Rot90.degrees)
        assertEquals(1, AvifAuxiliaryBoxes.Rotation.Rot90.wireValue)
        assertEquals(180, AvifAuxiliaryBoxes.Rotation.Rot180.degrees)
        assertEquals(2, AvifAuxiliaryBoxes.Rotation.Rot180.wireValue)
        assertEquals(270, AvifAuxiliaryBoxes.Rotation.Rot270.degrees)
        assertEquals(3, AvifAuxiliaryBoxes.Rotation.Rot270.wireValue)
    }

    @Test
    fun `Rotation fromDegrees normalizes negative and over-360 angles`() {
        assertEquals(AvifAuxiliaryBoxes.Rotation.Rot0, AvifAuxiliaryBoxes.Rotation.fromDegrees(0))
        assertEquals(AvifAuxiliaryBoxes.Rotation.Rot90, AvifAuxiliaryBoxes.Rotation.fromDegrees(90))
        assertEquals(AvifAuxiliaryBoxes.Rotation.Rot270, AvifAuxiliaryBoxes.Rotation.fromDegrees(-90))
        assertEquals(AvifAuxiliaryBoxes.Rotation.Rot90, AvifAuxiliaryBoxes.Rotation.fromDegrees(450))
    }

    @Test
    fun `Rotation fromDegrees rejects non-90-multiple`() {
        assertThrows(IllegalStateException::class.java) {
            AvifAuxiliaryBoxes.Rotation.fromDegrees(45)
        }
    }

    @Test
    fun `Rotation fromWireValue rejects out-of-range values`() {
        assertThrows(IllegalStateException::class.java) {
            AvifAuxiliaryBoxes.Rotation.fromWireValue(4)
        }
        assertThrows(IllegalStateException::class.java) {
            AvifAuxiliaryBoxes.Rotation.fromWireValue(-1)
        }
    }

    @Test
    fun `encodeIrot produces exactly 1 byte with the wireValue`() {
        for (rot in AvifAuxiliaryBoxes.Rotation.values()) {
            val payload = AvifAuxiliaryBoxes.encodeIrot(rot)
            assertEquals(1, payload.size)
            assertEquals(rot.wireValue.toByte(), payload[0])
        }
    }

    @Test
    fun `encodeIrot then decodeIrot round-trips every rotation`() {
        for (rot in AvifAuxiliaryBoxes.Rotation.values()) {
            val round = AvifAuxiliaryBoxes.decodeIrot(AvifAuxiliaryBoxes.encodeIrot(rot))
            assertEquals(rot, round)
        }
    }

    @Test
    fun `decodeIrot tolerates non-zero reserved bits in irot byte`() {
        val payload = byteArrayOf((0xFC or 0x01).toByte()) // garbage reserved bits + Rot90
        assertEquals(AvifAuxiliaryBoxes.Rotation.Rot90, AvifAuxiliaryBoxes.decodeIrot(payload))
    }

    @Test
    fun `decodeIrot throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodeIrot(ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodeIrot(ByteArray(2))
        }
    }

    @Test
    fun `MirrorAxis spec-aligned wire values are pinned`() {
        assertEquals(0, AvifAuxiliaryBoxes.MirrorAxis.Horizontal.wireValue)
        assertEquals(1, AvifAuxiliaryBoxes.MirrorAxis.Vertical.wireValue)
    }

    @Test
    fun `MirrorAxis fromWireValue rejects out-of-range values`() {
        assertThrows(IllegalStateException::class.java) {
            AvifAuxiliaryBoxes.MirrorAxis.fromWireValue(2)
        }
    }

    @Test
    fun `encodeImir then decodeImir round-trips both axes`() {
        for (axis in AvifAuxiliaryBoxes.MirrorAxis.values()) {
            val round = AvifAuxiliaryBoxes.decodeImir(AvifAuxiliaryBoxes.encodeImir(axis))
            assertEquals(axis, round)
        }
    }

    @Test
    fun `decodeImir tolerates non-zero reserved bits`() {
        val payload = byteArrayOf((0xFE or 0x01).toByte()) // garbage reserved bits + Vertical
        assertEquals(AvifAuxiliaryBoxes.MirrorAxis.Vertical, AvifAuxiliaryBoxes.decodeImir(payload))
    }

    @Test
    fun `decodeImir throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodeImir(ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodeImir(ByteArray(2))
        }
    }

    @Test
    fun `PixiPayload presets cover the common channel layouts`() {
        assertArrayEquals(intArrayOf(8), AvifAuxiliaryBoxes.PixiPayload.MONO_8.bitDepths)
        assertArrayEquals(intArrayOf(8, 8, 8), AvifAuxiliaryBoxes.PixiPayload.RGB_8.bitDepths)
        assertArrayEquals(intArrayOf(8, 8, 8, 8), AvifAuxiliaryBoxes.PixiPayload.RGBA_8.bitDepths)
        assertArrayEquals(intArrayOf(10, 10, 10), AvifAuxiliaryBoxes.PixiPayload.RGB_10.bitDepths)
        assertArrayEquals(intArrayOf(12, 12, 12), AvifAuxiliaryBoxes.PixiPayload.RGB_12.bitDepths)
    }

    @Test
    fun `PixiPayload rejects empty channel arrays`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.PixiPayload(intArrayOf())
        }
    }

    @Test
    fun `PixiPayload rejects bit depth less than 1 or greater than 255`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.PixiPayload(intArrayOf(8, 0, 8))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.PixiPayload(intArrayOf(8, 256, 8))
        }
    }

    @Test
    fun `PixiPayload equals compares the bitDepths arrays by content`() {
        val a = AvifAuxiliaryBoxes.PixiPayload(intArrayOf(8, 8, 8))
        val b = AvifAuxiliaryBoxes.PixiPayload(intArrayOf(8, 8, 8))
        val c = AvifAuxiliaryBoxes.PixiPayload(intArrayOf(10, 10, 10))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `encodePixi prefixes channel count then writes each bit depth`() {
        val payload = AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGBA_8)
        assertArrayEquals(byteArrayOf(4, 8, 8, 8, 8), payload)
    }

    @Test
    fun `encodePixi for 10-bit RGB produces the expected byte sequence`() {
        val payload = AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload.RGB_10)
        assertArrayEquals(byteArrayOf(3, 10, 10, 10), payload)
    }

    @Test
    fun `encodePixi then decodePixi round-trips every preset`() {
        for (preset in listOf(
            AvifAuxiliaryBoxes.PixiPayload.MONO_8,
            AvifAuxiliaryBoxes.PixiPayload.RGB_8,
            AvifAuxiliaryBoxes.PixiPayload.RGBA_8,
            AvifAuxiliaryBoxes.PixiPayload.RGB_10,
            AvifAuxiliaryBoxes.PixiPayload.RGB_12,
        )) {
            val round = AvifAuxiliaryBoxes.decodePixi(AvifAuxiliaryBoxes.encodePixi(preset))
            assertEquals(preset, round)
        }
    }

    @Test
    fun `decodePixi rejects empty payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodePixi(ByteArray(0))
        }
    }

    @Test
    fun `decodePixi rejects mismatched declared channel count`() {
        // Declares 3 channels but only 2 bytes follow.
        val truncated = byteArrayOf(3, 8, 8)
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodePixi(truncated)
        }
    }

    @Test
    fun `decodePixi rejects zero channel count`() {
        val zeroChan = byteArrayOf(0)
        assertThrows(IllegalArgumentException::class.java) {
            AvifAuxiliaryBoxes.decodePixi(zeroChan)
        }
    }

    @Test
    fun `decodePixi tolerates the maximum 255-channel monochrome payload`() {
        val depths = IntArray(255) { 8 }
        val payload = AvifAuxiliaryBoxes.encodePixi(AvifAuxiliaryBoxes.PixiPayload(depths))
        assertEquals(256, payload.size)
        val round = AvifAuxiliaryBoxes.decodePixi(payload)
        assertTrue(depths.contentEquals(round.bitDepths))
    }

    @Test
    fun `payload length and schema constants are pinned`() {
        assertEquals(1, AvifAuxiliaryBoxes.IROT_PAYLOAD_LENGTH)
        assertEquals(1, AvifAuxiliaryBoxes.IMIR_PAYLOAD_LENGTH)
        assertEquals(1, AvifAuxiliaryBoxes.SCHEMA_VERSION)
    }
}

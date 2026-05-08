package dev.pointandshoot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IsobmffSampleAspectTest {

    @Test
    fun `pasp payload length is exactly 8`() {
        assertEquals(8, IsobmffSampleAspect.PASP_PAYLOAD_LENGTH)
    }

    @Test
    fun `clap payload length is exactly 32`() {
        assertEquals(32, IsobmffSampleAspect.CLAP_PAYLOAD_LENGTH)
    }

    @Test
    fun `PaspPayload SQUARE is 1 by 1`() {
        assertEquals(1, IsobmffSampleAspect.PaspPayload.SQUARE.hSpacing)
        assertEquals(1, IsobmffSampleAspect.PaspPayload.SQUARE.vSpacing)
    }

    @Test
    fun `PaspPayload rejects spacings less than 1`() {
        assertThrows(IllegalArgumentException::class.java) { IsobmffSampleAspect.PaspPayload(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { IsobmffSampleAspect.PaspPayload(1, 0) }
        assertThrows(IllegalArgumentException::class.java) { IsobmffSampleAspect.PaspPayload(-1, 1) }
    }

    @Test
    fun `encodePasp produces exactly 8 big-endian bytes`() {
        val payload = IsobmffSampleAspect.encodePasp(IsobmffSampleAspect.PaspPayload(40, 33))
        assertEquals(8, payload.size)
        assertArrayEquals(byteArrayOf(0, 0, 0, 40, 0, 0, 0, 33), payload)
    }

    @Test
    fun `encodePasp then decodePasp round-trips`() {
        val src = IsobmffSampleAspect.PaspPayload(16, 11)
        val round = IsobmffSampleAspect.decodePasp(IsobmffSampleAspect.encodePasp(src))
        assertEquals(src, round)
    }

    @Test
    fun `decodePasp throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.decodePasp(ByteArray(7))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.decodePasp(ByteArray(9))
        }
    }

    @Test
    fun `ClapPayload rejects zero or negative widthD heightD horizOffD vertOffD widthN heightN`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(1920, 0, 1080, 1, 0, 1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(1920, 1, 1080, 0, 0, 1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(1920, 1, 1080, 1, 0, 0, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(1920, 1, 1080, 1, 0, 1, 0, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(0, 1, 1080, 1, 0, 1, 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload(1920, 1, 0, 1, 0, 1, 0, 1)
        }
    }

    @Test
    fun `encodeClap produces exactly 32 bytes`() {
        val payload = IsobmffSampleAspect.encodeClap(
            IsobmffSampleAspect.ClapPayload(1920, 1, 1080, 1, 0, 1, 0, 1),
        )
        assertEquals(32, payload.size)
    }

    @Test
    fun `encodeClap writes width n d height n d offsets in big-endian order`() {
        val payload = IsobmffSampleAspect.encodeClap(
            IsobmffSampleAspect.ClapPayload(0x12345678, 1, 0x9ABC, 1, -1, 2, 3, 4),
        )
        // widthN
        assertEquals(0x12.toByte(), payload[0])
        assertEquals(0x34.toByte(), payload[1])
        assertEquals(0x56.toByte(), payload[2])
        assertEquals(0x78.toByte(), payload[3])
        // widthD = 1
        assertEquals(0x00.toByte(), payload[4])
        assertEquals(0x00.toByte(), payload[5])
        assertEquals(0x00.toByte(), payload[6])
        assertEquals(0x01.toByte(), payload[7])
        // heightN = 0x9ABC
        assertEquals(0x00.toByte(), payload[8])
        assertEquals(0x00.toByte(), payload[9])
        assertEquals(0x9A.toByte(), payload[10])
        assertEquals(0xBC.toByte(), payload[11])
        // horizOffN = -1 → all 0xFF in two's complement
        assertEquals(0xFF.toByte(), payload[16])
        assertEquals(0xFF.toByte(), payload[17])
        assertEquals(0xFF.toByte(), payload[18])
        assertEquals(0xFF.toByte(), payload[19])
        // horizOffD = 2
        assertEquals(0x00.toByte(), payload[20])
        assertEquals(0x00.toByte(), payload[21])
        assertEquals(0x00.toByte(), payload[22])
        assertEquals(0x02.toByte(), payload[23])
        // vertOffN = 3
        assertEquals(0x00.toByte(), payload[24])
        assertEquals(0x00.toByte(), payload[25])
        assertEquals(0x00.toByte(), payload[26])
        assertEquals(0x03.toByte(), payload[27])
        // vertOffD = 4
        assertEquals(0x00.toByte(), payload[28])
        assertEquals(0x00.toByte(), payload[29])
        assertEquals(0x00.toByte(), payload[30])
        assertEquals(0x04.toByte(), payload[31])
    }

    @Test
    fun `encodeClap then decodeClap round-trips exactly`() {
        val src = IsobmffSampleAspect.ClapPayload(
            widthN = 4096, widthD = 1,
            heightN = 2160, heightD = 1,
            horizOffN = -7, horizOffD = 2,
            vertOffN = 11, vertOffD = 4,
        )
        val round = IsobmffSampleAspect.decodeClap(IsobmffSampleAspect.encodeClap(src))
        assertEquals(src, round)
    }

    @Test
    fun `decodeClap throws on wrong length`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.decodeClap(ByteArray(31))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.decodeClap(ByteArray(33))
        }
    }

    @Test
    fun `centeredCropOf returns identity for full-image crop`() {
        val clap = IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, 0, 0, 1920, 1080)
        assertEquals(1920, clap.widthN)
        assertEquals(1080, clap.heightN)
        assertEquals(0, clap.horizOffN)
        assertEquals(0, clap.vertOffN)
    }

    @Test
    fun `centeredCropOf computes a centered 1920x1080 crop within 4096x2160 coded image`() {
        val clap = IsobmffSampleAspect.ClapPayload.centeredCropOf(4096, 2160, 1088, 540, 1920, 1080)
        assertEquals(1920, clap.widthN)
        assertEquals(1, clap.widthD)
        assertEquals(1080, clap.heightN)
        assertEquals(1, clap.heightD)
        // (cropX, cropY) = (1088, 540), (cropW, cropH) = (1920, 1080) => crop center is at
        // ((2 * 1088 + 1919) / 2, (2 * 540 + 1079) / 2) = (4095/2, 2159/2). Coded center is at
        // ((4095)/2, (2159)/2) so offsets are zero.
        assertEquals(0, clap.horizOffN)
        assertEquals(2, clap.horizOffD)
        assertEquals(0, clap.vertOffN)
        assertEquals(2, clap.vertOffD)
    }

    @Test
    fun `centeredCropOf computes left-edge crop offsets relative to the coded center`() {
        val clap = IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, 0, 0, 800, 600)
        assertEquals(800, clap.widthN)
        assertEquals(600, clap.heightN)
        // cropCenterX2 = 0 + 800 - 1 = 799; codedCenterX2 = 1919; horizOffN = 799 - 1919 = -1120
        assertEquals(-1120, clap.horizOffN)
        // cropCenterY2 = 0 + 600 - 1 = 599; codedCenterY2 = 1079; vertOffN = 599 - 1079 = -480
        assertEquals(-480, clap.vertOffN)
        assertEquals(2, clap.horizOffD)
        assertEquals(2, clap.vertOffD)
    }

    @Test
    fun `centeredCropOf rejects crop overflowing the coded dimensions`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, 1500, 0, 1000, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, 0, 1000, 100, 200)
        }
    }

    @Test
    fun `centeredCropOf rejects negative or zero arguments`() {
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload.centeredCropOf(0, 1080, 0, 0, 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, -1, 0, 100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            IsobmffSampleAspect.ClapPayload.centeredCropOf(1920, 1080, 0, 0, 0, 100)
        }
    }

    @Test
    fun `SCHEMA_VERSION is pinned`() {
        assertEquals(1, IsobmffSampleAspect.SCHEMA_VERSION)
    }
}

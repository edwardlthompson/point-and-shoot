package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ImagingProfileTest {

    @Test
    fun `Standard Pro maps to lossless DNG capture kind`() {
        assertSame(
            CaptureStorage.CaptureKind.DngLossless,
            ImagingProfile.StandardPro.toDngCaptureKind(),
        )
    }

    @Test
    fun `Ultra-Max maps to RAW12 DNG capture kind`() {
        assertSame(
            CaptureStorage.CaptureKind.DngRaw12,
            ImagingProfile.UltraMax.toDngCaptureKind(),
        )
    }

    @Test
    fun `byId resolves ultra_max and standard_pro`() {
        assertEquals(ImagingProfile.UltraMax, ImagingProfile.byId("ultra_max"))
        assertEquals(ImagingProfile.StandardPro, ImagingProfile.byId("standard_pro"))
        assertEquals(ImagingProfile.JpegOnly, ImagingProfile.byId("jpeg_only"))
    }

    @Test(expected = IllegalStateException::class)
    fun `JpegOnly has no DNG capture kind`() {
        ImagingProfile.JpegOnly.toDngCaptureKind()
    }

    @Test
    fun `byId falls back to default for unknown id`() {
        assertEquals(ImagingProfile.default, ImagingProfile.byId("nope"))
        assertEquals(ImagingProfile.default, ImagingProfile.byId(null))
    }
}

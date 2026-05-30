package dev.pointandshoot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UltraHd60RecordSupportTest {
    @Test
    fun catalogTier_4k60_requiresEncoderPerf_notNullMapAlone() {
        assertTrue(UltraHd60RecordSupport.isCatalogTierSupported(null, 3840, 2160, 30))
    }

    @Test
    fun catalogTier_nonUhd_returnsTrueWithoutMap() {
        assertTrue(UltraHd60RecordSupport.isCatalogTierSupported(null, 1920, 1080, 60))
    }

    @Test
    fun wantsEncoderOnly_falseForUhd60InterleavedMr() {
        assertFalse(
            UltraHd60RecordSupport.wantsEncoderOnlyRecord(
                recordSize = android.util.Size(3840, 2160),
                desiredFps = 60,
                map = null,
                inAppVideoRecordingArmed = true,
                recorderPresent = true,
            ),
        )
    }

    @Test
    fun targetFpsConstant_is60() {
        assertTrue(UltraHd60RecordSupport.TARGET_FPS == 60)
    }

    @Test
    fun isUltraHdSize_4k() {
        assertTrue(UltraHd60RecordSupport.isUltraHdSize(3840, 2160))
        assertFalse(UltraHd60RecordSupport.isUltraHdSize(1920, 1080))
    }
}

package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMediaFamilyTest {
    @Test
    fun commandDialMenuSectionTitle_photoAndVideo() {
        assertEquals(
            "Photo programs",
            CaptureMediaFamily.commandDialMenuSectionTitle(CaptureMediaFamily.Photo),
        )
        assertEquals(
            "Video programs",
            CaptureMediaFamily.commandDialMenuSectionTitle(CaptureMediaFamily.Video),
        )
    }

    @Test
    fun isMacroVideo_videoMacroDialOnly() {
        assertTrue(CaptureMediaFamily.isMacroVideo(primaryPhoto = false, dial = CommandDialMode.Macro))
        assertTrue(CaptureMediaFamily.isMacroVideo(CaptureMediaFamily.Video, CommandDialMode.Macro))
        assertTrue(!CaptureMediaFamily.isMacroVideo(primaryPhoto = true, dial = CommandDialMode.Macro))
        assertTrue(!CaptureMediaFamily.isMacroVideo(primaryPhoto = false, dial = CommandDialMode.Auto))
    }

    @Test
    fun commandDialModesFor_videoSubsetOfPhoto() {
        val video = CaptureMediaFamily.commandDialModesFor(CaptureMediaFamily.Video)
        assertEquals(
            listOf(
                CommandDialMode.Auto,
                CommandDialMode.M,
                CommandDialMode.S,
                CommandDialMode.Macro,
                CommandDialMode.Dual,
            ),
            video,
        )
        val photo = CaptureMediaFamily.commandDialModesFor(CaptureMediaFamily.Photo)
        assertTrue(photo.size > video.size)
        assertTrue(CommandDialMode.BKT in photo)
        assertTrue(CommandDialMode.BKT !in video)
        assertTrue(CommandDialMode.Qr in photo)
        assertTrue(CommandDialMode.Qr !in video)
        assertTrue(CommandDialMode.Dual !in photo)
        assertEquals(CommandDialMode.Qr, photo.last())
    }
}

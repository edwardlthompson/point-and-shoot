package dev.pointandshoot.preview.session

import android.os.Handler
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PreviewSessionCreateEntryTest {

    @Test
    fun validate_noHandler() {
        assertEquals(
            PreviewSessionCreateEntry.Result.AbortNoHandler,
            PreviewSessionCreateEntry.validate(null, null),
        )
    }

    @Test
    fun validate_noSurface() {
        val handler = mock(Handler::class.java)
        assertEquals(
            PreviewSessionCreateEntry.Result.AbortNoPreviewSurface,
            PreviewSessionCreateEntry.validate(handler, null),
        )
    }

    @Test
    fun validate_invalidSurface() {
        val handler = mock(Handler::class.java)
        val surface = mock(Surface::class.java)
        `when`(surface.isValid).thenReturn(false)
        assertEquals(
            PreviewSessionCreateEntry.Result.AbortInvalidPreviewSurface,
            PreviewSessionCreateEntry.validate(handler, surface),
        )
    }

    @Test
    fun validate_ready() {
        val handler = mock(Handler::class.java)
        val surface = mock(Surface::class.java)
        `when`(surface.isValid).thenReturn(true)
        val result = PreviewSessionCreateEntry.validate(handler, surface)
        assertTrue(result is PreviewSessionCreateEntry.Result.Ready)
        assertEquals(surface, (result as PreviewSessionCreateEntry.Result.Ready).previewSurface)
    }
}

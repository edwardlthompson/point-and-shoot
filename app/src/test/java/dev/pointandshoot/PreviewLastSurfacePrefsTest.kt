package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewLastSurfacePrefsTest {
    @Test
    fun fromStored_mapsPersistedNames() {
        assertEquals(PreviewLastSurface.Video, PreviewLastSurface.fromStored("Video"))
        assertEquals(PreviewLastSurface.Gallery, PreviewLastSurface.fromStored("Gallery"))
        assertEquals(PreviewLastSurface.Photo, PreviewLastSurface.fromStored("Photo"))
        assertEquals(PreviewLastSurface.Photo, PreviewLastSurface.fromStored(null))
        assertEquals(PreviewLastSurface.Photo, PreviewLastSurface.fromStored("unknown"))
    }
}

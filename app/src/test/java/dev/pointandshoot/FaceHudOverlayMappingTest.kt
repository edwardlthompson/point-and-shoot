package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceHudOverlayMappingTest {
    @Test
    fun mapBufferPointToTile_matchesCenterCropOnContentHost() {
        val (x, y) =
            FaceHudOverlayMapping.mapBufferPointToTile(
                bufferX = 960f,
                bufferY = 720f,
                tileW = 1411,
                tileH = 1881,
                bufferW = 1920,
                bufferH = 1440,
                coverCrop = true,
            )
        val (expectedX, expectedY) =
            TexturePreviewFit.mapBufferToView(960f, 720f, 1411, 1881, 1920, 1440, coverCrop = true)
        assertEquals(expectedX, x, 0.5f)
        assertEquals(expectedY, y, 0.5f)
    }

}

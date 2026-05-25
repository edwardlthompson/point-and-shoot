package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformIntegrationTest {

    @Test
    fun parseDeepLink_preview() {
        val route = PlatformIntegration.parseDeepLinkString("pointandshoot://preview")
        assertNotNull(route)
        assertEquals(PNS_SCREEN_PREVIEW, route!!.screen)
        assertEquals(true, route.primaryPhoto)
    }

    @Test
    fun parseDeepLink_video() {
        val route = PlatformIntegration.parseDeepLinkString("pointandshoot://video")
        assertNotNull(route)
        assertEquals(false, route!!.primaryPhoto)
    }

    @Test
    fun parseDeepLink_gallery() {
        val route = PlatformIntegration.parseDeepLinkString("pointandshoot://gallery")
        assertNotNull(route)
        assertTrue(route!!.openGallery)
    }

    @Test
    fun parseDeepLink_unknownHost() {
        assertNull(PlatformIntegration.parseDeepLinkString("pointandshoot://unknown"))
    }

    @Test
    fun deepLinkRoute_gallery_setsOpenGalleryFlag() {
        val route = PlatformIntegration.parseDeepLinkString("pointandshoot://gallery")
        assertNotNull(route)
        assertTrue(route!!.openGallery)
        assertEquals(PNS_SCREEN_PREVIEW, route.screen)
    }
}

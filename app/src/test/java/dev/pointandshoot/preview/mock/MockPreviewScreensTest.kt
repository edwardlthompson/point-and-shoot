package dev.pointandshoot.preview.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockPreviewScreensTest {
    @Test
    fun isMockRoute_acceptsCanonicalAndLegacyAliases() {
        assertTrue(MockPreviewScreens.isMockRoute(MockPreviewScreens.ROUTE_MOCK))
        assertTrue(MockPreviewScreens.isMockRoute(MockPreviewScreens.ROUTE_PROHUD))
        assertTrue(MockPreviewScreens.isMockRoute(MockPreviewScreens.ROUTE_GLPREVIEW))
        assertFalse(MockPreviewScreens.isMockRoute("preview"))
        assertFalse(MockPreviewScreens.isMockRoute(null))
    }

    @Test
    fun normalizeRoute_preservesLaunchAlias() {
        assertEquals(MockPreviewScreens.ROUTE_MOCK, MockPreviewScreens.normalizeRoute(MockPreviewScreens.ROUTE_MOCK))
        assertEquals(MockPreviewScreens.ROUTE_PROHUD, MockPreviewScreens.normalizeRoute(MockPreviewScreens.ROUTE_PROHUD))
        assertEquals(MockPreviewScreens.ROUTE_GLPREVIEW, MockPreviewScreens.normalizeRoute(MockPreviewScreens.ROUTE_GLPREVIEW))
        assertEquals(MockPreviewScreens.ROUTE_MOCK, MockPreviewScreens.normalizeRoute(null))
    }
}

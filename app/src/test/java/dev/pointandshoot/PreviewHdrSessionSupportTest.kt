package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreviewHdrSessionSupportTest {

    @Test
    fun orderedDynamicRangeCandidates_prefersRecommendedThenSortedRest() {
        assertEquals(
            listOf(2L, 1L, 4L, 8L),
            PreviewHdrSessionSupport.orderedDynamicRangeCandidates(2L, listOf(8L, 1L, 4L, 2L)),
        )
    }

    @Test
    fun orderedDynamicRangeCandidates_noDuplicateWhenRecInSet() {
        assertEquals(
            listOf(5L, 1L, 3L),
            PreviewHdrSessionSupport.orderedDynamicRangeCandidates(5L, listOf(3L, 5L, 1L)),
        )
    }

    @Test
    fun orderedDynamicRangeCandidates_nullRecUsesSortedOnly() {
        assertEquals(
            listOf(1L, 9L),
            PreviewHdrSessionSupport.orderedDynamicRangeCandidates(null, listOf(9L, 1L)),
        )
    }

    @Test
    fun hdr10LivePreview_defaultsOff() {
        assertFalse(HudSettings().enableHdr10LivePreview)
    }
}

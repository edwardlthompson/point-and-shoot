package dev.pointandshoot.fleet

import org.junit.Assert.assertEquals
import org.junit.Test

class LeafDngHalReconcileNormalizeTest {

    @Test
    fun normalizeAsShotNeutralTriplet_normalizesToMaxOne() {
        val asn = LeafDngHalReconcile.normalizeAsShotNeutralTriplet(0.5f, 1f, 0.25f)
        assertEquals(0.5f, asn[0], 0.001f)
        assertEquals(1f, asn[1], 0.001f)
        assertEquals(0.25f, asn[2], 0.001f)
    }
}

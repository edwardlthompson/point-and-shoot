package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Test

class DngDefaultUserCropRatiosTest {

    @Test
    fun street35_normalized_edges_span_center_strip_of_active() {
        val e = DngDefaultUserCropRatios.normalizedEdges(FocalMode.Street35, 8160, 6144)
        val plan = CropPlan.centeredCrop(FocalMode.Street35, 8160, 6144)
        assertEquals(plan.cropTop / 6144f, e[0], 1e-5f)
        assertEquals(plan.cropLeft / 8160f, e[1], 1e-5f)
        assertEquals((plan.cropTop + plan.cropHeight) / 6144f, e[2], 1e-5f)
        assertEquals((plan.cropLeft + plan.cropWidth) / 8160f, e[3], 1e-5f)
    }

    @Test
    fun edges_are_monotonic_and_inside_unit_square() {
        val e = DngDefaultUserCropRatios.normalizedEdges(FocalMode.LongTele150, 4096, 3072)
        assert(e[0] <= e[2])
        assert(e[1] <= e[3])
        assert(e[0] in 0f..1f && e[1] in 0f..1f && e[2] in 0f..1f && e[3] in 0f..1f)
    }
}

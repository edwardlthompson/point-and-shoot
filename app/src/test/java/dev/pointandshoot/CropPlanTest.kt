package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CropPlanTest {

    @Test
    fun `35mm street crop is 1_5x centered on a 4096x3072 sensor`() {
        val plan = CropPlan.centeredCrop(FocalMode.Street35, sensorWidth = 4096, sensorHeight = 3072)
        assertEquals(1.5, plan.zoomFactor, 0.0001)
        assertEquals(2731, plan.cropWidth)
        assertEquals(2048, plan.cropHeight)
        assertEquals((4096 - 2731) / 2, plan.cropLeft)
        assertEquals((3072 - 2048) / 2, plan.cropTop)
        assertEquals(MeteringHint.Average, plan.meteringHint)
        assertEquals(AfHint.SinglePoint, plan.afHint)
    }

    @Test
    fun `50mm standard crop is 2_2x centered with center-weighted metering`() {
        val plan = CropPlan.centeredCrop(FocalMode.Standard50, sensorWidth = 4096, sensorHeight = 3072)
        assertEquals(2.2, plan.zoomFactor, 0.0001)
        assertEquals(1862, plan.cropWidth)
        assertEquals(1396, plan.cropHeight)
        assertEquals(MeteringHint.CenterWeighted, plan.meteringHint)
        assertEquals(AfHint.SinglePoint, plan.afHint)
    }

    @Test
    fun `85mm portrait crop selects Eye-AF priority`() {
        val plan = CropPlan.centeredCrop(FocalMode.Portrait85, sensorWidth = 3840, sensorHeight = 2160)
        assertEquals(1.16, plan.zoomFactor, 0.0001)
        assertEquals(AfHint.EyeAf, plan.afHint)
        assertEquals(MeteringHint.CenterWeighted, plan.meteringHint)
        // Crop must stay inside the sensor bounds.
        assertTrue(plan.cropLeft + plan.cropWidth <= 3840)
        assertTrue(plan.cropTop + plan.cropHeight <= 2160)
    }

    @Test
    fun `150mm long-tele is the 12MP center crop of a 50MP sensor`() {
        // LYT-600 native res ~ 8160x6120 (50 MP). A 12 MP center crop is a sqrt(50/12) = 2.041x linear.
        val plan = CropPlan.centeredCrop(FocalMode.LongTele150, sensorWidth = 8160, sensorHeight = 6120)
        assertEquals(2.04, plan.zoomFactor, 0.0001)
        // 8160 / 2.04 = 4000  -> 12.0 MP output (4000 x 3000)
        assertEquals(4000, plan.cropWidth)
        assertEquals(3000, plan.cropHeight)
        // Effective output area should be ~ 12 MP (within 1% of nominal).
        val outputMp = (plan.cropWidth.toLong() * plan.cropHeight.toLong()) / 1_000_000.0
        assertTrue("expected ~12 MP, got $outputMp", outputMp in 11.5..12.5)
        assertEquals(MeteringHint.CenterWeighted, plan.meteringHint)
        assertEquals(AfHint.EyeAf, plan.afHint)
    }

    @Test
    fun `150mm crop is centered on the LYT-600 native 8160x6120 frame`() {
        val plan = CropPlan.centeredCrop(FocalMode.LongTele150, sensorWidth = 8160, sensorHeight = 6120)
        val rightMargin = 8160 - plan.cropLeft - plan.cropWidth
        val bottomMargin = 6120 - plan.cropTop - plan.cropHeight
        assertTrue(kotlin.math.abs(plan.cropLeft - rightMargin) <= 1)
        assertTrue(kotlin.math.abs(plan.cropTop - bottomMargin) <= 1)
    }

    @Test
    fun `crop is centered (left equals right margin within rounding)`() {
        val plan = CropPlan.centeredCrop(FocalMode.Standard50, sensorWidth = 4000, sensorHeight = 3000)
        val rightMargin = 4000 - plan.cropLeft - plan.cropWidth
        val bottomMargin = 3000 - plan.cropTop - plan.cropHeight
        assertTrue("left $plan.cropLeft != right $rightMargin (>=2 diff)", kotlin.math.abs(plan.cropLeft - rightMargin) <= 1)
        assertTrue("top ${plan.cropTop} != bottom $bottomMargin (>=2 diff)", kotlin.math.abs(plan.cropTop - bottomMargin) <= 1)
    }

    @Test
    fun `tiny sensor still produces a valid 1x1 minimum crop`() {
        val plan = CropPlan.centeredCrop(FocalMode.Standard50, sensorWidth = 1, sensorHeight = 1)
        assertEquals(1, plan.cropWidth)
        assertEquals(1, plan.cropHeight)
        assertEquals(0, plan.cropLeft)
        assertEquals(0, plan.cropTop)
    }

    @Test
    fun `non-positive sensor dimensions throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            CropPlan.centeredCrop(FocalMode.Street35, sensorWidth = 0, sensorHeight = 1000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CropPlan.centeredCrop(FocalMode.Street35, sensorWidth = 1000, sensorHeight = -1)
        }
    }
}

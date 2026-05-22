package dev.pointandshoot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationWorkflowTest {

    @Test
    fun suggestExposureStops_brighterWhenNeutralTooDark() {
        val dark = floatArrayOf(0.2f, 0.2f, 0.2f)
        val stops = CalibrationWorkflow.suggestExposureStopsFromNeutralMean(dark)
        assertTrue(stops > 0.0)
    }

    @Test
    fun suggestExposureStops_darkerWhenNeutralTooBright() {
        val bright = floatArrayOf(0.85f, 0.85f, 0.85f)
        val stops = CalibrationWorkflow.suggestExposureStopsFromNeutralMean(bright)
        assertTrue(stops < 0.0)
    }

    @Test
    fun hudNaturalDefaults_softIspAndNoCreativeLut() {
        val hud = CalibrationWorkflow.hudNaturalDefaults(HudSettings())
        assertEquals(CalibrationWorkflow.NATURAL_HARDWARE_JPEG_ISP_BIAS, hud.hardwareJpegIspBias)
        assertEquals(LutCatalog.None.name, hud.selectedLutForStills)
    }

    @Test
    fun chartCornersForPlane_scalesLayoutToPlane() {
        val corners =
            CalibrationWorkflow.chartCornersForPlane(
                corners =
                    listOf(
                        androidx.compose.ui.geometry.Offset(0f, 0f),
                        androidx.compose.ui.geometry.Offset(100f, 0f),
                        androidx.compose.ui.geometry.Offset(100f, 50f),
                        androidx.compose.ui.geometry.Offset(0f, 50f),
                    ),
                layoutWidth = 100,
                layoutHeight = 50,
                planeWidth = 200,
                planeHeight = 100,
            )
        assertEquals(0f, corners.tl.x, 0.01f)
        assertEquals(200f, corners.tr.x, 0.01f)
        assertEquals(100f, corners.br.y, 0.01f)
    }
}

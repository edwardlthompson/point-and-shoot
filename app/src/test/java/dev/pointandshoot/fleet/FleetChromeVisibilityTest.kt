package dev.pointandshoot.fleet

import dev.pointandshoot.CommandDialMode
import dev.pointandshoot.HardwareCaps
import dev.pointandshoot.PreviewFpsSupport
import dev.pointandshoot.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.json.JSONObject

class FleetChromeVisibilityTest {
    private fun caps(): HardwareCaps =
        HardwareCaps(
            hasRawCapability = false,
            has12BitDepth = false,
            has120FpsHfr = false,
            hasFaceDetectFull = false,
            hasPreviewHistogram = false,
            aeCompensationStepsAvailable = 0,
            hasMacroMode = false,
            has10BitHdrPipeline = false,
        )

    private fun ctx(
        matrix: JSONObject? = null,
        caps: HardwareCaps = caps(),
        rootGranted: Boolean = false,
        activeCameraId: String? = "2",
    ): FleetUiVisibilityGate.VisibilityContext =
        FleetUiVisibilityGate.VisibilityContext(
            matrix = matrix,
            caps = caps,
            rootGranted = rootGranted,
            activeCameraId = activeCameraId,
        )

    @Test
    fun filterCommandDialModes_hidesQrWhenNotSupported() {
        val matrix =
            JSONObject(
                """
                {
                  "capabilityCatalog": [
                    {"id": "preview.qr", "deviceSupported": false}
                  ]
                }
                """.trimIndent(),
            )
        val filtered =
            FleetChromeVisibility.filterCommandDialModes(
                listOf(CommandDialMode.Auto, CommandDialMode.Qr),
                ctx(matrix = matrix),
            )
        assertEquals(listOf(CommandDialMode.Auto), filtered)
    }

    @Test
    fun filterFpsOptions_hidesStockHfrWhenVideoHfrUnsupported() {
        val matrix =
            JSONObject(
                """
                {
                  "capabilityCatalog": [
                    {"id": "video.hfr", "deviceSupported": false}
                  ]
                }
                """.trimIndent(),
            )
        val options =
            listOf(
                PreviewFpsSupport.QuickFpsOption(30, requiresRoot = false),
                PreviewFpsSupport.QuickFpsOption(120, requiresRoot = false),
                PreviewFpsSupport.QuickFpsOption(240, requiresRoot = true),
            )
        val filtered = FleetChromeVisibility.filterFpsOptions(options, ctx(matrix = matrix))
        assertEquals(listOf(30, 240), filtered.map { it.targetFps })
    }

    @Test
    fun filterVideoFormats_hides1080p30WhenUnsupported() {
        val matrix =
            JSONObject(
                """
                {
                  "capabilityCatalog": [
                    {"id": "video.regular.1080p30", "deviceSupported": false}
                  ]
                }
                """.trimIndent(),
            )
        assertFalse(
            FleetUiVisibilityGate.visible(
                FleetChromeVisibility.videoFormatFeatureId(VideoCodec.H264, 1920, 1080, 30)!!,
                ctx(matrix = matrix),
            ),
        )
    }

    @Test
    fun videoFormatFeatureId_maps1080p30() {
        assertEquals(
            "video.regular.1080p30",
            FleetChromeVisibility.videoFormatFeatureId(VideoCodec.H264, 1920, 1080, 30),
        )
    }

    @Test
    fun videoFormatFeatureId_maps4k30ToFourKRegular() {
        assertEquals(
            "video.4k_regular",
            FleetChromeVisibility.videoFormatFeatureId(VideoCodec.H264, 3840, 2160, 30),
        )
        assertEquals(
            "video.4k_regular",
            FleetChromeVisibility.videoFormatFeatureId(VideoCodec.H265, 3840, 2160, 30),
        )
    }

    @Test
    fun videoFormatFeatureId_maps4k60ToUhd60() {
        assertEquals(
            "video.uhd60",
            FleetChromeVisibility.videoFormatFeatureId(VideoCodec.H264, 3840, 2160, 60),
        )
    }
}

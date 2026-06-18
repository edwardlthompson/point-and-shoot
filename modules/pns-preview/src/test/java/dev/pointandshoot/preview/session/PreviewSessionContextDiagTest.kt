package dev.pointandshoot.preview.session

import dev.pointandshoot.CommandDialMode
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionContextDiagTest {

    @Test
    fun regularSessionLogLine_includesAeCouplingAndWantChase() {
        val line =
            PreviewSessionContextDiag.regularSessionLogLine(
                displayHz = 120f,
                desiredFps = 60,
                commandDialMode = CommandDialMode.H,
                manualIsoOverride = 400,
                manualExposureNsOverride = null,
                wantChase = true,
                wantYuv = true,
                yuvAttached = true,
                recordSurfacePresent = false,
                automationSuppressFacePipeline = false,
                sessionGen = 3L,
            )
        assertTrue(line.startsWith("PNS.PreviewSessionCtx "))
        assertTrue(line.contains("defaultDisplayHz=120.0"))
        assertTrue(line.contains("dial=H"))
        assertTrue(line.contains("aeCoupling=LOCKED_ISO_AUTO_SS"))
        assertTrue(line.contains("wantChase=true"))
        assertTrue(line.contains("useHighSpeed=false"))
        assertTrue(line.contains("wantYuv=true"))
        assertTrue(line.contains("sessionGen=3"))
    }

    @Test
    fun hfrSessionLogLine_marksHighSpeed() {
        val line =
            PreviewSessionContextDiag.hfrSessionLogLine(
                displayHz = null,
                desiredFps = 120,
                commandDialMode = CommandDialMode.Dual,
                automationSuppressFacePipeline = true,
                sessionGen = 9L,
            )
        assertTrue(line.contains("useHighSpeed=true"))
        assertTrue(line.contains("wantYuv=false"))
        assertTrue(line.contains("suppressFacePipeline=true"))
    }
}

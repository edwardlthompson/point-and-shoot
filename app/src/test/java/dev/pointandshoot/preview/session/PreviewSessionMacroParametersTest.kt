package dev.pointandshoot.preview.session

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewSessionMacroParametersTest {

    @Test
    fun shouldAttempt_falseWhenMacroOff() {
        assertFalse(
            PreviewSessionMacroParameters.shouldAttemptMacroSessionParameters(
                wantsMacroProgram = false,
                superMacroAdbProbe = false,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                camId = "2",
                ultraWideCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldAttempt_trueForAdbProbeOnUltraWide() {
        assertTrue(
            PreviewSessionMacroParameters.shouldAttemptMacroSessionParameters(
                wantsMacroProgram = false,
                superMacroAdbProbe = true,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                camId = "2",
                ultraWideCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldAttempt_falseWhenNotUltraWideCam() {
        assertFalse(
            PreviewSessionMacroParameters.shouldAttemptMacroSessionParameters(
                wantsMacroProgram = true,
                superMacroAdbProbe = false,
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                camId = "3",
                ultraWideCameraId = "2",
            ),
        )
    }

    @Test
    fun shouldAttempt_falseBelowApi33() {
        assertFalse(
            PreviewSessionMacroParameters.shouldAttemptMacroSessionParameters(
                wantsMacroProgram = true,
                superMacroAdbProbe = false,
                sdkInt = Build.VERSION_CODES.S,
                camId = "2",
                ultraWideCameraId = "2",
            ),
        )
    }
}

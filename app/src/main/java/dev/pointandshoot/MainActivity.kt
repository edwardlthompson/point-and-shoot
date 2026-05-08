package dev.pointandshoot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We handle all system insets ourselves (status/nav bars + cutout) in Compose.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val launchScreen = intent?.getStringExtra(EXTRA_PNS_SCREEN)
        val autoSweep = intent?.getBooleanExtra(EXTRA_PNS_AUTOSWEEP, false) ?: false
        val autoEnc = intent?.getBooleanExtra(EXTRA_PNS_AUTOENC, false) ?: false
        val autoDeepCaps = intent?.getBooleanExtra(EXTRA_PNS_AUTODEEPCAPS, false) ?: false
        val autoSessionMatrix = intent?.getBooleanExtra(EXTRA_PNS_AUTOSESSIONMATRIX, false) ?: false
        val autoHdrDcgRuntime = intent?.getBooleanExtra(EXTRA_PNS_AUTOHDRDCG, false) ?: false
        val autoCaptureLatency = intent?.getBooleanExtra(EXTRA_PNS_AUTOCAPTURELATENCY, false) ?: false
        val autoRawHdrExcl = intent?.getBooleanExtra(EXTRA_PNS_AUTORAWHDREXCL, false) ?: false
        val autoBurst = intent?.getBooleanExtra(EXTRA_PNS_AUTOBURST, false) ?: false
        val autoLogicalPhysical = intent?.getBooleanExtra(EXTRA_PNS_AUTOLOGICALPHYSICAL, false) ?: false
        val autoExhaustive = intent?.getBooleanExtra(EXTRA_PNS_AUTOEXHAUSTIVE, false) ?: false
        val includeLogical = intent?.getBooleanExtra(EXTRA_PNS_INCLUDE_LOGICAL, false) ?: false
        val exhaustiveHfrOnly = intent?.getBooleanExtra(EXTRA_PNS_EXHAUSTIVE_HFR_ONLY, false) ?: false
        val autoLegacy = intent?.getBooleanExtra(EXTRA_PNS_AUTOLEGACY, false) ?: false

        setContent {
            MaterialTheme {
                Surface {
                    CameraCapabilitiesProbe(
                        launchScreen = launchScreen,
                        autoSweep = autoSweep,
                        autoEncProbe = autoEnc,
                        autoDeepCaps = autoDeepCaps,
                        autoSessionMatrix = autoSessionMatrix,
                        autoHdrDcgRuntime = autoHdrDcgRuntime,
                        autoCaptureLatency = autoCaptureLatency,
                        autoRawHdrExclusivity = autoRawHdrExcl,
                        autoBurstProbe = autoBurst,
                        autoLogicalPhysical = autoLogicalPhysical,
                        autoExhaustive = autoExhaustive,
                        exhaustiveIncludeLogical = includeLogical,
                        exhaustiveHfrOnly = exhaustiveHfrOnly,
                        autoLegacyCamera1 = autoLegacy,
                    )
                }
            }
        }
    }
}

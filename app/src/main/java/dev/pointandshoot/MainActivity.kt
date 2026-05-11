package dev.pointandshoot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind the status bar + nav bar; pair with hideSystemBarsForImmersive() so the
        // finder uses the full display (swipe from edge reveals transient system bars).
        enableEdgeToEdge()

        // Cache the debug/diagnostics policy once so PnsLog.v / .d are no-ops in release.
        // Diagnostics dumps still go through Log.i directly, so the diagnostic dump path
        // remains unaffected when verbose is muted in release.
        PnsLog.init(applicationContext)

        // We handle merged cutout + gesture insets in Compose via rememberSystemInsetsDp.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBarsForImmersive()

        val launchScreen = resolveLaunchScreenForMain(intent)
        val imageCaptureReturn = resolveImageCaptureReturn()
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
            PnsTheme {
                Surface {
                    CameraCapabilitiesProbe(
                        launchScreen = launchScreen,
                        imageCaptureReturn = imageCaptureReturn,
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsForImmersive()
        }
    }

    /**
     * [EXTRA_PNS_SCREEN] wins when set (ADB / in-app navigation).
     * System camera intents ([MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA], etc.) map to the preview finder
     * so the app appears in quick-launch / default-camera style pickers.
     */
    private fun resolveLaunchScreenForMain(intent: Intent?): String? {
        val fromExtra = intent?.getStringExtra(EXTRA_PNS_SCREEN)
        if (!fromExtra.isNullOrEmpty()) return fromExtra
        return when (intent?.action) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            MediaStore.INTENT_ACTION_VIDEO_CAMERA,
            MediaStore.ACTION_IMAGE_CAPTURE_SECURE,
            MediaStore.ACTION_IMAGE_CAPTURE,
            -> PNS_SCREEN_PREVIEW
            else -> null
        }
    }

    private fun resolveImageCaptureReturn(): ImageCaptureReturnContract? {
        val inz = intent ?: return null
        if (inz.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
        return ImageCaptureReturnContract(
            host = this,
            callerOutputUri =
                IntentCompat.getParcelableExtra(inz, MediaStore.EXTRA_OUTPUT, Uri::class.java),
        )
    }

    private fun hideSystemBarsForImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/**
 * Activity was started with [MediaStore.ACTION_IMAGE_CAPTURE]; after a still capture, deliver a JPEG
 * to the caller ([MediaStore.EXTRA_OUTPUT] or thumbnail extra `data`) and [android.app.Activity.finish].
 */
class ImageCaptureReturnContract(
    val host: ComponentActivity,
    /** When non-null, caller expects the JPEG bytes written to this URI; otherwise a small bitmap is returned in `data`. */
    val callerOutputUri: Uri?,
)

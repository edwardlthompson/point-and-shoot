package dev.pointandshoot

import android.app.Activity
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Ref-counted save/restore for [NotificationManager.setInterruptionFilter] so foreground preview DND
 * and recording DND can nest without fighting over the saved filter.
 */
internal object InterruptionFilterHold {
    private var savedFilter: Int? = null
    private var refCount = 0

    fun acquire(nm: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (!nm.isNotificationPolicyAccessGranted) return false
        if (refCount++ == 0) {
            savedFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        }
        return true
    }

    fun release(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (refCount <= 0) return
        refCount--
        val restore = savedFilter
        if (refCount == 0 && restore != null && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(restore)
            savedFilter = null
        }
    }
}

@Composable
fun PreviewMaxBrightnessEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (!enabled || window == null) {
            return@DisposableEffect onDispose { }
        }
        val attrs = window.attributes
        val previousBrightness = attrs.screenBrightness
        attrs.screenBrightness = 1f
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            attrs.screenBrightness = previousBrightness
            window.attributes = attrs
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * Applies a total silence interruption filter while recording when the user has enabled the option
 * and granted [android.permission.ACCESS_NOTIFICATION_POLICY].
 */
@Composable
fun RecordingDndEffect(
    optionEnabled: Boolean,
    isRecording: Boolean,
) {
    val context = LocalContext.current
    DisposableEffect(optionEnabled, isRecording) {
        if (!optionEnabled || !isRecording || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return@DisposableEffect onDispose { }
        }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return@DisposableEffect onDispose { }
        InterruptionFilterHold.acquire(nm)
        onDispose {
            InterruptionFilterHold.release(nm)
        }
    }
}

/**
 * Applies total-silence interruption filter while the preview screen is composed when enabled and
 * policy access is granted; logs [PNS.ChromeUx] for scripted gates.
 */
@Composable
fun PreviewForegroundDndEffect(optionEnabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(optionEnabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.i("PNS.ChromeUx", "dndPreview=skipped_api")
            return@DisposableEffect onDispose { }
        }
        if (!optionEnabled) {
            Log.i("PNS.ChromeUx", "dndPreview=skipped_disabled")
            return@DisposableEffect onDispose { }
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm == null) {
            Log.w("PNS.ChromeUx", "dndPreview=skipped_no_service")
            return@DisposableEffect onDispose { }
        }
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.i("PNS.ChromeUx", "dndPreview=skipped_no_policy")
            return@DisposableEffect onDispose { }
        }
        InterruptionFilterHold.acquire(nm)
        Log.i("PNS.ChromeUx", "dndPreview=applied")
        onDispose {
            InterruptionFilterHold.release(nm)
        }
    }
}

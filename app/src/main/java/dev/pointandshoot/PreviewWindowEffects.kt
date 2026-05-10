package dev.pointandshoot

import android.app.Activity
import android.app.NotificationManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

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
        if (!nm.isNotificationPolicyAccessGranted) {
            return@DisposableEffect onDispose { }
        }
        val saved = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        onDispose {
            if (nm.isNotificationPolicyAccessGranted) {
                nm.setInterruptionFilter(saved)
            }
        }
    }
}

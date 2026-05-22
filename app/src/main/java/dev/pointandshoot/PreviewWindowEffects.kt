package dev.pointandshoot

import android.app.Activity
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Ref-counted save/restore for [NotificationManager.setInterruptionFilter] so foreground preview DND
 * and recording DND can nest without fighting over the saved filter.
 */
internal object InterruptionFilterHold {
    private var savedFilter: Int? = null
    private var refCount = 0
    private var previewRefCount = 0
    private var recordingRefCount = 0

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
        restoreIfIdle(nm)
    }

    private fun restoreIfIdle(nm: NotificationManager) {
        val restore = savedFilter
        if (refCount == 0 && restore != null && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(restore)
            savedFilter = null
        }
    }

    /** Preview-only acquire — pairs with [releasePreviewHold]. */
    fun acquirePreview(nm: NotificationManager): Boolean {
        if (previewRefCount > 0) return true
        val ok = acquire(nm)
        if (ok) previewRefCount = 1
        return ok
    }

    fun acquireRecording(nm: NotificationManager): Boolean {
        if (recordingRefCount > 0) return true
        val ok = acquire(nm)
        if (ok) recordingRefCount = 1
        return ok
    }

    fun releaseRecording(nm: NotificationManager) {
        if (recordingRefCount <= 0) return
        recordingRefCount--
        release(nm)
    }

    /**
     * Releases one preview hold. Returns true when the system filter was restored (no holds left).
     */
    fun releasePreviewHold(nm: NotificationManager): Boolean {
        if (previewRefCount <= 0) return false
        previewRefCount--
        release(nm)
        val restored = refCount == 0 && savedFilter == null
        if (restored) {
            Log.i("PNS.ChromeUx", "dndHold=restored filter savedFilterCleared ref=0")
        } else {
            Log.i(
                "PNS.ChromeUx",
                "dndHold=previewReleased refCount=$refCount previewRef=$previewRefCount recordingRef=$recordingRefCount",
            )
        }
        return restored
    }

    /** User turned off preview DND — release every preview ref (handles desync / missed dispose). */
    fun releaseAllPreviewHolds(nm: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        if (previewRefCount <= 0) return false
        var restored = false
        while (previewRefCount > 0) {
            if (releasePreviewHold(nm)) restored = true
        }
        return restored
    }

    @VisibleForTesting
    internal fun resetForTests() {
        refCount = 0
        previewRefCount = 0
        recordingRefCount = 0
        savedFilter = null
    }

    @VisibleForTesting
    internal fun refCountForTests(): Int = refCount

    @VisibleForTesting
    internal fun previewRefCountForTests(): Int = previewRefCount

    @VisibleForTesting
    internal fun recordingRefCountForTests(): Int = recordingRefCount
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
        InterruptionFilterHold.acquireRecording(nm)
        onDispose {
            InterruptionFilterHold.releaseRecording(nm)
        }
    }
}

/**
 * Applies total-silence interruption filter while the preview screen is composed when enabled and
 * policy access is granted; restores on toggle-off, composition dispose, and [Lifecycle.Event.ON_STOP].
 */
@Composable
fun PreviewForegroundDndEffect(optionEnabled: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val optionEnabledState = rememberUpdatedState(optionEnabled)
    DisposableEffect(optionEnabled, lifecycleOwner) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.i("PNS.ChromeUx", "dndPreview=skipped_api")
            return@DisposableEffect onDispose { }
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm == null) {
            Log.w("PNS.ChromeUx", "dndPreview=skipped_no_service")
            return@DisposableEffect onDispose { }
        }

        fun restore(token: String) {
            if (InterruptionFilterHold.releasePreviewHold(nm)) {
                Log.i("PNS.ChromeUx", "dndPreview=$token")
            }
        }

        fun apply(): Boolean {
            if (!optionEnabled) return false
            if (!nm.isNotificationPolicyAccessGranted) {
                Log.i("PNS.ChromeUx", "dndPreview=skipped_no_policy")
                return false
            }
            if (InterruptionFilterHold.acquirePreview(nm)) {
                Log.i("PNS.ChromeUx", "dndPreview=applied")
                return true
            }
            return false
        }

        if (!optionEnabled) {
            if (InterruptionFilterHold.releaseAllPreviewHolds(nm)) {
                Log.i("PNS.ChromeUx", "dndPreview=disabled_restored")
            } else {
                Log.i("PNS.ChromeUx", "dndPreview=skipped_disabled")
            }
            return@DisposableEffect onDispose { }
        }

        apply()

        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> restore("restored")
                    Lifecycle.Event.ON_START -> if (optionEnabledState.value) apply()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val token =
                if (!optionEnabledState.value) {
                    "disabled_restored"
                } else {
                    "restored"
                }
            if (InterruptionFilterHold.releaseAllPreviewHolds(nm)) {
                Log.i("PNS.ChromeUx", "dndPreview=$token")
            }
        }
    }
}

/** Call when persisting [PreviewChromePreferences.dndWhileInPreview] = false (toggle / settings). */
internal fun restoreSystemInterruptionFilterAfterPreviewDndDisabled(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    if (InterruptionFilterHold.releaseAllPreviewHolds(nm)) {
        Log.i("PNS.ChromeUx", "dndPreview=disabled_restored immediate")
    }
}

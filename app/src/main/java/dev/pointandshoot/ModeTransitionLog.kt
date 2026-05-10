package dev.pointandshoot

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicInteger

/**
 * BUILD_PLAN Milestone 5 Sprint 5.2: deterministic, grep-friendly logs for operator-facing mode
 * changes (camera / fps / dial / …) and preview pipeline restarts — supports scripted ADB review.
 */
object ModeTransitionLog {
    const val TAG = "PNS.ModeTransition"

    private val seq = AtomicInteger(0)

    /** UI or effect-driven field change (`from` is `unset` on first observation). */
    fun transition(kind: String, from: String, to: String, reason: String) {
        val n = seq.incrementAndGet()
        Log.i(TAG, "seq=$n kind=$kind from=$from to=$to reason=$reason")
    }

    /** Camera session teardown + reopen from [PreviewController.maybeRestartBody]. */
    fun previewPipelineRestart(
        cameraId: String,
        fps: Int,
        focalCrop: String?,
        commandDial: String,
    ) {
        val n = seq.incrementAndGet()
        Log.i(
            TAG,
            "seq=$n kind=preview_pipeline_restart cameraId=$cameraId fps=$fps focalCrop=${focalCrop ?: "null"} commandDial=$commandDial",
        )
    }
}

/**
 * Logs [ModeTransitionLog.transition] once whenever [value] changes (including from [unset]).
 */
@Composable
fun TrackModeTransition(kind: String, value: String, reason: String = "state") {
    val prev = remember(kind) { mutableStateOf<String?>(null) }
    SideEffect {
        val p = prev.value
        if (p != value) {
            ModeTransitionLog.transition(kind, p ?: "unset", value, reason)
            prev.value = value
        }
    }
}

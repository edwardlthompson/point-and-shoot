package dev.pointandshoot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/** Sprint **15.36** — waypoint rack focus interpolation (M-dial manual distance). */
object RackFocusPull {
    const val TAG = "PNS.RackFocus"

    val DURATION_MS_CHOICES: List<Int> = listOf(500, 1000, 2000, 3000)
    const val DEFAULT_DURATION_MS = 1000
    const val TICK_HZ = 30

    fun coerceDurationMs(ms: Int): Int =
        DURATION_MS_CHOICES.minByOrNull { kotlin.math.abs(it - ms) } ?: DEFAULT_DURATION_MS

    fun interpolateDiopters(from: Float, to: Float, progress: Float): Float =
        from + (to - from) * progress.coerceIn(0f, 1f)

    fun stepCount(durationMs: Int, hz: Int = TICK_HZ): Int =
        ((durationMs.coerceAtLeast(1) * hz) / 1000).coerceAtLeast(1)

    fun canRack(
        commandDialMode: CommandDialMode,
        previewFocusSelection: PreviewFocusSelection,
    ): Boolean =
        commandDialMode == CommandDialMode.M ||
            previewFocusSelection == PreviewFocusSelection.ManualDistance

    fun rackReady(near: Float?, far: Float?): Boolean = near != null && far != null

    suspend fun run(
        context: Context,
        from: Float,
        to: Float,
        durationMs: Int,
        onDiopters: (Float) -> Unit,
    ) {
        val duration = coerceDurationMs(durationMs)
        Log.i(TAG, "rackFocus from=$from to=$to durationMs=$duration")
        PnsAdbLog.i(context, "rackFocus from=$from to=$to durationMs=$duration")
        val steps = stepCount(duration)
        val delayMs = 1000L / TICK_HZ
        for (i in 0..steps) {
            if (!coroutineContext.isActive) {
                Log.i(TAG, "rackFocus aborted step=$i/$steps")
                PnsAdbLog.i(context, "rackFocus aborted step=$i/$steps")
                return
            }
            val progress = i.toFloat() / steps.toFloat()
            onDiopters(interpolateDiopters(from, to, progress))
            if (i < steps) delay(delayMs)
        }
        Log.i(TAG, "rackFocus done")
        PnsAdbLog.i(context, "rackFocus done")
    }
}

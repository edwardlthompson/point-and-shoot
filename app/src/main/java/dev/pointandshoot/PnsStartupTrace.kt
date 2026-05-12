package dev.pointandshoot

import android.hardware.camera2.CaptureResult
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cold-start instrumentation for [PERFORMANCE_BUDGETS.md] ("ready to shoot" row).
 *
 * [recordApplicationOnCreate] runs from [PnsApplication]. [maybeMarkFirstFrameReadyFromPreview]
 * runs from the preview [CaptureResult] path once AE/AF and sensor readout look stable.
 */
object PnsStartupTrace {
    const val MARKER: String = "pns.firstFrameReady"
    private const val TAG: String = "PNS.PerfStartup"

    @Volatile
    var appOnCreateElapsedRealtimeMs: Long = 0L
        internal set

    private val firstFrameReadyDone = AtomicBoolean(false)

    fun recordApplicationOnCreate() {
        if (appOnCreateElapsedRealtimeMs != 0L) return
        appOnCreateElapsedRealtimeMs = SystemClock.elapsedRealtime()
    }

    /**
     * One-shot: when preview repeating results show stable FPS, sensor readout, and AE/AF in a
     * non-searching state, emit a systrace slice + log line for scripted capture / Perfetto.
     */
    fun maybeMarkFirstFrameReadyFromPreview(
        result: CaptureResult,
        smoothedFps: Double,
    ) {
        if (firstFrameReadyDone.get()) return
        if (appOnCreateElapsedRealtimeMs == 0L) return
        if (smoothedFps < 8.0) return
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        if (iso == null && exp == null) return
        val ae = result.get(CaptureResult.CONTROL_AE_STATE)
        val aeOk =
            ae == null ||
                ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                ae == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                ae == CaptureResult.CONTROL_AE_STATE_INACTIVE
        if (!aeOk) return
        val af = result.get(CaptureResult.CONTROL_AF_STATE)
        val afOk =
            af == null ||
                af == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED ||
                af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
        if (!afOk) return
        if (!firstFrameReadyDone.compareAndSet(false, true)) return

        val now = SystemClock.elapsedRealtime()
        val sinceAppMs = now - appOnCreateElapsedRealtimeMs
        Trace.beginSection(MARKER)
        Trace.endSection()
        Log.i(TAG, "$MARKER elapsedSinceAppOnCreateMs=$sinceAppMs fps=${"%.1f".format(smoothedFps)} ae=$ae af=$af")
    }

    /** Unit tests: reset one-shot + app clock anchor. */
    internal fun resetForTests() {
        firstFrameReadyDone.set(false)
        appOnCreateElapsedRealtimeMs = 0L
    }
}

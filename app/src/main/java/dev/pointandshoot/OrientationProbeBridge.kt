package dev.pointandshoot

import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize

/**
 * Latest orientation/buffer diagnostic snapshot from [PreviewEngineScreen] for
 * [DebugMenuScreen] — avoids floating the probe over the live preview.
 */
object OrientationProbeBridge {
    /** Observed by [DebugMenuScreen]; updates from [PreviewMainViewport] via [update]. */
    val snapshotState: MutableState<OrientationProbeSnapshot> =
        mutableStateOf(OrientationProbeSnapshot.Empty)

    fun update(s: OrientationProbeSnapshot) {
        snapshotState.value = s
    }

    fun clear() {
        snapshotState.value = OrientationProbeSnapshot.Empty
    }
}

@Immutable
data class OrientationProbeSnapshot(
    val bufferSize: Size?,
    val centerViewSize: IntSize,
    val sensorOrientationDeg: Int?,
    val chromeRotationDegSnapped: Float,
    val chromeRotationDegSmooth: Float,
) {
    companion object {
        val Empty =
            OrientationProbeSnapshot(
                bufferSize = null,
                centerViewSize = IntSize.Zero,
                sensorOrientationDeg = null,
                chromeRotationDegSnapped = 0f,
                chromeRotationDegSmooth = 0f,
            )
    }
}

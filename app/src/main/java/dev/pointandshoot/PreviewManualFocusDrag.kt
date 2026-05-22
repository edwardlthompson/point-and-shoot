package dev.pointandshoot

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Horizontal drag on the finder to adjust manual focus distance (M dial / manual AF). */
fun Modifier.previewManualFocusDrag(
    enabled: Boolean,
    onDragPixels: (Float) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        detectHorizontalDragGestures { change, dragAmount ->
            change.consume()
            onDragPixels(dragAmount)
        }
    }
}

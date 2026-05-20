package dev.pointandshoot

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Vertical drag on the finder to adjust manual focus distance (M dial). */
fun Modifier.previewManualFocusDrag(
    enabled: Boolean,
    onDragPixels: (Float) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        detectVerticalDragGestures { change, dragAmount ->
            change.consume()
            onDragPixels(dragAmount)
        }
    }
}

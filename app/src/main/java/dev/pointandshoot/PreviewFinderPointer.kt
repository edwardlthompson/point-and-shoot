package dev.pointandshoot

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Modifier
import kotlin.math.abs

private const val SWIPE_DOMINANCE_RATIO = 1.2f

/**
 * Finder pointer routing: optional **tap-to-shoot** (DOWN/AF lock, UP/fire) plus optional **vertical
 * swipe** for front/rear camera (Milestone **10.4**). Swipe wins when movement is dominantly
 * vertical and exceeds [swipeThresholdPx] before UP; otherwise the tap pipeline runs.
 */
fun Modifier.previewFinderPointer(
    swipeEnabled: Boolean,
    swipeThresholdPx: Float,
    tapToShootEnabled: Boolean,
    tapCallbacks: TapToShootCallbacks,
    onSwipeUpToFront: () -> Unit,
    onSwipeDownToRear: () -> Unit,
    onTapFallbackFocus: () -> Unit,
): Modifier =
    pointerInput(
        swipeEnabled,
        swipeThresholdPx,
        tapToShootEnabled,
        tapCallbacks,
        onSwipeUpToFront,
        onSwipeDownToRear,
        onTapFallbackFocus,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
            val pointerId = down.id
            var total = Offset.Zero
            var swipeFired = false
            if (tapToShootEnabled) {
                tapCallbacks.onDown(down.position)
            }
            var done = false
            while (!done) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val extraPointerDown =
                    event.changes.any { ch ->
                        ch.id != pointerId && ch.pressed
                    }
                if (extraPointerDown) {
                    if (tapToShootEnabled && !swipeFired) {
                        tapCallbacks.onCancel()
                    }
                    done = true
                    continue
                }
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null) {
                    done = true
                    continue
                }
                if (change.changedToUp()) {
                    if (!swipeFired) {
                        when {
                            tapToShootEnabled -> tapCallbacks.onFire()
                            else -> onTapFallbackFocus()
                        }
                    }
                    done = true
                    continue
                }
                val pan = change.positionChange()
                total += pan
                val verticalDominant = abs(total.y) >= abs(total.x) * SWIPE_DOMINANCE_RATIO
                val swipeDistanceOk = abs(total.y) >= swipeThresholdPx
                if (swipeEnabled && !swipeFired && swipeDistanceOk && verticalDominant) {
                    swipeFired = true
                    if (tapToShootEnabled) {
                        tapCallbacks.onCancel()
                    }
                    if (total.y < 0f) {
                        onSwipeUpToFront()
                    } else {
                        onSwipeDownToRear()
                    }
                    change.consume()
                }
            }
        }
    }

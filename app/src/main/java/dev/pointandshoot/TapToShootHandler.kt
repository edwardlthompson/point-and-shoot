package dev.pointandshoot

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Tap-to-shoot gesture per BUILD_PLAN §6 (Phase 3): "lock AF/AE on DOWN,
 * fire on UP".
 *
 *   * `DOWN`: callback receives the touch location so the engine can move
 *     the AF/AE region (and trigger the focus lock).
 *   * `UP` (no cancel): the shutter callback fires.
 *   * `UP` after a cancel (drag-off / multi-touch): the shutter does NOT fire.
 *
 * This is intentionally engine-agnostic. The actual capture logic lives in
 * the Phase 1 capture engine; this handler exposes the lifecycle hooks.
 */
@Stable
interface TapToShootCallbacks {
    /** Down event: lock AF/AE at [downPosition] (preview-relative coordinates). */
    fun onDown(downPosition: Offset)

    /** Up event without cancellation: fire the shutter. */
    fun onFire()

    /** Up event after cancellation (drag-off, second pointer, etc.): release the lock. */
    fun onCancel()
}

/**
 * Attach the tap-to-shoot gesture to a Compose surface (typically the live
 * preview). Use `enabled = false` while the engine is busy (e.g., during
 * bracket sequence) to prevent overlapping captures.
 */
fun Modifier.tapToShoot(
    callbacks: TapToShootCallbacks,
    enabled: Boolean = true,
): Modifier = pointerInput(callbacks, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
        callbacks.onDown(down.position)

        val up = waitForUpOrCancellation(pass = PointerEventPass.Main)
        if (up == null) {
            callbacks.onCancel()
        } else {
            callbacks.onFire()
        }
    }
}

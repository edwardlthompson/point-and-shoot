package dev.pointandshoot.preview.capture

import android.media.Image
import android.media.ImageReader
import android.os.SystemClock

/**
 * Callback-order-safe `ImageReader` fetch helper for still / bracket captures.
 */
fun awaitNextImage(
    reader: ImageReader,
    timeoutMs: Long,
    pollSleepMs: Long = 4L,
): Image? {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs.coerceAtLeast(0L)
    while (true) {
        val image = runCatching { reader.acquireNextImage() }.getOrNull()
        if (image != null) return image
        if (SystemClock.elapsedRealtime() >= deadline) return null
        SystemClock.sleep(pollSleepMs.coerceAtLeast(1L))
    }
}

package dev.pointandshoot

import android.graphics.Bitmap
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sprint **PO.1** — central bitmap recycle + lightweight leak accounting for gallery / tray paths.
 */
object PnsBitmapGuard {
    const val TAG = "PNS.Bitmap"

    private val activeBitmaps = AtomicInteger(0)

    fun onAllocated(source: String, bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        val n = activeBitmaps.incrementAndGet()
        Log.d(TAG, "alloc source=$source active=$n ${bitmap.width}x${bitmap.height} kb=${bitmap.byteCount / 1024}")
    }

    fun safeRecycle(bitmap: Bitmap?, source: String): Boolean {
        if (bitmap == null) return false
        return try {
            if (!bitmap.isRecycled) {
                val kb = bitmap.byteCount / 1024
                bitmap.recycle()
                val n = activeBitmaps.decrementAndGet().coerceAtLeast(0)
                Log.d(TAG, "recycle source=$source active=$n freedKb=$kb")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "recycle failed source=$source", e)
            false
        }
    }

    /** Logs a warning when tracked bitmaps were not recycled before a composable / screen teardown. */
    fun logLeakCheck(component: String) {
        val n = activeBitmaps.get()
        if (n > 0) {
            Log.w(TAG, "leakCheck component=$component activeBitmaps=$n (expected 0)")
        } else {
            Log.d(TAG, "leakCheck component=$component ok activeBitmaps=0")
        }
    }

    fun activeCount(): Int = activeBitmaps.get()
}

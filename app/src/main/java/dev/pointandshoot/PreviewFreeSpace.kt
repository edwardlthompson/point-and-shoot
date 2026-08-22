package dev.pointandshoot

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/**
 * One StatFs policy for DCIM stills/video and APK cache.
 * Fail open when the probe misses so USB gates are not blocked.
 */
object PreviewFreeSpace {
    fun availableBytes(path: File?): Long? {
        if (path == null) return null
        return runCatching {
            val root = if (path.isDirectory) path else path.parentFile
            if (root == null || !root.exists()) return@runCatching null
            StatFs(root.absolutePath).availableBytes
        }.getOrNull()
    }

    @Suppress("UnusedParameter")
    fun availableBytesForDcim(context: Context, adbOverrideBytes: Long? = null): Long? {
        adbOverrideBytes?.let { override ->
            if (override >= 0L) return override
        }
        val root = Environment.getExternalStorageDirectory() ?: return null
        return availableBytes(root)
    }

    fun availableBytesForCache(context: Context): Long? = availableBytes(context.cacheDir)
}

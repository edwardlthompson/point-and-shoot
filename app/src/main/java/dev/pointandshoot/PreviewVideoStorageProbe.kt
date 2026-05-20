package dev.pointandshoot

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Log

/**
 * Free space on primary shared storage where [CaptureStorage] writes DCIM video.
 */
object PreviewVideoStorageProbe {
    private const val TAG = "PNS.StorageRemain"

    fun availableBytesForDcim(context: Context, adbOverrideBytes: Long? = null): Long? {
        adbOverrideBytes?.let { override ->
            if (override >= 0L) {
                Log.i(TAG, "availableBytes override=$override")
                return override
            }
        }
        return readStatFsAvailableBytes()
    }

    private fun readStatFsAvailableBytes(): Long? =
        runCatching {
            val root = Environment.getExternalStorageDirectory() ?: return@runCatching null
            if (!root.exists()) return@runCatching null
            val stat = StatFs(root.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocksLong * stat.blockSizeLong
            }
        }.onFailure { e ->
            Log.w(TAG, "StatFs failed err=${e.message}")
        }.getOrNull()
}

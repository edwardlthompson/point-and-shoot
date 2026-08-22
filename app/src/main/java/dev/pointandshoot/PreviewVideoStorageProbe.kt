package dev.pointandshoot

import android.content.Context
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
        return PreviewFreeSpace.availableBytesForDcim(context)
    }
}

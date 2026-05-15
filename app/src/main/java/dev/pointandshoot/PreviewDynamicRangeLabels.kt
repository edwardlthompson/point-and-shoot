package dev.pointandshoot

import android.hardware.camera2.params.DynamicRangeProfiles
import android.os.Build

/** Short labels for [android.hardware.camera2.params.DynamicRangeProfiles] constants (Milestone **10.6** readout). */
object PreviewDynamicRangeLabels {
    fun shortLabel(profile: Long): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return "dr=$profile"
        return when (profile) {
            DynamicRangeProfiles.STANDARD -> "SDR"
            DynamicRangeProfiles.HLG10 -> "HLG10"
            DynamicRangeProfiles.HDR10 -> "HDR10"
            DynamicRangeProfiles.HDR10_PLUS -> "HDR10+"
            else -> "dr=$profile"
        }
    }
}

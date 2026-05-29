package dev.pointandshoot

import android.content.Context

/**
 * Per-tray (photo vs video) readout + HUD slice restored when the user toggles the Photo/Video FAB.
 *
 * Persisted in SharedPreferences so each mode keeps its last ISO/shutter/AWB, FPS, OIS/EIS, and
 * video shutter-angle preset across app restarts.
 */
data class PreviewTrayReadoutSnapshot(
    val manualIso: Int? = null,
    val manualExposureNs: Long? = null,
    val manualAwbMode: Int? = null,
    val aeLocked: Boolean = false,
    val isoBand: ReadoutIsoBand = ReadoutIsoBand.FULL,
)

data class PreviewTrayModeSnapshot(
    val readout: PreviewTrayReadoutSnapshot = PreviewTrayReadoutSnapshot(),
    val targetFps: Int = 90,
    val enableLensOpticalStabilization: Boolean = true,
    val enableVideoStabilizationPreview: Boolean = false,
    val videoShutterAngle: String = VideoShutterAngle.Free.name,
) {
    companion object {
        fun defaults(photo: Boolean): PreviewTrayModeSnapshot =
            PreviewTrayModeSnapshot(
                targetFps = if (photo) 90 else 60,
            )
    }
}

object PreviewTrayModeStore {
    private const val PREFS = "pns_tray_mode_settings"

    private fun prefix(photo: Boolean) = if (photo) "photo_" else "video_"

    fun load(
        context: Context,
        photo: Boolean,
    ): PreviewTrayModeSnapshot {
        val p = prefix(photo)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("${p}fps")) {
            return PreviewTrayModeSnapshot.defaults(photo)
        }
        val isoBandName =
            prefs.getString("${p}iso_band", ReadoutIsoBand.FULL.name)
                ?: ReadoutIsoBand.FULL.name
        val isoBand =
            runCatching { ReadoutIsoBand.valueOf(isoBandName) }.getOrDefault(ReadoutIsoBand.FULL)
        val awbKey = "${p}awb_mode"
        val awbMode =
            if (prefs.contains(awbKey)) {
                prefs.getInt(awbKey, -1).takeIf { it >= 0 }
            } else {
                null
            }
        return PreviewTrayModeSnapshot(
            readout =
                PreviewTrayReadoutSnapshot(
                    manualIso = prefs.getInt("${p}iso", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
                    manualExposureNs =
                        prefs.getLong("${p}ss_ns", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE },
                    manualAwbMode = awbMode,
                    aeLocked = prefs.getBoolean("${p}ae_lock", false),
                    isoBand = isoBand,
                ),
            targetFps = prefs.getInt("${p}fps", if (photo) 90 else 60),
            enableLensOpticalStabilization = prefs.getBoolean("${p}ois", true),
            enableVideoStabilizationPreview = prefs.getBoolean("${p}eis", false),
            videoShutterAngle =
                prefs.getString("${p}shutter_angle", VideoShutterAngle.Free.name)
                    ?: VideoShutterAngle.Free.name,
        )
    }

    fun save(
        context: Context,
        photo: Boolean,
        snapshot: PreviewTrayModeSnapshot,
    ) {
        val p = prefix(photo)
        val r = snapshot.readout
        prefs(context).edit().apply {
            putInt("${p}fps", snapshot.targetFps)
            putBoolean("${p}ois", snapshot.enableLensOpticalStabilization)
            putBoolean("${p}eis", snapshot.enableVideoStabilizationPreview)
            putString("${p}shutter_angle", snapshot.videoShutterAngle)
            putString("${p}iso_band", r.isoBand.name)
            putBoolean("${p}ae_lock", r.aeLocked)
            if (r.manualIso != null) {
                putInt("${p}iso", r.manualIso)
            } else {
                remove("${p}iso")
            }
            if (r.manualExposureNs != null) {
                putLong("${p}ss_ns", r.manualExposureNs)
            } else {
                remove("${p}ss_ns")
            }
            if (r.manualAwbMode != null) {
                putInt("${p}awb_mode", r.manualAwbMode)
            } else {
                remove("${p}awb_mode")
            }
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

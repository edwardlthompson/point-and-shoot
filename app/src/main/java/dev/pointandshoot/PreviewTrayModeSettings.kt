package dev.pointandshoot

import android.content.Context

/**
 * Per-tray (photo vs video) readout + HUD slice restored when the user toggles the Photo/Video FAB
 * and on cold start for the last [PreviewLastSurface].
 *
 * Persisted in SharedPreferences so each mode keeps its last ISO/shutter/AWB, FPS, OIS/EIS,
 * video shutter-angle preset, prime focal target, and **command dial** across app restarts.
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
    val primeFocalTargetEqMm: Int? = null,
    /** Last Mode-dial program for this tray (Photo H vs Video Dual stay independent). */
    val commandDialMode: CommandDialMode = CommandDialMode.Auto,
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
            return PreviewTrayModeSnapshot.defaults(photo).copy(
                commandDialMode = migrateDialFromLegacyHud(context, photo),
            )
        }
        val isoBandName =
            prefs.getString("${p}iso_band", ReadoutIsoBand.FULL.persistValue())
                ?: ReadoutIsoBand.FULL.persistValue()
        val isoBand = ReadoutIsoBand.parsePersisted(isoBandName)
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
            primeFocalTargetEqMm =
                prefs.getInt("${p}prime_eq_mm", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE },
            commandDialMode = loadDial(prefs, p, context, photo),
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
            putString("${p}dial", snapshot.commandDialMode.name)
            if (snapshot.primeFocalTargetEqMm != null) {
                putInt("${p}prime_eq_mm", snapshot.primeFocalTargetEqMm)
            } else {
                remove("${p}prime_eq_mm")
            }
            putString("${p}iso_band", r.isoBand.persistValue())
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
        // Keep legacy global key in sync with the *active* tray for older readers / backup.
        HudSettings.saveCommandDialMode(context, snapshot.commandDialMode)
    }

    /**
     * Writes only the dial field for one tray without clobbering the other tray's program
     * (e.g. video clamp to Auto must not erase photo **H**).
     */
    fun saveCommandDialMode(
        context: Context,
        photo: Boolean,
        mode: CommandDialMode,
    ) {
        val snap = load(context, photo).copy(commandDialMode = mode)
        save(context, photo, snap)
    }

    /** Pure helper for JVM tests — maps a stored dial name into an allowed mode for [photo]. */
    fun resolveStoredDial(
        storedName: String?,
        photo: Boolean,
        legacyGlobal: CommandDialMode? = null,
    ): CommandDialMode {
        val parsed =
            storedName?.let { name ->
                CommandDialMode.entries.firstOrNull { it.name == name }
            }
        val candidate = parsed ?: legacyGlobal ?: CommandDialMode.Auto
        val family =
            if (photo) CaptureMediaFamily.Photo else CaptureMediaFamily.Video
        val allowed = CaptureMediaFamily.commandDialModesFor(family)
        return if (candidate in allowed) candidate else CommandDialMode.Auto
    }

    private fun loadDial(
        prefs: android.content.SharedPreferences,
        prefix: String,
        context: Context,
        photo: Boolean,
    ): CommandDialMode {
        val stored = prefs.getString("${prefix}dial", null)
        val legacy =
            if (stored == null) {
                HudSettings.loadCommandDialMode(context)
            } else {
                null
            }
        return resolveStoredDial(stored, photo, legacy)
    }

    private fun migrateDialFromLegacyHud(context: Context, photo: Boolean): CommandDialMode =
        resolveStoredDial(null, photo, HudSettings.loadCommandDialMode(context))

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

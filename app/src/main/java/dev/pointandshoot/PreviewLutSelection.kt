package dev.pointandshoot

/**
 * Which [LutCatalog] entry drives the GLES external-OES preview shader for the current mode.
 * Sprint **13V.11**: video-primary preview + recording both use [HudSettings.videoLut].
 */
object PreviewLutSelection {

    fun activeCatalog(
        isRecording: Boolean,
        videoPrimary: Boolean,
        hud: HudSettings,
    ): LutCatalog =
        when {
            isRecording -> hud.videoLut()
            videoPrimary -> hud.videoLut()
            else -> hud.stillsLut()
        }
}

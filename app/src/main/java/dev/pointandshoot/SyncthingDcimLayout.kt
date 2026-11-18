package dev.pointandshoot

/** Dated folder + stable name so Syncthing / desktop sync can archive the roll. */
object SyncthingDcimLayout {
    fun relativePath(item: MediaItem): String {
        val day = GalleryLibrary.dayKey(item.date)
        return "${PnsDcimPaths.FOLDER_NAME}/$day/${item.displayName}"
    }

    fun folderName(epochSec: Long): String = GalleryLibrary.dayKey(epochSec)
}

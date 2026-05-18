package dev.pointandshoot.quicksettings

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.pointandshoot.MainActivity

/**
 * Quick Settings tile for launching Point & Shoot video recording.
 *
 * Users can add this tile to the Quick Settings panel for one-tap video access.
 */
@RequiresApi(Build.VERSION_CODES.N)
class VideoTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            action = android.provider.MediaStore.ACTION_VIDEO_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        startActivityAndCollapse(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Video"
            contentDescription = "Launch Point & Shoot video"
            updateTile()
        }
    }
}

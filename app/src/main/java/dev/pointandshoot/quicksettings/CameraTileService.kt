package dev.pointandshoot.quicksettings

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.pointandshoot.MainActivity

/**
 * Quick Settings tile for launching Point & Shoot camera.
 *
 * Users can add this tile to the Quick Settings panel for one-tap camera access.
 */
@RequiresApi(Build.VERSION_CODES.N)
class CameraTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            action =
                if (isLocked) {
                    android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
                } else {
                    android.provider.MediaStore.ACTION_IMAGE_CAPTURE
                }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        startActivityAndCollapse(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Camera"
            contentDescription = "Launch Point & Shoot camera"
            updateTile()
        }
    }
}

package dev.pointandshoot.quicksettings

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.pointandshoot.MainActivity

/**
 * Quick Settings tile for launching Point & Shoot selfie camera.
 *
 * Users can add this tile to the Quick Settings panel for one-tap selfie access.
 */
@RequiresApi(Build.VERSION_CODES.N)
class SelfieTileService : TileService() {

    override fun onClick() {
        super.onClick()
        
        val intent = Intent(this, MainActivity::class.java).apply {
            action = android.provider.MediaStore.ACTION_IMAGE_CAPTURE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Launch to front camera via extra
            putExtra("pns_preview_camera_id", "1") // Front camera
        }
        
        startActivityAndCollapse(intent)
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Selfie"
            contentDescription = "Launch Point & Shoot selfie camera"
            updateTile()
        }
    }
}

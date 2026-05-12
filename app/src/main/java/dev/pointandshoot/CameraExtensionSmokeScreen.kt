package dev.pointandshoot

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * **`--es pns_screen cameraextsmoke`** (optional **`--es pns_preview_camera_id N`**).
 * When [finishActivityWhenDone] is true (cold-start route), the runner calls [android.app.Activity.finish].
 */
@Composable
fun CameraExtensionSmokeScreen(
    onBack: () -> Unit,
    preferredCameraId: String?,
    finishActivityWhenDone: Boolean,
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "Camera extension session smoke…",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CameraExtensionSessionSmokeRunner.runBlocking(activity, preferredCameraId, finishActivityWhenDone)
        }
        if (!finishActivityWhenDone) {
            onBack()
        }
    }
}

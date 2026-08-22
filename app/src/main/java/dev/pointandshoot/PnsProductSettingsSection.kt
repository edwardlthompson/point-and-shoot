@file:Suppress("FunctionNaming", "MagicNumber")

package dev.pointandshoot

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PnsProductSettingsSection() {
    val context = LocalContext.current
    var geotag by remember { mutableStateOf(PnsProductPrefs.geotagMode(context)) }
    var recipe by remember { mutableStateOf(PnsProductPrefs.recipe(context)) }
    var wear by remember { mutableStateOf(PnsProductPrefs.wearRemoteEnabled(context)) }
    var hdmi by remember { mutableStateOf(PnsProductPrefs.hdmiOutEnabled(context)) }
    var mjpeg by remember { mutableStateOf(PnsProductPrefs.mjpegWebcamEnabled(context)) }
    var ramp by remember { mutableStateOf(PnsProductPrefs.rampEnabled(context)) }
    var trip by remember { mutableStateOf(PnsProductPrefs.tripArmed(context)) }
    var airplane by remember { mutableStateOf(PnsProductPrefs.airplaneSafe(context)) }
    val blePerms =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val ok = grants.values.all { it }
            if (ok) {
                PnsProductPrefs.setWearRemoteEnabled(context, true)
                wear = true
                PnsWearBleServer.start(context)
            } else {
                Toast.makeText(context, "Bluetooth permission needed for Wear remote", Toast.LENGTH_LONG).show()
            }
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PreviewRailSectionTitle("Product systems")
        Text("Geotag", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PnsGeotagMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = {
                        geotag = mode
                        PnsProductPrefs.setGeotagMode(context, mode)
                    },
                ) {
                    Text(mode.label, color = if (geotag == mode) PnsColors.PhotoOrange else Color.White)
                }
            }
        }
        Text("Capture recipe", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        PnsProductPrefs.CaptureRecipe.entries.forEach { item ->
            OutlinedButton(
                onClick = {
                    recipe = item
                    PnsProductPrefs.setRecipe(context, item)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(item.label, color = if (recipe == item) PnsColors.PhotoOrange else Color.White)
            }
        }
        ToggleLine("Wear remote (BLE + LAN)", wear) { on ->
            if (on && Build.VERSION.SDK_INT >= 31) {
                blePerms.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_SCAN,
                    ),
                )
            } else {
                PnsProductPrefs.setWearRemoteEnabled(context, on)
                wear = on
                if (on) PnsWearBleServer.start(context) else PnsWearBleServer.stop()
            }
        }
        ToggleLine("HDMI clean feed", hdmi) { on ->
            hdmi = on
            PnsProductPrefs.setHdmiOutEnabled(context, on)
        }
        ToggleLine("MJPEG / USB webcam :${PnsExternalOutput.MJPEG_PORT}", mjpeg) { on ->
            mjpeg = on
            PnsProductPrefs.setMjpegWebcamEnabled(context, on)
            if (on) PnsMjpegStreamServer.start(context) else PnsMjpegStreamServer.stop()
        }
        OutlinedButton(
            onClick = { PnsUsbWebcam.openHostUsbSettings(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("USB Webcam settings (Windows inbox usbvideo.sys)", color = Color.White)
        }
        ToggleLine("Intervalometer ISO ramp", ramp) { on ->
            ramp = on
            PnsProductPrefs.setRamp(context, on, PnsProductPrefs.rampIsoStart(context), PnsProductPrefs.rampIsoEnd(context))
        }
        ToggleLine("Motion trip", trip) { on ->
            trip = on
            PnsProductPrefs.setTripArmed(context, on)
        }
        ToggleLine("Airplane-safe record", airplane) { on ->
            airplane = on
            PnsProductPrefs.setAirplaneSafe(context, on)
        }
        Text(
            "${PnsExternalOutput.statusLine()} · ${PnsUsbWebcam.statusLine()} · " +
                "Wear BLE: ${if (PnsWearBleServer.isAdvertising()) "advertising" else "off"}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
        Text(
            "Install wear-debug.apk on the watch. Phone LAN POST /remote?action=shutter",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
        )
    }
}

@Composable
private fun ToggleLine(title: String, on: Boolean, change: (Boolean) -> Unit) {
    OutlinedButton(onClick = { change(!on) }, modifier = Modifier.fillMaxWidth()) {
        Text("$title · ${if (on) "on" else "off"}", color = if (on) PnsColors.PhotoOrange else Color.White)
    }
}

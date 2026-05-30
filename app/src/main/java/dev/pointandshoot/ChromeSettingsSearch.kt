package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Nav target for the in-preview Settings dialog ([PreviewEngineScreen] `settingsSubPage`). */
data class ChromeSettingSearchHit(
    val title: String,
    val subtitle: String,
    val keywords: String,
    val subPage: String,
    /** Stable id for scroll + highlight flash (Milestone **17.4**). */
    val settingKey: String? = null,
)

fun buildChromeSettingsSearchIndex(): List<ChromeSettingSearchHit> =
    listOf(
        ChromeSettingSearchHit(
            "Shutter mode (single / timer / burst)",
            "Capture & stills",
            "shutter timer burst self single delay countdown",
            "capture",
            settingKey = "capture.shutter_mode",
        ),
        ChromeSettingSearchHit(
            "Self-timer delay",
            "Capture & stills",
            "timer countdown 3 5 10 seconds delay",
            "capture",
            settingKey = "capture.timer",
        ),
        ChromeSettingSearchHit(
            "Burst mode",
            "Capture & stills",
            "burst sequence multiple shots rapid",
            "capture",
            settingKey = "capture.burst",
        ),
        ChromeSettingSearchHit(
            "Shutter sound",
            "Capture & stills",
            "sound audio click mechanical silent haptic",
            "capture",
            settingKey = "capture.shutter_sound",
        ),
        ChromeSettingSearchHit(
            "Flash mode",
            "Capture & stills",
            "flash torch auto off rear",
            "capture",
            settingKey = "capture.flash",
        ),
        ChromeSettingSearchHit(
            "Save location / geotag",
            "Capture & stills",
            "gps geotag location embed exif dng jpeg",
            "capture",
            settingKey = "capture.geotag",
        ),
        ChromeSettingSearchHit(
            "On-screen shutter button",
            "Preview & behavior",
            "shutter tray button capture",
            "preview",
            settingKey = "preview.on_screen_shutter",
        ),
        ChromeSettingSearchHit(
            "Tap preview to capture",
            "Preview & behavior",
            "tap shoot touch finder",
            "preview",
            settingKey = "preview.tap_capture",
        ),
        ChromeSettingSearchHit(
            "Volume keys shutter",
            "Preview & behavior",
            "volume hardware button capture",
            "preview",
            settingKey = "preview.volume_shutter",
        ),
        ChromeSettingSearchHit(
            "Bluetooth remote shutter",
            "Preview & behavior",
            "bluetooth avrcp headset remote",
            "preview",
            settingKey = "preview.bluetooth_shutter",
        ),
        ChromeSettingSearchHit(
            "Max brightness in preview",
            "Preview & behavior",
            "brightness screen bright max",
            "preview",
            settingKey = "preview.max_brightness",
        ),
        ChromeSettingSearchHit(
            "Do Not Disturb (preview)",
            "Preview & behavior",
            "dnd silence notifications preview",
            "preview",
            settingKey = "preview.dnd_preview",
        ),
        ChromeSettingSearchHit(
            "Do Not Disturb (recording)",
            "Preview & behavior",
            "dnd silence notifications video record",
            "preview",
            settingKey = "preview.dnd_recording",
        ),
        ChromeSettingSearchHit(
            "Focus peaking",
            "Preview & behavior",
            "peaking false color edges focus manual",
            "preview",
            settingKey = "preview.focus_peaking",
        ),
        ChromeSettingSearchHit(
            "Focus mode",
            "Preview & behavior",
            "autofocus manual caf af rack",
            "preview",
            settingKey = "preview.focus_mode",
        ),
        ChromeSettingSearchHit(
            "Histogram overlay",
            "HUD & readouts",
            "histogram rgb luma overlay",
            "hud",
            settingKey = "hud.histogram",
        ),
        ChromeSettingSearchHit(
            "Horizon level",
            "HUD & readouts",
            "horizon level accelerometer tilt",
            "hud",
            settingKey = "hud.horizon",
        ),
        ChromeSettingSearchHit(
            "Eye-AF overlay",
            "HUD & readouts",
            "eye af face detect overlay pupil",
            "hud",
            settingKey = "hud.eye_af",
        ),
        ChromeSettingSearchHit(
            "Video tally",
            "HUD & readouts",
            "video tally recording red dot pip",
            "hud",
            settingKey = "hud.video_tally",
        ),
        ChromeSettingSearchHit(
            "Optical stabilization (OIS)",
            "Video & stabilization",
            "ois lens optical stabilization",
            "video",
            settingKey = "video.ois",
        ),
        ChromeSettingSearchHit(
            "Electronic stabilization (EIS)",
            "Video & stabilization",
            "eis electronic stabilization preview video",
            "video",
            settingKey = "video.eis",
        ),
        ChromeSettingSearchHit(
            "Video shutter angle",
            "Video & stabilization",
            "shutter angle 180 90 video iso chase",
            "video",
            settingKey = "video.shutter_angle",
        ),
        ChromeSettingSearchHit(
            "ISO band (readout)",
            "HUD & readouts",
            "iso band readout strip exposure manual",
            "hud",
            settingKey = "hud.iso_shutter",
        ),
        ChromeSettingSearchHit(
            "Crop guide",
            "Guides & framing",
            "crop guide aspect ratio framing",
            "guides",
            settingKey = "guides.crop",
        ),
        ChromeSettingSearchHit(
            "Framing grid",
            "Guides & framing",
            "grid thirds composition guide",
            "guides",
            settingKey = "guides.grid",
        ),
        ChromeSettingSearchHit(
            "Target frame rate",
            "Video",
            "fps frame rate target hfr",
            "fps",
            settingKey = "fps.target",
        ),
        ChromeSettingSearchHit(
            "Quick settings (all toggles)",
            "Quick settings",
            "quick qs toggle histogram dnd flash geotag",
            "quick",
            settingKey = "quick.all",
        ),
    )

@Composable
fun ChromeSettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text("Search settings…", color = Color.White.copy(alpha = 0.45f))
        },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* filter only */ }),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = PnsColors.PhotoOrange,
                focusedBorderColor = PnsColors.PhotoOrange.copy(alpha = 0.85f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.28f),
            ),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
fun ChromeSettingsSearchResults(
    query: String,
    onPick: (ChromeSettingSearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return
    val hits =
        remember(q) {
            buildChromeSettingsSearchIndex().filter { hit ->
                hit.title.lowercase().contains(q) ||
                    hit.subtitle.lowercase().contains(q) ||
                    hit.keywords.contains(q) ||
                    hit.keywords.split(' ').any { token -> token.startsWith(q) || q.startsWith(token) }
            }
        }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "${hits.size} result${if (hits.size == 1) "" else "s"}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.55f),
        )
        if (hits.isEmpty()) {
            Text(
                "No settings match \"$query\".",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else {
            hits.forEach { hit ->
                val shape = RoundedCornerShape(10.dp)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onPick(hit) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(hit.title, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            hit.subtitle,
                            color = Color.White.copy(alpha = 0.55f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

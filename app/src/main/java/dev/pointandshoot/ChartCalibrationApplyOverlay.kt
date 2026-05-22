package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * On-finder controls for chart calibration. Always exposes **Exit** so the mode
 * is not stuck on (overlay persists in [PreviewChromePreferences] until cleared).
 */
@Composable
fun ChartCalibrationApplyOverlay(
    modifier: Modifier = Modifier,
    overlayEnabled: Boolean,
    cornerCount: Int,
    onApply: () -> Unit,
    onAutoDetectCorners: () -> Unit,
    onExitCalibration: () -> Unit,
) {
    if (!overlayEnabled) return
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Chart calibration",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                TextButton(onClick = onExitCalibration) {
                    Text(
                        text = "Exit",
                        color = PnsColors.PhotoOrange,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Text(
                text = "Tap Exit, system Back, or turn off the overlay in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        val ready = cornerCount >= 4
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!ready) {
                Text(
                    text =
                        when (cornerCount) {
                            0 -> "Auto-detect or tap corners: TL → TR → BR → BL"
                            1 -> "Auto-detect or tap 3 more corners"
                            2 -> "Auto-detect or tap 2 more corners"
                            3 -> "Auto-detect or tap bottom-left"
                            else -> "Auto-detect or tap chart corners"
                        },
                    modifier =
                        Modifier
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAutoDetectCorners,
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Auto-detect")
                }
                if (ready) {
                    Button(
                        onClick = onApply,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = PnsColors.PhotoOrange,
                                contentColor = Color.Black,
                            ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Apply")
                    }
                }
                OutlinedButton(
                    onClick = onExitCalibration,
                    colors =
                        ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Text("Exit")
                }
            }
        }
    }
}

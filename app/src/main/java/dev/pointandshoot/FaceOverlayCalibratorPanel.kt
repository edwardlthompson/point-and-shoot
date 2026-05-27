package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Engineering D-pad for [FaceOverlayCalibration] on the live preview (eye-AF alignment).
 */
@Composable
fun FaceOverlayCalibratorPanel(
    calibration: FaceOverlayCalibration,
    onCalibrationChange: (FaceOverlayCalibration) -> Unit,
    onDone: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.Black.copy(alpha = 0.82f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Eye overlay calibration",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = "Align green marks with your eyes. Position: D-pad. Spread: ±. Box size: ⊕/⊖.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = calibration.toDiagString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF66FFCC),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CalIconButton(
                        onClick = {
                            onCalibrationChange(
                                calibration.copy(
                                    offsetViewY = calibration.offsetViewY - FaceOverlayCalibration.VIEW_NUDGE_STEP_PX,
                                ).clamped(),
                            )
                        },
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalIconButton(
                            onClick = {
                                onCalibrationChange(
                                    calibration.copy(
                                        offsetViewX = calibration.offsetViewX - FaceOverlayCalibration.VIEW_NUDGE_STEP_PX,
                                    ).clamped(),
                                )
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                        Box(modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)))
                        CalIconButton(
                            onClick = {
                                onCalibrationChange(
                                    calibration.copy(
                                        offsetViewX = calibration.offsetViewX + FaceOverlayCalibration.VIEW_NUDGE_STEP_PX,
                                    ).clamped(),
                                )
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.White,
                            )
                        }
                    }
                    CalIconButton(
                        onClick = {
                            onCalibrationChange(
                                calibration.copy(
                                    offsetViewY = calibration.offsetViewY + FaceOverlayCalibration.VIEW_NUDGE_STEP_PX,
                                ).clamped(),
                            )
                        },
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScaleRow(
                    label = "Spread",
                    onMinus = {
                        onCalibrationChange(
                            calibration.copy(
                                positionScale = calibration.positionScale - FaceOverlayCalibration.POSITION_SCALE_STEP,
                            ).clamped(),
                        )
                    },
                    onPlus = {
                        onCalibrationChange(
                            calibration.copy(
                                positionScale = calibration.positionScale + FaceOverlayCalibration.POSITION_SCALE_STEP,
                            ).clamped(),
                        )
                    },
                )
                ScaleRow(
                    label = "Box",
                    onMinus = {
                        onCalibrationChange(
                            calibration.copy(
                                markerSizeScale = calibration.markerSizeScale - FaceOverlayCalibration.MARKER_SIZE_SCALE_STEP,
                            ).clamped(),
                        )
                    },
                    onPlus = {
                        onCalibrationChange(
                            calibration.copy(
                                markerSizeScale = calibration.markerSizeScale + FaceOverlayCalibration.MARKER_SIZE_SCALE_STEP,
                            ).clamped(),
                        )
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Text("Reset", color = Color.White)
                }
                TextButton(onClick = onDone) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF66FFCC))
                    Text("Done", color = Color(0xFF66FFCC))
                }
            }
        }
    }
}

@Composable
private fun ScaleRow(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalIconButton(onClick = onMinus) {
                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White)
            }
            CalIconButton(onClick = onPlus) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun CalIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

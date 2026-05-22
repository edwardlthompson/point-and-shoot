package dev.pointandshoot

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Sprint **14.4** — finder overlay while [CommandDialMode.Qr] is active.
 * Actionable payloads show tappable **Open** / **Copy** (no auto-launch).
 */
@Composable
fun PreviewQrScanOverlay(
    decodedText: String?,
    action: QrScanAction?,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewUri = action as? QrScanAction.ViewUri
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(0.72f)
                    .border(
                        width = 2.dp,
                        color = PnsColors.PhotoOrange.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (decodedText == null) {
                    Text(
                        text = "Point at a QR or barcode",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                } else if (viewUri != null) {
                    Text(
                        text = "Link scanned — tap below to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = decodedText.take(96),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                textDecoration = TextDecoration.Underline,
                            ),
                        color = PnsColors.PhotoOrange,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onOpen),
                    )
                } else {
                    Text(
                        text = "Scanned: ${decodedText.take(64)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!decodedText.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (viewUri != null) {
                            OutlinedButton(onClick = onOpen) {
                                Text(viewUri.actionLabel)
                            }
                        }
                        TextButton(onClick = onCopy) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}

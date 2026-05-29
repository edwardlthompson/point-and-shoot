package dev.pointandshoot

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Sprint **15.37** — top-band chip while LAN tether + NSD are active. */
@Composable
fun WifiDirectTetherBanner(
    port: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier =
            modifier
                .padding(top = 2.dp)
                .background(PnsColors.Charcoal.copy(alpha = 0.92f), shape)
                .border(1.dp, PnsColors.PhotoOrange.copy(alpha = 0.65f), shape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Tether LAN :$port",
            style = MaterialTheme.typography.labelSmall,
            color = PnsColors.PhotoOrange,
        )
    }
}

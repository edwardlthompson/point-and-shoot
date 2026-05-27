package dev.pointandshoot

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sprint **14.9** — rotating orange ring in the top inset while the front camera is active.
 * Sits in the status band (not over the finder); horizontal cutout padding keeps the punch-hole clear.
 */
@Composable
fun PreviewSelfieRingIndicator(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(visible) {
        Log.i("PNS.ChromeUx", if (visible) "selfieRing=visible" else "selfieRing=hidden")
    }

    if (!visible) return

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        RotatingOrangeSelfieArc(
            ringSize = 40.dp,
            strokeWidth = 3.dp,
            contentDescription = "Selfie mode active",
        )
    }
}

@Composable
private fun RotatingOrangeSelfieArc(
    ringSize: Dp,
    strokeWidth: Dp,
    contentDescription: String,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "selfieRingSpin")
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1_800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "selfieRingRotation",
    )

    Canvas(
        modifier =
            Modifier
                .size(ringSize)
                .semantics { this.contentDescription = contentDescription },
    ) {
        val strokePx = strokeWidth.toPx()
        val diameter = size.minDimension - strokePx
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = PnsColors.PhotoOrange,
            startAngle = rotationDeg - 90f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }
}

/** True when [cameraId] is the active front-facing Camera2 id. */
fun isPreviewFrontCameraActive(
    cameraManager: android.hardware.camera2.CameraManager,
    cameraId: String?,
): Boolean = Camera2Facing.isFrontCamera(cameraManager, cameraId)

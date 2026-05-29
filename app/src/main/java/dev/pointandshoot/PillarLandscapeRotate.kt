package dev.pointandshoot

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Rotates composable +90° CW and swaps measured width/height so parent allocates correct pillar space.
 */
fun Modifier.pillarLandscapeRotate90Cw(): Modifier =
    layout { measurable, constraints ->
        val rotated =
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        val placeable = measurable.measure(rotated)
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2,
            ) {
                rotationZ = 90f
                transformOrigin = TransformOrigin.Center
            }
        }
    }

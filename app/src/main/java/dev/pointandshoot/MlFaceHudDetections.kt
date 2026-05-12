package dev.pointandshoot

import androidx.compose.ui.geometry.Offset

/** One ML Kit frame mapped into preview-buffer space for the face HUD. */
data class MlFaceHudDetections(
    val boxes: List<FaceTrackBoxBuffer>,
    val eyeMarks: List<EyeMark>,
)

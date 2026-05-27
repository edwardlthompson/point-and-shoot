package dev.pointandshoot

import android.content.Context
import android.util.Log
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/**
 * Manual correction for face/eye HUD overlays vs the GLES preview tile (Sprint **15.1**).
 *
 * Values are in **preview-tile view pixels** (same space as [EyeAfOverlay] after
 * [TexturePreviewFit.mapBufferToView] and mirror). Positive [offsetViewX] shifts marks right;
 * positive [offsetViewY] shifts marks down. [positionScale] expands/contracts marks about the
 * tile center (1.0 = identity).
 */
data class FaceOverlayCalibration(
    val offsetViewX: Float = 0f,
    val offsetViewY: Float = 0f,
    val positionScale: Float = 1f,
    /** Multiplier on [EyeAfOverlay] rectangle size (not position). */
    val markerSizeScale: Float = 1f,
) {
    fun isDefault(): Boolean =
        offsetViewX == 0f &&
            offsetViewY == 0f &&
            positionScale == 1f &&
            markerSizeScale == 1f

    fun clamped(): FaceOverlayCalibration =
        copy(
            positionScale = positionScale.coerceIn(POSITION_SCALE_MIN, POSITION_SCALE_MAX),
            markerSizeScale = markerSizeScale.coerceIn(MARKER_SIZE_SCALE_MIN, MARKER_SIZE_SCALE_MAX),
        )

    fun toDiagString(): String =
        "faceOverlayCal ox=${"%.1f".format(offsetViewX)} oy=${"%.1f".format(offsetViewY)} " +
            "posScale=${"%.3f".format(positionScale)} markerScale=${"%.2f".format(markerSizeScale)}"

    companion object {
        const val TAG = "PNS.FaceAlign"

        const val VIEW_NUDGE_STEP_PX = 2f
        const val POSITION_SCALE_STEP = 0.02f
        const val MARKER_SIZE_SCALE_STEP = 0.08f

        const val POSITION_SCALE_MIN = 0.75f
        const val POSITION_SCALE_MAX = 1.35f
        const val MARKER_SIZE_SCALE_MIN = 0.4f
        const val MARKER_SIZE_SCALE_MAX = 2.5f

        val Default = FaceOverlayCalibration()

        fun applyViewPoint(
            x: Float,
            y: Float,
            calibration: FaceOverlayCalibration,
            tileCenterX: Float,
            tileCenterY: Float,
        ): Offset {
            val cal = calibration.clamped()
            val px = x - tileCenterX
            val py = y - tileCenterY
            return Offset(
                tileCenterX + px * cal.positionScale + cal.offsetViewX,
                tileCenterY + py * cal.positionScale + cal.offsetViewY,
            )
        }

        fun applyViewMark(
            mark: EyeMark,
            calibration: FaceOverlayCalibration,
            tileCenterX: Float,
            tileCenterY: Float,
        ): EyeMark {
            val p = applyViewPoint(mark.position.x, mark.position.y, calibration, tileCenterX, tileCenterY)
            return mark.copy(position = p)
        }
    }
}

object FaceOverlayCalibrationStore {
    private const val PREFS = "pns_face_overlay_calibration"
    private const val KEY_OX = "offset_view_x"
    private const val KEY_OY = "offset_view_y"
    private const val KEY_POS_SCALE = "position_scale"
    private const val KEY_MARKER_SCALE = "marker_size_scale"

    fun load(context: Context): FaceOverlayCalibration {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FaceOverlayCalibration(
            offsetViewX = prefs.getFloat(KEY_OX, 0f),
            offsetViewY = prefs.getFloat(KEY_OY, 0f),
            positionScale = prefs.getFloat(KEY_POS_SCALE, 1f),
            markerSizeScale = prefs.getFloat(KEY_MARKER_SCALE, 1f),
        ).clamped()
    }

    fun save(context: Context, calibration: FaceOverlayCalibration) {
        val cal = calibration.clamped()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_OX, cal.offsetViewX)
            .putFloat(KEY_OY, cal.offsetViewY)
            .putFloat(KEY_POS_SCALE, cal.positionScale)
            .putFloat(KEY_MARKER_SCALE, cal.markerSizeScale)
            .commit()
        Log.i(FaceOverlayCalibration.TAG, "saved ${cal.toDiagString()}")
    }

    fun reset(context: Context) {
        save(context, FaceOverlayCalibration.Default)
    }
}

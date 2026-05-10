package dev.pointandshoot

import android.graphics.Matrix
import android.view.TextureView
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Preview uses **center-crop** scaling (`max` scale, ImageView-style `CENTER_CROP`):
 * the camera buffer always fills the [TextureView] without side pillarboxing; excess is
 * cropped top/bottom or left/right. This matches `BUILD_PLAN` finder acceptance (no side bars).
 *
 * The matrix produced by [computeCenterCropMatrix] is applied via [TextureView.setTransform],
 * which post-multiplies on top of the default stretch-to-view draw.
 *
 * [computeCenterFitMatrix] remains for tests / tooling that need letterboxed “show full frame”.
 *
 * [mapBufferToView] follows center-crop mapping so overlays and tap-to-focus align with pixels.
 */
object TexturePreviewFit {
    /**
     * Largest axis-aligned rectangle with aspect ratio [aspectWidthOverHeight] = W/H that fits in
     * `maxW × maxH`. Used to size the preview [TextureView] wrapper so its pixel aspect matches the
     * camera buffer (after any static quarter-turn metadata), avoiding stretch from rounding drift.
     */
    fun largestAxisAlignedRectWithAspect(
        maxW: Int,
        maxH: Int,
        aspectWidthOverHeight: Float,
    ): Pair<Int, Int> {
        if (maxW <= 0 || maxH <= 0 || aspectWidthOverHeight <= 0f) {
            return maxOf(1, maxW) to maxOf(1, maxH)
        }
        val mw = maxW.toFloat()
        val mh = maxH.toFloat()
        val wIfFullHeight = mh * aspectWidthOverHeight
        return if (wIfFullHeight <= mw) {
            maxOf(1, wIfFullHeight.toInt()) to maxH
        } else {
            maxW to maxOf(1, (mw / aspectWidthOverHeight).toInt())
        }
    }

    /**
     * Smallest axis-aligned rectangle with aspect [aspectWidthOverHeight] that **covers**
     * `maxW × maxH` (center-crop when clipped to the viewport). Used so the finder fills the
     * width without left/right pillarbars; overflow is clipped by the parent.
     */
    fun smallestCoveringAxisAlignedRectWithAspect(
        maxW: Int,
        maxH: Int,
        aspectWidthOverHeight: Float,
    ): Pair<Int, Int> {
        if (maxW <= 0 || maxH <= 0 || aspectWidthOverHeight <= 0f) {
            return maxOf(1, maxW) to maxOf(1, maxH)
        }
        val mw = maxW.toFloat()
        val mh = maxH.toFloat()
        val a = aspectWidthOverHeight
        val hIfFullW = mw / a
        return if (hIfFullW >= mh) {
            maxW to maxOf(1, hIfFullW.toInt())
        } else {
            maxOf(1, (mh * a).toInt()) to maxH
        }
    }

    fun applyCenterFit(
        textureView: TextureView,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
    ) {
        textureView.setTransform(
            computeCenterFitMatrix(viewWidthPx, viewHeightPx, bufferWidthPx, bufferHeightPx),
        )
    }

    /**
     * Builds the [Matrix] that, when fed to [TextureView.setTransform], renders
     * a `bufferWidthPx × bufferHeightPx` camera buffer letterbox-fit inside a
     * `viewWidthPx × viewHeightPx` view without cropping or distortion.
     *
     * Splitting this from [applyCenterFit] keeps the math pure for unit tests
     * (TextureView is an Android framework class and isn't available in JVM
     * tests).
     */
    fun computeCenterFitMatrix(
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
    ): Matrix {
        val matrix = Matrix()
        if (viewWidthPx <= 0 || viewHeightPx <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) {
            return matrix
        }
        val vw = viewWidthPx.toFloat()
        val vh = viewHeightPx.toFloat()
        val bw = bufferWidthPx.toFloat()
        val bh = bufferHeightPx.toFloat()
        val scale = min(vw / bw, vh / bh)
        val drawnW = bw * scale
        val drawnH = bh * scale
        // TextureView's default render stretches the buffer to viewW × viewH,
        // so we scale the stretched output back to its true (drawnW × drawnH)
        // size and translate to center it.
        val sx = drawnW / vw
        val sy = drawnH / vh
        val tx = (vw - drawnW) / 2f
        val ty = (vh - drawnH) / 2f
        matrix.setScale(sx, sy)
        matrix.postTranslate(tx, ty)
        return matrix
    }

    /**
     * Center-crop matrix: scales with `max(view/buffer)` so the buffer covers the view; crops
     * overflow (used for live preview).
     */
    fun computeCenterCropMatrix(
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
    ): Matrix {
        val matrix = Matrix()
        if (viewWidthPx <= 0 || viewHeightPx <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) {
            return matrix
        }
        val vw = viewWidthPx.toFloat()
        val vh = viewHeightPx.toFloat()
        val bw = bufferWidthPx.toFloat()
        val bh = bufferHeightPx.toFloat()
        val scale = max(vw / bw, vh / bh)
        val drawnW = bw * scale
        val drawnH = bh * scale
        val sx = drawnW / vw
        val sy = drawnH / vh
        val tx = (vw - drawnW) / 2f
        val ty = (vh - drawnH) / 2f
        matrix.setScale(sx, sy)
        matrix.postTranslate(tx, ty)
        return matrix
    }

    /**
     * Bounds of the letterbox/pillarbox-fit camera image in view pixels — i.e. the rect that
     * actually shows the camera buffer (the "live" area), with the unrendered black bars
     * outside. Use this to clip overlays (rule-of-thirds, horizon level reference, focus
     * peaking outline …) to the visible image so they don't bleed into the surrounding black.
     *
     * Returns the full view rect when sizes are degenerate so callers can still place
     * something sensible at boot time before the camera buffer size is known.
     */
    fun computeFitRect(
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
    ): FitRect {
        if (viewWidthPx <= 0 || viewHeightPx <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) {
            return FitRect(
                left = 0f,
                top = 0f,
                width = viewWidthPx.toFloat().coerceAtLeast(0f),
                height = viewHeightPx.toFloat().coerceAtLeast(0f),
            )
        }
        val vw = viewWidthPx.toFloat()
        val vh = viewHeightPx.toFloat()
        // Center-crop preview fills the TextureView — clip overlays to the full view rect.
        return FitRect(left = 0f, top = 0f, width = vw, height = vh)
    }

    /** Visible image rect in view coords under center-crop preview (full [TextureView]). */
    data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right: Float get() = left + width
        val bottom: Float get() = top + height
    }

    /**
     * Maps a point in camera-buffer pixel space to the corresponding location in
     * the [TextureView] view pixel space (same convention as [applyCenterFit]).
     */
    fun mapBufferToView(
        bufferX: Float,
        bufferY: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
        /** When true, center-**crop** (fills view); when false, center-**contain** (matches still JPEG framing). */
        coverCrop: Boolean = true,
    ): Pair<Float, Float> {
        if (viewWidthPx <= 0 || viewHeightPx <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) {
            return bufferX to bufferY
        }
        val vw = viewWidthPx.toFloat()
        val vh = viewHeightPx.toFloat()
        val bw = bufferWidthPx.toFloat()
        val bh = bufferHeightPx.toFloat()
        val scale =
            if (coverCrop) {
                max(vw / bw, vh / bh)
            } else {
                min(vw / bw, vh / bh)
            }
        val dx = (vw - bw * scale) / 2f
        val dy = (vh - bh * scale) / 2f
        return (bufferX * scale + dx) to (bufferY * scale + dy)
    }

    /** [mapBufferToView] followed by the same center rotation as [applyCenterFitWithUiTwist]. */
    fun mapBufferToViewWithUiTwist(
        bufferX: Float,
        bufferY: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
        uiTwistDegrees: Float,
        coverCrop: Boolean = true,
    ): Pair<Float, Float> {
        val (x, y) =
            mapBufferToView(
                bufferX,
                bufferY,
                viewWidthPx,
                viewHeightPx,
                bufferWidthPx,
                bufferHeightPx,
                coverCrop,
            )
        return rotateAroundCenter(x, y, viewWidthPx / 2f, viewHeightPx / 2f, uiTwistDegrees)
    }

    internal fun rotateAroundCenter(x: Float, y: Float, cx: Float, cy: Float, degrees: Float): Pair<Float, Float> {
        if (degrees == 0f) return x to y
        val rad = Math.toRadians(degrees.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        return (dx * c - dy * s + cx) to (dx * s + dy * c + cy)
    }

    /**
     * Inverse of [mapBufferToView]: maps a point in [TextureView] pixel space to camera-buffer space.
     */
    fun mapViewToBuffer(
        viewX: Float,
        viewY: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
        coverCrop: Boolean = true,
    ): Pair<Float, Float> {
        if (viewWidthPx <= 0 || viewHeightPx <= 0 || bufferWidthPx <= 0 || bufferHeightPx <= 0) {
            return viewX to viewY
        }
        val vw = viewWidthPx.toFloat()
        val vh = viewHeightPx.toFloat()
        val bw = bufferWidthPx.toFloat()
        val bh = bufferHeightPx.toFloat()
        val scale =
            if (coverCrop) {
                max(vw / bw, vh / bh)
            } else {
                min(vw / bw, vh / bh)
            }
        val dx = (vw - bw * scale) / 2f
        val dy = (vh - bh * scale) / 2f
        val bx = ((viewX - dx) / scale).coerceIn(0f, bw)
        val by = ((viewY - dy) / scale).coerceIn(0f, bh)
        return bx to by
    }

    /** Undo UI twist (see [mapBufferToViewWithUiTwist]), then [mapViewToBuffer]. */
    fun mapViewToBufferWithUiTwist(
        viewX: Float,
        viewY: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
        uiTwistDegrees: Float,
        coverCrop: Boolean = true,
    ): Pair<Float, Float> {
        val cx = viewWidthPx / 2f
        val cy = viewHeightPx / 2f
        val (xr, yr) = rotateAroundCenter(viewX, viewY, cx, cy, -uiTwistDegrees)
        return mapViewToBuffer(xr, yr, viewWidthPx, viewHeightPx, bufferWidthPx, bufferHeightPx, coverCrop)
    }

    /**
     * Applies center-crop or center-contain scaling, then rotates the preview **within** the
     * [TextureView] about its center so camera chrome can stay aligned using the same angle in Compose.
     */
    fun applyCenterFitWithUiTwist(
        textureView: TextureView,
        viewWidthPx: Int,
        viewHeightPx: Int,
        bufferWidthPx: Int,
        bufferHeightPx: Int,
        uiTwistDegrees: Float,
        coverCrop: Boolean = true,
    ) {
        val m =
            if (coverCrop) {
                computeCenterCropMatrix(viewWidthPx, viewHeightPx, bufferWidthPx, bufferHeightPx)
            } else {
                computeCenterFitMatrix(viewWidthPx, viewHeightPx, bufferWidthPx, bufferHeightPx)
            }
        if (uiTwistDegrees != 0f) {
            val cx = viewWidthPx / 2f
            val cy = viewHeightPx / 2f
            m.postRotate(uiTwistDegrees, cx, cy)
        }
        textureView.setTransform(m)
    }
}

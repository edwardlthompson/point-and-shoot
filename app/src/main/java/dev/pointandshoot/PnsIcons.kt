package dev.pointandshoot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Sony Photography Pro–style icon "cube" — a square pressable surface that contains a
 * vector glyph drawn directly via Canvas, no extra resource files. Used as the header for
 * each [ShortcutBlock] and for stand-alone rail buttons (close, HUD, developer menu).
 *
 * Drawing icons via Canvas keeps dependencies light for custom glyphs; standard controls also
 * use [IconCubeVectorButton] with Apache-2.0 Material Symbols–compatible vectors from
 * `material-icons-extended`.
 */
@Composable
fun IconCubeButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    icon: (DrawScope) -> Unit,
) {
    val borderColor =
        when {
            selected -> PnsColors.PhotoOrange
            enabled -> Color.White.copy(alpha = 0.30f)
            else -> Color.White.copy(alpha = 0.15f)
        }
    val bg =
        when {
            selected -> PnsColors.PhotoOrange.copy(alpha = 0.18f)
            else -> Color.Black.copy(alpha = 0.55f)
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .background(bg)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            icon(this)
        }
    }
}

/** Same chrome as [IconCubeButton] but renders a Material [ImageVector] (industry-standard glyphs). */
@Composable
fun IconCubeVectorButton(
    onClick: () -> Unit,
    contentDescription: String,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    selected: Boolean = false,
    enabled: Boolean = true,
    /**
     * Match [FpsQuickChip] border/fill/tints so focal-length and icon tiles read as one family
     * (distinct rounded blocks in the preview chrome grid).
     */
    chromeChipStyle: Boolean = false,
    /** Expand to the caller's max constraints; glyph scales with the shorter tile edge (~2× in the 7-column grid). */
    fillMaxTile: Boolean = false,
    /** When non-null, long-press invokes this (short tap still uses [onClick]). */
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor =
        when {
            chromeChipStyle && !enabled -> Color.White.copy(alpha = 0.12f)
            chromeChipStyle && selected -> PnsColors.PhotoOrange
            chromeChipStyle -> Color.White.copy(alpha = 0.35f)
            selected -> PnsColors.PhotoOrange
            enabled -> Color.White.copy(alpha = 0.30f)
            else -> Color.White.copy(alpha = 0.15f)
        }
    val bg =
        when {
            chromeChipStyle && !enabled -> Color.Black.copy(alpha = 0.25f)
            chromeChipStyle && selected -> PnsColors.PhotoOrange
            chromeChipStyle -> Color.Black.copy(alpha = 0.45f)
            selected -> PnsColors.PhotoOrange.copy(alpha = 0.18f)
            else -> Color.Black.copy(alpha = 0.55f)
        }
    val iconTint =
        when {
            chromeChipStyle && !enabled -> Color.White.copy(alpha = 0.35f)
            chromeChipStyle && selected -> Color.Black.copy(alpha = 0.92f)
            chromeChipStyle -> Color.White.copy(alpha = 0.92f)
            else -> Color.White.copy(alpha = if (enabled) 0.92f else 0.45f)
        }
    val layered =
        modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .background(bg)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            )
    if (fillMaxTile) {
        BoxWithConstraints(modifier = layered, contentAlignment = Alignment.Center) {
            val edge = minOf(maxWidth, maxHeight)
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(edge * 0.56f),
                tint = iconTint,
            )
        }
    } else {
        Box(modifier = layered.size(size), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * 0.52f),
                tint = iconTint,
            )
        }
    }
}

/** Glyph factories for the rail. Each takes a [DrawScope] and paints inside its current canvas. */
object PnsIcons {
    private val GlyphTint = Color.White.copy(alpha = 0.92f)

    private fun stroke(strokeWidth: Float, cap: StrokeCap = StrokeCap.Round) =
        Stroke(width = strokeWidth, cap = cap, join = StrokeJoin.Round)

    /** Two diagonal strokes — used for the "close / back" cube. */
    fun Close(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val pad = min(w, h) * 0.28f
            val sw = min(w, h) * 0.10f
            drawLine(
                GlyphTint,
                start = Offset(pad, pad),
                end = Offset(w - pad, h - pad),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawLine(
                GlyphTint,
                start = Offset(w - pad, pad),
                end = Offset(pad, h - pad),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
    }

    /** Stacked "lines" representing on-screen HUD readouts. */
    fun Hud(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sw = min(w, h) * 0.08f
            val left = w * 0.22f
            val right = w * 0.78f
            for (i in 0..2) {
                val y = h * (0.30f + 0.20f * i)
                drawLine(
                    GlyphTint.copy(alpha = if (i == 1) 0.6f else 0.92f),
                    start = Offset(left, y),
                    end = Offset(right - if (i == 0) w * 0.18f else 0f, y),
                    strokeWidth = sw,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    /** Three dots for "more / developer menu". */
    fun MoreDots(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val r = min(w, h) * 0.06f
            val cy = h / 2f
            for (i in 0..2) {
                val cx = w * (0.30f + 0.20f * i)
                drawCircle(GlyphTint, radius = r, center = Offset(cx, cy))
            }
        }
    }

    /** Stylised stopwatch face for "Target FPS". */
    fun Speed(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f + h * 0.04f
            val radius = min(w, h) * 0.32f
            val sw = min(w, h) * 0.08f
            drawCircle(GlyphTint, radius = radius, center = Offset(cx, cy), style = stroke(sw))
            // Top crown stem (stopwatch button)
            drawLine(
                GlyphTint,
                start = Offset(cx, cy - radius - sw * 0.6f),
                end = Offset(cx, cy - radius - sw * 1.6f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            // Hand pointing up-right
            drawLine(
                GlyphTint,
                start = Offset(cx, cy),
                end = Offset(cx + radius * 0.55f, cy - radius * 0.45f),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
            drawCircle(GlyphTint, radius = sw * 0.45f, center = Offset(cx, cy))
        }
    }

    /** Camera-lens iris — used for "Focal" and "Mode" cubes (filled aperture for Mode). */
    fun Lens(scope: DrawScope, filled: Boolean = false) {
        with(scope) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) * 0.34f
            val sw = min(w, h) * 0.08f
            drawCircle(GlyphTint, radius = radius, center = Offset(cx, cy), style = stroke(sw))
            // Six aperture blades — straight chords from points on the outer circle through
            // the centre, evenly spaced.
            val blades = 6
            for (i in 0 until blades) {
                val a = (i.toFloat() / blades) * (2 * Math.PI).toFloat()
                val x1 = cx + cos(a.toDouble()).toFloat() * radius
                val y1 = cy + sin(a.toDouble()).toFloat() * radius
                drawLine(
                    GlyphTint.copy(alpha = if (filled) 0.92f else 0.55f),
                    start = Offset(x1, y1),
                    end = Offset(cx, cy),
                    strokeWidth = sw * 0.7f,
                    cap = StrokeCap.Round,
                )
            }
            if (filled) {
                drawCircle(GlyphTint, radius = sw * 0.7f, center = Offset(cx, cy))
            }
        }
    }

    /** 3×3 grid for "Guides". */
    fun Grid(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sw = min(w, h) * 0.06f
            val pad = min(w, h) * 0.20f
            val left = pad
            val top = pad
            val right = w - pad
            val bottom = h - pad
            // Outer rect
            drawLine(GlyphTint, Offset(left, top), Offset(right, top), strokeWidth = sw)
            drawLine(GlyphTint, Offset(right, top), Offset(right, bottom), strokeWidth = sw)
            drawLine(GlyphTint, Offset(right, bottom), Offset(left, bottom), strokeWidth = sw)
            drawLine(GlyphTint, Offset(left, bottom), Offset(left, top), strokeWidth = sw)
            // Inner thirds
            val v1 = left + (right - left) / 3f
            val v2 = left + 2f * (right - left) / 3f
            val h1 = top + (bottom - top) / 3f
            val h2 = top + 2f * (bottom - top) / 3f
            val innerColor = GlyphTint.copy(alpha = 0.55f)
            drawLine(innerColor, Offset(v1, top), Offset(v1, bottom), strokeWidth = sw * 0.85f)
            drawLine(innerColor, Offset(v2, top), Offset(v2, bottom), strokeWidth = sw * 0.85f)
            drawLine(innerColor, Offset(left, h1), Offset(right, h1), strokeWidth = sw * 0.85f)
            drawLine(innerColor, Offset(left, h2), Offset(right, h2), strokeWidth = sw * 0.85f)
        }
    }

    /** Eye glyph for "Preview & keys" (visibility / what-you-see). */
    fun Eye(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sw = min(w, h) * 0.08f
            val cx = w / 2f
            val cy = h / 2f
            val rW = w * 0.32f
            val rH = h * 0.18f
            val path =
                Path().apply {
                    moveTo(cx - rW, cy)
                    cubicTo(
                        cx - rW, cy - rH * 1.6f,
                        cx + rW, cy - rH * 1.6f,
                        cx + rW, cy,
                    )
                    cubicTo(
                        cx + rW, cy + rH * 1.6f,
                        cx - rW, cy + rH * 1.6f,
                        cx - rW, cy,
                    )
                    close()
                }
            drawPath(path, color = GlyphTint, style = stroke(sw))
            drawCircle(GlyphTint, radius = min(w, h) * 0.10f, center = Offset(cx, cy))
        }
    }

    /**
     * Curved arrow (3/4 circle with arrowhead) for "Spin & probe" — visually denotes the
     * static-rotation cycle button + probe overlay diagnostic block.
     */
    fun Spin(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sw = min(w, h) * 0.08f
            val cx = w / 2f
            val cy = h / 2f
            val r = min(w, h) * 0.34f
            // Open arc 270° (skip the upper-right quarter so the arrow has a "gap" to live in)
            drawArc(
                color = GlyphTint,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(2f * r, 2f * r),
                style = stroke(sw),
            )
            // Arrowhead at the open end (top-right, angle = -90° = pointing right-and-up).
            // Arc end at angle 270° sweep starting from 0° → ends at angle 270°. In default
            // canvas math, 0° is the +X axis, sweeping CW (since +Y is down). 270° from +X CW
            // = -X (left). So the open gap is between -X axis (270°) and +X axis (0°), i.e.
            // the top arc.
            // Place arrowhead at the *start* (angle 0° = +X axis, right side of circle).
            val tipX = cx + r
            val tipY = cy
            val ahLen = r * 0.32f
            // Two strokes forming an "arrow" pointing into the arc (i.e. pointing CCW = up).
            drawLine(GlyphTint, Offset(tipX, tipY), Offset(tipX - ahLen * 0.7f, tipY - ahLen * 0.5f), strokeWidth = sw)
            drawLine(GlyphTint, Offset(tipX, tipY), Offset(tipX + ahLen * 0.5f, tipY - ahLen * 0.5f), strokeWidth = sw)
        }
    }

    /** Camera body silhouette for "Capture & tools". */
    fun Camera(scope: DrawScope) {
        with(scope) {
            val w = size.width
            val h = size.height
            val sw = min(w, h) * 0.08f
            val pad = min(w, h) * 0.18f
            val bodyTop = h * 0.34f
            val bodyBottom = h - pad
            val left = pad
            val right = w - pad
            val cx = w / 2f
            val cy = (bodyTop + bodyBottom) / 2f + h * 0.02f
            // Body outline (rounded rect drawn as 4 lines + arcs ≈ rectangle for simplicity)
            drawLine(GlyphTint, Offset(left, bodyTop), Offset(right, bodyTop), strokeWidth = sw)
            drawLine(GlyphTint, Offset(right, bodyTop), Offset(right, bodyBottom), strokeWidth = sw)
            drawLine(GlyphTint, Offset(right, bodyBottom), Offset(left, bodyBottom), strokeWidth = sw)
            drawLine(GlyphTint, Offset(left, bodyBottom), Offset(left, bodyTop), strokeWidth = sw)
            // "Hump" on top (viewfinder bump)
            drawLine(GlyphTint, Offset(cx - w * 0.12f, bodyTop), Offset(cx - w * 0.06f, h * 0.22f), strokeWidth = sw)
            drawLine(GlyphTint, Offset(cx - w * 0.06f, h * 0.22f), Offset(cx + w * 0.06f, h * 0.22f), strokeWidth = sw)
            drawLine(GlyphTint, Offset(cx + w * 0.06f, h * 0.22f), Offset(cx + w * 0.12f, bodyTop), strokeWidth = sw)
            // Lens
            drawCircle(GlyphTint, radius = min(w, h) * 0.18f, center = Offset(cx, cy), style = stroke(sw))
            drawCircle(GlyphTint, radius = min(w, h) * 0.06f, center = Offset(cx, cy))
        }
    }
}

package dev.pointandshoot

import kotlin.math.hypot
import kotlin.math.max

/**
 * Tracks ML faces by **center point** (buffer px) with separate smoothing for half-extents so the
 * overlay box stays pinned to the filtered centroid instead of jittering with independent corners.
 */
internal class MlFaceBoxSmoother(
    /** EMA blend toward new detection center each matched frame (higher = snappier). */
    private val centerAlpha: Float = 0.68f,
    /** EMA blend for half-width / half-height (lower = steadier box size). */
    private val sizeAlpha: Float = 0.26f,
    /** Max center distance to associate a detection with an existing track (px, buffer space). */
    private val matchDistFloorPx: Float = 96f,
    private val lostToleranceFrames: Int = 10,
) {
    private data class Track(
        var cx: Float,
        var cy: Float,
        var halfW: Float,
        var halfH: Float,
        var missed: Int,
    )

    private data class Det(
        val cx: Float,
        val cy: Float,
        val hw: Float,
        val hh: Float,
    )

    private val tracks = mutableListOf<Track>()

    fun clear() {
        tracks.clear()
    }

    fun update(detections: List<FaceTrackBoxBuffer>): List<FaceTrackBoxBuffer> {
        if (detections.isEmpty()) {
            tracks.forEach { it.missed++ }
            tracks.removeAll { it.missed > lostToleranceFrames }
            cullToLargestTrack()
            return primaryBoxOnly()
        }

        val dets =
            detections.map { det ->
                val cx = (det.left + det.right) / 2f
                val cy = (det.top + det.bottom) / 2f
                val hw = (det.right - det.left).coerceAtLeast(6f) / 2f
                val hh = (det.bottom - det.top).coerceAtLeast(6f) / 2f
                Det(cx, cy, hw, hh)
            }

        val usedDet = BooleanArray(dets.size)
        val next = mutableListOf<Track>()

        for (tr in tracks) {
            var bestDi = -1
            var bestDist = Float.MAX_VALUE
            for (di in dets.indices) {
                if (usedDet[di]) continue
                val d = dets[di]
                val thresh = matchThreshold(tr.halfW, tr.halfH, d.hw, d.hh)
                val dist = hypot(tr.cx - d.cx, tr.cy - d.cy)
                if (dist <= thresh && dist < bestDist) {
                    bestDist = dist
                    bestDi = di
                }
            }
            if (bestDi >= 0) {
                usedDet[bestDi] = true
                next.add(mergeTrack(tr, dets[bestDi]))
            } else {
                tr.missed++
                if (tr.missed <= lostToleranceFrames) next.add(tr)
            }
        }

        for (di in dets.indices) {
            if (!usedDet[di]) {
                val d = dets[di]
                next.add(Track(d.cx, d.cy, d.hw, d.hh, missed = 0))
            }
        }

        tracks.clear()
        tracks.addAll(next)
        cullToLargestTrack()
        return primaryBoxOnly()
    }

    /** One subject only: drop extra tracks so we never output duplicate boxes for the same face. */
    private fun cullToLargestTrack() {
        if (tracks.size <= 1) return
        val keep = tracks.maxByOrNull { boxArea(trackToBox(it)) } ?: return
        tracks.clear()
        tracks.add(keep)
    }

    private fun primaryBoxOnly(): List<FaceTrackBoxBuffer> {
        val b = tracks.maxByOrNull { boxArea(trackToBox(it)) } ?: return emptyList()
        return listOf(trackToBox(b))
    }

    private fun matchThreshold(hw1: Float, hh1: Float, hw2: Float, hh2: Float): Float {
        val span = max(max(hw1, hh1), max(hw2, hh2)) * 3.25f
        return max(matchDistFloorPx, span)
    }

    private fun mergeTrack(prev: Track, d: Det): Track {
        val ca = centerAlpha
        val sa = sizeAlpha
        return Track(
            cx = prev.cx + (d.cx - prev.cx) * ca,
            cy = prev.cy + (d.cy - prev.cy) * ca,
            halfW = prev.halfW + (d.hw - prev.halfW) * sa,
            halfH = prev.halfH + (d.hh - prev.halfH) * sa,
            missed = 0,
        )
    }

    private fun trackToBox(t: Track): FaceTrackBoxBuffer {
        val l = t.cx - t.halfW
        val r = t.cx + t.halfW
        val top = t.cy - t.halfH
        val bot = t.cy + t.halfH
        return FaceTrackBoxBuffer(l, top, r, bot, trackingLocked = false)
    }

    private fun boxArea(b: FaceTrackBoxBuffer): Float {
        val w = (b.right - b.left).coerceAtLeast(0f)
        val h = (b.bottom - b.top).coerceAtLeast(0f)
        return w * h
    }
}

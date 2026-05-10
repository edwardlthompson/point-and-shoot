package dev.pointandshoot

import android.hardware.camera2.params.Face

/**
 * Stable integer ids for [TrackerState] when Camera2 sometimes reports
 * [Face.FACE_ID_UNSUPPORTED] for every face.
 */
object FaceTrackingSupport {

    fun observedIds(faces: Array<Face>): Set<Int> =
        faces.map { stableFaceId(it) }.toSet()

    fun stableFaceId(face: Face): Int {
        val id = face.id
        if (id >= 0) return id
        val b = face.bounds
        val cx = b.centerX()
        val cy = b.centerY()
        return ((cx * 10007L + cy) xor (cx.toLong() shl 20)).toInt() and 0x7FFF_FFFF
    }
}

package dev.pointandshoot

import android.graphics.Rect
import android.media.FaceDetector
import android.media.Image
import android.util.Log
import androidx.compose.ui.geometry.Offset

/**
 * FOSS YUV face fallback when Camera2 [STATISTICS_FACES] is empty.
 *
 * Play/GitHub builds use ML Kit (`app/src/play`). This flavor keeps the same
 * call sites in [PreviewEngineScreen] but runs AOSP [FaceDetector] on a
 * downscaled RGB_565 frame. Eye marks come from the detector's inter-eye
 * midpoint (plus the same heuristic as ML Kit when that fails). Smile
 * probability uses [FossSmileEstimator] on the mouth band — not ML Kit
 * classification.
 *
 * Primary HUD path is still Camera2 [STATISTICS_FACE_DETECT_MODE] in main.
 */
object MlKitFaceTrackSupport {
    private const val TAG = "PNS.FaceTrack"
    private const val MAX_FACES = 8
    private val detectLock = Any()

    @Suppress("UNUSED_PARAMETER")
    fun maxSmilingProbability(
        image: Image,
        rotationDegrees: Int,
        timeoutMs: Long = 250L,
    ): Float? {
        val prepared = YuvRgb565FaceFrames.prepare(image, rotationDegrees) ?: return null
        try {
            val faces = detectOnBitmap(prepared) ?: return null
            var best: FaceDetectorGeometry.FaceHint? = null
            var bestArea = 0
            for (face in faces) {
                val area = face.boundsWidth * face.boundsHeight
                if (area > bestArea) {
                    bestArea = area
                    best = face
                }
            }
            val hint = best ?: return null
            val luma = YuvRgb565FaceFrames.mouthLuma(prepared.bitmap, hint) ?: return null
            return FossSmileEstimator.probabilityFromMouthLuma(
                luma,
                hint.mouthRight - hint.mouthLeft,
                hint.mouthBottom - hint.mouthTop,
            )
        } finally {
            prepared.bitmap.recycle()
        }
    }

    fun detectFacesToBufferBoxes(
        image: Image,
        rotationDegrees: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        mirrorHorizontally: Boolean,
        coverCrop: Boolean,
        timeoutMs: Long = 120L,
    ): List<FaceTrackBoxBuffer> =
        detectFacesHud(
            image,
            rotationDegrees,
            bufferWidth,
            bufferHeight,
            mirrorHorizontally,
            coverCrop,
            timeoutMs,
        ).boxes

    @Suppress("UNUSED_PARAMETER")
    fun detectFacesHud(
        image: Image,
        rotationDegrees: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        mirrorHorizontally: Boolean,
        coverCrop: Boolean,
        timeoutMs: Long = 120L,
    ): MlFaceHudDetections {
        if (bufferWidth <= 0 || bufferHeight <= 0) {
            return MlFaceHudDetections(emptyList(), emptyList())
        }
        val prepared = YuvRgb565FaceFrames.prepare(image, rotationDegrees)
            ?: return MlFaceHudDetections(emptyList(), emptyList())
        try {
            val hints = detectOnBitmap(prepared).orEmpty()
            val boxes = ArrayList<FaceTrackBoxBuffer>(hints.size)
            val eyes = ArrayList<EyeMark>(hints.size * 2)
            for (hint in hints) {
                val yuvBox = mapHintRectToYuv(hint, prepared) ?: continue
                val box =
                    TexturePreviewFit.mapYuvRectToFaceTrackBoxBuffer(
                        yuvBox,
                        prepared.yuvW,
                        prepared.yuvH,
                        bufferWidth,
                        bufferHeight,
                        coverCrop,
                        mirrorHorizontally,
                    ) ?: continue
                boxes.add(box)
                addEye(
                    hint.leftEyeX,
                    hint.leftEyeY,
                    hint.confidence,
                    prepared,
                    bufferWidth,
                    bufferHeight,
                    coverCrop,
                    mirrorHorizontally,
                    eyes,
                )
                addEye(
                    hint.rightEyeX,
                    hint.rightEyeY,
                    hint.confidence,
                    prepared,
                    bufferWidth,
                    bufferHeight,
                    coverCrop,
                    mirrorHorizontally,
                    eyes,
                )
            }
            if (boxes.isNotEmpty()) {
                Log.d(TAG, "foss FaceDetector boxes=${boxes.size} rot=${prepared.rotationDegrees}")
            }
            return MlFaceHudDetections(boxes, eyes)
        } finally {
            prepared.bitmap.recycle()
        }
    }

    private fun detectOnBitmap(prepared: YuvRgb565FaceFrames.PreparedFrame): List<FaceDetectorGeometry.FaceHint>? {
        val bmp = prepared.bitmap
        if (bmp.width < 2 || bmp.height < 2) return null
        val widthEven = FaceDetectorGeometry.evenFloor(bmp.width)
        if (widthEven != bmp.width) return null
        val found = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
        val count =
            synchronized(detectLock) {
                runCatching {
                    FaceDetector(bmp.width, bmp.height, MAX_FACES).findFaces(bmp, found)
                }.getOrDefault(0)
            }
        if (count <= 0) return emptyList()
        val out = ArrayList<FaceDetectorGeometry.FaceHint>(count)
        val mid = android.graphics.PointF()
        for (i in 0 until count) {
            val face = found[i] ?: continue
            face.getMidPoint(mid)
            val hint =
                FaceDetectorGeometry.fromEyesMidpoint(
                    midX = mid.x,
                    midY = mid.y,
                    eyesDistance = face.eyesDistance(),
                    imageWidth = bmp.width,
                    imageHeight = bmp.height,
                    confidence = face.confidence(),
                ) ?: continue
            out.add(hint)
        }
        return out
    }

    private fun mapHintRectToYuv(
        hint: FaceDetectorGeometry.FaceHint,
        prepared: YuvRgb565FaceFrames.PreparedFrame,
    ): Rect? {
        val tl =
            FaceDetectorGeometry.mapUprightPointToYuv(
                hint.boundsLeft.toFloat(),
                hint.boundsTop.toFloat(),
                prepared.bitmap.width,
                prepared.bitmap.height,
                prepared.rotationDegrees,
                prepared.preRotateW,
                prepared.preRotateH,
                prepared.yuvW,
                prepared.yuvH,
            ) ?: return null
        val br =
            FaceDetectorGeometry.mapUprightPointToYuv(
                hint.boundsRight.toFloat(),
                hint.boundsBottom.toFloat(),
                prepared.bitmap.width,
                prepared.bitmap.height,
                prepared.rotationDegrees,
                prepared.preRotateW,
                prepared.preRotateH,
                prepared.yuvW,
                prepared.yuvH,
            ) ?: return null
        val left = minOf(tl.first, br.first).toInt()
        val top = minOf(tl.second, br.second).toInt()
        val right = maxOf(tl.first, br.first).toInt()
        val bottom = maxOf(tl.second, br.second).toInt()
        if (right - left < 4 || bottom - top < 4) return null
        return Rect(left, top, right, bottom)
    }

    private fun addEye(
        detX: Float,
        detY: Float,
        confidence: Float,
        prepared: YuvRgb565FaceFrames.PreparedFrame,
        bufferWidth: Int,
        bufferHeight: Int,
        coverCrop: Boolean,
        mirrorHorizontally: Boolean,
        out: MutableList<EyeMark>,
    ) {
        val yuv =
            FaceDetectorGeometry.mapUprightPointToYuv(
                detX,
                detY,
                prepared.bitmap.width,
                prepared.bitmap.height,
                prepared.rotationDegrees,
                prepared.preRotateW,
                prepared.preRotateH,
                prepared.yuvW,
                prepared.yuvH,
            ) ?: return
        val (bx, by) =
            TexturePreviewFit.mapYuvPointToFaceTrackBuffer(
                yuv.first,
                yuv.second,
                prepared.yuvW,
                prepared.yuvH,
                bufferWidth,
                bufferHeight,
                coverCrop,
                mirrorHorizontally,
            )
        out.add(EyeMark(Offset(bx, by), confidence = confidence, trackingLocked = false))
    }
}

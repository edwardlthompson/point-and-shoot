package dev.pointandshoot

import android.media.Image
import androidx.compose.ui.geometry.Offset
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.util.concurrent.TimeUnit

/**
 * ML Kit face rectangles on the YUV analysis stream, mapped into **preview buffer** space for
 * [FaceTrackOverlay] when Camera2 [STATISTICS_FACES] is empty. Geometry uses
 * [TexturePreviewFit.mapYuvRectToFaceTrackBoxBuffer] so boxes match the GLES preview shader +
 * overlay mapping. With [LANDMARK_MODE_ALL], pupil marks are mapped for [EyeAfOverlay].
 */
object MlKitFaceTrackSupport {

    private val options =
        FaceDetectorOptions.Builder()
            // Fast path keeps up with high-rate YUV callbacks; center-anchored smoothing in
            // [MlFaceBoxSmoother] stabilizes the HUD. NNAPI/GPU use is automatic when available.
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

    private val detector: FaceDetector = FaceDetection.getClient(options)

    private val smileOptions =
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

    private val smileDetector: FaceDetector by lazy { FaceDetection.getClient(smileOptions) }

    /** Max [com.google.mlkit.vision.face.Face.smilingProbability] in [0,1], or null if unavailable. */
    fun maxSmilingProbability(
        image: Image,
        rotationDegrees: Int,
        timeoutMs: Long = 120L,
    ): Float? {
        if (image.width <= 0 || image.height <= 0) return null
        val input = InputImage.fromMediaImage(image, rotationDegrees)
        val faces: List<Face> =
            runCatching {
                Tasks.await(smileDetector.process(input), timeoutMs, TimeUnit.MILLISECONDS)
            }.getOrDefault(emptyList())
        var max: Float? = null
        for (face in faces) {
            val p = face.smilingProbability ?: continue
            if (max == null || p > max) max = p
        }
        return max
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
        detectFacesHud(image, rotationDegrees, bufferWidth, bufferHeight, mirrorHorizontally, coverCrop, timeoutMs).boxes

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
        // Snapshot dimensions before [Tasks.await]: ML Kit may release the [Image] when detection
        // completes, so [Image.getWidth] after await can throw "Image is already closed".
        val yuvW = image.width
        val yuvH = image.height
        if (yuvW <= 0 || yuvH <= 0) {
            return MlFaceHudDetections(emptyList(), emptyList())
        }
        val input = InputImage.fromMediaImage(image, rotationDegrees)
        val faces: List<Face> =
            runCatching {
                Tasks.await(detector.process(input), timeoutMs, TimeUnit.MILLISECONDS)
            }.getOrDefault(emptyList())
        val boxes = ArrayList<FaceTrackBoxBuffer>(faces.size)
        val eyes = ArrayList<EyeMark>(faces.size * 2)
        for (face in faces) {
            val box =
                TexturePreviewFit.mapYuvRectToFaceTrackBoxBuffer(
                    face.boundingBox,
                    yuvW,
                    yuvH,
                    bufferWidth,
                    bufferHeight,
                    coverCrop,
                    mirrorHorizontally,
                ) ?: continue
            boxes.add(box)
            collectMlEyesForFace(face, yuvW, yuvH, bufferWidth, bufferHeight, coverCrop, mirrorHorizontally, eyes)
        }
        return MlFaceHudDetections(boxes, eyes)
    }

    private fun collectMlEyesForFace(
        face: Face,
        yuvW: Int,
        yuvH: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        coverCrop: Boolean,
        mirrorHorizontally: Boolean,
        out: MutableList<EyeMark>,
    ) {
        val bounds = face.boundingBox
        fun addEye(yuvX: Float, yuvY: Float, confidence: Float) {
            val (bx, by) =
                TexturePreviewFit.mapYuvPointToFaceTrackBuffer(
                    yuvX,
                    yuvY,
                    yuvW,
                    yuvH,
                    bufferWidth,
                    bufferHeight,
                    coverCrop,
                    mirrorHorizontally,
                )
            out.add(EyeMark(Offset(bx, by), confidence = confidence, trackingLocked = false))
        }
        val leftLm = face.getLandmark(FaceLandmark.LEFT_EYE)
        val rightLm = face.getLandmark(FaceLandmark.RIGHT_EYE)
        if (leftLm != null) {
            addEye(leftLm.position.x, leftLm.position.y, 0.92f)
        }
        if (rightLm != null) {
            addEye(rightLm.position.x, rightLm.position.y, 0.92f)
        }
        if (leftLm == null && rightLm == null) {
            val cx = (bounds.left + bounds.right) / 2f
            val eyesY = bounds.top + bounds.height() * 0.35f
            val span = bounds.width().toFloat().coerceAtLeast(1f)
            addEye(cx - span * 0.18f, eyesY, 0.55f)
            addEye(cx + span * 0.18f, eyesY, 0.55f)
        }
    }
}

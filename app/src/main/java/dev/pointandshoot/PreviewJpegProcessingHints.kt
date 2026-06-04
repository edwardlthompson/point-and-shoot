package dev.pointandshoot

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.Build

/**
 * Maps persisted [HudSettings.hardwareJpegIspBias] into discrete Camera2 ISP keys for JPEG-bearing
 * requests (preview, hardware JPEG still, RAW companion pipeline, bracket stills).
 *
 * HALs expose **modes**, not analog sharpening dials — bias only nudges preference among advertised
 * values (see Milestone 5 Sprint 5.1 HUD copy).
 */
object PreviewJpegProcessingHints {

    fun applyToCaptureRequest(
        req: CaptureRequest.Builder,
        chars: CameraCharacteristics,
        hud: HudSettings,
        skipColorCorrection: Boolean,
    ) {
        val bias = hud.hardwareJpegIspBias.coerceIn(-2, 2)
        pickEdge(req, chars, bias)
        pickNoiseReduction(req, chars, bias)
        pickTonemap(req, chars, bias)
        if (!skipColorCorrection) {
            pickColorCorrection(req, chars, bias)
        }
    }

    private fun pickEdge(req: CaptureRequest.Builder, chars: CameraCharacteristics, bias: Int) {
        val avail = chars.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES) ?: return
        val ordered =
            intArrayOf(
                CaptureRequest.EDGE_MODE_OFF,
                CaptureRequest.EDGE_MODE_FAST,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY,
            )
        pickFirstMatching(avail, ordered, bias)?.let {
            req.set(CaptureRequest.EDGE_MODE, it)
        }
    }

    private fun pickNoiseReduction(req: CaptureRequest.Builder, chars: CameraCharacteristics, bias: Int) {
        val avail = chars.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES) ?: return
        val ordered = mutableListOf<Int>()
        ordered.add(CaptureRequest.NOISE_REDUCTION_MODE_OFF)
        if (Build.VERSION.SDK_INT >= 34 &&
            avail.contains(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
        ) {
            ordered.add(CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL)
        }
        ordered.add(CaptureRequest.NOISE_REDUCTION_MODE_FAST)
        ordered.add(CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
        pickFirstMatching(avail, ordered.toIntArray(), bias)?.let {
            req.set(CaptureRequest.NOISE_REDUCTION_MODE, it)
        }
    }

    private fun pickTonemap(req: CaptureRequest.Builder, chars: CameraCharacteristics, bias: Int) {
        val avail = chars.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES) ?: return
        val ordered =
            intArrayOf(
                CaptureRequest.TONEMAP_MODE_FAST,
                CaptureRequest.TONEMAP_MODE_HIGH_QUALITY,
            )
        pickFirstMatching(avail, ordered, bias)?.let {
            req.set(CaptureRequest.TONEMAP_MODE, it)
        }
    }

    @SuppressLint("NewApi")
    private fun pickColorCorrection(req: CaptureRequest.Builder, chars: CameraCharacteristics, bias: Int) {
        if (Build.VERSION.SDK_INT < 34) return
        val avail = chars.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES) ?: return
        val ordered =
            intArrayOf(
                CaptureRequest.COLOR_CORRECTION_MODE_FAST,
                CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY,
            )
        pickFirstMatching(avail, ordered, bias)?.let {
            req.set(CaptureRequest.COLOR_CORRECTION_MODE, it)
        }
    }

    private fun pickFirstMatching(
        available: IntArray,
        preferenceSoftToHard: IntArray,
        bias: Int,
    ): Int? {
        val present = preferenceSoftToHard.filter { available.contains(it) }
        if (present.isEmpty()) return null
        val center = (present.size - 1) / 2
        val idx = (center + bias).coerceIn(0, present.lastIndex)
        return present[idx]
    }
}

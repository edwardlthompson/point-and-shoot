package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import java.util.Locale
import kotlin.math.abs

/**
 * Variable / fixed lens aperture for the preview readout **F** chip and [CaptureRequest.LENS_APERTURE].
 *
 * Fleet-generic: driven only by [CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES] on the active
 * camera (e.g. Sony Xperia PRO-I main **f/2.0** / **f/4.0**).
 */
object PreviewApertureSupport {
    private const val APERTURE_MATCH_EPSILON = 0.05f

    fun availableApertures(chars: CameraCharacteristics): List<Float> =
        runCatching {
            chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty()
        }.getOrDefault(emptyList())
            .filter { it > 0f }
            .sorted()

    fun isVariable(chars: CameraCharacteristics): Boolean = availableApertures(chars).size > 1

    fun hasApertureRequestKey(chars: CameraCharacteristics): Boolean =
        chars.availableCaptureRequestKeys?.contains(CaptureRequest.LENS_APERTURE) == true

    fun canControl(chars: CameraCharacteristics): Boolean = isVariable(chars) && hasApertureRequestKey(chars)

    fun formatChipValue(fNumber: Float): String =
        String.format(Locale.US, "f/%.1f", fNumber)

    fun defaultAperture(options: List<Float>): Float =
        options.minOrNull() ?: options.first()

    fun matchesOption(value: Float, options: List<Float>): Boolean =
        options.any { abs(it - value) < APERTURE_MATCH_EPSILON }

    fun cycle(current: Float, options: List<Float>): Float {
        require(options.isNotEmpty())
        if (options.size == 1) return options.first()
        val idx = options.indexOfFirst { abs(it - current) < APERTURE_MATCH_EPSILON }.let { if (it < 0) 0 else it }
        return options[(idx + 1) % options.size]
    }

    /** Pure-list helpers for JVM tests (no CameraCharacteristics). */
    fun availableAperturesFromList(values: List<Float>): List<Float> =
        values.filter { it > 0f }.sorted()

    fun isVariableFromList(values: List<Float>): Boolean = availableAperturesFromList(values).size > 1

    fun cycleFromList(current: Float, options: List<Float>): Float = cycle(current, availableAperturesFromList(options))
}

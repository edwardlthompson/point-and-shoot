package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log

/**
 * OEM-style **hardware highlight AE** probing.
 *
 * **Reality check:** Through **compileSdk 36**, AOSP [`CaptureRequest`](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
 * documents standard modes (`CONTROL_AE_MODE_ON`, flash variants, external flash,
 * `CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY`, …) — **not** a public
 * `CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED` symbol in the SDK stubs this repo builds against.
 * Some vendor HALs may still advertise extra integer modes via
 * [`CONTROL_AE_AVAILABLE_MODES`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#CONTROL_AE_AVAILABLE_MODES);
 * future OEM/STUB builds could also expose a named field — we resolve it via reflection when present.
 *
 * **Root opt-in:** When the reflected constant is absent but the HAL lists **unknown** mode ints,
 * [resolveHighlightWeightedAeModeOrNull] can pick one (see `VendorHighlightAePrefs`) — gated on
 * persisted SU grant per BUILD_PLAN §9.
 *
 * When no hardware highlight mode exists, Point & Shoot keeps **AE ON** + [`HighlightMeter`] EV compensation
 * (software path documented in `BUILD_PLAN.md`).
 */
object HighlightAeModeSupport {

    private const val TAG = "PNS.HighlightAe"

    private val highlightWeightedAeMode: Int? by lazy {
        resolveOptionalIntField(CaptureRequest::class.java, "CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED")
    }

    /**
     * Returns the reflected mode integer when the current SDK/runtime exposes it; otherwise null.
     */
    fun highlightWeightedAeModeOrNull(): Int? = highlightWeightedAeMode

    /** Documented / reflected AE modes — anything else in [CONTROL_AE_AVAILABLE_MODES] is treated as vendor-extra. */
    private val standardAeModeInts: Set<Int> by lazy {
        val s = mutableSetOf(
            CaptureRequest.CONTROL_AE_MODE_OFF,
            CaptureRequest.CONTROL_AE_MODE_ON,
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH,
            CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH,
            CaptureRequest.CONTROL_AE_MODE_ON_EXTERNAL_FLASH,
        )
        listOf(
            "CONTROL_AE_MODE_ON_AUTO_FLASH_RED_EYE",
            "CONTROL_AE_MODE_ON_RED_EYE",
            "CONTROL_AE_MODE_ON_EXTERNAL_FLASH_AUTO",
            "CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY",
            "CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED",
        ).forEach { name ->
            resolveOptionalIntField(CaptureRequest::class.java, name)?.let { s.add(it) }
        }
        s
    }

    /** Exposes the standard AE mode int set for probe export ([AeHighlightProbe]). */
    fun standardAeModesForProbe(): Set<Int> = standardAeModeInts

    /**
     * True when [chars] lists the optional highlight-weighted mode and it resolved at runtime.
     */
    fun supportsHardwareHighlightWeighted(chars: CameraCharacteristics): Boolean {
        val mode = highlightWeightedAeMode ?: return false
        val avail = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        return avail.contains(mode)
    }

    /**
     * Highlight (H dial) hardware path: reflected highlight-weighted mode, or — when
     * [tryVendorExtraModes] — any non-standard int advertised in [CONTROL_AE_AVAILABLE_MODES].
     */
    fun supportsHardwareHighlightForHMode(
        chars: CameraCharacteristics,
        tryVendorExtraModes: Boolean,
    ): Boolean {
        if (supportsHardwareHighlightWeighted(chars)) return true
        if (!tryVendorExtraModes) return false
        return vendorExtraHighlightCandidates(chars).isNotEmpty()
    }

    /**
     * Resolves `CONTROL_AE_MODE` for highlight metering when the H dial is active.
     * Returns null to fall through to standard ON / auto-flash selection.
     */
    fun resolveHighlightWeightedAeModeOrNull(
        chars: CameraCharacteristics,
        tryVendorExtraModes: Boolean,
    ): Int? {
        val avail = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        val reflected = highlightWeightedAeMode
        if (reflected != null && avail.contains(reflected)) {
            return reflected
        }
        if (!tryVendorExtraModes) return null
        val extras = vendorExtraModesFiltered(avail, standardAeModeInts)
        return when (extras.size) {
            0 -> null
            1 -> extras[0]
            else -> {
                val chosen = extras.maxOrNull()!!
                Log.d(
                    TAG,
                    "vendor highlight AE: multiple extra modes $extras; choosing max=$chosen (experimental)",
                )
                chosen
            }
        }
    }

    /** JVM-testable: advertised modes minus a supplied standard set. */
    internal fun vendorExtraModesFiltered(avail: IntArray, standard: Set<Int>): List<Int> =
        avail.filter { it !in standard }.distinct().sorted()

    private fun vendorExtraHighlightCandidates(chars: CameraCharacteristics): List<Int> {
        val avail = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: intArrayOf()
        return vendorExtraModesFiltered(avail, standardAeModeInts)
    }

    private fun resolveOptionalIntField(clazz: Class<*>, name: String): Int? =
        runCatching {
            clazz.getField(name).getInt(null)
        }.getOrNull()
}

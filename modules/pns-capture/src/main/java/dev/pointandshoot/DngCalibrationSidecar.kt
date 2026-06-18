package dev.pointandshoot

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sidecar JSON written next to a DNG when a [CalibrationProfile] exists on device,
 * carrying [DngColorTags] arrays so desktop RAW processors (or a future in-app
 * TIFF patcher) can honor calibration without baking matrices into sensor pixels.
 *
 * Pair with [CaptureStorage.openCalibrationSidecarOutput] which reserves a
 * MediaStore row named like the DNG stem + `.pns-calibration.json`.
 *
 * This does not replace embedding tags inside the DNG TIFF IFD (blocked on public
 * [android.hardware.camera2.DngCreator] APIs); it gives a reproducible,
 * pull-friendly artifact alongside gallery-visible files.
 */
object DngCalibrationSidecar {

    const val MAGIC: String = "pns-dng-calibration-sidecar"
    const val VERSION: Int = 1

    /**
     * Stable UTF-8 JSON (pretty-printed) including profile provenance + DNG tag payloads.
     */
    fun encode(
        profile: CalibrationProfile,
        dngColor: DngColorTags.DngColor,
        sourceProfileAbsolutePath: String?,
    ): String {
        val root = JSONObject()
        root.put("magic", MAGIC)
        root.put("version", VERSION)
        if (sourceProfileAbsolutePath != null) {
            root.put("sourceProfilePath", sourceProfileAbsolutePath)
        } else {
            root.put("sourceProfilePath", JSONObject.NULL)
        }
        root.put("cameraId", profile.cameraId)
        root.put("targetId", profile.targetId)
        root.put("illuminant", profile.illuminant.name)
        root.put("capturedAtMs", profile.capturedAtMs)

        root.put("asShotNeutral", floatArrayToJson(dngColor.asShotNeutral))
        root.put("colorMatrix1", floatArrayToJson(dngColor.colorMatrix1))
        root.put("forwardMatrix1", floatArrayToJson(dngColor.forwardMatrix1))
        root.put("calibrationIlluminant1", dngColor.calibrationIlluminant1)

        return root.toString(2)
    }

    private fun floatArrayToJson(a: FloatArray): JSONArray {
        val ja = JSONArray()
        for (x in a) ja.put(x.toDouble())
        return ja
    }

    /** Companion filename for a gallery DNG `DISPLAY_NAME` (e.g. `pns_….dng`). */
    fun displayNameForSiblingDng(dngDisplayName: String): String {
        require(dngDisplayName.endsWith(".dng", ignoreCase = true)) {
            "expected .dng DISPLAY_NAME, was: $dngDisplayName"
        }
        return dngDisplayName.dropLast(4) + ".pns-calibration.json"
    }
}

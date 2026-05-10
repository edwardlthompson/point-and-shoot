package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range

/**
 * Builds discrete ISO / shutter / WB choices for readout-strip popup menus from
 * [CameraCharacteristics]. Used by [PreviewController.readoutMenuSnapshot].
 */
data class ReadoutMenuSnapshot(
    val isoChoices: List<Int?>,
    val exposureChoices: List<Long?>,
    val awbChoices: List<Int?>,
) {
    companion object {
        val EMPTY =
            ReadoutMenuSnapshot(
                listOf(null),
                listOf(null),
                listOf(null),
            )
    }
}

object ReadoutExposureCatalog {
    /** ISO stops commonly advertised; filtered to [SENSOR_INFO_SENSITIVITY_RANGE]. */
    private val isoTable =
        intArrayOf(
            50,
            64,
            80,
            100,
            125,
            160,
            200,
            250,
            320,
            400,
            500,
            640,
            800,
            1000,
            1250,
            1600,
            2000,
            2500,
            3200,
            4000,
            5000,
            6400,
            8000,
            10000,
            12800,
            16000,
            20000,
            25600,
            32000,
            40000,
            51200,
            102400,
        )

    fun isoChoices(chars: CameraCharacteristics): List<Int?> {
        val range = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return listOf(null)
        val list = ArrayList<Int?>()
        list.add(null)
        for (iso in isoTable) {
            if (iso in range.lower..range.upper) {
                list.add(iso)
            }
        }
        if (list.size == 1) {
            list.add(range.lower)
            if (range.upper != range.lower) {
                list.add(range.upper)
            }
        }
        return list
    }

    fun exposureChoices(chars: CameraCharacteristics): List<Long?> {
        val range = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: return listOf(null)
        val invDenoms =
            intArrayOf(
                8000,
                6000,
                4000,
                3200,
                2500,
                2000,
                1600,
                1250,
                1000,
                800,
                640,
                500,
                400,
                320,
                250,
                200,
                160,
                125,
                100,
                80,
                60,
                50,
                40,
                30,
                25,
                20,
                15,
                13,
                10,
                8,
                6,
                5,
                4,
                3,
                2,
                1,
            )
        val list = ArrayList<Long?>()
        list.add(null)
        for (d in invDenoms) {
            val ns = (1_000_000_000L / d.toLong()).coerceAtLeast(1000L)
            if (ns in range.lower..range.upper) {
                list.add(ns)
            }
        }
        for (sec in intArrayOf(2, 4, 8, 15, 30)) {
            val ns = sec * 1_000_000_000L
            if (ns in range.lower..range.upper) {
                list.add(ns)
            }
        }
        if (list.size == 1) {
            list.add(range.lower)
            if (range.upper != range.lower) {
                list.add(range.upper)
            }
        }
        return list.distinct().sortedWith(compareBy { it ?: 0L })
    }

    /**
     * AWB modes for popup; first entry is **null** = let the shooting program choose (no explicit override).
     */
    fun awbChoices(chars: CameraCharacteristics): List<Int?> {
        val avail = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf()
        val list = ArrayList<Int?>()
        list.add(null)
        val ordered =
            intArrayOf(
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
                CaptureRequest.CONTROL_AWB_MODE_OFF,
                CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT,
                CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT,
                CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT,
                CaptureRequest.CONTROL_AWB_MODE_TWILIGHT,
                CaptureRequest.CONTROL_AWB_MODE_SHADE,
            )
        val seen = HashSet<Int>()
        for (m in ordered) {
            if (avail.contains(m) && seen.add(m)) {
                list.add(m)
            }
        }
        for (m in avail) {
            if (!seen.contains(m)) {
                list.add(m)
                seen.add(m)
            }
        }
        return list
    }

    fun clampIso(
        range: Range<Int>?,
        value: Int,
    ): Int {
        if (range == null) return value
        return value.coerceIn(range.lower, range.upper)
    }

    fun clampExposure(
        range: Range<Long>?,
        value: Long,
    ): Long {
        if (range == null) return value
        return value.coerceIn(range.lower, range.upper)
    }
}

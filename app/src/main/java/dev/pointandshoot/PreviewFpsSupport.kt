package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Range
import android.util.Size
import dev.pointandshoot.fleet.FleetCapabilityGate

/**
 * Shared FPS catalog for quick settings: merges advertised Camera2 targets with
 * an extended ladder so root-only / vendor-unlock targets stay visible even when
 * [StreamConfigurationMap] does not list them yet.
 */
object PreviewFpsSupport {

    data class QuickFpsOption(
        val targetFps: Int,
        /** True when this target is not achievable via stock Camera2 on this camera. */
        val requiresRoot: Boolean,
    )

    private val extendedLadder =
        listOf(15, 24, 25, 30, 48, 50, 60, 90, 120, 144, 240, 480, 960, 1000)

    fun enumerateQuickFpsOptions(context: Context, cameraId: String?): List<QuickFpsOption> {
        if (cameraId.isNullOrBlank()) {
            return extendedLadder.map { QuickFpsOption(it, requiresRoot = false) }
        }
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val map =
            runCatching {
                cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull()

        val merged = linkedSetOf<Int>()
        merged.addAll(extendedLadder)
        runCatching { cm.getCameraCharacteristics(cameraId) }.getOrNull()?.let { chars ->
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.forEach { r ->
                merged.add(r.lower)
                merged.add(r.upper)
            }
            val sm = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            sm?.highSpeedVideoSizes?.forEach { s ->
                runCatching { sm.getHighSpeedVideoFpsRangesFor(s) }.getOrNull()?.forEach { r ->
                    merged.add(r.lower)
                    merged.add(r.upper)
                }
            }
        }

        val matrixHfrCeiling = FleetCapabilityGate.matrixHfrFpsCeiling(context, cameraId)
        return merged.sorted().map { fps ->
            val stockAchievable = canAchieveWithoutRoot(cm, cameraId, map, fps)
            val requiresRoot =
                when {
                    matrixHfrCeiling != null && fps > matrixHfrCeiling -> true
                    else -> !stockAchievable
                }
            QuickFpsOption(targetFps = fps, requiresRoot = requiresRoot)
        }.also { opts ->
            matrixHfrCeiling?.let { ceiling ->
                val maxStock = maxStockTargetFromOptions(opts)
                Log.d(
                    "PNS.FleetGate",
                    "fpsPicker cameraId=$cameraId matrixHfrCeiling=$ceiling maxStock=$maxStock",
                )
            }
        }
    }

    fun canAchieveWithoutRoot(
        cm: CameraManager,
        cameraId: String,
        map: StreamConfigurationMap?,
        desiredFps: Int,
    ): Boolean {
        if (pickNormalFpsRangeForCamera(cm, cameraId, desiredFps) != null) return true
        if (desiredFps >= 120 && pickHighSpeedTarget(map, desiredFps) != null) return true
        return false
    }

    private fun pickHighSpeedTarget(map: StreamConfigurationMap?, desiredFps: Int): Pair<Size, Range<Int>>? {
        if (map == null) return null
        val sizes = runCatching { map.highSpeedVideoSizes?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null
        val preferredOrder = listOf(Size(1920, 1080), Size(1280, 720))
        val candidateSizes = (preferredOrder.filter { p -> sizes.any { it == p } } + sizes).distinct()
        for (s in candidateSizes) {
            val ranges = runCatching { map.getHighSpeedVideoFpsRangesFor(s) }.getOrNull() ?: continue
            val exact = ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }
            if (exact != null) return s to exact
            val capped = ranges.firstOrNull { it.upper == desiredFps }
            if (capped != null) return s to capped
        }
        return null
    }

    private fun pickNormalFpsRangeForCamera(cm: CameraManager, camId: String, desiredFps: Int): Range<Int>? {
        if (desiredFps <= 0) return null
        val ranges =
            runCatching {
                cm.getCameraCharacteristics(camId).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            }.getOrNull().orEmpty()
        if (ranges.isEmpty()) return null
        ranges.firstOrNull { it.lower == desiredFps && it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.upper == desiredFps }?.let { return it }
        ranges.firstOrNull { it.lower <= desiredFps && it.upper >= desiredFps }?.let { return it }
        return null
    }

    internal fun maxStockTargetFromOptions(options: List<QuickFpsOption>): Int? =
        options.asSequence().filter { !it.requiresRoot }.map { it.targetFps }.maxOrNull()

    internal fun bestStockTargetAtOrBelow(options: List<QuickFpsOption>, ceiling: Int): Int? =
        options.asSequence().filter { !it.requiresRoot }.map { it.targetFps }.filter { it <= ceiling }.maxOrNull()

    /**
     * When the user switches **cameraId** (lens), keep [desiredFps] if it is still achievable without root
     * on the new camera; otherwise step down to the best stock target at or below the prior choice, then
     * the overall stock maximum, then **60** (photo-primary safe default).
     */
    fun clampFpsToAchievableWithoutRoot(context: Context, cameraId: String, desiredFps: Int): Int {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val map =
            runCatching {
                cm.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            }.getOrNull()
        if (canAchieveWithoutRoot(cm, cameraId, map, desiredFps)) return desiredFps
        val opts = enumerateQuickFpsOptions(context, cameraId)
        return bestStockTargetAtOrBelow(opts, desiredFps)
            ?: maxStockTargetFromOptions(opts)
            ?: 60
    }

    /**
     * Normal preview AE ranges often cap **4K preview** at 30 fps while the **encoder** still has an
     * exact H.264 performance point at 60 (CPH2583-class). Do not run [clampFpsToAchievableWithoutRoot]
     * on the video record target when the encode tier is probe-validated.
     */
    fun shouldSkipLensFpsClampForVideoEncode(
        encodeWidth: Int,
        encodeHeight: Int,
        desiredFps: Int,
        highSpeedMap: StreamConfigurationMap?,
    ): Boolean {
        if (desiredFps <= 0 || encodeWidth <= 0 || encodeHeight <= 0) return false
        val supportsAv1 = MediaCodecCapabilityProbe.supportsAv1Encoder()
        val h264 =
            VideoFormat(
                codec = VideoCodec.H264,
                resolution = Size(encodeWidth, encodeHeight),
                frameRate = desiredFps,
                bitrate = 0,
            )
        return InAppVideoFormatSelection.isFormatAvailableOnDevice(
            h264,
            highSpeedMap,
            supportsAv1,
        )
    }
}

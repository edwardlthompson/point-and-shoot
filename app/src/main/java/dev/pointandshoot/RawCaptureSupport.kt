package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import dev.pointandshoot.fleet.OnePlus13FleetPolicy
import dev.pointandshoot.fleet.StillDngBackend

/**
 * Host / ADB override for which advertised RAW stream to attach in preview (see
 * `pns_preview_raw_stream` in `CameraCapabilitiesProbe`).
 *
 * **Default** (in-tree): **RAW12 → RAW_SENSOR → RAW10** when all three are advertised — **USB-verified** scripted DNG
 * on **CPH2655-class** stacks (RAW10-first Milestone **10.1** order produced **ImageFormat 37** buffers that
 * **`DngCreator.writeImage`** rejected). Prefer **`pns_preview_raw_stream`** / **[Raw10Only]** for HAL matrix work.
 * Other values exist for OEM matrix testing where the advertised format does not deliver buffers.
 */
enum class RawStreamPreference {
    /** RAW12 → RAW_SENSOR → RAW10 (fleet default; probe `rawPickEffective=` matches this). */
    Default,

    /** RAW_SENSOR → RAW12 → RAW10 — tests HALs that advertise RAW12 but only fill RAW_SENSOR. */
    RawSensorFirst,

    /** Largest RAW12 only; `null` if unsupported. */
    Raw12Only,

    /** Largest RAW_SENSOR only; `null` if unsupported. */
    RawSensorOnly,

    /** Largest RAW10 only; `null` if unsupported (often incompatible with [DngCreator]). */
    Raw10Only,
}

/**
 * RAW still helpers for Phase 1 Camera2 capture ([BUILD_PLAN.md] §4).
 */
object RawCaptureSupport {

    /**
     * When true, [pickRawForLogicalMulticamPinnedAux] prefers [RawStreamPreference.RawSensorOnly] /
     * [RawStreamPreference.RawSensorFirst] — logical multi-camera with preview pinned to a non-wide
     * physical id while the session id stays the logical parent.
     */
    internal fun shouldPreferRawSensorForAuxPhysicalPreviewPin(
        userPreference: RawStreamPreference,
        logicalPhysicalChildren: Set<String>,
        previewPhysicalCameraId: String?,
        wideBackCameraId: String?,
    ): Boolean {
        if (userPreference != RawStreamPreference.Default) return false
        if (logicalPhysicalChildren.isEmpty()) return false
        val pin =
            previewPhysicalCameraId?.takeIf { it.isNotBlank() && it in logicalPhysicalChildren }
                ?: return false
        val wide = wideBackCameraId ?: return false
        return pin != wide
    }

    /**
     * Logical multi-camera on the **parent** [CameraDevice] id: prefer [ImageFormat.RAW_SENSOR] when
     * tele digital-eq is active but the HAL dropped preview [OutputConfiguration] physical pin
     * (still routes aux pixels on the logical RAW stream).
     */
    internal fun shouldPreferRawSensorForLogicalTeleFocalCrop(
        userPreference: RawStreamPreference,
        logicalPhysicalChildren: Set<String>,
        focalCropMode: FocalMode?,
    ): Boolean {
        if (userPreference != RawStreamPreference.Default) return false
        if (logicalPhysicalChildren.isEmpty()) return false
        return when (focalCropMode) {
            FocalMode.Portrait85, FocalMode.LongTele150 -> true
            else -> false
        }
    }

    private fun preferRawSensorForAuxBackStill(
        userPreference: RawStreamPreference,
        logicalPhysicalChildren: Set<String>,
        previewPhysicalCameraId: String?,
        wideBackCameraId: String?,
        focalCropMode: FocalMode?,
    ): Boolean =
        shouldPreferRawSensorForAuxPhysicalPreviewPin(
            userPreference,
            logicalPhysicalChildren,
            previewPhysicalCameraId,
            wideBackCameraId,
        ) ||
            shouldPreferRawSensorForLogicalTeleFocalCrop(
                userPreference,
                logicalPhysicalChildren,
                focalCropMode,
            )

    /**
     * **Leaf** back camera (no [CameraCharacteristics.getPhysicalCameraIds]) whose id is not the
     * resolved wide role — typical **UW / tele** opened as `cameraId=3` / `4` while wide stays `2`.
     * There is no preview [OutputConfiguration] pin on these sessions ([schedulePreviewPhysicalForFocalSlot]
     * only pins tele when `pair.first == logicalParent`), so [shouldPreferRawSensorForAuxPhysicalPreviewPin]
     * never ran; **RAW12** still decodes as dark/green in DNG while JPEG looks fine.
     */
    internal fun shouldUseLeafNonWideBackRawSensorPolicy(
        userPreference: RawStreamPreference,
        sessionCameraId: String,
        wideBackCameraId: String?,
        logicalPhysicalChildren: Set<String>,
        lensFacing: Int?,
    ): Boolean {
        if (userPreference != RawStreamPreference.Default) return false
        if (wideBackCameraId == null || sessionCameraId == wideBackCameraId) return false
        if (logicalPhysicalChildren.isNotEmpty()) return false
        if (lensFacing != null && lensFacing != CameraCharacteristics.LENS_FACING_BACK) return false
        return true
    }

    /**
     * RAW [ImageReader] format/size for the active preview session. Handles:
     * - **Leaf** non-wide back cameras (prefer `RAW_SENSOR` over packed `RAW12` for DngCreator).
     * - **Logical** multi-camera with a **non-wide** preview physical pin: prefer **`RAW_SENSOR` /
     *   `RAW_SENSOR`-first** from the **logical** stream map (same route as unpinned session RAW).
     *   [pickRawOutput] **Default** would try **RAW12** first on that map; some HALs still deliver plain
     *   Bayer for aux slots — wrong packing vs [DngCreator] + logical metadata reads as dark / green
     *   (CPH2655-class). This stays on the logical map (unlike [pickRawForLogicalMulticamPinnedAux]).
     * - **Logical** default: by default RAW is negotiated from the **logical** stream map so it
     *   matches **unpinned** RAW outputs (preview-only [OutputConfiguration.setPhysicalCameraId]).
     *   Use [usePhysicalChildRawStreamMapForLogicalSession] only when RAW/JPEG outputs are pinned to
     *   the same physical id **and** USB proof shows per-physical [TotalCaptureResult] for DNG.
     * Explicit [RawStreamPreference] from ADB / HUD (non-[RawStreamPreference.Default]) is never overridden.
     */
    fun pickRawOutputForPreviewSession(
        cm: CameraManager,
        cameraIds: List<String>,
        sessionCameraId: String,
        sessionCharacteristics: CameraCharacteristics?,
        previewPhysicalCameraId: String?,
        userPreference: RawStreamPreference,
        /** Active [PreviewController.setFocalCrop] mode; used when logical tele slots lose HAL preview pin. */
        focalCropMode: FocalMode? = null,
        /**
         * When **true**, non-wide preview physical pins may pick RAW size/format from that child's
         * [CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP] (legacy path; requires RAW surface
         * physically pinned). **Default false** (shipped): keep RAW pick on the **logical** map so
         * dimensions and packing match the unpinned RAW stream (avoids dark / green DNG on OEMs
         * with empty [TotalCaptureResult.physicalCameraTotalResults]).
         */
        usePhysicalChildRawStreamMapForLogicalSession: Boolean = false,
    ): Pair<Int, Size>? {
        val chars = sessionCharacteristics ?: return null
        if (userPreference != RawStreamPreference.Default) {
            return pickRawOutput(chars, userPreference)
        }
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) {
            return pickRawOutput(chars, userPreference)
        }
        val roles = BackCameraRoleResolver.resolve(cm, cameraIds)
        val wide = roles.wide
        val sessionChildren =
            runCatching { chars.physicalCameraIds?.toSet().orEmpty() }.getOrDefault(emptySet())
        if (OnePlus13FleetPolicy.appliesToDevice() && isLeafBackSession(sessionChildren, facing)) {
            when (OnePlus13FleetPolicy.stillDngBackend()) {
                StillDngBackend.MOTIONCAM_INSPIRED ->
                    pickRawAtActiveArrayRawSensor(chars)?.let { return it }
                StillDngBackend.FRAMEWORK_PROSHOT ->
                    pickRawOutputFromFormatOrder(chars, OnePlus13FleetPolicy.LEAF_RAW_FORMAT_ORDER)
                        ?.let { return it }
                StillDngBackend.MOTIONCAM_NATIVE -> Unit
            }
        }
        if (shouldUseLeafNonWideBackRawSensorPolicy(
                userPreference,
                sessionCameraId,
                wide,
                sessionChildren,
                facing,
            )
        ) {
            return pickRawOutput(chars, RawStreamPreference.RawSensorOnly)
                ?: pickRawOutput(chars, RawStreamPreference.RawSensorFirst)
                ?: pickRawOutput(chars, userPreference)
        }
        if (preferRawSensorForAuxBackStill(
                userPreference,
                sessionChildren,
                previewPhysicalCameraId,
                wide,
                focalCropMode,
            )
        ) {
            return pickRawOutput(chars, RawStreamPreference.RawSensorOnly)
                ?: pickRawOutput(chars, RawStreamPreference.RawSensorFirst)
                ?: pickRawOutput(chars, userPreference)
        }
        if (usePhysicalChildRawStreamMapForLogicalSession) {
            pickRawForLogicalMulticamPinnedAux(cm, cameraIds, chars, previewPhysicalCameraId, userPreference)
                ?.let { return it }
        }
        return pickRawOutput(chars, userPreference)
    }

    private fun pickRawForLogicalMulticamPinnedAux(
        cm: CameraManager,
        cameraIds: List<String>,
        chars: CameraCharacteristics,
        previewPhysicalCameraId: String?,
        userPreference: RawStreamPreference,
    ): Pair<Int, Size>? {
        val physicalChildren =
            runCatching { chars.physicalCameraIds?.toSet().orEmpty() }.getOrDefault(emptySet())
        val wide = BackCameraRoleResolver.resolve(cm, cameraIds).wide
        if (!preferRawSensorForAuxBackStill(userPreference, physicalChildren, previewPhysicalCameraId, wide, null)) {
            return null
        }
        val pin = previewPhysicalCameraId ?: return null
        val pinTrimmed = pin.trim().takeIf { it.isNotBlank() } ?: return null
        val auxSensorOnlyFirst = RawStreamPreference.RawSensorOnly
        val auxSensorFirst = RawStreamPreference.RawSensorFirst
        val physChars = runCatching { cm.getCameraCharacteristics(pinTrimmed) }.getOrNull()
        val fromPhysical =
            physChars?.let { pc ->
                pickRawOutput(pc, auxSensorOnlyFirst)
                    ?: pickRawOutput(pc, auxSensorFirst)
            }
        return fromPhysical
            ?: pickRawOutput(chars, auxSensorOnlyFirst)
            ?: pickRawOutput(chars, auxSensorFirst)
    }

    /**
     * JVM-testable core for [useNeutralColorPipelineForRawStill] (no [CameraManager]).
     *
     * @param sessionPhysicalChildren [CameraCharacteristics.getPhysicalCameraIds] for the **session** camera.
     */
    internal fun useNeutralColorPipelineForRawStillCore(
        wideBackCameraId: String?,
        sessionPhysicalChildren: Set<String>,
        lensFacing: Int?,
        sessionCameraId: String,
        previewPhysicalCameraId: String?,
        focalCropMode: FocalMode? = null,
    ): Boolean {
        val wide = wideBackCameraId ?: return false
        if (sessionPhysicalChildren.isEmpty()) {
            // Leaf physical id (UW/tele/wide): keep RAW_SENSOR pick in [pickRawOutputForPreviewSession],
            // but still apply ISP color correction on the still request — ProShot does; skipping CC
            // here mis-tags DngCreator metadata on CPH2655 aux (dark / green cast).
            return false
        }
        return preferRawSensorForAuxBackStill(
            RawStreamPreference.Default,
            sessionPhysicalChildren,
            previewPhysicalCameraId,
            wide,
            focalCropMode,
        )
    }

    /**
     * RAW+JPEG still capture: skip HQ color correction from [PreviewJpegProcessingHints] only on
     * **logical** sessions with a non-wide preview physical pin (or tele focal crop) — not on
     * leaf physical UW/tele/wide opens (ProShot-aligned CC on those stills).
     */
    fun useNeutralColorPipelineForRawStill(
        cm: CameraManager,
        cameraIds: List<String>,
        sessionCharacteristics: CameraCharacteristics,
        sessionCameraId: String,
        previewPhysicalCameraId: String?,
        focalCropMode: FocalMode? = null,
    ): Boolean {
        val wide = BackCameraRoleResolver.resolve(cm, cameraIds).wide ?: return false
        val children =
            runCatching { sessionCharacteristics.physicalCameraIds?.toSet().orEmpty() }
                .getOrDefault(emptySet())
        return useNeutralColorPipelineForRawStillCore(
            wide,
            children,
            sessionCharacteristics.get(CameraCharacteristics.LENS_FACING),
            sessionCameraId,
            previewPhysicalCameraId,
            focalCropMode,
        )
    }

    /** Same as [pickRawOutput] with [RawStreamPreference.Default] (RAW12 → RAW_SENSOR → RAW10). */
    fun pickRawOutput(characteristics: CameraCharacteristics): Pair<Int, Size>? =
        pickRawOutput(characteristics, RawStreamPreference.Default)

    /**
     * Same as [pickRawOutput] but honors [preference] for matrix / OEM diagnostics
     * (`pns_preview_raw_stream` ADB extra).
     */
    fun pickRawOutput(
        characteristics: CameraCharacteristics,
        preference: RawStreamPreference,
    ): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        return pickRawOutputFromMaps(
            raw12 = runCatching { map.getOutputSizes(ImageFormat.RAW12)?.toList() }.getOrNull(),
            raw10 = runCatching { map.getOutputSizes(ImageFormat.RAW10)?.toList() }.getOrNull(),
            rawSensor = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull(),
            preference = preference,
        )
    }

    internal fun isLeafBackSession(
        sessionPhysicalChildren: Set<String>,
        lensFacing: Int?,
    ): Boolean =
        lensFacing == CameraCharacteristics.LENS_FACING_BACK && sessionPhysicalChildren.isEmpty()

    /**
     * MotionCam-style: prefer [ImageFormat.RAW_SENSOR] at [SENSOR_INFO_ACTIVE_ARRAY_SIZE] when listed,
     * else largest RAW_SENSOR (not ProShot's max-area pick across formats).
     */
    internal fun pickRawAtActiveArrayRawSensor(characteristics: CameraCharacteristics): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        val active =
            characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return null
        val sizes =
            runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null
        val target = Size(active.width(), active.height())
        sizes.firstOrNull { it.width == target.width && it.height == target.height }
            ?.let { return ImageFormat.RAW_SENSOR to it }
        val largest =
            sizes.maxByOrNull { it.width.toLong() * it.height }
                ?: return null
        return ImageFormat.RAW_SENSOR to largest
    }

    /**
     * ProShot leaf RAW pick order on the **opened** camera map (Milestone **13.3c**).
     */
    internal fun pickRawOutputFromFormatOrder(
        characteristics: CameraCharacteristics,
        formatOrder: List<Int>,
    ): Pair<Int, Size>? {
        val map =
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: return null
        fun largest(fmt: Int): Size? {
            val sizes = runCatching { map.getOutputSizes(fmt)?.toList() }.getOrNull().orEmpty()
            return sizes.takeIf { it.isNotEmpty() }?.maxByOrNull { it.width.toLong() * it.height }
        }
        for (fmt in formatOrder) {
            largest(fmt)?.let { return fmt to it }
        }
        return null
    }

    /**
     * JVM tests: pick RAW_SENSOR WxH matching active array from size pairs.
     */
    internal fun pickRawSensorWxHForActiveArray(
        rawSensorSizes: List<Pair<Int, Int>>,
        activeW: Int,
        activeH: Int,
    ): Pair<Int, Int>? {
        if (rawSensorSizes.isEmpty() || activeW <= 0 || activeH <= 0) return null
        rawSensorSizes.firstOrNull { it.first == activeW && it.second == activeH }?.let { return it }
        return rawSensorSizes.maxByOrNull { it.first.toLong() * it.second }
    }

    /**
     * Pure RAW pick used by [pickRawOutput] and unit tests (no [CameraCharacteristics] required).
     */
    internal fun pickRawOutputFromMaps(
        raw12: List<Size>?,
        raw10: List<Size>?,
        rawSensor: List<Size>?,
        preference: RawStreamPreference = RawStreamPreference.Default,
    ): Pair<Int, Size>? {
        fun largest(sizes: List<Size>?): Size? =
            sizes?.takeIf { it.isNotEmpty() }?.maxByOrNull { it.width.toLong() * it.height }
        return when (preference) {
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.RawSensorFirst ->
                largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.Raw12Only -> largest(raw12)?.let { ImageFormat.RAW12 to it }
            RawStreamPreference.RawSensorOnly -> largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
            RawStreamPreference.Raw10Only -> largest(raw10)?.let { ImageFormat.RAW10 to it }
        }
    }

    /** Label for probe `rawPickEffective=` lines (Milestone **10.1**). */
    fun rawPickEffectiveLabel(format: Int?): String =
        when (format) {
            ImageFormat.RAW12 -> "RAW12"
            ImageFormat.RAW10 -> "RAW10"
            ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
            else -> "null"
        }

    /**
     * Largest JPEG still size from [StreamConfigurationMap], or `null` if the device
     * does not advertise [ImageFormat.JPEG] outputs.
     */
    fun pickLargestJpegSize(map: StreamConfigurationMap): Size? {
        val sizes =
            runCatching { map.getOutputSizes(ImageFormat.JPEG)?.toList() }.getOrNull().orEmpty()
        if (sizes.isEmpty()) return null
        return sizes.maxByOrNull { it.width.toLong() * it.height }
    }

    /**
     * Clockwise rotation (0, 90, 180, 270) for [Dng12Saver] / EXIF orientation,
     * from sensor characteristics + [Surface] rotation constant.
     */
    fun orientationClockwiseDegForDng(
        characteristics: CameraCharacteristics,
        surfaceRotation: Int,
    ): Int {
        val sensorOrientation =
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val deviceOrientationDegrees =
            when (surfaceRotation) {
                Surface.ROTATION_0 -> 0
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensorOrientation + deviceOrientationDegrees + 360) % 360
        } else {
            (sensorOrientation - deviceOrientationDegrees + 360) % 360
        }
    }

    /**
     * Maps [snapPhysicalCardinal] / [DeviceUiRotationState.physicalCardinalSnapDegrees] (0 =
     * natural portrait up, 90 = …, 270 = …) to [Surface.ROTATION_0]…[Surface.ROTATION_270] in
     * the same numeric space as [android.view.Display.getRotation], so it can be passed where
     * [orientationClockwiseDegForDng] expects a display rotation constant.
     *
     * See [DeviceUiRotationTest]: e.g. physical 270 (natural-RIGHT up, `ax ≈ +g`) matches
     * `Surface.ROTATION_90` on a portrait-natural phone.
     */
    fun surfaceRotationFromPhysicalCardinalSnap(physicalCardinalDeg: Int): Int {
        val d = ((physicalCardinalDeg % 360) + 360) % 360
        return when (d) {
            0 -> Surface.ROTATION_0
            90 -> Surface.ROTATION_270
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_90
            else -> Surface.ROTATION_0
        }
    }
}

fun Context.displayRotationCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    }

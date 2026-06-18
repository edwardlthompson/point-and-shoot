package dev.pointandshoot

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import dev.pointandshoot.LeafDngFleetPolicies
import dev.pointandshoot.StillDngBackend

/**
 * Host / ADB override for which advertised RAW stream to attach in preview (see
 * `pns_preview_raw_stream` in `CameraCapabilitiesProbe`).
 *
 * **Default** (in-tree): **RAW12 → RAW_SENSOR → RAW10** when all three are advertised — **USB-verified** scripted DNG
 * on legacy-class stacks (RAW10-first Milestone **10.1** order produced **ImageFormat 37** buffers that
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
    private const val TAG = "PNS.Cam"
    private const val ENABLE_EXPERIMENTAL_MULTIRES_DIRECT_SIZE = true

    /**
     * Locked [RawStreamPreference.Default] tier order (REG-20260513-002): **RAW12 → RAW_SENSOR → RAW10**.
     * Do not reorder without USB [pns_photo_capture_verify.ps1] / [pns_capture_pipeline_verify.ps1] proof.
     */
    internal val DEFAULT_RAW_STREAM_TIER_ORDER: IntArray =
        intArrayOf(
            ImageFormat.RAW12,
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW10,
        )
    private fun area(size: Size?): Long =
        if (size == null) -1L else size.width.toLong() * size.height.toLong()

    private fun largerOf(a: Size?, b: Size?): Size? =
        when {
            a == null -> b
            b == null -> a
            area(b) > area(a) -> b
            else -> a
        }

    /**
     * Max-resolution default policy keeps RAW10 last but prefers the larger stream between RAW12 and
     * RAW_SENSOR so `max_resolution` can actually increase effective still dimensions when supported.
     */
    internal fun chooseDefaultRawFormatForMaxResolution(
        raw12Area: Long?,
        rawSensorArea: Long?,
        raw10Area: Long?,
    ): Int? {
        val r12 = raw12Area ?: -1L
        val rs = rawSensorArea ?: -1L
        val r10 = raw10Area ?: -1L
        if (r12 < 0 && rs < 0 && r10 < 0) return null
        if (r12 >= 0 || rs >= 0) {
            return if (r12 >= rs) ImageFormat.RAW12 else ImageFormat.RAW_SENSOR
        }
        return if (r10 >= 0) ImageFormat.RAW10 else null
    }


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
     *   (legacy-class). This stays on the logical map (unlike [pickRawForLogicalMulticamPinnedAux]).
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
        photoResolutionMode: PhotoResolutionMode = PhotoResolutionMode.Binned,
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
            return pickRawOutput(chars, userPreference, photoResolutionMode)
        }
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        if (facing != null && facing != CameraCharacteristics.LENS_FACING_BACK) {
            return pickRawOutput(chars, userPreference, photoResolutionMode)
        }
        val roles = BackCameraRoleResolver.resolve(cm, cameraIds)
        val wide = roles.wide
        val sessionChildren =
            runCatching { chars.physicalCameraIds?.toSet().orEmpty() }.getOrDefault(emptySet())
        if (LeafDngFleetPolicies.active.appliesToDevice() && isLeafBackSession(sessionChildren, facing)) {
            when (LeafDngFleetPolicies.active.stillDngBackend()) {
                StillDngBackend.ALTREFERENCEAPP_INSPIRED ->
                    pickRawAtActiveArrayRawSensor(chars)?.let { return it }
                StillDngBackend.FRAMEWORK_REFERENCEAPP ->
                    pickRawOutputFromFormatOrder(chars, LeafDngFleetPolicies.active.leafRawFormatOrder())
                        ?.let { return it }
                StillDngBackend.ALTREFERENCEAPP_NATIVE -> Unit
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
                ?: pickRawOutput(chars, RawStreamPreference.RawSensorFirst, photoResolutionMode)
                ?: pickRawOutput(chars, userPreference, photoResolutionMode)
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
                ?: pickRawOutput(chars, RawStreamPreference.RawSensorFirst, photoResolutionMode)
                ?: pickRawOutput(chars, userPreference, photoResolutionMode)
        }
        if (usePhysicalChildRawStreamMapForLogicalSession) {
            pickRawForLogicalMulticamPinnedAux(cm, cameraIds, chars, previewPhysicalCameraId, userPreference)
                ?.let { return it }
        }
        return pickRawOutput(chars, userPreference, photoResolutionMode)
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
            // but still apply ISP color correction on the still request — ReferenceCam does; skipping CC
            // here mis-tags DngCreator metadata on legacy aux (dark / green cast).
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
     * leaf physical UW/tele/wide opens (ReferenceCam-aligned CC on those stills).
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
        pickRawOutput(characteristics, RawStreamPreference.Default, PhotoResolutionMode.Binned)

    /**
     * Same as [pickRawOutput] but honors [preference] for matrix / OEM diagnostics
     * (`pns_preview_raw_stream` ADB extra).
     */
    fun pickRawOutput(
        characteristics: CameraCharacteristics,
        preference: RawStreamPreference,
        photoResolutionMode: PhotoResolutionMode = PhotoResolutionMode.Binned,
    ): Pair<Int, Size>? {
        val defaultMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: return null
        if (photoResolutionMode != PhotoResolutionMode.MaxResolution || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return pickRawOutputFromMap(defaultMap, preference)
        }
        val maxMap =
            runCatching {
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
            }.getOrNull()
        if (maxMap == null) {
            return pickRawOutputFromMap(defaultMap, preference)
        }
        if (preference != RawStreamPreference.Default) {
            return pickRawOutputFromMap(maxMap, preference) ?: pickRawOutputFromMap(defaultMap, preference)
        }

        val maxRaw12 = largestRawSize(maxMap, ImageFormat.RAW12)
        val maxRawSensor = largestRawSize(maxMap, ImageFormat.RAW_SENSOR)
        val maxRaw10 = largestRawSize(maxMap, ImageFormat.RAW10)
        val defaultRaw12 = largestRawSize(defaultMap, ImageFormat.RAW12)
        val defaultRawSensor = largestRawSize(defaultMap, ImageFormat.RAW_SENSOR)
        val defaultRaw10 = largestRawSize(defaultMap, ImageFormat.RAW10)
        val hasLargerRawCandidate =
            area(maxRaw12) > area(defaultRaw12) ||
                area(maxRawSensor) > area(defaultRawSensor) ||
                area(maxRaw10) > area(defaultRaw10)

        val mergedRaw12 = largerOf(maxRaw12, defaultRaw12)
        val mergedRawSensor = largerOf(maxRawSensor, defaultRawSensor)
        val mergedRaw10 = largerOf(maxRaw10, defaultRaw10)
        Log.d(
            TAG,
            "maxResRawSupport larger=$hasLargerRawCandidate " +
                "r12Def=${defaultRaw12?.width}x${defaultRaw12?.height} r12Max=${maxRaw12?.width}x${maxRaw12?.height} " +
                "rsDef=${defaultRawSensor?.width}x${defaultRawSensor?.height} rsMax=${maxRawSensor?.width}x${maxRawSensor?.height} " +
                "r10Def=${defaultRaw10?.width}x${defaultRaw10?.height} r10Max=${maxRaw10?.width}x${maxRaw10?.height}",
        )

        return when (
            chooseDefaultRawFormatForMaxResolution(
                raw12Area = area(mergedRaw12).takeIf { it >= 0L },
                rawSensorArea = area(mergedRawSensor).takeIf { it >= 0L },
                raw10Area = area(mergedRaw10).takeIf { it >= 0L },
            )
        ) {
            ImageFormat.RAW12 -> ImageFormat.RAW12 to mergedRaw12!!
            ImageFormat.RAW_SENSOR -> ImageFormat.RAW_SENSOR to mergedRawSensor!!
            ImageFormat.RAW10 -> ImageFormat.RAW10 to mergedRaw10!!
            else -> pickRawOutputFromMap(maxMap, RawStreamPreference.Default) ?: pickRawOutputFromMap(defaultMap, RawStreamPreference.Default)
        }
    }

    internal fun isLeafBackSession(
        sessionPhysicalChildren: Set<String>,
        lensFacing: Int?,
    ): Boolean =
        lensFacing == CameraCharacteristics.LENS_FACING_BACK && sessionPhysicalChildren.isEmpty()

    /**
     * AltReferenceApp-style: prefer [ImageFormat.RAW_SENSOR] at [SENSOR_INFO_ACTIVE_ARRAY_SIZE] when listed,
     * else largest RAW_SENSOR (not ReferenceCam's max-area pick across formats).
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
     * ReferenceCam leaf RAW pick order on the **opened** camera map (Milestone **13.3c**).
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
                pickDefaultTierFromMaps(raw12, raw10, rawSensor)
            RawStreamPreference.RawSensorFirst ->
                largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.Raw12Only -> largest(raw12)?.let { ImageFormat.RAW12 to it }
            RawStreamPreference.RawSensorOnly -> largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
            RawStreamPreference.Raw10Only -> largest(raw10)?.let { ImageFormat.RAW10 to it }
        }
    }

    private fun pickDefaultTierFromMaps(
        raw12: List<Size>?,
        raw10: List<Size>?,
        rawSensor: List<Size>?,
    ): Pair<Int, Size>? {
        fun largest(sizes: List<Size>?): Size? =
            sizes?.takeIf { it.isNotEmpty() }?.maxByOrNull { it.width.toLong() * it.height }
        for (format in DEFAULT_RAW_STREAM_TIER_ORDER) {
            val size =
                when (format) {
                    ImageFormat.RAW12 -> largest(raw12)
                    ImageFormat.RAW_SENSOR -> largest(rawSensor)
                    ImageFormat.RAW10 -> largest(raw10)
                    else -> null
                }
            if (size != null) return format to size
        }
        return null
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
        return largestSizeForFormat(map, ImageFormat.JPEG, includeHighRes = true)
    }

    fun pickJpegOutputSizeForStill(
        characteristics: CameraCharacteristics,
        photoResolutionMode: PhotoResolutionMode,
    ): Size? {
        val defaultMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        if (photoResolutionMode != PhotoResolutionMode.MaxResolution || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return pickLargestJpegSize(defaultMap)
        }
        val maxMap =
            runCatching {
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
            }.getOrNull()
        val maxJpeg = maxMap?.let { pickLargestJpegSize(it) }
        val defaultJpeg = pickLargestJpegSize(defaultMap)
        val multiResJpeg = pickLargestMultiResolutionOutputSize(characteristics, ImageFormat.JPEG)
        val chosen =
            if (ENABLE_EXPERIMENTAL_MULTIRES_DIRECT_SIZE) {
                largerOf(largerOf(maxJpeg, defaultJpeg), multiResJpeg)
            } else {
                largerOf(maxJpeg, defaultJpeg)
            }
        val hasLargerJpegCandidate = area(maxJpeg) > area(defaultJpeg)
        Log.d(
            TAG,
            "maxResJpegSupport larger=$hasLargerJpegCandidate " +
                "jpegDef=${defaultJpeg?.width}x${defaultJpeg?.height} jpegMax=${maxJpeg?.width}x${maxJpeg?.height} " +
                "jpegMultiRes=${multiResJpeg?.width}x${multiResJpeg?.height} " +
                "multiResDirect=$ENABLE_EXPERIMENTAL_MULTIRES_DIRECT_SIZE chosen=${chosen?.width}x${chosen?.height}",
        )
        return chosen
    }

    private fun largestRawSize(
        map: StreamConfigurationMap,
        format: Int,
    ): Size? {
        return largestSizeForFormat(map, format, includeHighRes = true)
    }

    private fun largestSizeForFormat(
        map: StreamConfigurationMap,
        format: Int,
        includeHighRes: Boolean,
    ): Size? {
        val base = runCatching { map.getOutputSizes(format)?.toList() }.getOrNull().orEmpty()
        val high =
            if (includeHighRes) {
                runCatching { map.getHighResolutionOutputSizes(format)?.toList() }.getOrNull().orEmpty()
            } else {
                emptyList()
            }
        val all = (base + high)
            .distinctBy { it.width to it.height }
        return all
            .takeIf { it.isNotEmpty() }
            ?.maxByOrNull { it.width.toLong() * it.height }
    }

    private fun pickRawOutputFromMap(
        map: StreamConfigurationMap,
        preference: RawStreamPreference,
    ): Pair<Int, Size>? =
        pickRawOutputFromMaps(
            raw12 = runCatching { map.getOutputSizes(ImageFormat.RAW12)?.toList() }.getOrNull(),
            raw10 = runCatching { map.getOutputSizes(ImageFormat.RAW10)?.toList() }.getOrNull(),
            rawSensor = runCatching { map.getOutputSizes(ImageFormat.RAW_SENSOR)?.toList() }.getOrNull(),
            preference = preference,
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T> keyByName(chars: CameraCharacteristics, name: String): CameraCharacteristics.Key<T>? =
        chars.keys.firstOrNull { it.name == name } as? CameraCharacteristics.Key<T>

    /**
     * Experimental: pull max candidate sizes from multi-resolution output info (API 31+). This is
     * best-effort reflection so older API behavior remains safe.
     */
    private fun pickLargestMultiResolutionOutputSize(
        characteristics: CameraCharacteristics,
        format: Int,
    ): Size? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val key =
            keyByName<Any>(characteristics, "android.scaler.multiResolutionStreamConfigurationMap")
                ?: return null
        val map = runCatching { characteristics.get(key) }.getOrNull() ?: return null
        val infos = runCatching {
            val m = map.javaClass.getMethod("getOutputInfo", Int::class.javaPrimitiveType)
            m.invoke(map, format) as? Iterable<*>
        }.getOrNull() ?: return null
        var best: Size? = null
        for (info in infos) {
            if (info == null) continue
            val w = runCatching { info.javaClass.getMethod("getWidth").invoke(info) as Int }.getOrNull() ?: continue
            val h = runCatching { info.javaClass.getMethod("getHeight").invoke(info) as Int }.getOrNull() ?: continue
            best = largerOf(best, Size(w, h))
        }
        return best
    }

    private fun pickStillStreamConfigurationMap(
        characteristics: CameraCharacteristics,
        photoResolutionMode: PhotoResolutionMode,
    ): StreamConfigurationMap? {
        val defaultMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        if (photoResolutionMode != PhotoResolutionMode.MaxResolution) {
            return defaultMap
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return defaultMap
        }
        val maxMap = runCatching { characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION) }.getOrNull()
        return maxMap ?: defaultMap
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

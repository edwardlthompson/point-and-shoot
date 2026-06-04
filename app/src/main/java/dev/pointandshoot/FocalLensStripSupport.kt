package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dev.pointandshoot.fleet.FleetCameraProfiles
import dev.pointandshoot.fleet.FleetFocalRowPolicy
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Milestone **10.2** — focal row: **native** focal hints, **≥12 MP** gate for digital-eq. slots,
 * front-camera tele dimming, and shallow-cache **rescan** hint timing (see `BUILD_PLAN.md`).
 */
object FocalLensStripSupport {

    /** Window after shallow rescan prefs bump where we may show a non-blocking calibrating hint. */
    const val FOCAL_MAP_HINT_POST_RESCAN_MS: Long = 12_000L

    private const val NATIVE_MM_ROUND_SCALE = 10f
    private const val NATIVE_MM_INTEGER_EPSILON = 1e-3f
    // Exclude tiny/non-imaging auxiliary sensors from fleet focal slot routing.
    private const val MIN_FOCAL_ROUTING_SENSOR_MP = 2.0
    private val PRIME_EQ_MM = listOf(14, 16, 20, 24, 28, 35, 40, 50, 85, 100, 135, 200)

    fun isTeleSlot(slot: FocalMmSlot): Boolean =
        when (slot) {
            FocalMmSlot.M73, FocalMmSlot.M85, FocalMmSlot.M150 -> true
            else -> false
        }

    data class PrimeLensCandidate(
        val cameraId: String,
        val nativeEqMm: Int,
        val focalMm: Float,
        val sensorMp: Double,
    )

    data class PrimeLensAssignment(
        val targetEqMm: Int,
        val cameraId: String,
        val nativeEqMm: Int,
        val focalMm: Float,
        val effectiveMp: Double,
    ) {
        val isNative: Boolean
            get() = targetEqMm == nativeEqMm
    }

    /** Fixed 35mm-equivalent prime set requested for the chrome focal row. */
    fun primeEqTargets(): List<Int> = PRIME_EQ_MM

    /** Native camera 35mm-equivalent map for active camera ids. */
    fun cameraNativeEqById(context: Context, ids: List<String>): Map<String, Int> =
        collectPrimeLensCandidates(context, ids).associate { it.cameraId to it.nativeEqMm }

    /**
     * Assign each prime-equivalent focal target to exactly one camera:
     * - crop-only (`target >= native`)
     * - keep effective output >= 12 MP
     * - choose the candidate with highest effective MP
     */
    fun resolvePrimeLensAssignments(
        context: Context,
        ids: List<String>,
    ): List<PrimeLensAssignment> {
        return resolvePrimeLensAssignments(context, ids, PRIME_EQ_MM)
    }

    /**
     * Mathematical fleet resolver for arbitrary 35mm-equivalent [targets]. Picks the camera that
     * needs the least crop first, then resolves ties by effective output MP and native preference.
     */
    fun resolvePrimeLensAssignments(
        context: Context,
        ids: List<String>,
        targets: List<Int>,
    ): List<PrimeLensAssignment> {
        val candidates = collectPrimeLensCandidates(context, ids)
        if (candidates.isEmpty()) return emptyList()
        val normalizedTargets = targets.filter { it > 0 }.distinct().sorted()
        if (normalizedTargets.isEmpty()) return emptyList()
        return resolvePrimeLensAssignmentsFromCandidates(candidates, normalizedTargets)
    }

    internal fun resolvePrimeLensAssignmentsFromCandidates(
        candidates: List<PrimeLensCandidate>,
        targets: List<Int>,
    ): List<PrimeLensAssignment> {
        if (candidates.isEmpty()) return emptyList()
        return buildList {
            for (target in targets) {
                val cropCompatible =
                    candidates
                        .asSequence()
                        .filter { target >= it.nativeEqMm }
                        .map { c ->
                            Triple(
                                c,
                                effectiveMpForCrop(
                                    sensorMp = c.sensorMp,
                                    nativeEqMm = c.nativeEqMm,
                                    targetEqMm = target,
                                ),
                                target == c.nativeEqMm,
                            )
                        }.toList()
                val bestCropCompatible =
                    cropCompatible.minWithOrNull(
                        compareBy<Triple<PrimeLensCandidate, Double, Boolean>>(
                            { target - it.first.nativeEqMm }, // least crop first (native/closest below target)
                            { -it.second }, // then highest resulting MP
                            { if (it.third) 0 else 1 }, // tie-break: prefer native mapping
                            { -it.first.sensorMp }, // then higher-res sensor
                        ),
                    )
                val best =
                    bestCropCompatible
                        ?: candidates
                            .asSequence()
                            .map { c ->
                                Triple(
                                    c,
                                    c.sensorMp,
                                    target == c.nativeEqMm,
                                )
                            }.minWithOrNull(
                                compareBy<Triple<PrimeLensCandidate, Double, Boolean>>(
                                    { abs(target - it.first.nativeEqMm) }, // nearest native when target is wider
                                    { if (it.third) 0 else 1 }, // exact native match wins if present
                                    { -it.second }, // then highest available MP
                                    { -it.first.sensorMp }, // stable tie-breaker
                                ),
                            )
                if (best != null) {
                    val candidate = best.first
                    add(
                        PrimeLensAssignment(
                            targetEqMm = target,
                            cameraId = candidate.cameraId,
                            nativeEqMm = candidate.nativeEqMm,
                            focalMm = candidate.focalMm,
                            effectiveMp = best.second,
                        ),
                    )
                }
            }
        }
    }

    /** Slots that apply digital crop / eq. policy gated by [FocalSlotAvailability] on the wide sensor. */
    fun isDigitalEqPolicySlot(slot: FocalMmSlot): Boolean =
        when (slot) {
            FocalMmSlot.M35, FocalMmSlot.M50, FocalMmSlot.M85, FocalMmSlot.M150 -> true
            else -> false
        }

    fun digitalEqSlotsEnabledForWide(context: Context, ids: List<String>): Boolean {
        val arr = wideActiveArray(context, ids) ?: return false
        return FocalSlotAvailability.digitalEqSlotsEnabled(arr.width(), arr.height())
    }

    fun wideActiveArray(context: Context, ids: List<String>): android.graphics.Rect? {
        if (ids.isEmpty()) return null
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val wide = FleetCameraProfiles.resolvedRoles(context, ids).wide ?: return null
        return runCatching {
            cm.getCameraCharacteristics(wide).get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        }.getOrNull()
    }

    fun staticSlotEnabledForWide(context: Context, ids: List<String>, slot: FocalMmSlot): Boolean {
        val arr = wideActiveArray(context, ids) ?: return false
        val eqMm =
            when (slot) {
                FocalMmSlot.M35 -> 35
                FocalMmSlot.M50 -> 50
                FocalMmSlot.M85 -> 85
                FocalMmSlot.M150 -> 150
                else -> return true
            }
        return FocalSlotAvailability.staticSlotEnabled(arr.width(), arr.height(), eqMm)
    }

    /** First advertised native focal length (mm) for the routed camera (uses physical id when tele is pinned under a logical parent). */
    fun nativeFocalLengthMmForSlot(
        context: Context,
        slot: FocalMmSlot,
        ids: List<String>,
        matrix: org.json.JSONObject? = null,
    ): Float? {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val matrixCameraId = matrixPreferredCameraIdForSlot(slot, matrix)
        val charId =
            when {
                !matrixCameraId.isNullOrBlank() ->
                    if (matrixCameraId in ids) {
                        matrixCameraId
                    } else {
                        logicalParentForPhysicalCamera(cm, matrixCameraId, ids) ?: matrixCameraId
                    }
                else -> {
                    val pair = resolveFocalMmSlot(context, slot, ids) ?: return null
                    characteristicsCameraIdForNativeFocalHint(context, slot, ids, pair)
                }
            }
        val fl =
            runCatching {
                cm.getCameraCharacteristics(charId).get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            }.getOrNull()
                ?: return null
        return fl.firstOrNull()
    }

    /**
     * Camera id whose [CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS] should back the
     * focal-row native hint — matches [schedulePreviewPhysicalForFocalSlot] physical pin policy.
     */
    fun characteristicsCameraIdForNativeFocalHint(
        context: Context,
        slot: FocalMmSlot,
        ids: List<String>,
        pair: Pair<String, FocalMode?>,
    ): String {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val roles = FleetCameraProfiles.resolvedRoles(context, ids)
        val telePhysical = telePhysicalForPreviewPin(slot, roles)
        val uwPhysical = ultraWidePhysicalForPreviewPin(slot, roles)
        val physical =
            when {
                slot == FocalMmSlot.M73 || slot == FocalMmSlot.M85 || slot == FocalMmSlot.M150 -> {
                    val parent = telePhysical?.let { logicalParentForPhysicalCamera(cm, it, ids) }
                    if (telePhysical != null && parent != null && pair.first == parent) telePhysical else null
                }
                slot == FocalMmSlot.M14 -> {
                    val parent = uwPhysical?.let { logicalParentForPhysicalCamera(cm, it, ids) }
                    if (uwPhysical != null && parent != null && pair.first == parent) uwPhysical else null
                }
                else -> null
            }
        return physical ?: pair.first
    }

    fun formatShortNativeFocalMm(mm: Float): String {
        val rounded = (ceil(mm * NATIVE_MM_ROUND_SCALE) / NATIVE_MM_ROUND_SCALE).toFloat()
        val s =
            if (abs(rounded - rounded.toInt()) < NATIVE_MM_INTEGER_EPSILON) {
                rounded.toInt().toString()
            } else {
                String.format(Locale.US, "%.1f", rounded).trimEnd('0').trimEnd('.')
            }
        return "${s}mm"
    }

    /**
     * Tap-enabled when the slot maps, digital-eq. policy passes the MP gate (when applicable),
     * and rear-only tele slots are not offered while the **front** camera is active (`cameraId=1`).
     */
    /**
     * Sprint **15.13** — slot disabled when [FleetCameraStartupScan] marked the routed camera
     * grayscaled (< 12 MP sensor budget).
     */
    fun fleetScanGraysOutSlot(
        context: Context,
        slot: FocalMmSlot,
        ids: List<String>,
        matrix: org.json.JSONObject? = null,
    ): Boolean {
        val targetId =
            matrixPreferredCameraIdForSlot(slot, matrix)
                ?: resolveFocalMmSlot(context, slot, ids)?.first
                ?: return false
        val file = FleetCameraStartupScan.scanFile(context)
        if (!file.exists()) return false
        return FleetCameraStartupScan.loadFromFile(file).any { it.cameraId == targetId && it.grayscaled }
    }

    fun focalSlotInteractionEnabled(
        context: Context,
        slot: FocalMmSlot,
        ids: List<String>,
        selectedCameraId: String?,
        digitalEqOkOnWide: Boolean,
        matrix: org.json.JSONObject? = null,
    ): Boolean {
        val matrixPreferredId = matrixPreferredCameraIdForSlot(slot, matrix)
        val hasMapping =
            if (!matrixPreferredId.isNullOrBlank()) {
                matrixPreferredId in ids ||
                    logicalParentForPhysicalCamera(
                        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager,
                        matrixPreferredId,
                        ids,
                    ) != null
            } else {
                resolveFocalMmSlot(context, slot, ids) != null
            }
        if (!hasMapping) return false
        if (fleetScanGraysOutSlot(context, slot, ids, matrix)) return false
        if (selectedCameraId == "1" && isTeleSlot(slot)) return false
        if (isDigitalEqPolicySlot(slot)) {
            val matrixEnabled = matrixStaticSlotEnabled(slot, matrix)
            val staticEnabled = matrixEnabled ?: staticSlotEnabledForWide(context, ids, slot)
            if (!digitalEqOkOnWide && !staticEnabled) return false
            if (!staticEnabled) return false
        }
        return true
    }

    /** Display label + N/A from matrix policy when available (M18.7). */
    data class FocalChipPresentation(
        val labelMm: String,
        val subLabel: String?,
        val enabled: Boolean,
    )

    fun focalChipPresentation(
        context: Context,
        slot: FocalMmSlot,
        slotIndex: Int,
        ids: List<String>,
        selectedCameraId: String?,
        matrix: org.json.JSONObject?,
    ): FocalChipPresentation {
        val spec = dev.pointandshoot.fleet.FleetFocalRowPolicy.parseFromProduct(
            matrix?.optJSONObject(dev.pointandshoot.fleet.FleetDeviceMatrix.KEY_PRODUCT),
        )
        val policySlots = dev.pointandshoot.fleet.FleetFocalRowPolicy.buildSlots(spec)
        val policy = policySlots.getOrNull(slotIndex)
        val digitalEqOk = digitalEqSlotsEnabledForWide(context, ids)
        val enabled =
            focalSlotInteractionEnabled(context, slot, ids, selectedCameraId, digitalEqOk, matrix)
        val label =
            policy?.labelMm?.takeIf { it.isNotBlank() }
                ?: slot.labelMm
        val sub =
            when {
                !enabled -> policy?.subLabel ?: "N/A"
                else ->
                    nativeFocalLengthMmForSlot(context, slot, ids, matrix)
                        ?.let { formatShortNativeFocalMm(it) }
                        ?: policy?.subLabel
            }
        return FocalChipPresentation(label, sub, enabled)
    }

    private fun matrixPreferredCameraIdForSlot(
        slot: FocalMmSlot,
        matrix: org.json.JSONObject?,
    ): String? {
        val focalRow =
            matrix
                ?.optJSONObject(dev.pointandshoot.fleet.FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("focalRow")
                ?: return null
        val slotKey =
            when (slot) {
                FocalMmSlot.M14 -> "m14"
                FocalMmSlot.M23 -> "m23"
                FocalMmSlot.M35 -> "m35"
                FocalMmSlot.M50 -> "m50"
                FocalMmSlot.M73 -> "m73"
                FocalMmSlot.M85 -> "m85"
                FocalMmSlot.M150 -> "m150"
            }
        val slotCameraId =
            focalRow
                .optJSONObject("slotAssignments")
                ?.optJSONObject(slotKey)
                ?.optString("cameraId")
                ?.takeIf { it.isNotBlank() }
        if (!slotCameraId.isNullOrBlank()) return slotCameraId
        return when (slot) {
            FocalMmSlot.M14 -> focalRow.optString("uwCameraId")
            FocalMmSlot.M23, FocalMmSlot.M35, FocalMmSlot.M50 -> focalRow.optString("wideCameraId")
            FocalMmSlot.M73, FocalMmSlot.M85, FocalMmSlot.M150 -> focalRow.optString("teleCameraId")
        }.takeIf { it.isNotBlank() }
    }

    private fun matrixStaticSlotEnabled(slot: FocalMmSlot, matrix: org.json.JSONObject?): Boolean? {
        val key =
            when (slot) {
                FocalMmSlot.M35 -> "m35"
                FocalMmSlot.M50 -> "m50"
                FocalMmSlot.M85 -> "m85"
                FocalMmSlot.M150 -> "m150"
                else -> return null
            }
        val staticSlots =
            matrix
                ?.optJSONObject(dev.pointandshoot.fleet.FleetDeviceMatrix.KEY_PRODUCT)
                ?.optJSONObject("focalRow")
                ?.optJSONObject("staticSlots")
                ?: return null
        return staticSlots.optJSONObject(key)?.optBoolean("available")
    }

    private fun collectPrimeLensCandidates(context: Context, ids: List<String>): List<PrimeLensCandidate> {
        if (ids.isEmpty()) return emptyList()
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val overrides = FleetCameraStartupScan.loadMpOverrides(context)
        val primary = collectPrimeLensCandidates(cm, ids, includeLogicalAggregates = false)
        if (primary.size >= 2) {
            return primary.map { c ->
                val overrideMp = overrides[c.cameraId]?.megapixels ?: 0.0
                if (overrideMp > c.sensorMp) c.copy(sensorMp = overrideMp) else c
            }
        }
        val fallback = collectPrimeLensCandidates(cm, ids, includeLogicalAggregates = true)
        if (fallback.isEmpty()) return primary
        val mergedByCameraId =
            linkedMapOf<String, PrimeLensCandidate>().apply {
                fallback.forEach { put(it.cameraId, it) }
                primary.forEach { put(it.cameraId, it) }
            }
        return mergedByCameraId.values
            .map { c ->
                val overrideMp = overrides[c.cameraId]?.megapixels ?: 0.0
                if (overrideMp > c.sensorMp) c.copy(sensorMp = overrideMp) else c
            }.sortedBy { it.nativeEqMm }
    }

    private fun collectPrimeLensCandidates(
        cm: CameraManager,
        ids: List<String>,
        includeLogicalAggregates: Boolean,
    ): List<PrimeLensCandidate> =
        ids
            .asSequence()
            .filter { it != "1" } // front camera never participates in rear focal row
            .mapNotNull { id ->
                val chars = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
                if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    return@mapNotNull null
                }
                val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return@mapNotNull null
                if (!caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    return@mapNotNull null
                }
                val isLogicalAggregate = chars.physicalCameraIds?.isNotEmpty() == true
                if (!includeLogicalAggregates && isLogicalAggregate) {
                    return@mapNotNull null
                }
                val mp = FleetCameraStartupScan.sensorMegapixels(chars)
                if (mp < MIN_FOCAL_ROUTING_SENSOR_MP) {
                    return@mapNotNull null
                }
                val focal =
                    focalForPrimeAssignments(
                        chars = chars,
                        preferWideForLogicalAggregate = includeLogicalAggregates && isLogicalAggregate,
                    ) ?: return@mapNotNull null
                val focalEq = focalLength35mmFromFocalMm(chars, focal) ?: return@mapNotNull null
                PrimeLensCandidate(id, focalEq, focal, mp)
            }.toList()

    private fun focalForPrimeAssignments(
        chars: CameraCharacteristics,
        preferWideForLogicalAggregate: Boolean,
    ): Float? {
        val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: return null
        if (focalLengths.isEmpty()) return null
        return if (preferWideForLogicalAggregate) {
            focalLengths.minOrNull()
        } else {
            focalLengths.maxOrNull()
        }
    }

    private fun focalLength35mmFromFocalMm(
        chars: CameraCharacteristics,
        focalMm: Float,
    ): Int? {
        val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        if (sensorSize.width <= 0f) return null
        val diagSensor = hypot(sensorSize.width.toDouble(), sensorSize.height.toDouble())
        val diag35 = 43.27
        return (focalMm * (diag35 / diagSensor)).roundToInt().coerceIn(10, 300)
    }

    private fun effectiveMpForCrop(
        sensorMp: Double,
        nativeEqMm: Int,
        targetEqMm: Int,
    ): Double {
        if (sensorMp <= 0.0 || targetEqMm <= 0 || nativeEqMm <= 0) return 0.0
        if (targetEqMm < nativeEqMm) return 0.0
        val linearCrop = nativeEqMm.toDouble() / targetEqMm.toDouble()
        return sensorMp * linearCrop * linearCrop
    }
}

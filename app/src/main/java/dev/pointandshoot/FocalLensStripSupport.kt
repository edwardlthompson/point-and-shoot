package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dev.pointandshoot.fleet.FleetCameraProfiles
import java.util.Locale
import kotlin.math.abs
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

    fun isTeleSlot(slot: FocalMmSlot): Boolean =
        when (slot) {
            FocalMmSlot.M73, FocalMmSlot.M85, FocalMmSlot.M150 -> true
            else -> false
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
    ): Float? {
        val pair = resolveFocalMmSlot(context, slot, ids) ?: return null
        val charId = characteristicsCameraIdForNativeFocalHint(context, slot, ids, pair)
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
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
        val rounded = (mm * NATIVE_MM_ROUND_SCALE).roundToInt() / NATIVE_MM_ROUND_SCALE
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
    fun fleetScanGraysOutSlot(context: Context, slot: FocalMmSlot, ids: List<String>): Boolean {
        val pair = resolveFocalMmSlot(context, slot, ids) ?: return false
        val file = FleetCameraStartupScan.scanFile(context)
        if (!file.exists()) return false
        return FleetCameraStartupScan.loadFromFile(file).any { it.cameraId == pair.first && it.grayscaled }
    }

    fun focalSlotInteractionEnabled(
        context: Context,
        slot: FocalMmSlot,
        ids: List<String>,
        selectedCameraId: String?,
        digitalEqOkOnWide: Boolean,
    ): Boolean {
        if (resolveFocalMmSlot(context, slot, ids) == null) return false
        if (fleetScanGraysOutSlot(context, slot, ids)) return false
        if (selectedCameraId == "1" && isTeleSlot(slot)) return false
        if (isDigitalEqPolicySlot(slot)) {
            if (!digitalEqOkOnWide && !staticSlotEnabledForWide(context, ids, slot)) return false
            if (!staticSlotEnabledForWide(context, ids, slot)) return false
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
            focalSlotInteractionEnabled(context, slot, ids, selectedCameraId, digitalEqOk)
        val label =
            policy?.labelMm?.takeIf { it.isNotBlank() }
                ?: slot.labelMm
        val sub =
            when {
                !enabled -> policy?.subLabel ?: "N/A"
                else ->
                    nativeFocalLengthMmForSlot(context, slot, ids)?.let { formatShortNativeFocalMm(it) }
                        ?: policy?.subLabel
            }
        return FocalChipPresentation(label, sub, enabled)
    }
}

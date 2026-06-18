package dev.pointandshoot.fleet

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import dev.pointandshoot.BackCameraRoleResolver
import dev.pointandshoot.BracketPattern
import dev.pointandshoot.LeafDngFleetPolicy
import dev.pointandshoot.StillDngBackend
import dev.pointandshoot.RawStillProcessingHints

/**
 * Canonical Legacy device (LegacySku-class) RAW / focal policy (Milestone **13.2**).
 * See `docs/FLEET_ONEPLUS13_RAW_POLICY.md` in the repo.
 *
 * Historical filenames: `OnePlus13FleetPolicy.kt`, docs referring to `LegacyDeviceFleetPolicy`.
 */
object LegacyFleetPolicy : LeafDngFleetPolicy {
    const val POLICY_ID: String = "oneplus_13_legacy_sku"

    /** Dodge / DODGE_PROFILE rear leaf ids. */
    const val CANONICAL_UW: String = "3"
    const val CANONICAL_WIDE: String = "2"
    const val CANONICAL_TELE: String = "4"
    const val CANONICAL_LOGICAL: String = "0"
    override val canonicalUw: String get() = CANONICAL_UW
    override val canonicalWide: String get() = CANONICAL_WIDE
    override val canonicalTele: String get() = CANONICAL_TELE


    /** ReferenceCam leaf RAW pick order on opened id map (M13.3c). */
    /** ReferenceCam still order on opened map: 32, 37, 38, 36. */
    val LEAF_RAW_FORMAT_ORDER: List<Int> =
        listOf(
            ImageFormat.RAW_SENSOR,
            ImageFormat.RAW10,
            ImageFormat.RAW12,
            ImageFormat.RAW_PRIVATE,
        )

    override fun appliesToDevice(): Boolean {
        val model = Build.MODEL ?: return false
        return appliesToDeviceModel(model)
    }

    /** JVM / audit helper — primary fleet **CPH2583** must not match. */
    internal fun appliesToDeviceModel(model: String): Boolean {
        val upper = model.uppercase()
        return upper.contains("LEGACYSKU") || upper.contains("CPH2653")
    }

    fun canonicalRoles(ids: List<String>): BackCameraRoleResolver.Roles? =
        canonicalRolesWhen(appliesToDevice(), ids)

    /** Unit tests — pass `deviceApplies = true` to assert dodge table without [Build.MODEL]. */
    internal fun canonicalRolesWhen(
        deviceApplies: Boolean,
        ids: List<String>,
    ): BackCameraRoleResolver.Roles? {
        if (!deviceApplies) return null
        if (CANONICAL_WIDE !in ids || CANONICAL_UW !in ids || CANONICAL_TELE !in ids) {
            return null
        }
        return BackCameraRoleResolver.Roles(
            wide = CANONICAL_WIDE,
            ultraWide = CANONICAL_UW,
            tele = CANONICAL_TELE,
            longTele = null,
        )
    }

    /**
     * When canonical ids are present, prefer dodge table over focal clustering
     * (USB-verified tele routing — do not regress to logical-only tele).
     */
    fun mergeRoles(
        enumerated: BackCameraRoleResolver.Roles,
        ids: List<String>,
    ): BackCameraRoleResolver.Roles = canonicalRoles(ids) ?: enumerated

    fun logicalCameraId(ids: List<String>): String? =
        CANONICAL_LOGICAL.takeIf { it in ids }

    fun applyProfileDefaults(profile: FleetCameraProfile): FleetCameraProfile {
        if (!appliesToDevice()) return profile
        val leaf = profile.physicalCameraIds.isEmpty()
        return profile.copy(
            prefersRawSensor = leaf || profile.role != FleetCameraRole.LOGICAL,
            lensShadingMapOnStill =
                profile.lensShadingMapOnStill ||
                    (leaf && profile.role != FleetCameraRole.FRONT),
            supportsRawVideo =
                leaf &&
                    profile.rawFormatsAdvertised.isNotEmpty() &&
                    profile.role != FleetCameraRole.FRONT,
        )
    }

    /**
     * LegacySku still DNG: [StillDngBackend.FRAMEWORK_REFERENCEAPP] — USB matrix bisect `20260519_030756`
     * proved AltReferenceApp-inspired IQ left tele **B channel crushed** (render_green_delta ~0.42);
     * ReferenceCam path restores tele (delta ~0.03, `tele_ok` in `dng_color_metric.py`). UW remains open.
     */
    override fun stillDngBackend(): StillDngBackend = stillDngBackendWhen(appliesToDevice())

    /** JVM tests — pass `deviceApplies = true` without relying on [Build.MODEL]. */
    internal fun stillDngBackendWhen(deviceApplies: Boolean): StillDngBackend =
        if (deviceApplies) {
            StillDngBackend.FRAMEWORK_REFERENCEAPP
        } else {
            StillDngBackend.FRAMEWORK_REFERENCEAPP
        }

    override fun leafRawFormatOrder(): List<Int> =
        if (appliesToDevice()) LEAF_RAW_FORMAT_ORDER else emptyList()

    /** Sprint **13.8b** — buffered preview RAW + [android.hardware.camera2.TotalCaptureResult] pairs. */
    fun zslStillRingCapacity(): Int = if (appliesToDevice()) 6 else 4

    /** Enable [android.hardware.camera2.CaptureRequest.CONTROL_ENABLE_ZSL] on preview repeating when ZSL still is selected. */
    fun enableZslOnPreviewRepeating(): Boolean = appliesToDevice()

    /**
     * Sprint **13.8c** — HDR still MVP: **3** EV-bracketed DNGs (middle = reference), no in-app merge.
     * Deferred: single merged DNG / ISP blend ([BUILD_PLAN.md] 13.8c).
     */
    fun hdrStillBracketPattern(): BracketPattern =
        if (appliesToDevice()) {
            BracketPattern.Three
        } else {
            BracketPattern.Three
        }

    /** EV spacing between bracket stops (1.0 EV × 3 shots → −1 / 0 / +1 on LegacyDevice step grid). */
    fun hdrStillEvStep(): Double = 1.0

    fun hdrStillShotCount(): Int = hdrStillBracketPattern().shotCount

    /**
     * ReferenceCam does minimal post-[DngCreator] TIFF edits. Skip [StillCaptureMetadata.applyToDngUri]
     * on all rear leaf ids (UW / wide / tele) so DNGs stay DngCreator + still IQ only.
     */
    fun skipStillMetadataApplyOnLeafDng(sessionCameraId: String): Boolean {
        if (!appliesToDevice()) return false
        return sessionCameraId in setOf(CANONICAL_UW, CANONICAL_WIDE, CANONICAL_TELE)
    }

    /** ReferenceCam does not stamp P&S LUT/software auxiliary strings on leaf DNGs. */
    fun skipDngSoftwareDescriptionOnLeaf(sessionCameraId: String): Boolean =
        useReferenceAppPureDngSave() &&
            sessionCameraId in setOf(CANONICAL_UW, CANONICAL_WIDE, CANONICAL_TELE)

    /**
     * Wide (cam **2**) DNG: [DngCreator] only — HAL color tags are trustworthy on LYT-808.
     */
    override fun useReferenceAppPureDngSave(): Boolean = useReferenceAppPureDngSaveWhen(appliesToDevice())

    internal fun useReferenceAppPureDngSaveWhen(deviceApplies: Boolean): Boolean = deviceApplies

    /**
     * Leaf still [CaptureRequest] mirrors ReferenceCam decompile (crop + still IQ + HAL AE only).
     * See [dev.pointandshoot.ReferenceAppLeafStillCaptureRequest].
     */
    fun useExactReferenceAppLeafStillCaptureRequest(): Boolean =
        useExactReferenceAppLeafStillCaptureRequestWhen(appliesToDevice())

    internal fun useExactReferenceAppLeafStillCaptureRequestWhen(deviceApplies: Boolean): Boolean =
        useReferenceAppPureDngSaveWhen(deviceApplies)

    /**
     * Bisect-only — copying ReferenceCam reference CM/FM/ASN onto different RAW pixels worsened green cast
     * (May 2026 USB). Shipped path: [useReferenceAppPureDngSave] + [StillCaptureIqPolicy] only.
     */
    override fun useReferenceAppReferenceCalibration(): Boolean = false

    /**
     * Bisect-only — post-save ASN/FM reconcile on aux leaf. Off when [useReferenceAppPureDngSave] ships
     * (matches ReferenceCam: `DngCreator` only, no TIFF color surgery).
     */
    override fun useLegacyLeafAuxColorReconcile(): Boolean = false

    /** @see useLegacyLeafAuxColorReconcile */
    fun useUwReferenceAppAsnReconcile(): Boolean = useLegacyLeafAuxColorReconcile()

    /**
     * **13.3h bisect only** — wide CM/FM on aux DNGs broke ACR openability (see `docs/DNG_OPENABILITY_REGRESSIONS.md` R2).
     */
    override fun useWideLeafCalibrationForAuxDng(): Boolean = false

    /** **13.3g bisect only** — ReferenceCam decompile still path has no AE-precapture loop before still. */
    override fun useReferenceAppStillPrecapture(): Boolean = false

    /** Bisect only — ASN TIFF patch after save (not ReferenceCam parity). */
    override fun useLegacyAsnReconcileOnly(): Boolean = false

    /** Bisect only — full HAL-cal CM/FM/NCP TIFF reconcile. */
    override fun useHalColorCalibrationReconcile(): Boolean = false

    /** ACR can mis-profile when 50708 does not match Adobe's built-in camera DB. */
    fun skipUniqueCameraModelOnLeafDng(sessionCameraId: String): Boolean =
        appliesToDevice() &&
            sessionCameraId in setOf(CANONICAL_UW, CANONICAL_WIDE, CANONICAL_TELE)

    fun proShotLeafStillSkipsStopRepeating(sessionCameraId: String): Boolean = false

    fun proShotStillAeExposureCompensationSteps(sessionCameraId: String): Int = 0

    /**
     * Latch precapture ISO/exp on aux stills when wide-calibration mode is on (tele under-integrates).
     */
    fun proShotLatchManualExposureOnStill(sessionCameraId: String): Boolean =
        useWideLeafCalibrationForAuxDng() &&
            sessionCameraId in setOf(CANONICAL_UW, CANONICAL_TELE)

    /** Scale aux integration toward wide-like brightness (USB tele ~2.5× under ReferenceCam). */
    fun adjustReferenceAppExposureLatch(
        sessionCameraId: String,
        latch: RawStillProcessingHints.ReferenceAppExposureLatch,
        chars: CameraCharacteristics,
    ): RawStillProcessingHints.ReferenceAppExposureLatch {
        if (!useWideLeafCalibrationForAuxDng()) return latch
        val scale =
            when (sessionCameraId) {
                CANONICAL_TELE -> 2.5f
                CANONICAL_UW -> 1f
                else -> 1f
            }
        if (scale == 1f) return latch
        val maxNs =
            chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.upper ?: latch.expNs
        return latch.copy(
            expNs = (latch.expNs.toDouble() * scale).toLong().coerceAtMost(maxNs),
        )
    }
}

/** Documentation alias for historical `LegacyDeviceFleetPolicy` / `OnePlus13FleetPolicy` naming. */
typealias LegacyDeviceFleetPolicy = LegacyFleetPolicy

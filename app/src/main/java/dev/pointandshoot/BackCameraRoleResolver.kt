package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.abs

/**
 * Maps logical Camera2 ids to wide / ultra-wide / tele for focal-preset routing.
 *
 * **Primary path:** same clustering as [buildDodgeMappingRows] — only **non-logical-aggregate**
 * back cameras (`physicalCameraIds` empty) participate; roles come from [LENS_INFO_AVAILABLE_FOCAL_LENGTHS].
 *
 * **Fallback:** legacy string ids (`2` wide, `3` UW, `4`/`5`/`6` tele) when the device exposes no
 * such split (e.g. single logical back only).
 */
object BackCameraRoleResolver {

    data class Roles(
        val wide: String?,
        val ultraWide: String?,
        /** ~2–3× “portrait” tele used for 73 / 85 mm slots (not the longest periscope). */
        val tele: String?,
        /** Longest focal rear module when present (150 mm slot); null on 3-lens stacks. */
        val longTele: String? = null,
    )

    private val TELE_LEGACY_ORDER = listOf("4", "5", "6")

    fun resolve(cm: CameraManager, ids: List<String>): Roles {
        val infos = enumerateBackPhysical(cm, ids)
        if (infos.isNotEmpty()) {
            return rolesFromEnumeratedPhysicals(infos)
        }
        val wide = logicalBackAggregateWide(cm, ids) ?: wideCameraLegacy(ids)
        return Roles(
            wide = wide,
            ultraWide = ultraWideLegacy(cm, ids, wide),
            tele = teleLegacy(cm, ids, wide),
            longTele = null,
        )
    }

    /**
     * JVM unit-test hook: same clustering as [resolve] after [enumerateBackPhysical], without a
     * [CameraManager]. Order of [idsAndFocals] does not matter.
     */
    internal fun rolesFromEnumeratedPhysicalsForTests(idsAndFocals: List<Pair<String, Float?>>): Roles =
        rolesFromEnumeratedPhysicals(
            idsAndFocals.map { BackPhys(id = it.first, focalMm = it.second) },
        )

    private fun rolesFromEnumeratedPhysicals(infos: List<BackPhys>): Roles {
        require(infos.isNotEmpty()) { "use legacy path for empty enumeration" }
        if (infos.size >= 4) {
            val sorted = infos.sortedBy { it.focalMm ?: 0f }
            val n = sorted.size
            val uw = sorted[0].id
            val wide = sorted[1].id
            val tele = sorted[n - 2].id
            val longTele = sorted[n - 1].id
            return Roles(wide = wide, ultraWide = uw, tele = tele, longTele = longTele)
        }
        if (infos.size == 3) {
            val sortedFocal = infos.mapNotNull { it.focalMm }.sorted()
            val uw =
                infos.minByOrNull { it.focalMm ?: Float.POSITIVE_INFINITY }?.id
            val tele =
                infos.maxByOrNull { it.focalMm ?: Float.NEGATIVE_INFINITY }?.id
            val middleTarget = sortedFocal.getOrNull(1)
            val main =
                infos
                    .filter { it.id != uw && it.id != tele }
                    .minByOrNull { cam ->
                        abs((cam.focalMm ?: 0f) - (middleTarget ?: 0f))
                    }?.id
                    ?: infos.firstOrNull { it.id != uw && it.id != tele }?.id
            return Roles(wide = main, ultraWide = uw, tele = tele, longTele = null)
        }
        if (infos.size == 2) {
            val sorted = infos.sortedBy { it.focalMm ?: 0f }
            return Roles(
                ultraWide = sorted[0].id,
                wide = sorted[1].id,
                tele = null,
                longTele = null,
            )
        }
        return Roles(wide = infos[0].id, ultraWide = null, tele = null, longTele = null)
    }

    private data class BackPhys(val id: String, val focalMm: Float?)

    private fun enumerateBackPhysical(cm: CameraManager, ids: List<String>): List<BackPhys> =
        ids.mapNotNull { id ->
            val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
                ?: return@mapNotNull null
            if (cc.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                return@mapNotNull null
            }
            val physical = runCatching { cc.physicalCameraIds.toList() }.getOrDefault(emptyList())
            if (physical.isNotEmpty()) return@mapNotNull null
            val focal = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            BackPhys(id = id, focalMm = focal)
        }

    private fun teleLogicalPresent(ids: List<String>): Set<String> =
        TELE_LEGACY_ORDER.filter { it in ids }.toSet()

    /** Legacy UW: prefer `3`, else smallest focal among non-wide non-tele candidates. */
    private fun ultraWideLegacy(cm: CameraManager, ids: List<String>, wide: String?): String? {
        if (ids.contains("3")) return "3"
        val wideResolved = wide ?: wideCameraLegacy(ids)
        val teleIds = teleLogicalPresent(ids)
        val candidates =
            ids.filter { id ->
                id != "1" && id != wideResolved && id !in teleIds
            }
        if (candidates.size == 1) return candidates.first()
        val withFocal =
            candidates.mapNotNull { id ->
                val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull()
                    ?: return@mapNotNull null
                val f = cc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
                    ?: return@mapNotNull null
                id to f
            }
        if (withFocal.isNotEmpty()) {
            return withFocal.minByOrNull { it.second }!!.first
        }
        return candidates.firstOrNull()
    }

    private fun teleLegacy(cm: CameraManager, ids: List<String>, wide: String?): String? {
        TELE_LEGACY_ORDER.firstOrNull { ids.contains(it) }?.let { return it }
        val wideResolved = wide ?: wideCameraLegacy(ids)
        val uw = ultraWideLegacy(cm, ids, wideResolved)
        return ids.firstOrNull { id -> id != "1" && id != wideResolved && id != uw }
    }

    /**
     * Back-facing **logical** camera that wraps multiple physical ids (e.g. OnePlus `0` → `[2,3,4]`).
     * When [enumerateBackPhysical] is empty, [wideCameraLegacy] would pick physical `2` alone; some HALs
     * disconnect on RAW session create for that id while the logical wrapper works (see `DODGE_PROFILE.md`).
     */
    private fun logicalBackAggregateWide(cm: CameraManager, ids: List<String>): String? {
        val candidates =
            ids.mapNotNull { id ->
                val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: return@mapNotNull null
                if (cc.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    return@mapNotNull null
                }
                val children = cc.physicalCameraIds.toSet()
                if (children.size < 2) return@mapNotNull null
                id
            }
        if (candidates.isEmpty()) return null
        if ("0" in candidates) return "0"
        return candidates.minWith(compareBy({ it.toIntOrNull() ?: Int.MAX_VALUE }, { it }))
    }

    private fun wideCameraLegacy(ids: List<String>): String? =
        when {
            ids.contains("2") -> "2"
            ids.isEmpty() -> null
            else -> ids.firstOrNull { it != "1" }
        }
}

/**
 * Physical tele module chosen for preview output pin / focal-row native hints on **73 / 85 / 150** mm slots.
 * Always the clustering **mid-tele** ([Roles.tele]); 85 / 150 mm use digital crops on that sensor ([DODGE_PROFILE.md]).
 */
internal fun telePhysicalForPreviewPin(slot: FocalMmSlot, roles: BackCameraRoleResolver.Roles): String? =
    when (slot) {
        FocalMmSlot.M73, FocalMmSlot.M85, FocalMmSlot.M150 -> roles.tele
        else -> null
    }

/** Physical ultra-wide for preview [OutputConfiguration] pin on **14 mm** when the slot opens the logical parent. */
internal fun ultraWidePhysicalForPreviewPin(slot: FocalMmSlot, roles: BackCameraRoleResolver.Roles): String? =
    if (slot == FocalMmSlot.M14) roles.ultraWide else null

/**
 * When [physicalCameraId] is a **child** of a logical multi-camera id (e.g. tele `4` under logical `0`),
 * opening the **physical** id directly can crash some OEM HALs; open [logicalParent] and route the
 * preview [OutputConfiguration] with [android.hardware.camera2.params.OutputConfiguration.setPhysicalCameraId].
 */
fun logicalParentForPhysicalCamera(cm: CameraManager, physicalCameraId: String, ids: List<String>): String? {
    for (id in ids) {
        val cc = runCatching { cm.getCameraCharacteristics(id) }.getOrNull() ?: continue
        val children = runCatching { cc.physicalCameraIds }.getOrNull() ?: continue
        if (physicalCameraId in children) return id
    }
    return null
}

fun resolveFocalMmSlot(context: Context, slot: FocalMmSlot, ids: List<String>): Pair<String, FocalMode?>? {
    val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val roles = BackCameraRoleResolver.resolve(cm, ids)
    return resolveFocalMmSlotWithRoles(cm, ids, roles, slot)
}

/**
 * JVM-friendly hook for unit tests (fixed [BackCameraRoleResolver.Roles] without [CameraManager] stubs).
 */
internal fun resolveFocalMmSlotWithRoles(
    cm: CameraManager,
    ids: List<String>,
    roles: BackCameraRoleResolver.Roles,
    slot: FocalMmSlot,
): Pair<String, FocalMode?>? {
    val wide = roles.wide ?: return null
    val uw = roles.ultraWide
    /** Prefer opening an enumerated **physical** tele id directly when listed ([DODGE_PROFILE.md] `cameraId=4`). */
    fun teleOpenablePair(physicalId: String?, mode: FocalMode?): Pair<String, FocalMode?>? =
        physicalId?.let { tid ->
            when {
                tid in ids -> tid to mode
                else ->
                    logicalParentForPhysicalCamera(cm, tid, ids)?.let { parent -> parent to mode }
                        ?: (tid to mode)
            }
        }
    fun uwOpenablePair(): Pair<String, FocalMode?>? =
        uw?.let { uwId ->
            when {
                uwId in ids -> uwId to null
                else ->
                    logicalParentForPhysicalCamera(cm, uwId, ids)?.let { parent -> parent to null }
                        ?: (uwId to null)
            }
        }
    return when (slot) {
        FocalMmSlot.M14 -> uwOpenablePair()
        FocalMmSlot.M23 -> wide to null
        FocalMmSlot.M35 -> wide to FocalMode.Street35
        FocalMmSlot.M50 -> wide to FocalMode.Standard50
        FocalMmSlot.M73 -> teleOpenablePair(telePhysicalForPreviewPin(slot, roles), null)
        FocalMmSlot.M85 -> teleOpenablePair(telePhysicalForPreviewPin(slot, roles), FocalMode.Portrait85)
        FocalMmSlot.M150 ->
            teleOpenablePair(
                telePhysicalForPreviewPin(slot, roles),
                FocalMode.LongTele150,
            )
    }
}

fun focalMmSlotIsActive(
    context: Context,
    slot: FocalMmSlot,
    ids: List<String>,
    cameraId: String?,
    focal: FocalMode?,
): Boolean {
    val resolved = resolveFocalMmSlot(context, slot, ids) ?: return false
    return resolved.first == cameraId && resolved.second == focal
}

/**
 * Initial preview camera: prefer **M23** wide mapping from [resolveFocalMmSlot]; else first id.
 * Used so row-0 **23mm** chip highlights on cold start (Milestone 9 / BUILD_PLAN).
 */
internal fun pickCameraIdFromM23Resolve(
    m23: Pair<String, FocalMode?>?,
    ids: List<String>,
): String? {
    if (ids.isEmpty()) return null
    val wide = m23?.first?.takeIf { it in ids }
    return wide ?: ids.firstOrNull()
}

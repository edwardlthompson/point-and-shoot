package dev.pointandshoot.fleet

import android.content.Context
import android.util.Log
import dev.pointandshoot.FocalMmSlot
import dev.pointandshoot.fleet.FleetCameraProfiles
import org.json.JSONObject

/**
 * Builds `product.focalRow` for matrix schema v2 (Milestone **18.7**).
 */
object FleetFocalRowProductBuilder {

    private val CHIP_TARGETS = listOf(14, 23, 35, 50, 73, 85, 150)

    fun build(
        context: Context,
        cameraIds: List<String>,
        focalEntries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>,
    ): JSONObject {
        val app = context.applicationContext
        val roles = FleetCameraProfiles.resolvedRoles(app, cameraIds)
        val assignments =
            dev.pointandshoot.FocalLensStripSupport
                .resolvePrimeLensAssignments(app, cameraIds, CHIP_TARGETS)
        val byTarget = assignments.associateBy { it.targetEqMm }
        val assignedCameraIds = assignments.map { it.cameraId }.distinct()
        val uwCameraId =
            byTarget[14]?.cameraId
                ?: roles.ultraWide
                ?: assignedCameraIds.firstOrNull()
        val wideCameraId =
            byTarget[23]?.cameraId
                ?: byTarget[35]?.cameraId
                ?: roles.wide
                ?: assignedCameraIds.firstOrNull()
        val teleCameraId =
            byTarget[73]?.cameraId
                ?: byTarget[85]?.cameraId
                ?: byTarget[150]?.cameraId
                ?: roles.tele
                ?: assignedCameraIds.lastOrNull()
        val uwMm = nativeLabelMm(byTarget[14], focalEntries, uwCameraId)
        val wideMm = nativeLabelMm(byTarget[23], focalEntries, wideCameraId)
        val teleMm = nativeLabelMm(byTarget[73] ?: byTarget[85], focalEntries, teleCameraId)
        val monochromeCameraId = dedicatedMonochromeCameraId(app, cameraIds)
        val staticCrop =
            JSONObject().apply {
                for (mm in listOf(35, 50, 85, 150)) {
                    val mp = byTarget[mm]?.effectiveMp ?: 0.0
                    put("$mm", mp)
                }
            }
        val staticSlots =
            JSONObject().apply {
                for (mm in listOf(35, 50, 85, 150)) {
                    val mp = staticCrop.optDouble("$mm", 0.0)
                    put(
                        "m$mm",
                        JSONObject().apply {
                            put("available", mp >= FleetFocalRowPolicy.MIN_CROP_MP)
                            put("effectiveMp", mp)
                            if (mp < FleetFocalRowPolicy.MIN_CROP_MP) put("reason", "crop_below_12mp")
                        },
                    )
                }
            }
        val slotAssignments =
            JSONObject().apply {
                CHIP_TARGETS.forEach { mm ->
                    val assignment = byTarget[mm]
                    put(
                        "m$mm",
                        JSONObject().apply {
                            if (assignment == null) {
                                put("available", false)
                            } else {
                                val chromeSafe =
                                    assignment.targetEqMm == assignment.nativeEqMm ||
                                        assignment.effectiveMp >= FleetFocalRowPolicy.MIN_CROP_MP
                                put("available", chromeSafe)
                                put("cameraId", assignment.cameraId)
                                put("nativeEqMm", assignment.nativeEqMm)
                                put("effectiveMp", assignment.effectiveMp)
                                put("cropFactor", assignment.nativeEqMm.toDouble() / mm.toDouble())
                            }
                        },
                    )
                }
            }
        return JSONObject().apply {
            uwMm?.let { put("nativeUwMm", it) }
            wideMm?.let { put("nativeWideMm", it) }
            teleMm?.let { put("nativeTeleMm", it) }
            uwCameraId?.let { put("uwCameraId", it) }
            wideCameraId?.let { put("wideCameraId", it) }
            teleCameraId?.let { put("teleCameraId", it) }
            put("staticCropMp", staticCrop)
            put("staticSlots", staticSlots)
            put("slotAssignments", slotAssignments)
            put(
                "specialRoles",
                JSONObject().apply {
                    put("dedicatedMacro", teleCameraId != null && hasMacroCandidate(focalEntries))
                    put("dedicatedMonochrome", monochromeCameraId != null)
                    put(
                        "monochromeCaptureTierHint",
                        if (monochromeCameraId != null) "tiered_raw_jpeg_preview_fallback" else "none",
                    )
                    put("monochromeCaptureFallbackArmed", monochromeCameraId != null)
                    monochromeCameraId?.let { put("monochromeCameraId", it) }
                },
            )
        }
    }

    /** Map matrix focal row slot index to [FocalMmSlot]. */
    fun focalMmSlotForIndex(index: Int): FocalMmSlot? = FocalMmSlot.entries.getOrNull(index)

    private fun nativeLabelMm(
        assignment: dev.pointandshoot.FocalLensStripSupport.PrimeLensAssignment?,
        entries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>,
        cameraId: String?,
    ): String? {
        assignment?.nativeEqMm?.let { return it.toString() }
        if (cameraId == null) return null
        val mm = entries.firstOrNull { it.cameraId == cameraId }?.focalMm35 ?: return null
        return mm.toString()
    }

    private fun hasMacroCandidate(entries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>): Boolean =
        entries.any { it.focalMm35 <= 20.0 && it.megapixels >= 8.0 }

    private fun dedicatedMonochromeCameraId(
        context: Context,
        cameraIds: List<String>,
    ): String? = dev.pointandshoot.findDedicatedMonochromeCameraId(context, cameraIds)
}

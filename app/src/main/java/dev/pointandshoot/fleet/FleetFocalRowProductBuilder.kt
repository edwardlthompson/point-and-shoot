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

    fun build(
        context: Context,
        cameraIds: List<String>,
        focalEntries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>,
    ): JSONObject {
        val app = context.applicationContext
        val roles = FleetCameraProfiles.resolvedRoles(app, cameraIds)
        val wideMp =
            focalEntries.firstOrNull { it.cameraId == roles.wide }?.megapixels
                ?: focalEntries.maxOfOrNull { it.megapixels }
                ?: 0.0
        val uwMm = nativeLabelMm(focalEntries, roles.ultraWide)
        val wideMm = nativeLabelMm(focalEntries, roles.wide)
        val teleMm = nativeLabelMm(focalEntries, roles.tele)
        val staticCrop =
            JSONObject().apply {
                for (mm in listOf(35, 50, 85, 150)) {
                    val mp = estimateStaticMp(wideMp, mm)
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
        return JSONObject().apply {
            uwMm?.let { put("nativeUwMm", it) }
            wideMm?.let { put("nativeWideMm", it) }
            teleMm?.let { put("nativeTeleMm", it) }
            roles.ultraWide?.let { put("uwCameraId", it) }
            roles.wide?.let { put("wideCameraId", it) }
            roles.tele?.let { put("teleCameraId", it) }
            put("staticCropMp", staticCrop)
            put("staticSlots", staticSlots)
            put(
                "specialRoles",
                JSONObject().apply {
                    put("dedicatedMacro", roles.tele != null && hasMacroCandidate(focalEntries))
                    put("dedicatedMonochrome", focalEntries.any { it.grayscaled })
                },
            )
        }
    }

    /** Map matrix focal row slot index to [FocalMmSlot]. */
    fun focalMmSlotForIndex(index: Int): FocalMmSlot? = FocalMmSlot.entries.getOrNull(index)

    private fun nativeLabelMm(
        entries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>,
        cameraId: String?,
    ): String? {
        if (cameraId == null) return null
        val mm = entries.firstOrNull { it.cameraId == cameraId }?.focalMm35 ?: return null
        return mm.toString()
    }

    private fun estimateStaticMp(wideMp: Double, eqMm: Int): Double {
        if (wideMp <= 0.0) return 0.0
        val cropFactor = when (eqMm) {
            35 -> 1.0
            50 -> 0.72
            85 -> 0.42
            150 -> 0.24
            else -> 1.0
        }
        return (wideMp * cropFactor * cropFactor).coerceAtLeast(0.0)
    }

    private fun hasMacroCandidate(entries: List<dev.pointandshoot.FleetCameraStartupScan.SlotEntry>): Boolean =
        entries.any { it.focalMm35 <= 20.0 && it.megapixels >= 8.0 }
}

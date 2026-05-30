package dev.pointandshoot.fleet

import org.json.JSONObject

/**
 * Fleet-adaptive focal row (M18.7) — seven chrome slots with per-device native labels
 * and static 35/50/85/150 mm crop gates (≥ 12 MP).
 */
object FleetFocalRowPolicy {
    const val MIN_CROP_MP = 12

    data class FocalSlot(
        val slotIndex: Int,
        val labelMm: String,
        val subLabel: String? = null,
        val enabled: Boolean = true,
        val isStaticCrop: Boolean = false,
        val catalogId: String? = null,
    )

    data class FocalRowSpec(
        val nativeUwLabel: String?,
        val nativeWideLabel: String?,
        val nativeTeleLabel: String?,
        val hasDedicatedMacro: Boolean = false,
        val hasDedicatedMonochrome: Boolean = false,
        val staticCropMpGate: Map<Int, Int> = emptyMap(),
    )

    fun parseFromProduct(product: JSONObject?): FocalRowSpec {
        val focal = product?.optJSONObject("focalRow") ?: return FocalRowSpec(null, null, null)
        val gateJson = focal.optJSONObject("staticCropMp") ?: JSONObject()
        val gate =
            buildMap {
                listOf(35, 50, 85, 150).forEach { mm ->
                    val mp = gateJson.optInt("$mm", -1)
                    if (mp >= 0) put(mm, mp)
                }
            }
        return FocalRowSpec(
            nativeUwLabel = focal.optString("nativeUwMm").takeIf { it.isNotBlank() },
            nativeWideLabel = focal.optString("nativeWideMm").takeIf { it.isNotBlank() },
            nativeTeleLabel = focal.optString("nativeTeleMm").takeIf { it.isNotBlank() },
            hasDedicatedMacro = focal.optBoolean("dedicatedMacro", false),
            hasDedicatedMonochrome = focal.optBoolean("dedicatedMonochrome", false),
            staticCropMpGate = gate,
        )
    }

    fun staticSlotEnabled(spec: FocalRowSpec, mm: Int): Boolean {
        val mp = spec.staticCropMpGate[mm] ?: return true
        return mp >= MIN_CROP_MP
    }

    /** Default seven-slot layout — labels filled from matrix when present. */
    fun buildSlots(spec: FocalRowSpec): List<FocalSlot> =
        listOf(
            FocalSlot(0, spec.nativeUwLabel ?: "UW", catalogId = "lens.uw"),
            FocalSlot(1, spec.nativeWideLabel ?: "Wide", catalogId = "lens.wide"),
            FocalSlot(2, "35", isStaticCrop = true, enabled = staticSlotEnabled(spec, 35)),
            FocalSlot(3, "50", isStaticCrop = true, enabled = staticSlotEnabled(spec, 50)),
            FocalSlot(4, spec.nativeTeleLabel ?: "Tele", catalogId = "lens.tele"),
            FocalSlot(
                5,
                "85",
                isStaticCrop = true,
                enabled = staticSlotEnabled(spec, 85),
                subLabel = if (staticSlotEnabled(spec, 85)) null else "N/A",
            ),
            FocalSlot(
                6,
                "150",
                isStaticCrop = true,
                enabled = staticSlotEnabled(spec, 150),
                subLabel = if (staticSlotEnabled(spec, 150)) null else "N/A",
            ),
        )
}

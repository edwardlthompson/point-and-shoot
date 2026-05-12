package dev.pointandshoot

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import java.lang.reflect.Modifier

/**
 * Probe-only helpers: enumerate Camera2 AE / antibanding / flash characteristics,
 * label mode ints via reflection on [CaptureRequest], classify vendor-extra AE modes
 * (same rules as [HighlightAeModeSupport]), filter vendor key names for AE/highlight,
 * and snapshot root/prefs context — all for markdown export from [CameraCapabilitiesProbe].
 */
object AeHighlightProbe {

    private val aeModeLabels: Map<Int, String> by lazy {
        reflectStaticIntConstantNames(CaptureRequest::class.java, "CONTROL_AE_MODE_")
    }

    private val aeAntibandingLabels: Map<Int, String> by lazy {
        reflectStaticIntConstantNames(CaptureRequest::class.java, "CONTROL_AE_ANTIBANDING_MODE_")
    }

    private fun reflectStaticIntConstantNames(clazz: Class<*>, prefix: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        var c: Class<*>? = clazz
        while (c != null && c != Any::class.java) {
            for (field in c.declaredFields) {
                if (!Modifier.isStatic(field.modifiers)) continue
                if (field.type != Int::class.javaPrimitiveType) continue
                val name = field.name
                if (!name.startsWith(prefix)) continue
                runCatching {
                    val v = field.getInt(null)
                    if (!out.containsKey(v)) out[v] = name
                }
            }
            c = c.superclass
        }
        return out
    }

    fun appendDeviceWideSection(sb: StringBuilder, appContext: Context) {
        sb.appendLine("## AE / highlight — device context (root + prefs)")
        sb.appendLine()
        val disk = RootCapabilityStore.loadOrUnknown(appContext)
        val staticProbe = RootCapabilityProbe.probeStatic()
        sb.appendLine("- RootCapabilityStore (persisted): `${disk.name}` — ${disk.displayName}")
        sb.appendLine("- RootCapabilityProbe.probeStatic(): `${staticProbe.name}` — ${staticProbe.displayName}")
        sb.appendLine("- VendorHighlightAePrefs.tryExtraModes: `${VendorHighlightAePrefs.isTryExtraModesEnabled(appContext)}`")
        val hw = HighlightAeModeSupport.highlightWeightedAeModeOrNull()
        val hwLabel = hw?.let { v ->
            val nm = aeModeLabels[v]
            if (nm != null) "`$v` (`$nm`)" else "`$v` (_runtime value; no CONTROL_AE_MODE_* label_)"
        } ?: "_null — field absent from CaptureRequest on this runtime/SDK_"
        sb.appendLine("- Reflected CONTROL_AE_MODE_ON_HIGHLIGHT_WEIGHTED: $hwLabel")
        sb.appendLine()
    }

    fun appendPerCameraSections(
        sb: StringBuilder,
        cameraId: String,
        cc: CameraCharacteristics,
        reqKeys: List<String>,
        sessionKeys: List<String>,
        resKeys: List<String>,
    ) {
        sb.appendLine("### AE / highlight — Camera2 + vendor key names (camera $cameraId)")
        sb.appendLine()

        val availAe = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
        sb.appendLine("- `CONTROL_AE_AVAILABLE_MODES` raw: ${availAe?.contentToString() ?: "null"}")
        if (availAe != null) {
            val standard = HighlightAeModeSupport.standardAeModesForProbe()
            for (mode in availAe.distinct().sorted()) {
                val label = aeModeLabels[mode]
                val labelStr = label ?: "_no matching CONTROL_AE_MODE_* constant on this SDK_"
                val cat = if (mode in standard) "standard" else "vendor-extra"
                sb.appendLine("  - `$mode` → $labelStr (**$cat**)")
            }
            val extras = HighlightAeModeSupport.vendorExtraModesFiltered(availAe, standard)
            sb.appendLine(
                "- Vendor-extra mode ints (not in standard/reflected set): " +
                    if (extras.isEmpty()) "(none)" else extras.joinToString(prefix = "[", postfix = "]"),
            )
        }
        sb.appendLine()

        val anti = cc.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES)
        sb.appendLine("- `CONTROL_AE_AVAILABLE_ANTIBANDING_MODES` raw: ${anti?.contentToString() ?: "null"}")
        if (anti != null) {
            for (v in anti.distinct().sorted()) {
                val lb = aeAntibandingLabels[v]
                sb.appendLine(
                    "  - `$v` → ${lb ?: "_no matching CONTROL_AE_ANTIBANDING_MODE_* constant_"}",
                )
            }
        }
        sb.appendLine()

        val aeLock = cc.get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE)
        sb.appendLine("- `CONTROL_AE_LOCK_AVAILABLE`: ${aeLock?.toString() ?: "null"}")

        val flashAvail = cc.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        sb.appendLine("- `FLASH_INFO_AVAILABLE`: ${flashAvail?.toString() ?: "null"}")
        sb.appendLine()

        val pool = (reqKeys + sessionKeys + resKeys).distinct()
        sb.appendLine("- **Vendor / AE / highlight** key names (substring filter on request ∪ session ∪ result):")
        val aeKeys = filterAeHighlightVendorKeys(pool)
        if (aeKeys.isEmpty()) {
            sb.appendLine("  - _(none matched filters)_")
        } else {
            aeKeys.forEach { sb.appendLine("  - `$it`") }
        }
        sb.appendLine()
    }

    private fun filterAeHighlightVendorKeys(keys: List<String>): List<String> {
        val needles = listOf(
            "highlight", "Highlight", "HIGHLIGHT", "weighted", "Weighted",
            "spot", "Spot", "meter", "Meter", "metering", "Metering",
            "exposure", "Exposure", "ae.", "Ae", "anti", "Anti", "banding",
            "lux", "Lux", "brightness", "Brightness", "compensation", "Compensation",
            "flash", "Flash", "hdr", "HDR", "dcg", "DCG",
        )
        return keys.filter { k -> needles.any { n -> k.contains(n) } }
            .distinct()
            .sorted()
    }
}

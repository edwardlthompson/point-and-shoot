package dev.pointandshoot.fleet

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import java.io.File
import java.util.Locale
import org.json.JSONObject

/**
 * Persists [FleetProfilesSnapshot] under app `files/` (Milestone **13.2**).
 *
 * Host pull (debuggable): `adb exec-out run-as dev.pointandshoot cat files/fleet_camera_profiles_<model>.json`
 */
object FleetCameraProfileStore {
    private fun sanitizeModel(model: String): String =
        model.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')

    fun fileNameForModel(model: String = Build.MODEL): String =
        "fleet_camera_profiles_${sanitizeModel(model)}.json"

    fun file(context: Context, model: String = Build.MODEL): File =
        File(context.applicationContext.filesDir, fileNameForModel(model))

    fun save(context: Context, snapshot: FleetProfilesSnapshot) {
        val f = file(context, snapshot.deviceModel)
        f.writeText(snapshot.toJson().toString(2))
    }

    fun load(context: Context): FleetProfilesSnapshot? {
        val f = file(context)
        if (!f.isFile) return null
        val root = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return null
        return FleetProfilesSnapshot.fromJson(root)
    }

    fun appendProbeMarkdown(sb: StringBuilder, snapshot: FleetProfilesSnapshot) {
        sb.appendLine("### Fleet profiles (Milestone 13.2)")
        sb.appendLine()
        sb.appendLine("- policy: `${snapshot.policyId ?: "none"}`")
        sb.appendLine("- logical: `${snapshot.logicalCameraId ?: "null"}`")
        sb.appendLine("- leaf RAW order: ${snapshot.leafRawFormatOrder.joinToString()}")
        sb.appendLine("- on-disk: `${fileNameForModel(snapshot.deviceModel)}`")
        sb.appendLine()
        sb.appendLine("| cameraId | role | RAW fmts | shading map | prefers RAW_SENSOR | RAW_SENSOR max |")
        sb.appendLine("|---:|---|---|---:|---:|---|")
        for (p in snapshot.profiles.sortedBy { it.cameraId }) {
            sb.appendLine(
                "| ${p.cameraId} | ${p.role.name} | ${p.rawFormatsAdvertised.joinToString()} | " +
                    "${p.lensShadingMapOnStill} | ${p.prefersRawSensor} | ${p.largestRawSensorWxH ?: "-"} |",
            )
        }
        sb.appendLine()
        sb.appendLine("```json")
        sb.appendLine(snapshot.toJson().toString(2))
        sb.appendLine("```")
        sb.appendLine()
    }

    /** Milestone **13.5** — id roster from [FleetCameraCatalog] (probe export / host docs). */
    fun appendCatalogMarkdown(sb: StringBuilder, context: Context, probeHiddenIds: Boolean = false) {
        val cm = context.applicationContext.getSystemService(CameraManager::class.java) as CameraManager
        val catalog = FleetCameraCatalog.build(cm, probeHiddenIds = probeHiddenIds)
        sb.appendLine("### Fleet camera catalog (Milestone 13.5)")
        sb.appendLine()
        sb.appendLine("- device: `${catalog.deviceModel}`")
        sb.appendLine("- public ids: ${catalog.publicIds.joinToString()}")
        sb.appendLine("- entries: ${catalog.entries.size} (hidden probe: $probeHiddenIds)")
        sb.appendLine()
        sb.appendLine("| cameraId | physical children | hidden probe |")
        sb.appendLine("|---:|---|---:|")
        for (e in catalog.entries) {
            val phys =
                if (e.physicalChildIds.isEmpty()) "-" else e.physicalChildIds.sorted().joinToString()
            sb.appendLine("| ${e.cameraId} | $phys | ${e.isHiddenProbe} |")
        }
        sb.appendLine()
        val oem = FleetOemOverrides.onePlus13Cph2655()
        if (catalog.deviceModel.contains("CPH2655", ignoreCase = true) ||
            catalog.deviceModel.contains("CPH2653", ignoreCase = true)
        ) {
            sb.appendLine("OEM aliases (${oem.modelPattern}): ${oem.aliasCameraIds.entries.joinToString { "${it.key}→${it.value}" }}")
            sb.appendLine()
        }
    }
}

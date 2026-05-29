package dev.pointandshoot.fleet

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Human-readable fleet capability summary for ADB pull (Milestone **17.1**).
 *
 * Written to [FleetDeviceMatrixStore.SUMMARY_FILE_NAME] alongside matrix JSON.
 */
object FleetCapabilitySummaryMarkdown {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault())

    fun render(root: JSONObject): String = buildString {
        appendLine("# Point & Shoot — device capability summary")
        appendLine()
        renderDeviceHeader(root)
        appendLine()
        renderFocalSlots(root)
        appendLine()
        renderCameras(root)
        appendLine()
        renderCatalog(root)
        appendLine()
        renderRescanHint(root)
    }

    private fun StringBuilder.renderDeviceHeader(root: JSONObject) {
        val meta = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)
        val dev = root.optJSONObject(FleetDeviceMatrix.KEY_DEVICE)
        appendLine("## Device")
        appendLine("- **Manufacturer:** ${dev?.optString("manufacturer") ?: "?"}")
        appendLine("- **Model:** ${dev?.optString("model") ?: "?"} (${dev?.optString("device") ?: "?"})")
        appendLine("- **Scan tier:** ${meta?.optString("scanTier") ?: "?"}")
        appendLine("- **Generated:** ${formatEpoch(meta?.optLong("generatedAtEpochMs", 0L) ?: 0L)}")
        appendLine("- **Fingerprint prefix:** ${meta?.optString("fingerprintSha256Prefix") ?: "?"}")
        appendLine("- **App version code:** ${meta?.optLong("appVersionCode", -1) ?: -1}")
        appendLine("- **SDK / patch:** ${meta?.optInt("sdkInt", -1)} / ${meta?.optString("securityPatch") ?: "?"}")
        appendLine("- **Matrix JSON:** `files/${FleetDeviceMatrixStore.MATRIX_FILE_NAME}`")
    }

    private fun StringBuilder.renderFocalSlots(root: JSONObject) {
        appendLine("## Focal slots (product)")
        val slots = root.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONArray("focalSlots") ?: JSONArray()
        if (slots.length() == 0) {
            appendLine("_No focal slots — run Rescan full._")
            return
        }
        appendLine("| mm (35eq) | cameraId | MP | visible |")
        appendLine("|-----------|----------|-----|---------|")
        for (i in 0 until slots.length()) {
            val s = slots.optJSONObject(i) ?: continue
            val gray = s.optBoolean("grayscaled", false)
            appendLine("| ${s.optInt("focalMm35")} | ${s.optString("cameraId")} | ${s.optDouble("megapixels")} | ${if (gray) "dim" else "yes"} |")
        }
    }

    private fun StringBuilder.renderCameras(root: JSONObject) {
        appendLine("## Per-camera summary")
        val cams = root.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: JSONArray()
        for (i in 0 until cams.length()) {
            val c = cams.optJSONObject(i) ?: continue
            val id = c.optString("cameraId")
            appendLine("### Camera $id")
            appendLine("- HFR max @1080: ${c.opt("hfrMaxFpsAt1080") ?: "—"} fps")
            appendLine("- RAW pick: ${c.optString("rawPickEffective", "—")} ${c.optString("rawPickSize", "")}")
            appendLine("- Hardware: ${c.optString("hardwareLevel", "—")}")
            val gates = c.optJSONObject("featureGates")
            if (gates != null) {
                appendLine("- Gates: RAW=${gateLine(gates, "raw")} HFR=${gateLine(gates, "hfr")} FACE=${gateLine(gates, "face")}")
            }
            val face = c.optJSONArray("faceDetectModes")
            if (face != null && face.length() > 0) {
                appendLine("- Face detect modes: ${jsonIntList(face)}")
            }
            val stream = findDeepStream(root, id)
            if (stream != null) {
                appendAeFps(stream, c)
                appendStreamFormats(stream)
            }
            appendLine()
        }
    }

    private fun StringBuilder.renderCatalog(root: JSONObject) {
        appendLine("## Feature catalog")
        val cat = root.optJSONArray(FleetDeviceMatrix.KEY_CAPABILITY_CATALOG)
        if (cat == null || cat.length() == 0) {
            appendLine("_Catalog not built — rescan after app update._")
            return
        }
        appendLine("| Feature | On device | In app | Root | UI |")
        appendLine("|---------|-----------|--------|------|-----|")
        for (i in 0 until cat.length()) {
            val r = cat.optJSONObject(i) ?: continue
            val dev = if (r.optBoolean("deviceSupported")) "yes" else "no"
            val app = r.optString("appStatus", "?")
            val rootOnly = if (r.optBoolean("rootOnly")) "yes" else ""
            val surf = r.optJSONArray("surfacing")?.let { arr ->
                (0 until arr.length()).joinToString(",") { j -> arr.optString(j) }
            } ?: ""
            appendLine("| ${r.optString("displayName")} | $dev | $app | $rootOnly | $surf |")
        }
    }

    private fun StringBuilder.renderRescanHint(root: JSONObject) {
        appendLine("## Rescan")
        val tier = root.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optString("scanTier")
        appendLine("- Current tier: **$tier**")
        if (tier == "quick") {
            appendLine("- For full stream/format inventory: Engineering hub → Device capability matrix → **Rescan full**")
        }
        appendLine("- Re-run after OS or app update (invalidates on fingerprint / versionCode change).")
        appendLine("- ADB: `adb exec-out run-as dev.pointandshoot cat files/${FleetDeviceMatrixStore.SUMMARY_FILE_NAME}`")
    }

    private fun findDeepStream(root: JSONObject, cameraId: String): JSONObject? {
        val deep = root.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.optJSONObject("deepCaps") ?: return null
        val cams = deep.optJSONArray("cameras") ?: return null
        for (i in 0 until cams.length()) {
            val c = cams.optJSONObject(i) ?: continue
            if (c.optString("cameraId") == cameraId) return c.optJSONObject("streamConfigurationMap")
        }
        return null
    }

    private fun StringBuilder.appendAeFps(stream: JSONObject, cam: JSONObject) {
        val ae = stream.optJSONArray("aeTargetFpsRanges")
        if (ae != null && ae.length() > 0) {
            appendLine("- AE target FPS ranges: ${rangeList(ae)}")
        }
        val hs = stream.optJSONArray("highSpeedVideo")
        if (hs != null && hs.length() > 0) {
            appendLine("- High-speed video:")
            for (i in 0 until hs.length()) {
                val h = hs.optJSONObject(i) ?: continue
                appendLine("  - ${h.optInt("w")}x${h.optInt("h")}: ${rangeList(h.optJSONArray("fpsRanges"))}")
            }
        }
    }

    private fun StringBuilder.appendStreamFormats(stream: JSONObject) {
        val byFmt = stream.optJSONObject("outputSizesByFormat") ?: return
        val keys = byFmt.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = byFmt.optJSONArray(k) ?: continue
            if (arr.length() == 0) continue
            val largest = largestSize(arr)
            appendLine("- $k: ${arr.length()} sizes (max $largest)")
        }
        val ratios = stream.optJSONArray("aspectRatios")
        if (ratios != null && ratios.length() > 0) {
            appendLine("- Aspect ratios: ${ratioList(ratios)}")
        }
    }

    private fun gateLine(gates: JSONObject, key: String): String {
        val g = gates.optJSONObject(key) ?: return "?"
        return "adv=${g.optBoolean("advertised")}/sess=${g.optBoolean("sessionOk")}/app=${g.optBoolean("appEnabled")}"
    }

    private fun formatEpoch(ms: Long): String =
        if (ms <= 0L) "?" else fmt.format(Instant.ofEpochMilli(ms))

    private fun jsonIntList(arr: JSONArray): String =
        (0 until arr.length()).joinToString(",") { arr.optInt(it).toString() }

    private fun rangeList(arr: JSONArray?): String {
        if (arr == null) return "—"
        return (0 until arr.length()).joinToString("; ") { i ->
            val r = arr.optJSONObject(i)
            "[${r?.optInt("lower")}..${r?.optInt("upper")}]"
        }
    }

    private fun largestSize(arr: JSONArray): String {
        var best = 0L
        var label = "?"
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val w = o.optInt("w").toLong()
            val h = o.optInt("h").toLong()
            val px = w * h
            if (px > best) {
                best = px
                label = "${o.optInt("w")}x${o.optInt("h")}"
            }
        }
        return label
    }

    private fun ratioList(arr: JSONArray): String =
        (0 until arr.length()).joinToString(", ") { i -> arr.optString(i) }
}

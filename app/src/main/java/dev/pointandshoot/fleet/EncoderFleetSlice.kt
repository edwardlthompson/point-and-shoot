package dev.pointandshoot.fleet

import android.content.Context
import android.media.MediaFormat
import dev.pointandshoot.EncoderAttemptJsonAdapter
import dev.pointandshoot.supportsSurfaceEncoding
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fleet matrix `encoder` slice from [dev.pointandshoot.EncoderProbeCore] surface probes and
 * latest `enc_probe_*.json` / `exhaustive_probe_*.json` on disk (Milestone **16.13**).
 */
object EncoderFleetSlice {
    private val MIME_ORDER =
        listOf(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            "video/av01",
        )

    fun build(context: Context): JSONObject {
        val app = context.applicationContext
        val surface = JSONObject().apply {
            for (mime in MIME_ORDER) {
                put(mime, supportsSurfaceEncoding(mime))
            }
        }
        val encProbe = findLatestEncProbe(app)
        val exhaustive = EncoderAttemptJsonAdapter.loadLatest(app)
        val bestRows = JSONArray()
        var source = "static_surface_only"
        var sourceFile: String? = null

        if (encProbe != null) {
            source = "enc_probe"
            sourceFile = encProbe.name
            parseEncProbe(encProbe, bestRows)
        } else if (exhaustive != null) {
            source = "exhaustive_probe"
            sourceFile = exhaustive.sourceFile.name
            parseExhaustiveSummary(exhaustive.attempts, bestRows)
        }

        return JSONObject().apply {
            put("source", source)
            put("sourceFile", sourceFile ?: JSONObject.NULL)
            put("surfaceEncoding", surface)
            put("bestByCameraFps", bestRows)
            put(
                "verifyScripts",
                JSONObject().apply {
                    put("videoMatrix", "scripts/pns_video_matrix_verify.ps1")
                    put("codecColorCompare", "scripts/pns_video_codec_color_compare.ps1")
                },
            )
            put("note", "Informational — run Engineering hub encoder probe to refresh enc_probe_* on device.")
        }
    }

    private fun findLatestEncProbe(context: Context): File? {
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        return dirs
            .flatMap { dir ->
                dir.listFiles()?.filter { f ->
                    f.isFile && f.name.startsWith("enc_probe_") && f.name.endsWith(".json")
                }.orEmpty()
            }
            .maxByOrNull { it.lastModified() }
    }

    private fun parseEncProbe(file: File, out: JSONArray) {
        val root = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        val results = root.optJSONArray("results") ?: return
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val best = row.optJSONObject("best") ?: continue
            out.put(
                JSONObject().apply {
                    put("cameraId", row.optString("cameraId"))
                    put("targetFps", row.optInt("fps"))
                    put("ok", best.optBoolean("ok"))
                    put("measuredFps", best.optDouble("measuredFps"))
                    put("note", best.optString("note"))
                    put("sessionKind", best.optString("sessionKind"))
                    val size = best.optJSONObject("size")
                    put(
                        "size",
                        if (size == null) JSONObject.NULL else "${size.optInt("w")}x${size.optInt("h")}",
                    )
                },
            )
        }
    }

    private fun parseExhaustiveSummary(attempts: List<dev.pointandshoot.EncoderAttempt>, out: JSONArray) {
        val byKey = linkedMapOf<String, dev.pointandshoot.EncoderAttempt>()
        for (a in attempts) {
            val key = "${a.cameraId}_${a.fpsUpper}_${a.mime}"
            val prev = byKey[key]
            if (prev == null || a.measuredFps > prev.measuredFps) {
                byKey[key] = a
            }
        }
        for ((_, a) in byKey) {
            out.put(
                JSONObject().apply {
                    put("cameraId", a.cameraId)
                    put("targetFps", a.fpsUpper)
                    put("ok", a.ok)
                    put("measuredFps", a.measuredFps)
                    put("mime", a.mime ?: JSONObject.NULL)
                    put("sessionKind", a.sessionKind.name)
                    put("size", a.sizeLabel)
                    put("note", a.note)
                },
            )
        }
    }
}

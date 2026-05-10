package dev.pointandshoot

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Android-side JSON adapter that reads `exhaustive_probe_<utc>.json` (written
 * by `ExhaustiveMediaProbeScreen`) and produces a `List<EncoderAttempt>`
 * suitable for [EncoderResultAggregator.summarize].
 *
 * The pure-data decode step is separated into [decode] so it can be unit-tested
 * by passing a [JSONObject] built in-memory (no file IO, no probe artifacts on
 * disk required). The Android-touching part is just file discovery
 * ([loadLatest]) and `JSONObject(text)` construction.
 *
 * Schema (subset we care about):
 * ```
 * {
 *   "cameras": [
 *     {
 *       "cameraId": "0",
 *       "hfrAttempts":     [ { "ok": true, "measuredFps": 240.0, "note": "...", "sessionKind": "hfr", "size": {"w": 1920, "h": 1080}, "fpsRange": {"lower": 240, "upper": 240} }, ... ],
 *       "regularAttempts": [ { ... same shape, "sessionKind": "regular" }, ... ]
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * Mime is extracted from the `note` field via [EncoderAttempt.extractMimeFromNote]
 * because the upstream probe writes the mime token there rather than as a
 * first-class JSON field.
 */
object EncoderAttemptJsonAdapter {

    /**
     * Lists every `exhaustive_probe_*.json` under [root], recursively up to [maxDepth].
     * Used by [loadLatest] so nested dirs (e.g. `hfr-runs/`) still hydrate About.
     */
    fun collectExhaustiveProbeJsonFiles(root: File, maxDepth: Int = 8): List<File> {
        if (maxDepth < 0 || !root.isDirectory) return emptyList()
        val out = mutableListOf<File>()
        val children = root.listFiles() ?: return emptyList()
        for (f in children) {
            when {
                f.isFile &&
                    f.name.startsWith("exhaustive_probe_") &&
                    f.name.endsWith(".json") -> out.add(f)
                f.isDirectory -> out.addAll(collectExhaustiveProbeJsonFiles(f, maxDepth - 1))
            }
        }
        return out
    }

    /**
     * Find the most recent `exhaustive_probe_*.json` under
     * `getExternalFilesDir(null)` (recursive, includes nested folders such as
     * pulled `hfr-runs/`), parse it, and return the flattened attempt list.
     * Returns `null` if no artifact exists, the file is unreadable, or the JSON
     * is malformed - callers should fall back to a static known-good list.
     *
     * Returns a [LoadResult] that includes the source file path so the UI can
     * surface "hydrated from probe run <timestamp>".
     */
    fun loadLatest(context: Context): LoadResult? {
        val dir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        val candidates = collectExhaustiveProbeJsonFiles(dir)
        val newest = candidates.maxByOrNull { it.lastModified() } ?: return null
        return runCatching {
            val json = JSONObject(newest.readText(Charsets.UTF_8))
            LoadResult(
                sourceFile = newest,
                generatedAt = json.optString("generatedAt", ""),
                runId = json.optString("runId", ""),
                attempts = decode(json),
            )
        }.getOrNull()
    }

    /**
     * Pure decode: turns a probe-artifact [root] JSON into a flat list of
     * [EncoderAttempt]. Skips malformed entries silently rather than failing
     * the whole load - a single bad row should not blank the HUD.
     */
    fun decode(root: JSONObject): List<EncoderAttempt> {
        val out = mutableListOf<EncoderAttempt>()
        val cameras = root.optJSONArray("cameras") ?: return out
        for (i in 0 until cameras.length()) {
            val camObj = cameras.optJSONObject(i) ?: continue
            val cameraId = camObj.optString("cameraId", "?")
            decodeArray(out, cameraId, SessionKind.Hfr, camObj.optJSONArray("hfrAttempts"))
            decodeArray(out, cameraId, SessionKind.Regular, camObj.optJSONArray("regularAttempts"))
        }
        return out
    }

    private fun decodeArray(
        sink: MutableList<EncoderAttempt>,
        cameraId: String,
        defaultKind: SessionKind,
        arr: org.json.JSONArray?,
    ) {
        if (arr == null) return
        for (j in 0 until arr.length()) {
            val obj = arr.optJSONObject(j) ?: continue
            val sizeObj = obj.optJSONObject("size") ?: continue
            val fpsObj = obj.optJSONObject("fpsRange") ?: continue
            val note = obj.optString("note", "")
            val rawKind = obj.optString("sessionKind", "")
            val kind = when (rawKind.lowercase()) {
                "hfr" -> SessionKind.Hfr
                "regular" -> SessionKind.Regular
                else -> defaultKind
            }
            sink.add(
                EncoderAttempt(
                    cameraId = cameraId,
                    sessionKind = kind,
                    width = sizeObj.optInt("w"),
                    height = sizeObj.optInt("h"),
                    fpsLower = fpsObj.optInt("lower"),
                    fpsUpper = fpsObj.optInt("upper"),
                    mime = EncoderAttempt.extractMimeFromNote(note),
                    ok = obj.optBoolean("ok", false),
                    measuredFps = obj.optDouble("measuredFps", 0.0),
                    note = note,
                ),
            )
        }
    }

    data class LoadResult(
        val sourceFile: File,
        val generatedAt: String,
        val runId: String,
        val attempts: List<EncoderAttempt>,
    )
}

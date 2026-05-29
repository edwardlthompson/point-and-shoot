package dev.pointandshoot.fleet

import android.util.Log

/**
 * Redacted `dumpsys media.camera` excerpt for fleet matrix appendix (Milestone **16.12**).
 */
object FleetHalAppendix {
    private const val TAG = "PNS.FleetMatrix"
    private const val MAX_CHARS = 120_000

    fun captureRedacted(): String? =
        runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "media.camera"))
            val raw = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            redact(raw).take(MAX_CHARS)
        }.onFailure { e ->
            Log.w(TAG, "hal dumpsys capture failed: ${e.message}")
        }.getOrNull()

    fun redact(text: String): String =
        text
            .replace(Regex("""/data/user/\d+/[^\s"'<>]+"""), "[APP_DATA]")
            .replace(Regex("""/storage/[^\s"'<>]+"""), "[STORAGE]")
            .replace(Regex("""/vendor/[^\s"'<>]+"""), "[VENDOR_PATH]")
            .replace(Regex("""\b[0-9a-f]{16}\b""", RegexOption.IGNORE_CASE), "[HEX_ID]")
}

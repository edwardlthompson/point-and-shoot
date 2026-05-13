package dev.pointandshoot

import android.content.Context
import android.util.Log
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * In-memory ring buffer of recent capture / session events for triage (RAW still failures,
 * configure failures, [CameraDevice.StateCallback.onError], etc.).
 *
 * A copy can be written to [LATEST_FILE_NAME] under the app **files** dir (best-effort) when
 * [flushToFilesDir] runs — debuggable pull: `adb exec-out run-as dev.pointandshoot cat files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt`.
 *
 * [DiagnosticsMode.dump] appends the same text as a markdown section when diagnostics mode is on.
 */
object PnsCapturePipelineDiagnostics {

    const val TAG = "PNS.CaptureDiag"
    const val LATEST_FILE_NAME = "PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt"

    private const val CAPACITY = 128

    private val lock = Any()
    private val buffer = arrayOfNulls<Entry>(CAPACITY)
    private var writeSeq = 0

    private data class Entry(
        val epochMs: Long,
        val kind: String,
        val message: String,
        val extras: String,
    )

    /**
     * Records one line (newest wins eviction at [CAPACITY]).
     * [extras] are flattened as `k=v` pairs separated by spaces (values should stay short).
     */
    fun record(
        kind: String,
        message: String,
        extras: Map<String, String> = emptyMap(),
    ) {
        val flat =
            if (extras.isEmpty()) {
                ""
            } else {
                extras.entries.joinToString(" ") { "${it.key}=${it.value}" }
            }
        val e = Entry(System.currentTimeMillis(), kind, message, flat)
        synchronized(lock) {
            buffer[Math.floorMod(writeSeq, CAPACITY)] = e
            writeSeq++
        }
    }

    /** Newest-first snapshot for markdown / file export (bounded by [CAPACITY]). */
    fun formatReportSection(): String {
        val lines = snapshotNewestFirst().map { ent ->
            val ts = java.time.Instant.ofEpochMilli(ent.epochMs).toString()
            buildString {
                append("- `").append(ts).append("` **").append(ent.kind).append("** ")
                append(ent.message)
                if (ent.extras.isNotBlank()) {
                    append(" — ").append(ent.extras)
                }
            }
        }
        return buildString {
            appendLine("## Capture pipeline ring (last ${lines.size} events, newest first)")
            if (lines.isEmpty()) {
                appendLine("(empty)")
            } else {
                lines.forEach { appendLine(it) }
            }
        }
    }

    private fun snapshotNewestFirst(): List<Entry> {
        synchronized(lock) {
            val n = writeSeq.coerceAtMost(CAPACITY)
            if (n == 0) return emptyList()
            val out = ArrayList<Entry>(n)
            for (i in 0 until n) {
                val idx = Math.floorMod(writeSeq - 1 - i, CAPACITY)
                buffer[idx]?.let { out.add(it) }
            }
            return out
        }
    }

    /**
     * Overwrites [LATEST_FILE_NAME] in app-private files (UTF-8).
     * Safe to call from any thread; failures are swallowed after [Log.w].
     */
    fun flushToFilesDir(context: Context) {
        val body = formatReportSection()
        runCatching {
            File(context.applicationContext.filesDir, LATEST_FILE_NAME).writeText(body, StandardCharsets.UTF_8)
        }.onFailure { Log.w(TAG, "flush diagnostics file failed", it) }
    }

    /** Clears the ring (e.g. after a successful probe export). */
    fun clear() {
        synchronized(lock) {
            for (i in buffer.indices) buffer[i] = null
            writeSeq = 0
        }
    }
}

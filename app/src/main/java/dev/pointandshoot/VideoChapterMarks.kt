@file:Suppress("MagicNumber")

package dev.pointandshoot

import java.util.concurrent.CopyOnWriteArrayList

/** Markers dropped while a clip is recording; written as a sidecar after stop. */
object VideoChapterMarks {
    data class Mark(val offsetMs: Long, val label: String)

    private val marks = CopyOnWriteArrayList<Mark>()

    @Volatile
    var recordingStartedAtMs: Long = 0L

    fun clear() {
        marks.clear()
        recordingStartedAtMs = System.currentTimeMillis()
    }

    fun addElapsed(label: String = "mark") {
        val started = recordingStartedAtMs
        val offset = if (started <= 0L) 0L else (System.currentTimeMillis() - started).coerceAtLeast(0L)
        add(offset, label)
    }

    fun add(offsetMs: Long, label: String = "mark") {
        if (offsetMs < 0L) return
        marks += Mark(offsetMs, label)
    }

    fun snapshot(): List<Mark> = marks.toList()

    fun sidecarText(): String =
        snapshot().joinToString("\n") { mark ->
            val sec = mark.offsetMs / 1000L
            val ms = mark.offsetMs % 1000L
            "${sec}.${ms.toString().padStart(3, '0')} ${mark.label}"
        }
}

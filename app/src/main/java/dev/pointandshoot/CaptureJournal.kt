@file:Suppress("MagicNumber")

package dev.pointandshoot

import java.util.concurrent.CopyOnWriteArrayList

/** In-session saved / failed list a photographer can read without logcat. */
object CaptureJournal {
    const val MAX_EVENTS: Int = 50

    data class Event(
        val atMs: Long,
        val ok: Boolean,
        val message: String,
    )

    private val events = CopyOnWriteArrayList<Event>()

    fun record(ok: Boolean, message: String, atMs: Long = System.currentTimeMillis()) {
        events.add(0, Event(atMs, ok, message.trim().take(160)))
        while (events.size > MAX_EVENTS) {
            events.removeAt(events.lastIndex)
        }
    }

    fun snapshot(): List<Event> = events.toList()

    fun clear() {
        events.clear()
    }

    fun latestLine(): String? {
        val event = events.firstOrNull() ?: return null
        val mark = if (event.ok) "saved" else "failed"
        return "$mark · ${event.message}"
    }
}

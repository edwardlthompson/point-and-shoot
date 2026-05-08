package dev.pointandshoot

/**
 * Pure-data, Nikon-style "3D tracking" persistence per BUILD_PLAN \u00a74:
 * "Nikon-style 3D tracking persistence logic".
 *
 * The tracker takes per-frame snapshots of detector outputs (faces or AF
 * candidates, identified by stable IDs) and decides which subjects are
 * **locked** (worth driving AF/AE toward) vs. **transient** (a one-frame
 * blip the engine should ignore).
 *
 * Hysteresis policy (Nikon-inspired):
 *   * A new ID needs [acquireFrames] consecutive presents to lock.
 *   * A locked ID survives [keepAliveFrames] consecutive absents before
 *     being dropped (reduces flicker when the detector misses a frame).
 *
 * The tracker is **stateful but pure** - no Android types, no clocks. Wire
 * `update(...)` from the capture pipeline once per `CaptureResult`. The
 * resulting [Snapshot] tells the UI overlays which IDs to draw with the
 * "locked" affordance and which to draw transiently (or not at all).
 */
class TrackerState(
    private val acquireFrames: Int = 3,
    private val keepAliveFrames: Int = 5,
) {
    init {
        require(acquireFrames > 0) { "acquireFrames must be > 0 (was $acquireFrames)" }
        require(keepAliveFrames >= 0) { "keepAliveFrames must be >= 0 (was $keepAliveFrames)" }
    }

    private data class Track(
        var presentStreak: Int,
        var absentStreak: Int,
        var locked: Boolean,
    )

    private val tracks = mutableMapOf<Int, Track>()

    /**
     * Feed one frame's worth of detector IDs. Returns the post-update snapshot.
     * Calls are O(observed + tracked).
     */
    fun update(observedIds: Set<Int>): Snapshot {
        // Bump the present streak (or insert) for every observed id.
        for (id in observedIds) {
            val t = tracks.getOrPut(id) { Track(presentStreak = 0, absentStreak = 0, locked = false) }
            t.presentStreak += 1
            t.absentStreak = 0
            if (!t.locked && t.presentStreak >= acquireFrames) {
                t.locked = true
            }
        }

        // Bump the absent streak for every tracked id we *didn't* see; drop
        // the ones that exceeded keepAliveFrames.
        val iterator = tracks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in observedIds) {
                entry.value.presentStreak = 0
                entry.value.absentStreak += 1
                if (entry.value.absentStreak > keepAliveFrames) {
                    iterator.remove()
                }
            }
        }

        return snapshot()
    }

    /** Current locked + transient ID sets without mutating state. */
    fun snapshot(): Snapshot {
        val locked = mutableSetOf<Int>()
        val transient = mutableSetOf<Int>()
        for ((id, t) in tracks) {
            if (t.locked) locked.add(id) else transient.add(id)
        }
        return Snapshot(locked = locked, transient = transient)
    }

    /** Discard all state. */
    fun reset() {
        tracks.clear()
    }

    data class Snapshot(val locked: Set<Int>, val transient: Set<Int>)
}

package dev.pointandshoot

import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.util.Log

/**
 * Sprint **13.8b** — ring buffer for ZSL still (preview RAW frames + [TotalCaptureResult]).
 */
data class ZslStillFrameSlot(
    val sequence: Long,
    val image: Image,
    val result: TotalCaptureResult,
)

class ZslStillFrameRing(
    private val capacity: Int,
) {
    init {
        require(capacity >= 1) { "capacity must be >= 1" }
    }

    private data class MutableSlot(
        val sequence: Long,
        var image: Image?,
        var result: TotalCaptureResult?,
    )

    private val slots = ArrayDeque<MutableSlot>()
    private var seq = 0L

    /** Host tests — result-only slot (paired when image arrives). */
    internal fun offerPlaceholder(): Long = offerResultOnly()

    private fun offerResultOnly(): Long {
        seq += 1
        while (slots.size >= capacity) {
            evictFirst()
        }
        slots.addLast(MutableSlot(seq, null, null))
        return seq
    }

    fun offerResult(result: TotalCaptureResult): Long {
        val last = slots.lastOrNull()
        if (last != null && last.result == null) {
            last.result = result
            return last.sequence
        }
        return addSlot(image = null, result = result)
    }

    fun offerImage(image: Image): Long {
        val last = slots.lastOrNull()
        if (last != null && last.image == null) {
            last.image = image
            return last.sequence
        }
        return addSlot(image = image, result = null)
    }

    private fun addSlot(image: Image?, result: TotalCaptureResult?): Long {
        seq += 1
        while (slots.size >= capacity) {
            evictFirst()
        }
        slots.addLast(MutableSlot(seq, image, result))
        return seq
    }

    private fun evictFirst() {
        val removed = slots.removeFirst()
        runCatching { removed.image?.close() }
    }

    /** Latest complete pair (image + result), without removing. */
    fun peekBestForStill(): ZslStillFrameSlot? = completeSlots().maxByOrNull { it.sequence }?.toSlot()

    /** Sprint **15.18** — alias for histogram / metering peek (same as [peekBestForStill]). */
    fun peekLastFrame(): ZslStillFrameSlot? = peekBestForStill()

    /**
     * Removes and returns the best complete pair; closes all other buffered [Image]s.
     */
    fun takeBestForStill(): ZslStillFrameSlot? {
        val best = completeSlots().maxByOrNull { it.sequence } ?: return null
        val out = best.toSlot()
        slots.forEach { slot ->
            if (slot.sequence != best.sequence) {
                runCatching { slot.image?.close() }
            }
        }
        slots.removeAll { it.sequence == best.sequence }
        return out
    }

    fun size(): Int = slots.size

    fun completeCount(): Int = completeSlots().size

    fun clear(closeImages: Boolean = true) {
        if (closeImages) {
            slots.forEach { runCatching { it.image?.close() } }
        }
        slots.clear()
    }

    private fun completeSlots(): List<MutableSlot> =
        slots.filter { it.image != null && it.result != null }

    private fun MutableSlot.toSlot(): ZslStillFrameSlot =
        ZslStillFrameSlot(
            sequence = sequence,
            image = image!!,
            result = result!!,
        )

    companion object {
        private const val TAG = "PNS.ZslRing"

        internal fun logDiag(ring: ZslStillFrameRing?, event: String) {
            if (ring == null) return
            Log.i(TAG, "$event size=${ring.size()} complete=${ring.completeCount()}")
        }
    }
}

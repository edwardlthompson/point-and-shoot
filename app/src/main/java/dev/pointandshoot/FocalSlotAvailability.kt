package dev.pointandshoot

/**
 * Sprint **10.2** — policy for when **35 / 50 / 85 / 150** mm focal-equivalent chips are meaningful
 * vs a **≥ 12 MP** rear budget (see `BUILD_PLAN.md` Milestone **10.2**).
 *
 * UI graying / lens-strip wiring stays **[MIXED]**; this object is the pure gate for hosts + unit tests.
 */
object FocalSlotAvailability {
    /** Minimum effective megapixels on the active rear stream to offer digital eq. slots. */
    const val MIN_MEGAPIXELS_FOR_DIGITAL_EQ_SLOTS: Double = 12.0

    private const val MEGAPIXEL_SCALE = 1_000_000.0

    fun megapixelsFromActiveArray(widthPx: Int, heightPx: Int): Double =
        widthPx.toLong() * heightPx / MEGAPIXEL_SCALE

    /**
     * When `true`, **35 / 50 / 85 / 150** mm digital crop modes are allowed (subject to per-lens routing elsewhere).
     */
    fun digitalEqSlotsEnabled(activeArrayWidthPx: Int, activeArrayHeightPx: Int): Boolean {
        if (activeArrayWidthPx <= 0 || activeArrayHeightPx <= 0) return false
        val mp = megapixelsFromActiveArray(activeArrayWidthPx, activeArrayHeightPx)
        return mp >= MIN_MEGAPIXELS_FOR_DIGITAL_EQ_SLOTS && mp.isFinite()
    }
}

package dev.pointandshoot

import android.hardware.camera2.CameraCharacteristics
import android.util.Range

/**
 * ISO auto-range clamp for the readout strip (Sprint **14.7**, updated as checklist range picker).
 *
 * Stops are still chosen from [ReadoutExposureCatalog]; the band only filters the menu and
 * hard-clamps picks in [PreviewController].
 */
data class ReadoutIsoBand(
    /** Inclusive minimum ISO for this clamp; null means sensor auto-range. */
    val minIso: Int?,
    /** Inclusive maximum ISO for this clamp; null means sensor auto-range. */
    val maxIso: Int?,
) {
    init {
        val bothNull = minIso == null && maxIso == null
        val bothSet = minIso != null && maxIso != null
        require(bothNull || bothSet) { "ReadoutIsoBand requires both min/max or neither." }
        if (bothSet) {
            require(minIso > 0 && maxIso > 0 && minIso <= maxIso) {
                "Invalid ISO bounds: min=$minIso max=$maxIso"
            }
        }
    }

    val isAutoRange: Boolean
        get() = minIso == null || maxIso == null

    val menuLabel: String
        get() =
            if (isAutoRange) {
                "Auto (sensor range)"
            } else {
                "ISO $minIso - $maxIso"
            }

    fun contains(iso: Int): Boolean {
        if (isAutoRange) return true
        return iso in minIso!!..maxIso!!
    }

    /** Clamp [value] to this band and the HAL [range] (when present). */
    fun clampPick(
        range: Range<Int>?,
        value: Int,
    ): Int {
        var v =
            if (isAutoRange) {
                value
            } else {
                value.coerceIn(minIso!!, maxIso!!)
            }
        if (range != null) {
            v = v.coerceIn(range.lower, range.upper)
        }
        return v
    }

    /** Filter standard ISO table entries to those inside this band and the sensor range. */
    fun filterStops(
        range: Range<Int>?,
        table: IntArray = ReadoutExposureCatalog.ISO_STOP_TABLE,
    ): List<Int> {
        val tableFloor = table.firstOrNull() ?: 1
        val tableCeil = table.lastOrNull() ?: tableFloor
        val lo =
            if (isAutoRange) {
                range?.lower ?: tableFloor
            } else if (range != null) {
                maxOf(minIso!!, range.lower)
            } else {
                minIso!!
            }
        val hi =
            if (isAutoRange) {
                range?.upper ?: tableCeil
            } else if (range != null) {
                minOf(maxIso!!, range.upper)
            } else {
                maxIso!!
            }
        if (lo > hi) return emptyList()
        return table.filter { it in lo..hi }
    }

    fun persistValue(): String =
        if (isAutoRange) {
            "auto"
        } else {
            "range:${minIso}-${maxIso}"
        }

    companion object {
        val AUTO: ReadoutIsoBand = ReadoutIsoBand(null, null)
        val FULL: ReadoutIsoBand = AUTO

        val quickPresets: List<ReadoutIsoBand> =
            listOf(
                AUTO,
                fromBounds(100, 400),
                fromBounds(100, 800),
                fromBounds(100, 3200),
            )

        fun fromBounds(
            minIso: Int,
            maxIso: Int,
        ): ReadoutIsoBand = ReadoutIsoBand(minIso, maxIso)

        fun parsePersisted(raw: String?): ReadoutIsoBand {
            val token = raw?.trim().orEmpty()
            if (token.isBlank()) return AUTO
            if (token.equals("auto", ignoreCase = true)) return AUTO
            // Backward compatibility for older enum persistence values.
            when (token.uppercase()) {
                "FULL" -> return AUTO
                "BAND_100_400" -> return fromBounds(100, 400)
                "BAND_100_800" -> return fromBounds(100, 800)
                "BAND_100_3200" -> return fromBounds(100, 3200)
            }
            if (token.startsWith("range:", ignoreCase = true)) {
                val body = token.substringAfter(':', "")
                val parts = body.split('-', limit = 2)
                val lo = parts.getOrNull(0)?.toIntOrNull()
                val hi = parts.getOrNull(1)?.toIntOrNull()
                if (lo != null && hi != null && lo > 0 && hi > 0 && lo <= hi) {
                    return fromBounds(lo, hi)
                }
            }
            return AUTO
        }
    }
}


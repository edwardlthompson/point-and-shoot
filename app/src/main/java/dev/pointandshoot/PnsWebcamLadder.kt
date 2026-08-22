@file:Suppress("MagicNumber")

package dev.pointandshoot

/**
 * Sustained webcam encode rungs. Start at 4K60; step down only for heat or configure fail.
 * No disk mux — encode heat only.
 */
object PnsWebcamLadder {
    data class Tier(
        val width: Int,
        val height: Int,
        val fps: Int,
        val bitrate: Int,
        val name: String,
    ) {
        val isUhd60: Boolean
            get() = width >= 3840 && height >= 2160 && fps >= 60

        val pixels: Long
            get() = width.toLong() * height.toLong()
    }

    val Tiers: List<Tier> =
        listOf(
            Tier(3840, 2160, 60, 16_000_000, "uhd60"),
            Tier(3840, 2160, 30, 10_000_000, "uhd30"),
            Tier(1920, 1080, 60, 8_000_000, "1080p60"),
            Tier(1280, 720, 30, 3_000_000, "720p30"),
        )

    fun indexForThermal(thermalStatus: Int): Int =
        when {
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_CRITICAL -> 3
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_SEVERE -> 2
            thermalStatus >= ApiLevelGuards.THERMAL_STATUS_MODERATE -> 1
            else -> 0
        }

    fun pick(thermalStatus: Int, floorIndex: Int, allowUhd: Boolean): Tier {
        var idx = maxOf(indexForThermal(thermalStatus), floorIndex.coerceIn(0, Tiers.lastIndex))
        if (!allowUhd && idx < 2) idx = 2
        return Tiers[idx]
    }

    fun nextFloor(floorIndex: Int): Int? {
        val next = floorIndex + 1
        return if (next <= Tiers.lastIndex) next else null
    }
}

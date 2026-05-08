package dev.pointandshoot

/**
 * Pure-data builder for the "Color & LUT credits" sub-block on
 * [AboutScreen]. Walks the [LutCatalog] enum and produces one display row per
 * catalog entry per BUILD_PLAN §7 ("About / Heritage: extend AboutScreen.kt
 * with a 'Color & LUT credits' sub-block listing the bundled LUTs, sources,
 * SPDX, and SHA256 (auto-derived from `LutCatalog.kt` so it never drifts)").
 *
 * No Android imports; safe for unit testing on the JVM.
 */
object LutCreditsBuilder {

    /**
     * Produce one [LutCreditRow] per [LutCatalog] entry, filtered to entries
     * whose [LutCatalog.spdx] is in [LutCatalog.ALLOWED_SPDX] (defense-in-
     * depth: even if a non-FOSS entry slipped past the catalog test, the
     * About-page would not surface it).
     */
    fun creditsFromCatalog(): List<LutCreditRow> =
        LutCatalog.entries
            .filter { it.spdx in LutCatalog.ALLOWED_SPDX }
            .map { entry ->
                LutCreditRow(
                    displayName = entry.displayName,
                    description = entry.description,
                    spdx = entry.spdx,
                    source = entry.source,
                    scope = entry.scope.name,
                )
            }

    data class LutCreditRow(
        val displayName: String,
        val description: String,
        val spdx: String,
        val source: String,
        val scope: String,
    )
}

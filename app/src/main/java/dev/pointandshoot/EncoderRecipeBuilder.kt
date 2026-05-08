package dev.pointandshoot

/**
 * Pure-data hydration of the AboutScreen "From the latest probe" subsection
 * from a runtime [EncoderSummary]. Closes the trailing "engine consumption"
 * remainder of BUILD_PLAN §2 ("drive HUD chips / About-page recipe list off
 * `EncoderSummary`").
 *
 * The builder is intentionally Android-free so it can be exercised on the
 * JVM without a Compose runtime; the AboutScreen consumes its output via a
 * thin Compose adapter (`LiveRecipeCard` / `LiveErrorCard`).
 *
 * Design choice: per camera we surface only the **best HFR row** and the
 * **best regular row** by descending `fpsUpper`, then descending area, then
 * by mime (lexicographic, for deterministic ordering). Surfacing more rows
 * per camera floods the About page; the curated [KNOWN_GOOD_RECIPES] list
 * already covers the broader landscape.
 */
object EncoderRecipeBuilder {

    /**
     * Build one or two [Row]s per camera (best HFR + best regular). Cameras
     * with no successful HFR or regular attempts are silently skipped.
     * Returns rows sorted by `cameraId` then HFR-first within camera so the
     * About page reads top-down by physical camera.
     */
    fun recipesFromSummary(summary: EncoderSummary): List<Row> {
        if (summary.knownGood.isEmpty()) return emptyList()
        val out = mutableListOf<Row>()
        val byCamera = summary.knownGood.groupBy { it.cameraId }.toSortedMap()
        for ((camId, attempts) in byCamera) {
            // Best HFR for this camera (already sorted by descending fps + area in summary).
            attempts.firstOrNull { it.sessionKind == SessionKind.Hfr }
                ?.let { out += rowOf(it, camId) }
            attempts.firstOrNull { it.sessionKind == SessionKind.Regular }
                ?.let { out += rowOf(it, camId) }
        }
        return out
    }

    /**
     * Surface the most-frequent failure modes (canonical error → count) so the
     * About page can warn contributors away from known dead ends. Caller picks
     * a sensible cap; default is 5.
     */
    fun errorRowsFromSummary(summary: EncoderSummary, maxRows: Int = 5): List<ErrorRow> {
        require(maxRows > 0) { "maxRows must be > 0 (was $maxRows)" }
        if (summary.canonicalErrors.isEmpty()) return emptyList()
        return summary.canonicalErrors
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(maxRows)
            .map { (note, count) -> ErrorRow(canonicalError = note, count = count) }
    }

    /**
     * Headline counters for the "live probe" section header. `null` indicates
     * "no recent probe artifacts found" (the AboutScreen hides the section
     * entirely in that case).
     */
    fun headlineCounts(summary: EncoderSummary?): HeadlineCounts? {
        if (summary == null || summary.totalAttempts == 0) return null
        return HeadlineCounts(
            totalAttempts = summary.totalAttempts,
            totalOk = summary.totalOk,
            totalFail = summary.totalFail,
            cameraCount = summary.byCamera.size,
        )
    }

    // ---------- types ----------

    data class Row(
        val cameraId: String,
        val sessionKind: SessionKind,
        val sizeLabel: String,
        val fpsLabel: String,
        val mime: String,
        val measuredFps: Double,
        val title: String,
    )

    data class ErrorRow(val canonicalError: String, val count: Int)

    data class HeadlineCounts(
        val totalAttempts: Int,
        val totalOk: Int,
        val totalFail: Int,
        val cameraCount: Int,
    ) {
        val okPercent: Int
            get() = if (totalAttempts == 0) 0 else (totalOk * 100 + totalAttempts / 2) / totalAttempts
    }

    // ---------- helpers ----------

    private fun rowOf(attempt: EncoderAttempt, cameraId: String): Row = Row(
        cameraId = cameraId,
        sessionKind = attempt.sessionKind,
        sizeLabel = attempt.sizeLabel,
        fpsLabel = attempt.fpsLabel,
        mime = attempt.mime ?: EncoderAttempt.extractMimeFromNote(attempt.note) ?: "(unknown)",
        measuredFps = attempt.measuredFps,
        title = titleFor(cameraId, attempt),
    )

    private fun titleFor(cameraId: String, attempt: EncoderAttempt): String =
        "Camera $cameraId : ${attempt.sessionKind.name} ${attempt.fpsUpper}fps ${attempt.sizeLabel}"
}

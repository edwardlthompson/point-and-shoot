package dev.pointandshoot

/**
 * Pure-data aggregator for the exhaustive media-encoder probe artifacts.
 *
 * The probe (see [ExhaustiveMediaProbeScreen]) produces one
 * `exhaustive_probe_<utc>.json` per run. Each camera in that JSON has two
 * arrays of attempts: `hfrAttempts` and `regularAttempts`. Each attempt is
 * one `cameraId + size + fpsRange + mime` combo with a measured outcome.
 *
 * This aggregator answers the open BUILD_PLAN \u00a72 V&V question:
 *   *"which cameraId+size+fps+mimetype combinations are actually stable for
 *    HFR encode (AVC/HEVC), including exact failure modes"*
 *
 * It is deliberately split from any Android JSON code so the logic can be
 * unit-tested on the JVM without needing `org.json` on the test classpath.
 * The Android-side glue (read latest `exhaustive_probe_*.json` from
 * `getExternalFilesDir(null)`, parse with `JSONObject`, build
 * `List<EncoderAttempt>`) sits in a thin adapter, not here.
 *
 * Usage:
 * ```
 * val attempts: List<EncoderAttempt> = adapter.parseLatest(context)
 * val summary = EncoderResultAggregator.summarize(attempts)
 * summary.knownGood.forEach { hud.addRecipe(it) }
 * summary.canonicalErrors.forEach { (note, count) -> log.w("PNS.Probe", "$count attempts: $note") }
 * ```
 */
object EncoderResultAggregator {

    fun summarize(attempts: List<EncoderAttempt>): EncoderSummary {
        if (attempts.isEmpty()) {
            return EncoderSummary(
                totalAttempts = 0,
                totalOk = 0,
                totalFail = 0,
                knownGood = emptyList(),
                knownBad = emptyList(),
                byCamera = emptyMap(),
                canonicalErrors = emptyMap(),
            )
        }

        val ok = attempts.filter { it.ok }
        val fail = attempts.filterNot { it.ok }

        val byCamera = attempts
            .groupBy { it.cameraId }
            .mapValues { (camId, list) ->
                CameraEncoderSummary(
                    cameraId = camId,
                    ok = list.count { it.ok },
                    fail = list.count { !it.ok },
                    bestHfrFps = list.filter { it.ok && it.sessionKind == SessionKind.Hfr }
                        .maxOfOrNull { it.fpsUpper } ?: 0,
                    bestRegularFps = list.filter { it.ok && it.sessionKind == SessionKind.Regular }
                        .maxOfOrNull { it.fpsUpper } ?: 0,
                )
            }
            .toSortedMap() // deterministic ordering for tests

        val canonicalErrors = fail
            .map { canonicalize(it.note) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()

        // Sort known-good by camera, then descending fps, then descending area
        // (more interesting results first for HUD / About-page rendering).
        val knownGoodSorted = ok.sortedWith(
            compareBy<EncoderAttempt> { it.cameraId }
                .thenByDescending { it.fpsUpper }
                .thenByDescending { it.width.toLong() * it.height.toLong() }
                .thenBy { it.mime ?: "" },
        )

        // Sort known-bad by canonical error frequency (most-common first), then by camera.
        val errorRank = canonicalErrors.toList().sortedByDescending { it.second }
            .mapIndexed { idx, (note, _) -> note to idx }
            .toMap()
        val knownBadSorted = fail.sortedWith(
            compareBy<EncoderAttempt> { errorRank[canonicalize(it.note)] ?: Int.MAX_VALUE }
                .thenBy { it.cameraId }
                .thenByDescending { it.fpsUpper },
        )

        return EncoderSummary(
            totalAttempts = attempts.size,
            totalOk = ok.size,
            totalFail = fail.size,
            knownGood = knownGoodSorted,
            knownBad = knownBadSorted,
            byCamera = byCamera,
            canonicalErrors = canonicalErrors,
        )
    }

    /**
     * Look up the best (highest sustained fps, largest area) HFR success on a
     * given camera. Returns null if no HFR success was recorded for that camera.
     */
    fun bestHfrRecipe(summary: EncoderSummary, cameraId: String): EncoderAttempt? =
        summary.knownGood
            .filter { it.cameraId == cameraId && it.sessionKind == SessionKind.Hfr }
            .firstOrNull()

    /**
     * Reduce a free-form note to a canonical error key for grouping. Different
     * call sites emit slightly different message tails for the same underlying
     * failure ("errno -38 Function not implemented at line 41" vs
     * "errno -38 Function not implemented somewhere else"), so we collapse to
     * the errno number only - that IS the canonical failure identifier on
     * Linux. The full original message is still available on every
     * [EncoderAttempt.note] for human-readable display.
     *
     * Examples:
     *   "errno -38 Function not implemented at line 41"  -> "errno -38"
     *   "mime=video/avc unsupported on this codec"       -> "mime unsupported"
     *   "encoder configure failed: invalid format"        -> "encoder configure failed: invalid format"
     */
    fun canonicalize(note: String): String {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) return ""
        // Errno: collapse to just `errno <N>` so different message tails group together.
        val errno = Regex("""errno\s*(-?\d+)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (errno != null) return "errno ${errno.groupValues[1]}"
        // "mime=video/avc unsupported" or similar - drop the specific mime.
        val mimeUnsup = Regex("""mime=\S+\s+unsupported""", RegexOption.IGNORE_CASE).find(trimmed)
        if (mimeUnsup != null) return "mime unsupported"
        // Configure / start failure tail - keep the first sentence (capped).
        return trimmed.substringBefore('\n').take(120)
    }
}

/**
 * One row from `hfrAttempts` / `regularAttempts`. Pure data; no Android types.
 *
 * The `mime` field may be null when the underlying probe's `EncoderProbeResult`
 * stored the mime in the `note` field instead of as a first-class attribute.
 * Callers can recover it from the note via [extractMimeFromNote] when needed.
 */
data class EncoderAttempt(
    val cameraId: String,
    val sessionKind: SessionKind,
    val width: Int,
    val height: Int,
    val fpsLower: Int,
    val fpsUpper: Int,
    val mime: String?,
    val ok: Boolean,
    val measuredFps: Double,
    val note: String,
) {
    /** Pretty `WxH` size for HUD / report rendering. */
    val sizeLabel: String get() = "${width}x$height"
    /** Pretty `[lo, hi]` fps range for HUD / report rendering. */
    val fpsLabel: String get() = "[$fpsLower, $fpsUpper]"

    companion object {
        /**
         * Recover the mime type from a probe note like `mime=video/avc unsupported`.
         * Returns null if no mime token is present.
         */
        fun extractMimeFromNote(note: String): String? =
            Regex("""mime=(\S+)""").find(note)?.groupValues?.get(1)
    }
}

enum class SessionKind {
    /** `createConstrainedHighSpeedCaptureSession` path. */
    Hfr,

    /** Standard `createCaptureSession` path. */
    Regular,
}

data class EncoderSummary(
    val totalAttempts: Int,
    val totalOk: Int,
    val totalFail: Int,
    /** All successful attempts, sorted by camera then descending fps then descending area. */
    val knownGood: List<EncoderAttempt>,
    /** All failed attempts, grouped by canonical error frequency. */
    val knownBad: List<EncoderAttempt>,
    /** Per-camera roll-up. */
    val byCamera: Map<String, CameraEncoderSummary>,
    /** Canonicalized failure notes -> count, useful for HUD warning chips. */
    val canonicalErrors: Map<String, Int>,
)

data class CameraEncoderSummary(
    val cameraId: String,
    val ok: Int,
    val fail: Int,
    val bestHfrFps: Int,
    val bestRegularFps: Int,
)

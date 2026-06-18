package dev.pointandshoot.fleet

import org.json.JSONObject

/** Hub pre-flight checks for public leaderboard contribution (Milestone 25). */
object LeaderboardReadiness {
    enum class Level { GREEN, YELLOW, RED }

    data class Check(
        val label: String,
        val level: Level,
        val detail: String,
    )

    data class Report(
        val checks: List<Check>,
        val contributeEnabled: Boolean,
        val overall: Level,
        val publicDeviceSlug: String?,
        val publicDeviceUrl: String?,
    )

    fun evaluate(
        matrix: JSONObject?,
        parityReport: JSONObject?,
        ingestConfigured: Boolean,
        publicBaseUrl: String = "",
    ): Report {
        val checks = mutableListOf<Check>()
        val scanTier = matrix?.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.optString("scanTier").orEmpty()
        val tierFull = scanTier == "full"
        checks +=
            Check(
                label = "Matrix scan tier",
                level = if (tierFull) Level.GREEN else Level.RED,
                detail = if (tierFull) "full" else scanTier.ifBlank { "unknown — run Rescan full" },
            )

        val rearLens = rearLensInfoStatus(matrix)
        checks +=
            Check(
                label = "Rear lensInfo (sensor mm²)",
                level =
                    when {
                        rearLens.allPresent -> Level.GREEN
                        rearLens.anyPresent && tierFull -> Level.YELLOW
                        tierFull -> Level.RED
                        else -> Level.YELLOW
                    },
                detail = rearLens.detail,
            )

        val mode = parityReport?.optString("mode").orEmpty()
        val fullSweep = mode.equals("full", ignoreCase = true)
        checks +=
            Check(
                label = "Parity sweep mode",
                level = if (fullSweep) Level.GREEN else Level.YELLOW,
                detail = mode.ifBlank { "none — run Full sweep" },
            )

        val betrayal = parityReport?.optInt("resolutionBetrayalIndex", -1) ?: -1
        checks +=
            Check(
                label = "Resolution betrayal index",
                level = if (betrayal >= 0) Level.GREEN else Level.YELLOW,
                detail = if (betrayal >= 0) "$betrayal (higher = more hidden high-res)" else "run full sweep",
            )

        val rom = matrix?.let { detectedRomFlavor(it) } ?: "unknown"
        checks +=
            Check(
                label = "ROM flavor (detected)",
                level = Level.GREEN,
                detail = rom,
            )

        checks +=
            Check(
                label = "Ingest URL configured",
                level = if (ingestConfigured) Level.GREEN else Level.RED,
                detail = if (ingestConfigured) "ok" else "BuildConfig.LEADERBOARD_INGEST_URL empty",
            )

        val overall =
            when {
                checks.any { it.level == Level.RED } -> Level.RED
                checks.any { it.level == Level.YELLOW } -> Level.YELLOW
                else -> Level.GREEN
            }
        val slug = matrix?.let { LeaderboardDeviceSlug.fromMatrix(it) }
        val publicUrl = slug?.let { LeaderboardDeviceSlug.publicDeviceUrl(it, publicBaseUrl) }
        val contribute =
            ingestConfigured &&
                parityReport != null &&
                fullSweep &&
                tierFull &&
                rearLens.allPresent
        return Report(
            checks = checks,
            contributeEnabled = contribute,
            overall = overall,
            publicDeviceSlug = slug,
            publicDeviceUrl = publicUrl,
        )
    }

    data class RearLensStatus(
        val rearCount: Int,
        val withLensInfo: Int,
        val allPresent: Boolean,
        val anyPresent: Boolean,
        val detail: String,
    )

    fun rearLensInfoStatus(matrix: JSONObject?): RearLensStatus {
        val cams = matrix?.optJSONArray(FleetDeviceMatrix.KEY_CAMERAS) ?: return RearLensStatus(0, 0, false, false, "no matrix")
        var rear = 0
        var withInfo = 0
        for (i in 0 until cams.length()) {
            val cam = cams.optJSONObject(i) ?: continue
            if (!isRearRole(cam)) continue
            rear++
            val w = cam.optJSONObject("lensInfo")?.optJSONObject("sensorPhysicalSizeMm")?.optDouble("widthMm") ?: 0.0
            if (w > 0) withInfo++
        }
        val detail =
            when {
                rear == 0 -> "no rear cameras enumerated"
                withInfo == rear -> "$withInfo/$rear rear cameras"
                withInfo > 0 -> "$withInfo/$rear rear cameras (partial — full rescan)"
                else -> "0/$rear rear cameras — run full rescan"
            }
        return RearLensStatus(
            rearCount = rear,
            withLensInfo = withInfo,
            allPresent = rear > 0 && withInfo == rear,
            anyPresent = withInfo > 0,
            detail = detail,
        )
    }

    fun detectedRomFlavor(matrix: JSONObject): String {
        val product = matrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT) ?: return "unknown"
        val buildId = product.optJSONObject("buildIdentity")
        val unlock = product.optJSONObject("experimentalUnlockState")
        if (unlock?.optBoolean("rootGranted") == true) return "root_unlocked"
        val tags = buildId?.optString("tags").orEmpty()
        val display = buildId?.optString("display").orEmpty()
        val type = buildId?.optString("type").orEmpty()
        if (tags.contains("test-keys")) return "custom_likely"
        if (display.contains("lineage", ignoreCase = true) ||
            display.contains("crdroid", ignoreCase = true) ||
            display.contains("evolution", ignoreCase = true)
        ) {
            return "custom_likely"
        }
        if (type == "userdebug") return "engineering"
        if (tags.contains("release-keys")) return "stock"
        return "unknown"
    }

    private fun isRearRole(cam: JSONObject): Boolean {
        val role = cam.optJSONObject("fleetPolicy")?.optString("role").orEmpty()
        if (role in setOf("LOGICAL", "FRONT")) return false
        if (role in setOf("UW", "WIDE", "TELE", "TELE_AUX", "MACRO", "UNKNOWN")) return true
        return role.isNotBlank() && role != "FRONT"
    }
}

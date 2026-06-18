package dev.pointandshoot

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Stable JSON serializer + parser for [CalibrationProfile] per BUILD_PLAN §7
 * ("Profile JSON saved to app-private storage
 * `getExternalFilesDir(null)/calibration/<illuminant>_<utc>.json`; pulled into
 * `<OutDir>/calibration/` (default `.\\hfr-runs\\calibration\\`) by
 * `pns_hfr_autorun.ps1 -PullCalibration`").
 *
 * The schema is intentionally hand-rolled on top of `org.json` (Android
 * framework type; the test classpath has the real `org.json:json` lib via
 * `testImplementation`) so we do not pull a serialization framework just for
 * one data class.
 *
 * Schema (`SCHEMA_VERSION` is the only field that may break compatibility):
 * ```
 * {
 *   "version": 1,
 *   "cameraId": "...",
 *   "targetId": "...",
 *   "illuminant": "D65",
 *   "capturedAtMs": 1714760000000,
 *   "wbGains": { "r": 1.18, "g": 1.0, "b": 0.92 },
 *   "ccm": [
 *     [1.05, 0.04, -0.03],
 *     [0.02, 0.95, 0.02],
 *     [-0.04, 0.06, 1.02]
 *   ],
 *   "bias": [0.0, 0.0, 0.0],
 *   "mtf50Lpph": 1647.5         // omitted when null
 * }
 * ```
 */
object CalibrationProfileJsonAdapter {

    /** Bumped only on incompatible schema changes. */
    const val SCHEMA_VERSION: Int = 1

    /**
     * Serialize the profile to a pretty-printed JSON string. The output is
     * stable enough to be diff-friendly: keys appear in the documented order
     * and floats are rendered without scientific notation.
     */
    fun encode(profile: CalibrationProfile): String {
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("cameraId", profile.cameraId)
        root.put("targetId", profile.targetId)
        root.put("illuminant", profile.illuminant.name)
        root.put("capturedAtMs", profile.capturedAtMs)

        val wb = JSONObject()
        wb.put("r", profile.wbGains.r.toDouble())
        wb.put("g", profile.wbGains.g.toDouble())
        wb.put("b", profile.wbGains.b.toDouble())
        root.put("wbGains", wb)

        val ccm = JSONArray()
        ccm.put(rowOf(profile.ccm.m00, profile.ccm.m01, profile.ccm.m02))
        ccm.put(rowOf(profile.ccm.m10, profile.ccm.m11, profile.ccm.m12))
        ccm.put(rowOf(profile.ccm.m20, profile.ccm.m21, profile.ccm.m22))
        root.put("ccm", ccm)

        val bias = JSONArray()
        bias.put(profile.bias.r.toDouble())
        bias.put(profile.bias.g.toDouble())
        bias.put(profile.bias.b.toDouble())
        root.put("bias", bias)

        profile.mtf50Lpph?.let { mtf -> root.put("mtf50Lpph", mtf.toDouble())
        }

        return root.toString(2)
    }

    /**
     * Parse a JSON string into a [CalibrationProfile]. Throws
     * [IllegalArgumentException] for any structural problem (missing keys,
     * wrong shapes, unsupported `version`, unknown illuminant, invalid
     * gains/bias). The error message names the offending field so support
     * tickets can pinpoint malformed user-imported profiles.
     */
    fun decode(text: String): CalibrationProfile {
        val root = try {
            JSONObject(text)
        } catch (ex: JSONException) {
            throw IllegalArgumentException("not a JSON object: ${ex.message}", ex)
        }

        val version = root.optInt("version", -1)
        require(version == SCHEMA_VERSION) {
            "unsupported version $version (expected $SCHEMA_VERSION)"
        }
        val cameraId = root.optString("cameraId").also {
            require(it.isNotBlank()) { "cameraId must not be blank" }
        }
        val targetId = root.optString("targetId").also {
            require(it.isNotBlank()) { "targetId must not be blank" }
        }
        val illuminantName = root.optString("illuminant").also {
            require(it.isNotBlank()) { "illuminant must not be blank" }
        }
        val illuminant = try {
            CalibrationProfile.Illuminant.valueOf(illuminantName)
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException(
                "unknown illuminant '$illuminantName'; allowed: " +
                    CalibrationProfile.Illuminant.entries.joinToString { it.name },
                ex,
            )
        }

        val capturedAtMs = root.optLong("capturedAtMs", -1L)
        require(capturedAtMs >= 0L) { "capturedAtMs must be >= 0 (was $capturedAtMs)" }

        val wbObj = root.optJSONObject("wbGains")
            ?: throw IllegalArgumentException("missing wbGains")
        val wbGains = CalibrationProfile.WbGains(
            r = wbObj.requireDouble("wbGains.r").toFloat(),
            g = wbObj.requireDouble("wbGains.g").toFloat(),
            b = wbObj.requireDouble("wbGains.b").toFloat(),
        )

        val ccmArr = root.optJSONArray("ccm")
            ?: throw IllegalArgumentException("missing ccm")
        require(ccmArr.length() == 3) { "ccm must be 3 rows (was ${ccmArr.length()})" }
        val rows = (0 until 3).map { i ->
            val row = ccmArr.optJSONArray(i)
                ?: throw IllegalArgumentException("ccm row $i must be an array")
            require(row.length() == 3) { "ccm row $i must have 3 columns (was ${row.length()})" }
            FloatArray(3) { ch ->
                val v = row.optDouble(ch, Double.NaN)
                require(!v.isNaN()) { "ccm[$i][$ch] must be a number" }
                v.toFloat()
            }
        }
        val ccm = CalibrationProfile.Ccm(
            m00 = rows[0][0], m01 = rows[0][1], m02 = rows[0][2],
            m10 = rows[1][0], m11 = rows[1][1], m12 = rows[1][2],
            m20 = rows[2][0], m21 = rows[2][1], m22 = rows[2][2],
        )

        val biasArr = root.optJSONArray("bias")
            ?: throw IllegalArgumentException("missing bias")
        require(biasArr.length() == 3) { "bias must be length 3 (was ${biasArr.length()})" }
        val bias = CalibrationProfile.Bias(
            r = biasArr.requireDoubleAt(0, "bias[0]").toFloat(),
            g = biasArr.requireDoubleAt(1, "bias[1]").toFloat(),
            b = biasArr.requireDoubleAt(2, "bias[2]").toFloat(),
        )

        val mtf50 = if (root.has("mtf50Lpph") && !root.isNull("mtf50Lpph")) {
            root.requireDouble("mtf50Lpph").toFloat().also {
                require(it >= 0f) { "mtf50Lpph must be >= 0 (was $it)" }
            }
        } else {
            null
        }

        return CalibrationProfile(
            wbGains = wbGains,
            ccm = ccm,
            bias = bias,
            mtf50Lpph = mtf50,
            illuminant = illuminant,
            capturedAtMs = capturedAtMs,
            cameraId = cameraId,
            targetId = targetId,
        )
    }

    /**
     * Stable filename for a profile: `<illuminant>_<utc>.json`. Matches the
     * BUILD_PLAN §7 spec for `getExternalFilesDir(null)/calibration/`.
     */
    fun filenameFor(profile: CalibrationProfile, utc: String): String =
        "${profile.illuminant.name}_${utc}.json"

    // ---------- helpers ----------

    private fun rowOf(a: Float, b: Float, c: Float): JSONArray {
        val r = JSONArray()
        r.put(a.toDouble())
        r.put(b.toDouble())
        r.put(c.toDouble())
        return r
    }

    private fun JSONObject.requireDouble(field: String): Double {
        // Consume a possibly-dotted path like "wbGains.r" - this object should
        // already be the inner one; the field is rendered into the error.
        val key = field.substringAfterLast('.')
        if (!has(key)) throw IllegalArgumentException("missing $field")
        val v = optDouble(key, Double.NaN)
        if (v.isNaN()) throw IllegalArgumentException("$field must be a number")
        return v
    }

    private fun JSONArray.requireDoubleAt(index: Int, field: String): Double {
        val v = optDouble(index, Double.NaN)
        if (v.isNaN()) throw IllegalArgumentException("$field must be a number")
        return v
    }
}

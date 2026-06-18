package dev.pointandshoot.fleet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * Per-camera calibration tags (CM/FM/ASN) extracted from ReferenceCam reference DNGs on LegacySku.
 * Bundled as [ASSET_PATH]; regenerate with [scripts/referenceapp_ref_extract_calibration.py].
 */
object ReferenceAppReferenceCalibration {
    private const val TAG = "PNS.ReferenceAppRefCal"
    const val ASSET_PATH = "fleet/legacy_device_referenceapp_calibration.json"

    data class Slot(
        val asnRationalNd: LongArray,
        val colorMatrix1Nd: IntArray,
        val colorMatrix2Nd: IntArray,
        val forwardMatrix1Nd: IntArray,
        val forwardMatrix2Nd: IntArray,
        /** ReferenceCam reference Bayer R/G (center crop). */
        val bayerRg: Float,
        /** ReferenceCam reference Bayer B/G (center crop). */
        val bayerBg: Float,
    ) {
        fun colorMatrix1(): Array<android.util.Rational> = srationalsFromNd(colorMatrix1Nd)
        fun colorMatrix2(): Array<android.util.Rational> = srationalsFromNd(colorMatrix2Nd)
        fun forwardMatrix1(): Array<android.util.Rational> = srationalsFromNd(forwardMatrix1Nd)
        fun forwardMatrix2(): Array<android.util.Rational> = srationalsFromNd(forwardMatrix2Nd)
    }

    private val cache = AtomicReference<Map<String, Slot>?>(null)

    fun forCameraId(context: Context, sessionCameraId: String): Slot? =
        slots(context)[sessionCameraId]

    fun slots(context: Context): Map<String, Slot> {
        cache.get()?.let { return it }
        val loaded = loadFromAssets(context)
        cache.set(loaded)
        return loaded
    }

    internal fun clearCacheForTests() {
        cache.set(null)
    }

    private fun loadFromAssets(context: Context): Map<String, Slot> {
        return runCatching {
            context.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                parse(JSONObject(reader.readText()))
            }
        }.getOrElse { e ->
            Log.e(TAG, "failed to load $ASSET_PATH", e)
            emptyMap()
        }
    }

    fun parse(root: JSONObject): Map<String, Slot> {
        require(root.optString("schema").startsWith("legacy_device_referenceapp_calibration")) {
            "unexpected schema ${root.optString("schema")}"
        }
        val slotsObj = root.getJSONObject("slots")
        val out = linkedMapOf<String, Slot>()
        for (key in slotsObj.keys()) {
            val slotJson = slotsObj.getJSONObject(key)
            out[key] =
                Slot(
                    asnRationalNd = longArrayFromNd(slotJson.getJSONArray("asn_rational_nd")),
                    colorMatrix1Nd = intArrayFromNd(slotJson.getJSONArray("color_matrix1_srational_nd")),
                    colorMatrix2Nd = intArrayFromNd(slotJson.getJSONArray("color_matrix2_srational_nd")),
                    forwardMatrix1Nd = intArrayFromNd(slotJson.getJSONArray("forward_matrix1_srational_nd")),
                    forwardMatrix2Nd = intArrayFromNd(slotJson.getJSONArray("forward_matrix2_srational_nd")),
                    bayerRg = slotJson.optDouble("bayer_rg", 1.0).toFloat(),
                    bayerBg = slotJson.optDouble("bayer_bg", 1.0).toFloat(),
                )
        }
        return out
    }

    private fun longArrayFromNd(arr: org.json.JSONArray): LongArray {
        val out = LongArray(arr.length())
        for (i in 0 until arr.length()) {
            out[i] = arr.getLong(i)
        }
        return out
    }

    private fun intArrayFromNd(arr: org.json.JSONArray): IntArray {
        require(arr.length() == 18) { "expected 9 srationals (18 ints), got ${arr.length()}" }
        return IntArray(18) { arr.getInt(it) }
    }

    private fun srationalsFromNd(nd: IntArray): Array<android.util.Rational> {
        require(nd.size == 18) { "expected 18 ints, got ${nd.size}" }
        return Array(9) { i ->
            val n = nd[i * 2]
            val d = nd[i * 2 + 1].coerceAtLeast(1)
            android.util.Rational(n, d)
        }
    }
}

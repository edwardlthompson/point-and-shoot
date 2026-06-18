package dev.pointandshoot.fleet

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.pointandshoot.fleet.BuildConfig
import dev.pointandshoot.PnsAdbLog
import dev.pointandshoot.PnsConnectivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Opt-in public leaderboard submission (parity sweep + matrix bundle).
 * Log tag: [TAG]. No network unless [PnsConnectivity.isLeaderboardContributeEnabled].
 */
object FleetLeaderboardSubmit {
    const val TAG = "PNS.LeaderboardSubmit"

    data class SubmitResult(val ok: Boolean, val httpCode: Int, val message: String, val submissionId: String? = null)

    fun ingestUrl(): String? = BuildConfig.LEADERBOARD_INGEST_URL.trim().takeIf { it.isNotEmpty() }

    fun buildRedactedMatrix(matrix: JSONObject): JSONObject {
        val copy = JSONObject(matrix.toString())
        copy.optJSONObject(FleetDeviceMatrix.KEY_APPENDIX)?.remove("halDumpsysMediaCamera")
        copy.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("buildIdentity")?.let { bi ->
            val display = bi.optString("display")
            if (display.length > 80) bi.put("display", display.take(80))
        }
        return copy
    }

    fun submissionDigest(parity: JSONObject, matrix: JSONObject): String {
        val canon =
            buildString {
                append(parity.optJSONArray("cells")?.toString().orEmpty())
                append("|")
                append(matrix.optJSONObject(FleetDeviceMatrix.KEY_SCAN_META)?.toString().orEmpty())
                append("|")
                append(parity.optInt("catalogVersion", 0))
            }
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(canon.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun signingCertSha256(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val sig = info.signingInfo?.apkContentsSigners?.firstOrNull() ?: return@runCatching null
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    fun submit(
        context: Context,
        parityReport: JSONObject,
        matrix: JSONObject,
        romReported: LeaderboardRomReport.Reported = LeaderboardRomReport.Reported.UNSPECIFIED,
    ): SubmitResult {
        if (!PnsConnectivity.isLeaderboardContributeEnabled(context)) {
            return SubmitResult(false, 0, "contribute_disabled")
        }
        val url = ingestUrl()
        if (url.isNullOrEmpty()) {
            return SubmitResult(false, 0, "ingest_url_unconfigured")
        }
        val detected = LeaderboardRomReport.detectedFlavor(matrix)
        if (!LeaderboardRomReport.isConsistent(romReported, detected)) {
            return SubmitResult(false, 0, "rom_reported_mismatch detected=$detected")
        }
        val redactedMatrix = buildRedactedMatrix(matrix)
        val body =
            JSONObject().apply {
                put("schema", "pns.leaderboard_submission.v1")
                put("submittedUtc", java.time.Instant.now().toString())
                put("appVersionCode", BuildConfig.VERSION_CODE)
                put("appSigningCertSha256", signingCertSha256(context) ?: JSONObject.NULL)
                put("submissionDigest", submissionDigest(parityReport, redactedMatrix))
                put("parityReport", parityReport)
                put("matrix", redactedMatrix)
                put("romReported", romReported.wire)
                put("romDetected", detected)
                put(
                    "measurementContext",
                    parityReport.optJSONObject("measurementContext")
                        ?: JSONObject().apply {
                            put("api", "camera2")
                            put("cameraXProbed", redactedMatrix.optJSONObject(FleetDeviceMatrix.KEY_CAMERA_X) != null)
                            put("oemCameraAppTested", false)
                        },
                )
                redactedMatrix.optJSONObject(FleetDeviceMatrix.KEY_PRODUCT)?.optJSONObject("buildIdentity")?.let { bi ->
                    put("buildDisplay", bi.optString("display").take(80))
                }
                LeaderboardAntutuPrefs.read(context)?.let { score ->
                    put(
                        "antutuScore",
                        JSONObject().apply {
                            put("total", score.total)
                            score.cpu?.let { put("cpu", it) }
                            score.gpu?.let { put("gpu", it) }
                            score.mem?.let { put("mem", it) }
                            score.ux?.let { put("ux", it) }
                            put("capturedUtc", java.time.Instant.now().toString())
                            score.antutuAppVersion?.let { put("antutuAppVersion", it) }
                        },
                    )
                }
                put(
                    "clientMeta",
                    JSONObject().apply {
                        put("locale", context.resources.configuration.locales[0]?.toLanguageTag() ?: "en")
                        put("submissionSource", "hub_manual")
                        put("publicDeviceSlug", LeaderboardDeviceSlug.fromMatrix(redactedMatrix))
                    },
                )
            }
        return runCatching {
            val conn = (URL(url.trimEnd('/') + "/v1/submit").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val respText = stream?.let { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
            conn.disconnect()
            val submissionId =
                runCatching { JSONObject(respText).optString("submissionId").takeIf { it.isNotEmpty() } }.getOrNull()
            val ok = code in 200..299
            PnsAdbLog.i(context, "leaderboardSubmit ok=$ok code=$code id=$submissionId")
            Log.i(TAG, "submit ok=$ok code=$code id=$submissionId")
            SubmitResult(ok, code, if (ok) "submitted" else respText.take(200), submissionId)
        }.getOrElse { e ->
            Log.e(TAG, "submit failed", e)
            SubmitResult(false, 0, e.message ?: "error")
        }
    }
}

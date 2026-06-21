package dev.pointandshoot

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sprint **29.2** — SAF JSON export/import for HUD + chrome + workflow prefs.
 * Catalog: `product.settings_export`.
 */
object SettingsExportBundle {
    private const val TAG = "PNS.SettingsExport"
    const val SCHEMA = "pns_settings_export.v1"
    const val SCHEMA_VERSION = 1
    const val MIME_TYPE = "application/json"
    const val AUTOMATION_SUBDIR = "settings_export"
    const val AUTOMATION_FILENAME = "pns_settings_export_latest.json"

    data class ExportResult(
        val keyCount: Int,
        val path: String? = null,
    )

    data class ImportResult(
        val keyCount: Int,
        val stripExifPrivacyTags: Boolean,
    )

    private val HUD_SKIP_ON_IMPORT =
        setOf(
            "intervalometer_running",
        )

    private val CHROME_SKIP_ON_IMPORT =
        setOf(
            "last_rear_camera_id",
        )

    private val FLOAT_IMPORT_KEYS =
        setOf(
            "audio_gain_db",
            "focus_breathing_k",
            "rack_wp_near",
            "rack_wp_far",
            "anamorphic_squeeze",
            "shutter_sound_volume",
        )

    fun defaultAutomationFile(context: Context): File {
        val dir = File(context.applicationContext.filesDir, AUTOMATION_SUBDIR)
        dir.mkdirs()
        return File(dir, AUTOMATION_FILENAME)
    }

    fun buildJson(context: Context): String {
        val app = context.applicationContext
        val utc = nowUtcCompact()
        val root =
            JSONObject()
                .put("schema", SCHEMA)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("exportedAtUtc", utc)
                .put(
                    "prefs",
                    JSONObject()
                        .put("hud", prefsToJson(app.getSharedPreferences(HudSettings.PREFS_NAME, Context.MODE_PRIVATE)))
                        .put(
                            "chrome",
                            prefsToJson(
                                app.getSharedPreferences(PreviewChromePreferences.PREFS_NAME, Context.MODE_PRIVATE),
                            ),
                        )
                        .put(
                            "ux",
                            prefsToJson(app.getSharedPreferences(UxSettings.PREFS_NAME, Context.MODE_PRIVATE)),
                        )
                        .put(
                            "workflow",
                            prefsToJson(
                                app.getSharedPreferences("pns_workflow_presets", Context.MODE_PRIVATE),
                            ),
                        ),
                )
        return root.toString(2)
    }

    fun exportToAutomationFile(context: Context): ExportResult? {
        val file = defaultAutomationFile(context)
        return runCatching {
            val json = buildJson(context)
            file.writeText(json, Charsets.UTF_8)
            val keys = countKeys(json)
            Log.i(TAG, "exportAutomation path=${file.absolutePath} keys=$keys")
            ExportResult(keyCount = keys, path = file.absolutePath)
        }.getOrElse {
            Log.e(TAG, "exportAutomation failed: ${it.message}")
            null
        }
    }

    fun writeToUri(context: Context, uri: Uri): ExportResult? {
        val app = context.applicationContext
        return runCatching {
            val json = buildJson(context)
            app.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
            } ?: return null
            val keys = countKeys(json)
            Log.i(TAG, "exportSaf ok keys=$keys")
            ExportResult(keyCount = keys)
        }.getOrElse {
            Log.e(TAG, "exportSaf failed: ${it.message}")
            null
        }
    }

    fun importFromUri(context: Context, uri: Uri): ImportResult? {
        val app = context.applicationContext
        val json =
            app.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: return null
        return importFromJson(app, json)
    }

    fun importFromAutomationFile(context: Context): ImportResult? {
        val file = defaultAutomationFile(context)
        if (!file.isFile) return null
        return runCatching {
            importFromJson(context.applicationContext, file.readText(Charsets.UTF_8))
        }.getOrElse {
            Log.e(TAG, "importAutomation failed: ${it.message}")
            null
        }
    }

    fun suggestedExportFilename(): String {
        return "pns_settings_export_${nowUtcCompact()}.json"
    }

    internal fun importFromJson(context: Context, json: String): ImportResult? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val schema = root.optString("schema", "")
        if (schema != SCHEMA) {
            Log.w(TAG, "import schema mismatch: $schema")
            return null
        }
        val prefsRoot = root.optJSONObject("prefs") ?: return null
        var imported = 0
        imported += mergePrefs(
            context.getSharedPreferences(HudSettings.PREFS_NAME, Context.MODE_PRIVATE),
            prefsRoot.optJSONObject("hud"),
            HUD_SKIP_ON_IMPORT,
        )
        imported += mergePrefs(
            context.getSharedPreferences(PreviewChromePreferences.PREFS_NAME, Context.MODE_PRIVATE),
            prefsRoot.optJSONObject("chrome"),
            CHROME_SKIP_ON_IMPORT,
        )
        imported += mergePrefs(
            context.getSharedPreferences(UxSettings.PREFS_NAME, Context.MODE_PRIVATE),
            prefsRoot.optJSONObject("ux"),
            emptySet(),
        )
        imported += mergePrefs(
            context.getSharedPreferences("pns_workflow_presets", Context.MODE_PRIVATE),
            prefsRoot.optJSONObject("workflow"),
            emptySet(),
        )
        val strip =
            PreviewChromePreferences.load(context).stripExifPrivacyTags
        Log.i(TAG, "import ok keys=$imported stripExif=$strip")
        return ImportResult(keyCount = imported, stripExifPrivacyTags = strip)
    }

    internal fun prefsToJson(prefs: SharedPreferences): JSONObject {
        val out = JSONObject()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> out.put(key, value)
                is Int -> out.put(key, value)
                is Long -> out.put(key, value)
                is Float -> out.put(key, value.toDouble())
                is Double -> out.put(key, value)
                is String -> out.put(key, value)
                is Set<*> -> {
                    val arr = JSONArray()
                    @Suppress("UNCHECKED_CAST")
                    (value as Set<String>).sorted().forEach { arr.put(it) }
                    out.put(key, arr)
                }
                else -> Log.w(TAG, "prefsToJson skip unsupported type for $key")
            }
        }
        return out
    }

    private fun mergePrefs(
        prefs: SharedPreferences,
        block: JSONObject?,
        skipKeys: Set<String>,
    ): Int {
        if (block == null) return 0
        val editor = prefs.edit()
        var count = 0
        for (key in block.keys()) {
            if (key in skipKeys) continue
            count += applyImportKey(editor, block, key)
        }
        editor.commit()
        return count
    }

    private fun applyImportKey(
        editor: SharedPreferences.Editor,
        block: JSONObject,
        key: String,
    ): Int =
        when (val raw = block.opt(key)) {
            is Boolean -> {
                editor.putBoolean(key, raw)
                1
            }
            is String -> {
                editor.putString(key, raw)
                1
            }
            is JSONArray -> {
                editor.putStringSet(key, jsonArrayToStringSet(raw))
                1
            }
            is Int, is Long, is Double -> {
                applyNumericImportKey(editor, block, key)
                1
            }
            else -> 0
        }

    private fun jsonArrayToStringSet(raw: JSONArray): Set<String> {
        val set = linkedSetOf<String>()
        for (i in 0 until raw.length()) {
            val s = raw.optString(i, null)?.trim()
            if (!s.isNullOrEmpty()) set.add(s)
        }
        return set
    }

    private fun applyNumericImportKey(
        editor: SharedPreferences.Editor,
        block: JSONObject,
        key: String,
    ) {
        if (key in FLOAT_IMPORT_KEYS) {
            editor.putFloat(key, block.optDouble(key).toFloat())
            return
        }
        val asLong = block.optLong(key)
        if (asLong in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            editor.putInt(key, asLong.toInt())
        } else {
            editor.putLong(key, asLong)
        }
    }

    private fun countKeys(json: String): Int {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
        val prefsRoot = root.optJSONObject("prefs") ?: return 0
        var total = 0
        for (section in listOf("hud", "chrome", "ux", "workflow")) {
            val block = prefsRoot.optJSONObject(section)
            if (block != null) total += block.length()
        }
        return total
    }

    private fun nowUtcCompact(): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}

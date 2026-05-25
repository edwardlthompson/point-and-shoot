package dev.pointandshoot

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Sprint **UX.3** — optional backup of P&S DCIM captures to a user-chosen folder (SAF tree).
 *
 * Works with any cloud the user already syncs (Syncthing, Nextcloud, Google Drive folder, etc.)
 * without bundling a proprietary SDK. Android Auto Backup still covers prefs via
 * [pns_backup_rules.xml] / [pns_data_extraction_rules.xml].
 */
object CloudCaptureBackup {
    const val TAG = "PNS.CloudBackup"
    const val PREFS_NAME = "pns_cloud_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TREE_URI = "tree_uri"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SYNCED_IDS = "synced_media_ids"
    private const val BACKUP_DIR_NAME = "Point-and-Shoot"
    private const val MANIFEST_FILE = "pns_backup_manifest.json"
    private const val PROBE_SUBDIR = "ux_cloud_backup_probe"
    private const val MAX_SYNCED_ID_ENTRIES = 800

    data class SyncResult(
        val attempted: Int,
        val copied: Int,
        val skipped: Int,
        val failed: Int,
    )

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
        PnsAdbLog.i(context, "cloudBackup enabled=$enabled")
    }

    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).commit()
    }

    fun loadTreeUri(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() }

    fun saveTreeUri(context: Context, treeUri: Uri) {
        prefs(context).edit().putString(KEY_TREE_URI, treeUri.toString()).commit()
        PnsAdbLog.i(context, "cloudBackup folderSet uri=$treeUri")
    }

    fun clearTreeUri(context: Context) {
        prefs(context).edit().remove(KEY_TREE_URI).commit()
    }

    fun persistTreePermission(context: Context, treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { e ->
            Log.w(TAG, "takePersistableUriPermission failed: ${e.message}")
        }
        saveTreeUri(context, treeUri)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun queueUri(context: Context, uri: Uri, displayName: String? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                backupUri(context.applicationContext, uri, displayName, allowProbeDir = false)
            }.onFailure { e ->
                Log.w(TAG, "queueUri failed uri=$uri: ${e.message}")
            }
        }
    }

    suspend fun syncRecentCaptures(
        context: Context,
        maxItems: Int = 40,
        allowProbeDir: Boolean = false,
    ): SyncResult =
        withContext(Dispatchers.IO) {
            if (!isEnabled(context.applicationContext)) {
                PnsAdbLog.i(context, "cloudBackup sync skipped enabled=false")
                return@withContext SyncResult(0, 0, 0, 0)
            }
            if (!networkAllowsBackup(context.applicationContext)) {
                PnsAdbLog.i(context, "cloudBackup sync skipped network")
                return@withContext SyncResult(0, 0, 0, 0)
            }
            val items = PnsMediaStoreGallery.loadIndex(context.applicationContext, maxItems)
            var copied = 0
            var skipped = 0
            var failed = 0
            items.forEach { item ->
                when (
                    backupUri(
                        context.applicationContext,
                        item.uri,
                        item.displayName,
                        allowProbeDir = allowProbeDir,
                    )
                ) {
                    BackupOutcome.Copied -> copied++
                    BackupOutcome.Skipped -> skipped++
                    BackupOutcome.Failed -> failed++
                    BackupOutcome.Disabled -> return@withContext SyncResult(
                        items.size,
                        copied,
                        skipped,
                        failed,
                    )
                }
            }
            PnsAdbLog.i(
                context,
                "cloudBackup sync ok attempted=${items.size} copied=$copied skipped=$skipped failed=$failed",
            )
            SyncResult(items.size, copied, skipped, failed)
        }

    private enum class BackupOutcome {
        Copied,
        Skipped,
        Failed,
        Disabled,
    }

    private fun backupUri(
        context: Context,
        sourceUri: Uri,
        displayName: String?,
        allowProbeDir: Boolean,
    ): BackupOutcome {
        if (!isEnabled(context)) return BackupOutcome.Disabled
        if (!networkAllowsBackup(context)) {
            Log.d(TAG, "backup deferred (network) uri=$sourceUri")
            return BackupOutcome.Skipped
        }
        val mediaId = sourceUri.lastPathSegment ?: sourceUri.toString()
        if (isAlreadySynced(context, mediaId)) return BackupOutcome.Skipped

        val name =
            displayName?.takeIf { it.isNotBlank() }
                ?: queryDisplayName(context, sourceUri)
                ?: "pns_${System.currentTimeMillis()}"
        val mime = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"

        val treeUri = loadTreeUri(context)
        val copied =
            if (treeUri != null) {
                copyToDocumentTree(context, treeUri, sourceUri, name, mime)
            } else if (allowProbeDir) {
                copyToProbeDir(context, sourceUri, name)
            } else {
                PnsAdbLog.i(context, "cloudBackup skipped noFolder uri=$sourceUri")
                false
            }
        if (!copied) return BackupOutcome.Failed

        markSynced(context, mediaId)
        appendManifestEntry(context, treeUri, allowProbeDir, sourceUri, name, mime)
        Log.i(TAG, "cloudBackup copied name=$name id=$mediaId")
        PnsAdbLog.i(context, "cloudBackup copied name=$name")
        return BackupOutcome.Copied
    }

    private fun copyToDocumentTree(
        context: Context,
        treeUri: Uri,
        sourceUri: Uri,
        fileName: String,
        mime: String,
    ): Boolean =
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val backupDir =
                root.findFile(BACKUP_DIR_NAME)
                    ?: root.createDirectory(BACKUP_DIR_NAME)
                    ?: return false
            val safeName = sanitizeFileName(fileName)
            val existing = backupDir.findFile(safeName)
            if (existing != null && existing.length() > 0L) return true
            val dest = backupDir.createFile(mime, safeName) ?: return false
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(dest.uri, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: return false
            } ?: return false
            true
        }.getOrElse {
            Log.w(TAG, "copyToDocumentTree failed: ${it.message}")
            false
        }

    private fun copyToProbeDir(context: Context, sourceUri: Uri, fileName: String): Boolean =
        runCatching {
            val dir = File(context.getExternalFilesDir(null), PROBE_SUBDIR)
            dir.mkdirs()
            val out = File(dir, sanitizeFileName(fileName))
            if (out.exists() && out.length() > 0L) return true
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            true
        }.getOrElse {
            Log.w(TAG, "copyToProbeDir failed: ${it.message}")
            false
        }

    private fun appendManifestEntry(
        context: Context,
        treeUri: Uri?,
        allowProbeDir: Boolean,
        sourceUri: Uri,
        name: String,
        mime: String,
    ) {
        val entry =
            JSONObject()
                .put("uri", sourceUri.toString())
                .put("displayName", name)
                .put("mime", mime)
                .put("backedUpAtMs", System.currentTimeMillis())
        if (treeUri != null) {
            appendManifestToTree(context, treeUri, entry) ?: Unit
        } else if (allowProbeDir) {
            appendManifestToProbeDir(context, entry)
        }
    }

    private fun appendManifestToTree(context: Context, treeUri: Uri, entry: JSONObject): Boolean? =
        runCatching {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val backupDir =
                root.findFile(BACKUP_DIR_NAME) ?: root.createDirectory(BACKUP_DIR_NAME) ?: return false
            val manifest =
                backupDir.findFile(MANIFEST_FILE)
                    ?: backupDir.createFile("application/json", MANIFEST_FILE.removeSuffix(".json"))
                    ?: return false
            val arr = readManifestArray(context, manifest.uri)
            arr.put(entry)
            context.contentResolver.openOutputStream(manifest.uri, "wt")?.use { out ->
                out.write(arr.toString(2).toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        }.getOrNull()

    private fun appendManifestToProbeDir(context: Context, entry: JSONObject) {
        runCatching {
            val dir = File(context.getExternalFilesDir(null), PROBE_SUBDIR)
            dir.mkdirs()
            val file = File(dir, MANIFEST_FILE)
            val arr =
                if (file.isFile) {
                    JSONArray(file.readText())
                } else {
                    JSONArray()
                }
            arr.put(entry)
            file.writeText(arr.toString(2))
        }
    }

    private fun readManifestArray(context: Context, uri: Uri): JSONArray =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                JSONArray(input.bufferedReader().readText())
            } ?: JSONArray()
        }.getOrElse { JSONArray() }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    private fun networkAllowsBackup(context: Context): Boolean {
        if (!isWifiOnly(context)) return true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isAlreadySynced(context: Context, mediaId: String): Boolean {
        val set = prefs(context).getStringSet(KEY_SYNCED_IDS, emptySet()) ?: emptySet()
        return mediaId in set
    }

    private fun markSynced(context: Context, mediaId: String) {
        val prefs = prefs(context)
        val next = (prefs.getStringSet(KEY_SYNCED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        next.add(mediaId)
        while (next.size > MAX_SYNCED_ID_ENTRIES) {
            next.remove(next.first())
        }
        prefs.edit().putStringSet(KEY_SYNCED_IDS, next).apply()
    }

    private fun sanitizeFileName(raw: String): String =
        raw.replace(Regex("""[\\/:*?"<>|]"""), "_").take(180)
}

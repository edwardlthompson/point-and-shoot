package dev.pointandshoot

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Downloads a GitHub APK, checks package + optional SHA-256, then starts the system installer.
 */
object PnsApkInstaller {
    private const val TAG = "PNS.ApkInstall"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val HTTP_OK = 200
    private const val HTTP_TEMP_REDIRECT = 307
    private const val HTTP_PERM_REDIRECT = 308
    private const val MAX_REDIRECTS = 5
    private const val MIME_APK = "application/vnd.android.package-archive"
    private const val CACHE_DIR = "updates"
    private const val BUFFER_BYTES = 8_192
    private const val PROGRESS_MAX = 100
    private const val HEX_MASK = 0xff
    private const val APK_SLACK_BYTES = 8L * 1024L * 1024L
    private const val DEFAULT_APK_BUDGET_BYTES = 56L * 1024L * 1024L
    private const val MAX_APK_MIB = 150L
    internal const val MAX_APK_BYTES: Long = MAX_APK_MIB * 1024L * 1024L
    internal const val SAFE_APK_NAME: String = "update.apk"
    private const val APK_NAME_MIN = 5
    private const val APK_NAME_MAX = 80
    private const val ZIP_SIG_P: Byte = 0x50
    private const val ZIP_SIG_K: Byte = 0x4B
    private const val ZIP_LOCAL_B2: Byte = 3
    private const val ZIP_LOCAL_B3: Byte = 4
    private val ZIP_LOCAL = byteArrayOf(ZIP_SIG_P, ZIP_SIG_K, ZIP_LOCAL_B2, ZIP_LOCAL_B3)
    private val installLock = AtomicBoolean(false)

    data class Progress(val percent: Int, val receivedBytes: Long = 0L, val totalBytes: Long = 0L)

    data class Request(
        val url: String,
        val sha256Url: String? = null,
        val expectedVersion: String? = null,
        val sizeBytes: Long = 0L,
    )

    sealed class Outcome {
        data object Started : Outcome()

        data object NeedPermission : Outcome()

        data object WrongPackage : Outcome()

        data object WrongVersion : Outcome()

        data object WrongSigner : Outcome()

        data object HashMismatch : Outcome()

        data object Cancelled : Outcome()

        data object NoSpace : Outcome()

        data object Blocked : Outcome()

        data object Network : Outcome()

        data object Busy : Outcome()

        data object Failed : Outcome()
    }

    fun isInstallInFlight(): Boolean = installLock.get()

    fun canRequestInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun isActiveNetworkMetered(context: Context): Boolean {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        return cm.isActiveNetworkMetered
    }

    fun openInstallPermissionSettings(context: Context): Boolean {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("ReturnCount", "LongMethod")
    fun installFromUrl(
        context: Context,
        url: String,
        sha256Url: String? = null,
        expectedVersion: String? = null,
        expectedSizeBytes: Long = 0L,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
    ): Outcome {
        if (!canRequestInstalls(context)) {
            openInstallPermissionSettings(context)
            return Outcome.NeedPermission
        }
        if (!installLock.compareAndSet(false, true)) {
            Log.w(TAG, "installer=busy")
            return Outcome.Busy
        }
        return try {
            installFromUrlLocked(
                context,
                url,
                sha256Url,
                expectedVersion,
                expectedSizeBytes,
                shouldCancel,
                onProgress,
            )
        } finally {
            installLock.set(false)
        }
    }

    @Suppress("ReturnCount", "LongMethod")
    private fun installFromUrlLocked(
        context: Context,
        url: String,
        sha256Url: String?,
        expectedVersion: String?,
        expectedSizeBytes: Long,
        shouldCancel: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): Outcome {
        if (!declaredSizeWithinCap(expectedSizeBytes, 0L)) {
            Log.w(TAG, "installer=over_cap")
            return Outcome.Blocked
        }
        if (!hasRoomForApk(PreviewFreeSpace.availableBytesForCache(context), expectedSizeBytes)) {
            Log.w(TAG, "installer=no_space")
            return Outcome.NoSpace
        }
        val expectedSha =
            if (!sha256Url.isNullOrBlank()) fetchExpectedSha256(context, sha256Url) else null
        val dir = cacheDir(context)
        val cached = cachedApkMatchingSha(dir, expectedSha)
        if (cached != null) {
            val rejected = rejectUnsafeApk(context, cached, expectedVersion)
            if (rejected != null) {
                cached.delete()
                return rejected
            }
            pruneUnmatchedCache(dir, cached)
            onProgress(Progress(PROGRESS_MAX, cached.length(), cached.length()))
            return if (startInstaller(context, cached)) Outcome.Started else Outcome.Failed
        }
        val fetch = downloadApk(context, url, expectedSizeBytes, shouldCancel, onProgress)
        if (fetch.cancelled) return Outcome.Cancelled
        if (fetch.noSpace) return Outcome.NoSpace
        if (fetch.blocked) return Outcome.Blocked
        if (fetch.network) return Outcome.Network
        val apk = fetch.file ?: return Outcome.Network
        if (shouldCancel()) {
            apk.delete()
            return Outcome.Cancelled
        }
        val rejected = rejectUnsafeApk(context, apk, expectedVersion)
        if (rejected != null) {
            apk.delete()
            return rejected
        }
        if (!sha256Url.isNullOrBlank()) {
            val expected = expectedSha ?: fetchExpectedSha256(context, sha256Url)
            val actual = sha256Hex(apk)
            if (expected == null || actual == null || expected != actual) {
                apk.delete()
                Log.w(TAG, "installer=hash_mismatch")
                return Outcome.HashMismatch
            }
        }
        pruneUnmatchedCache(dir, apk)
        return if (startInstaller(context, apk)) Outcome.Started else Outcome.Failed
    }

    private fun rejectUnsafeApk(
        context: Context,
        apk: File,
        expectedVersion: String?,
    ): Outcome? {
        if (!isZipApk(apk) || archivePackageInfo(context, apk) == null) {
            Log.w(TAG, "installer=not_apk")
            return Outcome.Blocked
        }
        if (!expectedPackageMatches(packageNameForApk(context, apk), context.packageName)) {
            Log.w(TAG, "installer=wrong_package")
            return Outcome.WrongPackage
        }
        if (!expectedVersionMatches(versionNameForApk(context, apk), expectedVersion)) {
            Log.w(TAG, "installer=wrong_version")
            return Outcome.WrongVersion
        }
        if (!versionCodeIsNewer(versionCodeForApk(context, apk), PnsAppInfo.versionCode(context))) {
            Log.w(TAG, "installer=not_newer_version_code")
            return Outcome.WrongVersion
        }
        if (!PnsAppSigning.sameSignerAsInstalled(context, apk.absolutePath)) {
            Log.w(TAG, "installer=wrong_signer")
            return Outcome.WrongSigner
        }
        return null
    }

    fun startInstaller(context: Context, apk: File): Boolean {
        if (!apk.isFile || apk.length() <= 0L) return false
        val uri =
            FileProvider.getUriForFile(
                context,
                SharingManager.FILE_PROVIDER_AUTHORITY,
                apk,
            )
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_APK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        return try {
            context.startActivity(intent)
            Log.i(TAG, "installer=started bytes=${apk.length()}")
            true
        } catch (ex: Exception) {
            Log.w(TAG, "installer=failed ${ex.javaClass.simpleName}")
            false
        }
    }

    fun pruneCache(context: Context) {
        cacheDir(context).listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    internal fun availableBytesForCache(context: Context): Long? =
        PreviewFreeSpace.availableBytesForCache(context)

    internal fun pruneUnmatchedCache(dir: File, keep: File?) {
        val keepPath = keep?.canonicalFile?.path
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.canonicalFile.path != keepPath) file.delete()
        }
    }

    internal fun downloadedBytesMatchLength(actual: Long, contentLength: Long): Boolean {
        if (contentLength <= 0L) return true
        return actual == contentLength
    }

    internal fun downloadedBytesMatchDeclared(
        actual: Long,
        contentLength: Long,
        githubSizeBytes: Long,
    ): Boolean {
        if (!downloadedBytesMatchLength(actual, contentLength)) return false
        if (githubSizeBytes <= 0L) return true
        return actual == githubSizeBytes
    }

    internal fun versionCodeIsNewer(apkCode: Long, installedCode: Long): Boolean =
        apkCode > 0L && apkCode > installedCode

    fun peekSha256Short(context: Context, sha256Url: String?): String? {
        if (sha256Url.isNullOrBlank()) return null
        return PnsProductUpdate.formatSha256Short(fetchExpectedSha256(context, sha256Url))
    }

    internal fun isHttpsDownloadUrl(url: String): Boolean =
        url.startsWith("https://") && url.isNotBlank()

    internal fun isAllowedApkDownloadHost(host: String?): Boolean {
        val h = host?.trim()?.lowercase().orEmpty()
        if (h.isEmpty()) return false
        return h == "github.com" || h.endsWith(".githubusercontent.com")
    }

    internal fun requiredDownloadBytes(sizeBytes: Long): Long =
        if (sizeBytes > 0L) sizeBytes + APK_SLACK_BYTES else DEFAULT_APK_BUDGET_BYTES

    internal fun hasRoomForApk(availableBytes: Long?, sizeBytes: Long): Boolean {
        if (availableBytes == null) return true
        return availableBytes >= requiredDownloadBytes(sizeBytes)
    }

    internal fun declaredSizeWithinCap(githubSizeBytes: Long, contentLength: Long): Boolean {
        if (githubSizeBytes > MAX_APK_BYTES) return false
        if (contentLength > MAX_APK_BYTES) return false
        return true
    }

    internal fun safeApkFileName(raw: String?): String {
        val base = raw?.substringAfterLast('/')?.substringBefore('?')?.trim().orEmpty()
        val cleaned = base.filter { ch -> ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-' }
        if (!cleaned.endsWith(".apk", ignoreCase = true)) return SAFE_APK_NAME
        if (cleaned.contains("..") || cleaned.length !in APK_NAME_MIN..APK_NAME_MAX) return SAFE_APK_NAME
        return cleaned
    }

    internal fun destInCacheDir(dir: File, name: String): File? {
        val dest = File(dir, name).canonicalFile
        val root = dir.canonicalFile
        val prefix = root.path + File.separator
        if (dest.path != root.path && !dest.path.startsWith(prefix)) return null
        return dest
    }

    internal fun isZipApk(file: File): Boolean {
        if (!file.isFile || file.length() < ZIP_LOCAL.size.toLong()) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(ZIP_LOCAL.size)
                if (input.read(magic) < ZIP_LOCAL.size) return@use false
                magic[0] == ZIP_LOCAL[0] && magic[1] == ZIP_LOCAL[1]
            }
        }.getOrDefault(false)
    }

    internal fun formatProgress(progress: Progress): String {
        val received = PnsProductUpdate.formatMegabytes(progress.receivedBytes)
        val total = PnsProductUpdate.formatMegabytes(progress.totalBytes)
        return if (received != null && total != null) {
            "${progress.percent}% · $received / $total"
        } else {
            "${progress.percent}%"
        }
    }

    internal fun sha256Matches(actual: String?, expected: String?): Boolean {
        val want = expected?.trim()?.lowercase().orEmpty()
        val got = actual?.trim()?.lowercase().orEmpty()
        return want.isNotEmpty() && got == want
    }

    internal fun cachedApkMatchingSha(dir: File, expectedSha: String?): File? {
        val want = expectedSha?.trim()?.lowercase().orEmpty()
        if (want.isEmpty() || !dir.isDirectory) return null
        return dir.listFiles()?.firstOrNull { file ->
            file.isFile && sha256Matches(sha256Hex(file), want)
        }
    }

    internal fun declaredSizesAgree(contentLength: Long, githubSizeBytes: Long): Boolean {
        if (contentLength <= 0L || githubSizeBytes <= 0L) return true
        return contentLength == githubSizeBytes
    }

    internal fun isRedirectStatus(code: Int): Boolean =
        code == HttpURLConnection.HTTP_MOVED_PERM ||
            code == HttpURLConnection.HTTP_MOVED_TEMP ||
            code == HttpURLConnection.HTTP_SEE_OTHER ||
            code == HTTP_TEMP_REDIRECT ||
            code == HTTP_PERM_REDIRECT

    internal fun resolveRedirectUrl(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        return runCatching { URL(URL(current), location).toString() }.getOrNull()
    }

    internal fun expectedPackageMatches(apkPackage: String?, installedPackage: String): Boolean =
        !apkPackage.isNullOrBlank() && apkPackage == installedPackage

    internal fun expectedVersionMatches(apkVersion: String?, expected: String?): Boolean {
        if (expected.isNullOrBlank()) return true
        return !apkVersion.isNullOrBlank() && apkVersion.trim() == expected.trim()
    }

    internal data class FetchResult(
        val file: File?,
        val cancelled: Boolean,
        val blocked: Boolean = false,
        val noSpace: Boolean = false,
        val network: Boolean = false,
    )

    internal fun downloadApk(
        context: Context,
        url: String,
        expectedSizeBytes: Long = 0L,
        shouldCancel: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {},
    ): FetchResult {
        if (!isHttpsDownloadUrl(url) ||
            !isAllowedApkDownloadHost(runCatching { URL(url).host }.getOrNull())
        ) {
            return FetchResult(null, cancelled = false, blocked = true)
        }
        if (!declaredSizeWithinCap(expectedSizeBytes, 0L)) {
            return FetchResult(null, cancelled = false, blocked = true)
        }
        if (!hasRoomForApk(PreviewFreeSpace.availableBytesForCache(context), expectedSizeBytes)) {
            return FetchResult(null, cancelled = false, noSpace = true)
        }
        val dir = cacheDir(context)
        val dest = destInCacheDir(dir, safeApkFileName(url)) ?: return FetchResult(null, false, blocked = true)
        return try {
            val conn = openAllowedDownload(url, context) ?: return FetchResult(null, false, blocked = true)
            try {
                writeDownload(conn, dest, expectedSizeBytes, shouldCancel, onProgress)
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            dest.delete()
            FetchResult(null, cancelled = shouldCancel(), network = !shouldCancel())
        }
    }

    private fun openAllowedDownload(url: String, context: Context): HttpURLConnection? {
        var current = url
        var last: HttpURLConnection? = null
        repeat(MAX_REDIRECTS) {
            if (!isHttpsDownloadUrl(current) ||
                !isAllowedApkDownloadHost(runCatching { URL(current).host }.getOrNull())
            ) {
                last?.disconnect()
                return null
            }
            val conn = URL(current).openConnection() as HttpURLConnection
            last?.disconnect()
            last = conn
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Point-and-Shoot/${PnsAppInfo.versionName(context)}")
            val code = conn.responseCode
            if (isRedirectStatus(code)) {
                current = resolveRedirectUrl(current, conn.getHeaderField("Location")) ?: run {
                    conn.disconnect()
                    return null
                }
                return@repeat
            }
            return conn
        }
        last?.disconnect()
        return null
    }

    @Suppress("ReturnCount")
    private fun writeDownload(
        conn: HttpURLConnection,
        dest: File,
        expectedSizeBytes: Long,
        shouldCancel: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): FetchResult {
        if (conn.responseCode != HTTP_OK) return FetchResult(null, false, network = true)
        if (!declaredSizeWithinCap(expectedSizeBytes, conn.contentLengthLong)) {
            dest.delete()
            return FetchResult(null, cancelled = false, blocked = true)
        }
        if (!declaredSizesAgree(conn.contentLengthLong, expectedSizeBytes)) {
            dest.delete()
            return FetchResult(null, cancelled = false, blocked = true)
        }
        if (shouldCancel()) {
            dest.delete()
            return FetchResult(null, true)
        }
        val completed = copyWithProgress(conn, dest, shouldCancel, onProgress)
        if (!completed) {
            dest.delete()
            return FetchResult(null, true)
        }
        if (dest.length() <= 0L) {
            dest.delete()
            return FetchResult(null, false)
        }
        if (!downloadedBytesMatchDeclared(dest.length(), conn.contentLengthLong, expectedSizeBytes)) {
            dest.delete()
            return FetchResult(null, cancelled = false, blocked = true)
        }
        if (!isZipApk(dest)) {
            dest.delete()
            return FetchResult(null, cancelled = false, blocked = true)
        }
        onProgress(Progress(PROGRESS_MAX, dest.length(), dest.length()))
        return FetchResult(dest, false)
    }

    private fun copyWithProgress(
        conn: HttpURLConnection,
        dest: File,
        shouldCancel: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): Boolean {
        val input = conn.inputStream
        val out = dest.outputStream()
        return try {
            copyStream(input, out, conn.contentLengthLong, shouldCancel, onProgress)
        } finally {
            input.close()
            out.close()
        }
    }

    private fun copyStream(
        input: InputStream,
        out: OutputStream,
        total: Long,
        shouldCancel: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ): Boolean {
        val buf = ByteArray(BUFFER_BYTES)
        var received = 0L
        while (true) {
            if (shouldCancel()) return false
            val n = input.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            received += n
            onProgress(Progress(progressPercent(received, total), received, total))
        }
        return !shouldCancel()
    }

    internal fun progressPercent(received: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((received * PROGRESS_MAX) / total).toInt().coerceIn(0, PROGRESS_MAX)
    }

    internal fun sha256Hex(file: File): String? =
        runCatching { digestFileSha256(file) }.getOrNull()

    private fun digestFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and HEX_MASK) }
    }

    private fun fetchExpectedSha256(context: Context, url: String): String? {
        val conn = openAllowedDownload(url, context) ?: return null
        return try {
            if (conn.responseCode != HTTP_OK) return null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            PnsProductUpdate.parseSha256Sidecar(text)
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun packageNameForApk(context: Context, apk: File): String? =
        archivePackageInfo(context, apk)?.packageName

    private fun versionNameForApk(context: Context, apk: File): String? =
        archivePackageInfo(context, apk)?.versionName

    private fun versionCodeForApk(context: Context, apk: File): Long {
        val info = archivePackageInfo(context, apk) ?: return 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    private fun archivePackageInfo(context: Context, apk: File) =
        try {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
        } catch (_: Exception) {
            null
        }
}

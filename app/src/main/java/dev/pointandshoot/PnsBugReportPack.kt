@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Redacted support zip: version, journal, last-good session. No serials, no photos. */
object PnsBugReportPack {
    fun write(context: Context): File {
        val dest = File(File(context.cacheDir, "share").apply { mkdirs() }, "pns_bug_pack.zip")
        ZipOutputStream(dest.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("report.txt"))
            zos.write(text(context).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return dest
    }

    fun text(context: Context): String =
        buildString {
            appendLine("Point & Shoot bug pack")
            appendLine("versionName=${PnsAppInfo.versionName(context)}")
            appendLine("versionCode=${PnsAppInfo.versionCode(context)}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("model=${Build.MODEL}")
            appendLine("fingerprintPrefix=${Build.FINGERPRINT.take(24)}")
            appendLine("recipe=${PnsProductPrefs.recipe(context).id}")
            appendLine("geotag=${PnsProductPrefs.geotagMode(context).storageId}")
            appendLine("power=${PnsPowerProfile.load(context).storageId}")
            appendLine("hdmi=${PnsExternalOutput.presentationActive} display=${PnsExternalOutput.lastDisplayName ?: "-"}")
            appendLine("wearBle=${PnsWearBleServer.isAdvertising()}")
            appendLine("journal=${CaptureJournal.latestLine() ?: "-"}")
            appendLine("lastGood=${PnsLastGoodSession.formatHint(PnsLastGoodSession.load(context)) ?: "-"}")
            appendLine("recording=${PnsForegroundCapture.isRecording}")
        }
}

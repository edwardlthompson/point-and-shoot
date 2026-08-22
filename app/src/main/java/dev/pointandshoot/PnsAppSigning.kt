package dev.pointandshoot

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object PnsAppSigning {
    private const val HEX_MASK = 0xff
    fun isDebugOrTestSigned(context: Context): Boolean {
        val signatures =
            try {
                signingBytes(context)
            } catch (_: Exception) {
                return false
            }
        if (signatures.isEmpty()) return false
        val factory = CertificateFactory.getInstance("X.509")
        return signatures.any { raw ->
            val cert =
                factory.generateCertificate(ByteArrayInputStream(raw)) as? X509Certificate
                    ?: return@any false
            isAndroidDebugSubject(cert.subjectX500Principal.name)
        }
    }

    internal fun isAndroidDebugSubject(dn: String): Boolean =
        dn.contains("CN=Android Debug", ignoreCase = true)

    fun sameSignerAsInstalled(context: Context, apkPath: String): Boolean =
        fingerprintsMatch(signingBytes(context), signingBytesForArchive(context, apkPath))

    internal fun fingerprintsMatch(installed: List<ByteArray>, archive: List<ByteArray>): Boolean {
        if (installed.isEmpty() || archive.isEmpty()) return false
        val installedFp = installed.map { fingerprintSha256(it) }.toSet()
        return archive.any { fingerprintSha256(it) in installedFp }
    }

    internal fun fingerprintSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { b -> "%02x".format(b.toInt() and HEX_MASK) }
    }

    private fun signingBytesForArchive(context: Context, apkPath: String): List<ByteArray> {
        return try {
            val pm = context.packageManager
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    PackageManager.GET_SIGNING_CERTIFICATES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_SIGNATURES
                }
            val info = pm.getPackageArchiveInfo(apkPath, flags) ?: return emptyList()
            info.applicationInfo?.sourceDir = apkPath
            info.applicationInfo?.publicSourceDir = apkPath
            signersFrom(info)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun signingBytes(context: Context): List<ByteArray> {
        val pm = context.packageManager
        val pkg = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signersFrom(pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES))
        } else {
            @Suppress("DEPRECATION")
            signersFrom(pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES))
        }
    }

    private fun signersFrom(info: PackageInfo): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { it.toByteArray() }
        }
    }
}

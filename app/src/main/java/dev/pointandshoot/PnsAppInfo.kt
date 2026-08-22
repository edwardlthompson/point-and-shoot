package dev.pointandshoot

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Host-readable app identity for DNG metadata + diagnostics. */
object PnsAppInfo {

    fun versionName(context: Context): String =
        runCatching {
            val pm = context.applicationContext.packageManager
            val pkg = context.applicationContext.packageName
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0).versionName
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "0.0.0"

    fun versionCode(context: Context): Long =
        runCatching {
            val pm = context.applicationContext.packageManager
            val pkg = context.applicationContext.packageName
            val info =
                if (Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)
}

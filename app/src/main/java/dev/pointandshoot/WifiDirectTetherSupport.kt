package dev.pointandshoot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Sprint **15.37** — LAN / Wi‑Fi Direct tether helpers (NSD + runtime permissions). */
object WifiDirectTetherSupport {
    const val NSD_SERVICE_TYPE = "_pns-tether._tcp."
    const val NSD_SERVICE_NAME = "PNS-Tether"

    /** Permissions required before binding `0.0.0.0` and registering NSD. */
    fun requiredPermissions(): Array<String> =
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.toTypedArray()

    fun hasPermissions(context: Context): Boolean =
        requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
}

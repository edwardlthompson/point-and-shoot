@file:Suppress("MagicNumber")

package dev.pointandshoot

import android.content.Context
import android.content.SharedPreferences

/** Device-local product-system prefs (not update cache; publish password stays local). */
object PnsProductPrefs {
    private const val PREFS: String = "pns_product_systems"
    const val PREFIX_IMPORTED_LUT: String = "imported:"

    enum class CaptureRecipe(val id: String, val label: String) {
        None("none", "None"),
        Concert("concert", "Concert / silent"),
        Museum("museum", "Museum / no flash"),
        Airplane("airplane", "Airplane-safe record"),
        Astro("astro", "Astro / star trail"),
        Document("document", "Document scan"),
        ;

        companion object {
            fun fromId(raw: String?): CaptureRecipe =
                entries.firstOrNull { it.id == raw } ?: None
        }
    }

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun geotagMode(context: Context): PnsGeotagMode =
        PnsGeotagMode.fromStorage(prefs(context).getString("geotag_mode", null), false)

    fun setGeotagMode(context: Context, mode: PnsGeotagMode) {
        prefs(context).edit().putString("geotag_mode", mode.storageId).apply()
    }

    fun stripGpsOnShare(context: Context): Boolean = prefs(context).getBoolean("strip_gps_share", true)

    fun setStripGpsOnShare(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("strip_gps_share", on).apply()
    }

    fun wearRemoteEnabled(context: Context): Boolean = prefs(context).getBoolean("wear_remote", false)

    fun setWearRemoteEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("wear_remote", on).apply()
    }

    fun hdmiOutEnabled(context: Context): Boolean = prefs(context).getBoolean("hdmi_out", true)

    fun setHdmiOutEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("hdmi_out", on).apply()
    }

    fun mjpegWebcamEnabled(context: Context): Boolean = prefs(context).getBoolean("mjpeg_webcam", false)

    fun setMjpegWebcamEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("mjpeg_webcam", on).apply()
    }

    fun usbWebcamMode(context: Context): Boolean = prefs(context).getBoolean("usb_webcam_mode", false)

    fun setUsbWebcamMode(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("usb_webcam_mode", on).apply()
    }

    fun cleanHdmiFeed(context: Context): Boolean = prefs(context).getBoolean("hdmi_clean", true)

    fun recipe(context: Context): CaptureRecipe =
        CaptureRecipe.fromId(prefs(context).getString("recipe", CaptureRecipe.None.id))

    fun setRecipe(context: Context, recipe: CaptureRecipe) {
        prefs(context).edit().putString("recipe", recipe.id).apply()
    }

    fun rampEnabled(context: Context): Boolean = prefs(context).getBoolean("ramp_on", false)

    fun rampIsoStart(context: Context): Int = prefs(context).getInt("ramp_iso_s", 100)

    fun rampIsoEnd(context: Context): Int = prefs(context).getInt("ramp_iso_e", 800)

    fun setRamp(context: Context, on: Boolean, start: Int, end: Int) {
        prefs(context).edit()
            .putBoolean("ramp_on", on)
            .putInt("ramp_iso_s", start.coerceIn(50, 25600))
            .putInt("ramp_iso_e", end.coerceIn(50, 25600))
            .apply()
    }

    fun focusStackCount(context: Context): Int = prefs(context).getInt("focus_stack_n", 5).coerceIn(2, 12)

    fun setFocusStackCount(context: Context, n: Int) {
        prefs(context).edit().putInt("focus_stack_n", n.coerceIn(2, 12)).apply()
    }

    fun tripArmed(context: Context): Boolean = prefs(context).getBoolean("trip_armed", false)

    fun setTripArmed(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("trip_armed", on).apply()
    }

    fun bulbArmed(context: Context): Boolean = prefs(context).getBoolean("bulb_armed", false)

    fun setBulbArmed(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("bulb_armed", on).apply()
    }

    fun selectedImportedLut(context: Context): String? =
        prefs(context).getString("imported_lut", null)?.takeIf { it.isNotBlank() }

    fun setSelectedImportedLut(context: Context, name: String?) {
        prefs(context).edit().putString("imported_lut", name.orEmpty()).apply()
    }

    fun lookForLens(context: Context, cameraId: String): String? =
        prefs(context).getString("look_$cameraId", null)?.takeIf { it.isNotBlank() }

    fun setLookForLens(context: Context, cameraId: String, lutName: String?) {
        prefs(context).edit().putString("look_$cameraId", lutName.orEmpty()).apply()
    }

    fun syncthingLayout(context: Context): Boolean = prefs(context).getBoolean("syncthing_dcim", false)

    fun setSyncthingLayout(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("syncthing_dcim", on).apply()
    }

    fun writeXmpSidecar(context: Context): Boolean = prefs(context).getBoolean("dng_xmp", true)

    fun airplaneSafe(context: Context): Boolean =
        recipe(context) == CaptureRecipe.Airplane || prefs(context).getBoolean("airplane_safe", false)

    fun setAirplaneSafe(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("airplane_safe", on).apply()
    }

    fun publishUrl(context: Context): String = prefs(context).getString("publish_url", "").orEmpty()

    fun setPublishUrl(context: Context, url: String) {
        prefs(context).edit().putString("publish_url", url.trim()).apply()
    }

    fun publishKind(context: Context): String = prefs(context).getString("publish_kind", "webdav").orEmpty()

    fun setPublishKind(context: Context, kind: String) {
        prefs(context).edit().putString("publish_kind", kind).apply()
    }

    fun peopleAlbumsOptIn(context: Context): Boolean = prefs(context).getBoolean("people_albums", false)

    fun safRecordTree(context: Context): String? =
        prefs(context).getString("saf_record_tree", null)?.takeIf { it.isNotBlank() }

    fun setSafRecordTree(context: Context, uri: String?) {
        prefs(context).edit().putString("saf_record_tree", uri.orEmpty()).apply()
    }

    fun jpegCreditEnabled(context: Context): Boolean = prefs(context).getBoolean("jpeg_credit_on", false)

    fun setJpegCreditEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("jpeg_credit_on", on).apply()
    }
}

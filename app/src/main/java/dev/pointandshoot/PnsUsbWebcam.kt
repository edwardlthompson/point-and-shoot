@file:Suppress("MagicNumber", "LoopWithTooManyJumpStatements")

package dev.pointandshoot

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.net.NetworkInterface

/**
 * USB webcam product mode.
 *
 * Windows class-compliant path: OEM **USB Webcam** (UVC gadget / DeviceAsWebcam).
 * Host loads inbox **usbvideo.sys** — no third-party driver. A third-party app cannot
 * own `/dev/video` UVC itself (system-signed DeviceAsWebcam only).
 *
 * P&S look-over-USB path: keep preview + MJPEG and carry it on the cable via USB
 * tethering (RNDIS/NCM) and/or `adb forward tcp:28770`.
 */
object PnsUsbWebcam {
    const val TAG: String = "PNS.UsbWebcam"
    const val ACTION_USB_STATE: String = "android.hardware.usb.action.USB_STATE"
    const val ACTION_CONNECTED_DEVICES: String = "android.settings.CONNECTED_DEVICE_SETTINGS"
    const val EXTRA_CONNECTED: String = "connected"
    const val EXTRA_UVC: String = "uvc"
    const val EXTRA_RNDIS: String = "rndis"
    const val EXTRA_NCM: String = "ncm"
    const val EXTRA_ADB: String = "adb"
    const val WINDOWS_INBOX_DRIVER: String = "usbvideo.sys"
    const val WINDOWS_DEVICE_NAME: String = "USB Video Device"
    /** Lineage / AOSP UsbService — same function the Settings “Webcam” radio sets. */
    const val SVC_SET_FUNCTIONS_UVC: String = "svc usb setFunctions uvc"
    const val SVC_LOCK_FUNCTIONS_UVC: String = "svc usb setScreenUnlockedFunctions uvc"
    const val SETTINGS_CONNECTED_DEVICES_ACTIVITY: String =
        "com.android.settings.Settings\$ConnectedDeviceDashboardActivity"

    data class UsbLink(
        val connected: Boolean = false,
        val configured: Boolean = false,
        val uvc: Boolean = false,
        val rndis: Boolean = false,
        val ncm: Boolean = false,
        val adb: Boolean = false,
    ) {
        val usbData: Boolean get() = connected && (configured || adb || rndis || ncm || uvc)
        val tether: Boolean get() = rndis || ncm
    }

    @Volatile
    var active: Boolean = false
        private set

    @Volatile
    var link: UsbLink = UsbLink()
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastHostHint: String = ""
        private set

    private val main: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var host: WeakReference<Activity>? = null
    private var registered = false

    private val usbReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != ACTION_USB_STATE) return
                link = parseUsbState(intent)
                lastHostHint = buildHostHint(context)
                Log.i(
                    TAG,
                    "usb connected=${link.connected} cfg=${link.configured} uvc=${link.uvc} " +
                        "rndis=${link.rndis} ncm=${link.ncm} adb=${link.adb} hint=$lastHostHint",
                )
                if (active && link.uvc) {
                    lastError = null
                }
            }
        }

    fun parseUsbState(intent: Intent): UsbLink =
        UsbLink(
            connected = intent.getBooleanExtra(EXTRA_CONNECTED, false),
            configured = intent.getBooleanExtra("configured", false),
            uvc = intent.getBooleanExtra(EXTRA_UVC, false),
            rndis = intent.getBooleanExtra(EXTRA_RNDIS, false),
            ncm = intent.getBooleanExtra(EXTRA_NCM, false),
            adb = intent.getBooleanExtra(EXTRA_ADB, false),
        )

    fun parseUsbState(
        connected: Boolean,
        configured: Boolean,
        uvc: Boolean,
        rndis: Boolean,
        ncm: Boolean,
        adb: Boolean,
    ): UsbLink = UsbLink(connected, configured, uvc, rndis, ncm, adb)

    fun statusLine(): String {
        if (!active) return "Webcam off"
        val path =
            when {
                link.uvc -> "UVC · Windows $WINDOWS_DEVICE_NAME ($WINDOWS_INBOX_DRIVER)"
                link.tether -> "USB tether · ${lastHostHint.ifBlank { "waiting for PC NIC" }}"
                link.adb -> "ADB USB · adb forward tcp:${PnsExternalOutput.MJPEG_PORT}"
                link.connected -> "USB cable · switch PC to Webcam or File transfer"
                else -> "Plug USB-C into the PC"
            }
        val err = lastError?.let { " · $it" } ?: ""
        return "Webcam $path$err"
    }

    fun start(activity: Activity) {
        host = WeakReference(activity)
        active = true
        PnsProductPrefs.setMjpegWebcamEnabled(activity, true)
        PnsProductPrefs.setUsbWebcamMode(activity, true)
        PnsMjpegStreamServer.start(activity.applicationContext)
        register(activity)
        keepScreen(activity, true)
        refreshStickyUsb(activity)
        lastHostHint = buildHostHint(activity)
        tryEnableUvcFunction(activity)
        tryStartUsbTether(activity)
        // Do not open USB Settings here — that backgrounds preview and trips
        // ERROR_CAMERA_DEVICE (4). Shutter / banner still call openHostUsbSettings.
        Log.i(TAG, "started ${statusLine()}")
    }

    fun stop() {
        val act = host?.get()
        if (act != null) {
            keepScreen(act, false)
            unregister(act)
            PnsProductPrefs.setUsbWebcamMode(act, false)
        }
        active = false
        lastError = null
        Log.i(TAG, "stopped")
    }

    fun usbPickerIntents(): List<Intent> =
        listOf(
            Intent().setClassName("com.android.settings", SETTINGS_CONNECTED_DEVICES_ACTIVITY),
            Intent("android.settings.USB_SETTINGS"),
            Intent(ACTION_CONNECTED_DEVICES),
            Intent("com.android.settings.USB_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )

    /**
     * Camera / Zoom / Teams need the OEM UVC gadget, not P&S HTTP.
     * If Root settings already granted SU, flip `svc usb setFunctions uvc` (no new prompt).
     * Otherwise open Lineage **Connected devices → USB** so the user can tap **Webcam**.
     */
    fun openHostUsbSettings(context: Context) {
        Thread({
            val privileged = tryEnableUvcPrivileged(context)
            main.post {
                refreshStickyUsb(context)
                if (privileged || link.uvc) {
                    lastError = null
                    Log.i(TAG, "OEM UVC requested privileged=$privileged uvc=${link.uvc}")
                    return@post
                }
                launchUsbPicker(context)
            }
        }, "pns-uvc-enable").start()
    }

    private fun launchUsbPicker(context: Context) {
        for (intent in usbPickerIntents()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(intent) }.isSuccess
            if (ok) {
                Log.i(TAG, "opened USB picker ${intent.component ?: intent.action}")
                return
            }
        }
        lastError = "open USB settings failed"
        Log.w(TAG, lastError!!)
    }

    fun usbIpv4Addresses(): List<String> {
        val out = mutableListOf<String>()
        val ifaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
        for (nic in ifaces) {
            if (!nic.isUp || nic.isLoopback) continue
            val name = nic.name.lowercase()
            val usbish =
                name.contains("rndis") ||
                    name.contains("ncm") ||
                    name.contains("usb") ||
                    name.contains("teth") ||
                    name.startsWith("en")
            if (!usbish && !name.startsWith("wlan") && !name.startsWith("ap")) continue
            for (addr in nic.inetAddresses) {
                val host = addr.hostAddress ?: continue
                if (host.contains(':')) continue
                if (host.startsWith("127.")) continue
                if (usbish || host.startsWith("192.168.42.") || host.startsWith("192.168.137.")) {
                    out += host
                }
            }
        }
        return out.distinct()
    }

    private fun buildHostHint(context: Context): String {
        val ips = usbIpv4Addresses()
        val usb = ips.firstOrNull()
        val port = PnsExternalOutput.MJPEG_PORT
        return when {
            link.uvc -> "$WINDOWS_DEVICE_NAME · driver $WINDOWS_INBOX_DRIVER"
            usb != null -> "http://$usb:$port/mjpeg"
            link.adb -> "http://127.0.0.1:$port/mjpeg after adb forward"
            else -> {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val props: LinkProperties? = cm?.getLinkProperties(cm.activeNetwork)
                val lan =
                    props?.linkAddresses
                        ?.mapNotNull { it.address.hostAddress }
                        ?.firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") }
                if (lan != null) "Wi-Fi fallback http://$lan:$port/mjpeg" else ""
            }
        }
    }

    private fun register(context: Context) {
        if (registered) return
        val filter = IntentFilter(ACTION_USB_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            context.applicationContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.applicationContext.registerReceiver(usbReceiver, filter)
        }
        registered = true
    }

    private fun unregister(context: Context) {
        if (!registered) return
        runCatching { context.applicationContext.unregisterReceiver(usbReceiver) }
        registered = false
    }

    private fun refreshStickyUsb(context: Context) {
        val sticky =
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(null, IntentFilter(ACTION_USB_STATE), Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(null, IntentFilter(ACTION_USB_STATE))
            }
        if (sticky != null) {
            link = parseUsbState(sticky)
        }
    }

    /**
     * Best-effort. [UsbManager.setCurrentFunctions] is @SystemApi + MANAGE_USB;
     * OEMs throw SecurityException. We still try so a privileged/debug build can flip UVC.
     */
    private fun tryEnableUvcFunction(context: Context) {
        val usb = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val functionUvc = 1L shl 7
        val functionAdb = 1L shl 0
        val ok =
            runCatching {
                val method = usb.javaClass.methods.firstOrNull { it.name == "setCurrentFunctions" && it.parameterTypes.size == 1 }
                    ?: return
                method.invoke(usb, functionUvc or functionAdb)
                true
            }.getOrDefault(false)
        if (ok) {
            Log.i(TAG, "setCurrentFunctions uvc+adb requested")
        } else {
            Log.i(TAG, "UVC function is OEM/Settings-gated (needs Webcam radio or SU)")
        }
    }

    /**
     * Only when the user already tapped Grant Su. Never forks `su` from [start].
     */
    private fun tryEnableUvcPrivileged(context: Context): Boolean {
        if (!RootCapabilityStore.loadOrUnknown(context).grantsPrivileged) return false
        val cmd = "$SVC_SET_FUNCTIONS_UVC; $SVC_LOCK_FUNCTIONS_UVC"
        val outcome =
            runCatching {
                val process = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                val done = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)
                if (!done) {
                    runCatching { process.destroyForcibly() }
                    return@runCatching false
                }
                process.exitValue() == 0
            }.getOrDefault(false)
        Log.i(TAG, "privileged $cmd ok=$outcome")
        return outcome
    }

    private fun tryStartUsbTether(context: Context) {
        if (link.uvc) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val started =
            runCatching {
                val start =
                    cm.javaClass.methods.firstOrNull { method ->
                        method.name == "startTethering" && method.parameterTypes.size >= 3
                    } ?: return@runCatching false
                val callbackClass = start.parameterTypes.getOrNull(2)
                val cb =
                    if (callbackClass != null && callbackClass.isInterface) {
                        java.lang.reflect.Proxy.newProxyInstance(
                            callbackClass.classLoader,
                            arrayOf(callbackClass),
                        ) { _, _, _ -> null }
                    } else {
                        null
                    }
                when (start.parameterTypes.size) {
                    3 -> start.invoke(cm, 1, true, cb)
                    4 -> start.invoke(cm, 1, true, cb, main)
                    else -> return@runCatching false
                }
                true
            }.getOrDefault(false)
        if (started) {
            Log.i(TAG, "USB tethering requested")
        } else {
            Log.i(TAG, "USB tethering needs the system USB / hotspot panel")
        }
    }

    private fun keepScreen(activity: Activity, on: Boolean) {
        main.post {
            if (activity.isFinishing) return@post
            if (on) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

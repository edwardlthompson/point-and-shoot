package dev.pointandshoot

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Sprint **15.37** — mDNS registration for LAN tether (`_pns-tether._tcp`).
 */
class TetherNsdRegistrar {
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile
    private var registered = false

    fun isRegistered(): Boolean = registered

    fun register(context: Context, port: Int) {
        unregister()
        val appCtx = context.applicationContext
        val nsd = appCtx.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: run {
            Log.w(TAG, "NsdManager unavailable")
            return
        }
        val info =
            NsdServiceInfo().apply {
                serviceName = WifiDirectTetherSupport.NSD_SERVICE_NAME
                serviceType = WifiDirectTetherSupport.NSD_SERVICE_TYPE
                setPort(port)
            }
        val listener =
            object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    registered = true
                    Log.i(TAG, "nsdRegistered name=${info.serviceName} type=${info.serviceType} port=${info.port}")
                }

                override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    registered = false
                    Log.w(TAG, "nsdRegistrationFailed error=$errorCode")
                }

                override fun onServiceUnregistered(info: NsdServiceInfo) {
                    registered = false
                    Log.i(TAG, "nsdUnregistered")
                }

                override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "nsdUnregistrationFailed error=$errorCode")
                }
            }
        nsdManager = nsd
        registrationListener = listener
        runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { e ->
            Log.w(TAG, "registerService failed: ${e.message}")
            nsdManager = null
            registrationListener = null
        }
    }

    fun unregister() {
        val nsd = nsdManager
        val listener = registrationListener
        nsdManager = null
        registrationListener = null
        registered = false
        if (nsd != null && listener != null) {
            runCatching { nsd.unregisterService(listener) }
        }
    }

    companion object {
        const val TAG = "PNS.TetherNsd"
    }
}

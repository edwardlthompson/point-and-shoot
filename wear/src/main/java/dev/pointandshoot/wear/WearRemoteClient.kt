@file:Suppress("MagicNumber")

package dev.pointandshoot.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class WearRemoteClient(private val context: Context) {
    data class Status(
        val connected: Boolean,
        val via: String,
        val recording: Boolean,
        val ready: Boolean,
        val phoneTimerSec: Int,
        val detail: String,
    )

    private val main = Handler(Looper.getMainLooper())
    private val gattRef = AtomicReference<BluetoothGatt?>(null)
    private val writeRef = AtomicReference<BluetoothGattCharacteristic?>(null)
    private val scanning = AtomicBoolean(false)
    private var scanCallback: ScanCallback? = null

    @Volatile
    var lastHost: String = "192.168.1.1"

    @Volatile
    var lastStatus: Status = Status(false, "none", false, false, 0, "Not connected")
        private set

    fun send(action: WearRemoteProtocol.Action, timerSec: Int = 0): Status {
        val ble = sendBle(action, timerSec)
        if (ble.connected) return ble
        return sendHttp(action, timerSec)
    }

    fun pollStatus(): Status {
        val http = runCatching { httpGetStatus() }.getOrNull()
        if (http != null) {
            lastStatus = http
            return http
        }
        val gatt = gattRef.get()
        val write = writeRef.get()
        lastStatus =
            if (gatt != null && write != null) {
                Status(true, "ble", lastStatus.recording, true, lastStatus.phoneTimerSec, "BLE connected")
            } else if (gatt != null) {
                Status(false, "ble", false, false, 0, "BLE discovering")
            } else {
                Status(false, "none", false, false, 0, "No phone")
            }
        if (!lastStatus.connected) {
            main.post { startBleScan() }
        }
        return lastStatus
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        if (!scanning.compareAndSet(false, true)) return
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: run {
            scanning.set(false)
            return
        }
        val adapter = mgr.adapter ?: run {
            scanning.set(false)
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            scanning.set(false)
            return
        }
        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(WearRemoteProtocol.BLE_SERVICE_UUID))
                .build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val cb =
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    stopScanLocked()
                    connectGatt(result)
                }

                override fun onScanFailed(errorCode: Int) {
                    scanning.set(false)
                    lastStatus = Status(false, "ble", false, false, 0, "BLE scan $errorCode")
                    main.postDelayed({ startBleScan() }, 4_000)
                }
            }
        scanCallback = cb
        runCatching { scanner.startScan(listOf(filter), settings, cb) }
            .onFailure {
                scanning.set(false)
                return
            }
        main.postDelayed(
            {
                if (scanning.get() && writeRef.get() == null) {
                    stopScanLocked()
                    main.postDelayed({ startBleScan() }, 2_500)
                }
            },
            8_000,
        )
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScanLocked()
        runCatching { gattRef.getAndSet(null)?.close() }
        writeRef.set(null)
    }

    @SuppressLint("MissingPermission")
    private fun stopScanLocked() {
        scanning.set(false)
        val cb = scanCallback ?: return
        scanCallback = null
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        runCatching { mgr.adapter?.bluetoothLeScanner?.stopScan(cb) }
    }

    private fun sendHttp(action: WearRemoteProtocol.Action, timerSec: Int): Status {
        val q = if (action == WearRemoteProtocol.Action.Timer) "&sec=$timerSec" else ""
        val url =
            URL(
                "http://${lastHost}:${WearRemoteProtocol.DEFAULT_PORT}" +
                    "${WearRemoteProtocol.COMMAND_PATH}?action=${action.wire}$q",
            )
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 2_500
            conn.readTimeout = 2_500
            conn.doInput = true
            val code = conn.responseCode
            conn.disconnect()
            val ok = code in 200..299
            Status(
                connected = ok,
                via = "http",
                recording = lastStatus.recording,
                ready = ok,
                phoneTimerSec = if (action == WearRemoteProtocol.Action.Timer && ok) timerSec else 0,
                detail = if (ok) "HTTP ${action.wire}" else "HTTP $code",
            ).also { lastStatus = it }
        } catch (e: Exception) {
            Status(false, "http", false, false, 0, e.message ?: "HTTP failed").also { lastStatus = it }
        }
    }

    private fun httpGetStatus(): Status? {
        val url = URL("http://${lastHost}:${WearRemoteProtocol.DEFAULT_PORT}${WearRemoteProtocol.STATUS_PATH}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 1_500
        conn.readTimeout = 1_500
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val recording = body.contains("\"recording\":true")
        val ready = body.contains("\"ready\":true")
        val timer = Regex("\"timer\":(\\d+)").find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return Status(ready || body.contains("\"ok\":true"), "http", recording, ready, timer, "LAN $lastHost")
    }

    @SuppressLint("MissingPermission")
    private fun sendBle(action: WearRemoteProtocol.Action, timerSec: Int): Status {
        val gatt = gattRef.get()
        val write = writeRef.get()
        if (gatt == null || write == null) {
            return Status(false, "ble", false, false, 0, "BLE not ready")
        }
        val payload =
            if (action == WearRemoteProtocol.Action.Timer) {
                byteArrayOf(action.ble, timerSec.toByte())
            } else {
                byteArrayOf(action.ble)
            }
        write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        write.value = payload
        val ok = gatt.writeCharacteristic(write)
        return Status(
            connected = ok,
            via = "ble",
            recording = lastStatus.recording,
            ready = true,
            phoneTimerSec = if (action == WearRemoteProtocol.Action.Timer && ok) timerSec else lastStatus.phoneTimerSec,
            detail = if (ok) "BLE ${action.wire}" else "BLE write failed",
        ).also { lastStatus = it }
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt(result: ScanResult) {
        result.device.connectGatt(
            context,
            true,
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gattRef.set(gatt)
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gattRef.set(null)
                        writeRef.set(null)
                        lastStatus = Status(false, "ble", false, false, 0, "BLE dropped")
                        runCatching { gatt.close() }
                        main.postDelayed({ startBleScan() }, 1_200)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val svc = gatt.getService(UUID.fromString(WearRemoteProtocol.BLE_SERVICE_UUID))
                    val write = svc?.getCharacteristic(UUID.fromString(WearRemoteProtocol.BLE_WRITE_UUID))
                    writeRef.set(write)
                    lastStatus =
                        Status(
                            write != null,
                            "ble",
                            false,
                            write != null,
                            0,
                            if (write != null) "BLE ready" else "No write char",
                        )
                    if (write == null) {
                        runCatching { gatt.disconnect() }
                    }
                }
            },
        )
    }
}

@file:Suppress("MagicNumber", "ReturnCount")

package dev.pointandshoot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

/**
 * Phone-side BLE GATT server for the Wear remote. FOSS — no Play Services.
 * Starts only when [PnsProductPrefs.wearRemoteEnabled] is true.
 */
object PnsWearBleServer {
    const val TAG: String = "PNS.WearBle"

    @Volatile
    private var gatt: BluetoothGattServer? = null

    @Volatile
    private var advertiser: BluetoothLeAdvertiser? = null

    @Volatile
    private var advertising: Boolean = false

    fun isAdvertising(): Boolean = advertising && gatt != null

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val app = context.applicationContext
        if (!PnsProductPrefs.wearRemoteEnabled(app)) {
            stop()
            return
        }
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "no BLE feature")
            return
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val connect = app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
            val advertise = app.checkSelfPermission(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            if (connect != PackageManager.PERMISSION_GRANTED || advertise != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "missing BLE advertise/connect permission")
                return
            }
        }
        if (gatt != null && advertising) return
        val mgr = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val adapter = mgr.adapter ?: return
        if (!adapter.isEnabled) {
            Log.w(TAG, "bluetooth off")
            return
        }
        stopUnlocked()
        val server =
            mgr.openGattServer(
                app,
                object : BluetoothGattServerCallback() {
                    override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                        Log.i(TAG, "conn device=${device.address} state=$newState status=$status")
                    }

                    override fun onCharacteristicWriteRequest(
                        device: BluetoothDevice,
                        requestId: Int,
                        characteristic: BluetoothGattCharacteristic,
                        preparedWrite: Boolean,
                        responseNeeded: Boolean,
                        offset: Int,
                        value: ByteArray?,
                    ) {
                        val cmd = PnsRemoteProtocol.parseBle(value ?: ByteArray(0))
                        if (cmd != null) {
                            PnsRemoteCommandBus.post(cmd.copy(source = "ble:${device.address}"))
                        }
                        if (responseNeeded) {
                            runCatching {
                                gatt?.sendResponse(
                                    device,
                                    requestId,
                                    BluetoothGatt.GATT_SUCCESS,
                                    0,
                                    value,
                                )
                            }
                        }
                    }
                },
            ) ?: return
        val service =
            BluetoothGattService(
                UUID.fromString(PnsRemoteProtocol.BLE_SERVICE_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )
        val write =
            BluetoothGattCharacteristic(
                UUID.fromString(PnsRemoteProtocol.BLE_WRITE_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        val status =
            BluetoothGattCharacteristic(
                UUID.fromString(PnsRemoteProtocol.BLE_STATUS_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ,
            )
        service.addCharacteristic(write)
        service.addCharacteristic(status)
        if (!server.addService(service)) {
            Log.w(TAG, "addService failed")
            runCatching { server.close() }
            return
        }
        gatt = server
        val adv = adapter.bluetoothLeAdvertiser
        advertiser = adv
        if (adv == null) {
            Log.w(TAG, "no LE advertiser; GATT still up for connected watches")
            advertising = false
            return
        }
        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
        val data =
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid.fromString(PnsRemoteProtocol.BLE_SERVICE_UUID))
                .build()
        runCatching { adapter.name = PnsRemoteProtocol.BLE_ADVERTISE_NAME }
        adv.startAdvertising(
            settings,
            data,
            object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    advertising = true
                    Log.i(TAG, "advertising name=${PnsRemoteProtocol.BLE_ADVERTISE_NAME}")
                }

                override fun onStartFailure(errorCode: Int) {
                    advertising = false
                    Log.w(TAG, "advertise fail code=$errorCode")
                }
            },
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        stopUnlocked()
    }

    @SuppressLint("MissingPermission")
    private fun stopUnlocked() {
        advertising = false
        runCatching { advertiser?.stopAdvertising(object : AdvertiseCallback() {}) }
        advertiser = null
        runCatching { gatt?.close() }
        gatt = null
    }
}

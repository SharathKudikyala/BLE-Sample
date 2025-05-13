package com.app.blesample.ble

import GattClientManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.app.blesample.LogLevel

class BLECentralManager(private val context: Context, private val callback: BLECallback) :
    ScanCallbackReceiver {

    var scanResultListener: ((BluetoothDevice, String) -> Unit)? = null
    private var gattClientManager: GattClientManager =
        GattClientManager(context, callback, this)


    fun startAsCentral() {
        gattClientManager.startScanning()
    }

    fun stopAsCentral() {
        gattClientManager?.disconnect()
    }

    fun connectToDevice(device: BluetoothDevice, deviceUniqueID: String) {
        gattClientManager?.connect(device, deviceUniqueID)
    }

    fun sendMessage(message: String) {
        gattClientManager?.sendMessage(message)
    }

    companion object {
        private const val TAG = "BLECentralManager"
    }

    interface BLECallback {
        fun onConnected()
        fun onMessageReceived(message: String)
        fun onLog(logLevel: LogLevel, message: String)
    }

    override fun onDeviceFound(device: BluetoothDevice, deviceUniqueID: String) {
        scanResultListener?.invoke(device, deviceUniqueID)
    }
}

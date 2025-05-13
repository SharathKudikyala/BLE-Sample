package com.app.blesample.ble

import android.bluetooth.BluetoothDevice

interface ScanCallbackReceiver {
    fun onDeviceFound(device: BluetoothDevice, uniqueDeviceID: String)
}
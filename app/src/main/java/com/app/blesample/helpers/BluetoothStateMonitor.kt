package com.app.blesample.helpers

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.app.blesample.LogLevel
import com.app.blesample.ble.BLECentralManager

class BluetoothStateMonitor(
    private val context: Context,
    private val onBluetoothTurnedOn: () -> Unit,
    private val onBluetoothTurnedOff: () -> Unit = {},
    private val callback: BLECentralManager.BLECallback
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                    BluetoothAdapter.STATE_ON -> {
                        callback.onLog(LogLevel.INFO, "Bluetooth turned on.")
                        onBluetoothTurnedOn()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        callback.onLog(LogLevel.INFO, "Bluetooth turned off.")
                        onBluetoothTurnedOff()
                    }
                }
            }
        }
    }

    fun start() {
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    fun stop() {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            callback.onLog(LogLevel.ERROR, "Could not stop BluetoothStateMonitor - ${e.message}")
        }
    }

}

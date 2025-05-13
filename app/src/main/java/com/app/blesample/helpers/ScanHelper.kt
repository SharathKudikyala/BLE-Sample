package com.app.blesample.helpers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.app.blesample.LogLevel
import com.app.blesample.ble.SERVICE_DATA_UUID
import java.util.UUID

class ScanHelper(
    context: Context,
    private val serviceUuid: UUID,
    private val onDeviceFound: (BluetoothDevice, String) -> Unit,
    private val onLog: (LogLevel, String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val handler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null

    fun startScan(durationMs: Long = 5000L) {
        if (scanCallback != null) stopScan()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) {
                val serviceData = result.scanRecord?.getServiceData(ParcelUuid(SERVICE_DATA_UUID))
                val uniqueId = serviceData?.toString(Charsets.UTF_8)

                if(uniqueId.isNullOrEmpty())
                    return

                Log.d(TAG, "Found device with uniqueId = $uniqueId")

                val deviceName =
                    result.device.name ?: result.scanRecord?.deviceName ?: result.device.address
                onLog(LogLevel.DEBUG, "Found device: $deviceName ($uniqueId)")
                onDeviceFound(result.device, uniqueId)
            }

            override fun onScanFailed(errorCode: Int) {
                onLog(LogLevel.ERROR, "Scan failed: $errorCode")
            }
        }

        onLog(LogLevel.DEBUG, "Scan started")
        bluetoothAdapter.bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        handler.postDelayed(
            { stopScan() }, durationMs
        )
    }

    fun stopScan() {
        scanCallback?.let {
            bluetoothAdapter.bluetoothLeScanner?.stopScan(it)
            scanCallback = null
            onLog(LogLevel.DEBUG, "Scan stopped")
        }
    }

    companion object {
        private const val TAG = "ScanHelper"
    }
}

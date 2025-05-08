package com.app.blesample

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import android.util.Log

class BLECentralManager(private val context: Context, private val callback: BLECallback) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    var scanResultListener: ((BluetoothDevice) -> Unit)? = null
    private var connectionStartTime: Long = 0L

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            device?.name?.let {
                Log.d(TAG, "onScanResult: Device = $device")
                scanResultListener?.invoke(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            callback.onLog(LogLevel.ERROR, "Scan failed with error: $errorCode")
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(BLEPeripheralManager.SERVICE_UUID.toString()))
            .build()

        callback.onLog(LogLevel.INFO, "Scanning Devices...")
        scanner.startScan(listOf(scanFilter), scanSettings, scanCallback)

        Handler(Looper.getMainLooper()).postDelayed({
            stopScan()
        }, 20000)
    }

    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner.stopScan(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        callback.onLog(LogLevel.INFO, "Connecting to device = ${device.name}")
        connectionStartTime = System.nanoTime()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val timeTaken = (System.nanoTime() - connectionStartTime) / 1_000_000
                callback.onLog(LogLevel.INFO, "Connected to GATT server in $timeTaken ms")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                callback.onLog(LogLevel.INFO, "Disconnected from GATT server.")
                gatt.close()
                bluetoothGatt = null
                connectionStartTime = 0L
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(BLEPeripheralManager.SERVICE_UUID)
                val writeChar = service?.getCharacteristic(BLEPeripheralManager.CHARACTERISTIC_UUID)
                val notifyChar =
                    service?.getCharacteristic(BLEPeripheralManager.NOTIFY_CHARACTERISTIC_UUID)

                if (writeChar == null) {
                    callback.onLog(LogLevel.ERROR, "Write Characteristic not found")
                } else {
                    callback.onLog(LogLevel.DEBUG, "Write Characteristic found")
                }

                if (notifyChar == null) {
                    callback.onLog(LogLevel.ERROR, "Notify Characteristic not found")
                    return
                }

                val props = notifyChar.properties
                val isNotifiable = props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                callback.onLog(LogLevel.DEBUG, "Notify Char - isNotifiable = $isNotifiable")
                if (isNotifiable) {
                    val notifySuccess = gatt.setCharacteristicNotification(notifyChar, true)
                    callback.onLog(
                        if (notifySuccess) LogLevel.DEBUG else LogLevel.WARN,
                        "setCharacteristicNotification() = $notifySuccess"
                    )

                    val descriptor = notifyChar.getDescriptor(BLEPeripheralManager.DESCRIPTOR_UUID)
                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val descriptorWriteSuccess = gatt.writeDescriptor(descriptor)
                    callback.onLog(
                        if (descriptorWriteSuccess) LogLevel.DEBUG else LogLevel.WARN,
                        "Descriptor write success: $descriptorWriteSuccess"
                    )

                    callback.onConnected()
                } else {
                    callback.onLog(LogLevel.ERROR, "Notify Characteristic is not notifiable")
                }
            } else {
                callback.onLog(LogLevel.WARN, "onServicesDiscovered failed with status $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            callback.onLog(LogLevel.DEBUG, "Descriptor written, status: $status")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val message = characteristic.value.toString(Charsets.UTF_8)
            callback.onMessageReceived(message)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            callback.onLog(LogLevel.DEBUG, "Message sent to Server, status = $status")
        }
    }

    fun sendMessage(message: String) {
        val service = bluetoothGatt?.getService(BLEPeripheralManager.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BLEPeripheralManager.CHARACTERISTIC_UUID)
        if (characteristic == null) {
            callback.onLog(LogLevel.ERROR, "Characteristic not found for writing")
            return
        }

        characteristic.value = message.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        callback.onLog(LogLevel.INFO, "Sending message: $message")

        Handler(Looper.getMainLooper()).postDelayed({
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            callback.onLog(LogLevel.DEBUG, "Write initiated = $success")
        }, 500)
    }

    companion object {
        private const val TAG = "BLECentralManager"
    }

    interface BLECallback {
        fun onConnected()
        fun onMessageReceived(message: String)
        fun onLog(logLevel: LogLevel, message: String)
    }
}
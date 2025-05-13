package com.app.blesample.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import com.app.blesample.LogLevel
import com.app.blesample.UniqueIdProvider
import com.app.blesample.ble.BLECentralManager.BLECallback
import java.util.concurrent.CopyOnWriteArraySet

class BLEPeripheralManager(private val context: Context, private val callback: BLECallback) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var connectedDevices: MutableSet<BluetoothDevice> = CopyOnWriteArraySet()

    fun startAdvertising() {
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            callback.onLog(LogLevel.ERROR, "BLE Advertising not supported on this device")
            return
        }


        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val uniqueId = UniqueIdProvider.getOrCreateId(context)
        callback.onLog(LogLevel.DEBUG, "Device UniqueID = $uniqueId")
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .addServiceData(ParcelUuid(SERVICE_DATA_UUID), uniqueId.toByteArray(Charsets.UTF_8))
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        startGattServer()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            callback.onLog(LogLevel.INFO, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            callback.onLog(LogLevel.ERROR, "Advertising failed: $errorCode")
        }
    }

    private fun startGattServer() {
        callback.onLog(LogLevel.DEBUG, "Start Gatt Server")
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        // Characteristic for Central to write to Peripheral
        val writeCharacteristic = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )

        // Characteristic for Peripheral to notify Central
        val notifyCharacteristic = BluetoothGattCharacteristic(
            NOTIFY_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(
            DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        notifyCharacteristic.addDescriptor(cccd)

        service.addCharacteristic(writeCharacteristic)
        service.addCharacteristic(notifyCharacteristic)

        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            device?.let {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectedDevices.add(device)
                    callback.onLog(LogLevel.INFO, "Server Connected to ${device.address}")
                    callback.onLog(
                        LogLevel.INFO,
                        "Connected Clients Count = ${connectedDevices.size}"
                    )
                } else {
                    connectedDevices.remove(device)
                    callback.onLog(LogLevel.INFO, "Server Disconnected from ${device.address}")
                    callback.onLog(
                        LogLevel.INFO,
                        "Connected Clients Count = ${connectedDevices.size}"
                    )
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (characteristic?.uuid == CHARACTERISTIC_UUID) {
                val value = "Server Ready".toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
                callback.onLog(LogLevel.DEBUG, "Read request from ${device?.address}")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            val message = value?.toString(Charsets.UTF_8)
            callback.onLog(LogLevel.INFO, "Message from ${device?.address}: $message")

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }

            // Send ACK as notification via notifyCharacteristic (ffe2)
            /*val notifyChar = gattServer?.getService(SERVICE_UUID)
                ?.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
            val ackMessage = "ACK: $message"
            notifyChar?.value = ackMessage.toByteArray(Charsets.UTF_8)
            if (device != null && notifyChar != null) {
                gattServer?.notifyCharacteristicChanged(device, notifyChar, false)
            }*/
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (descriptor?.uuid == DESCRIPTOR_UUID) {
                descriptor.value =
                    if (value contentEquals BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                callback.onLog(
                    LogLevel.DEBUG,
                    "Notification subscription change from ${device?.address}"
                )
            }
        }
    }

    fun stopAdvertising() {
        callback.onLog(LogLevel.DEBUG, "Stopping BLE Advertising...")

        try {
            advertiser?.let { adv ->
                adv.stopAdvertising(advertiseCallback)
                callback.onLog(LogLevel.INFO, "BLE Advertising stopped successfully.")
            }
        } catch (e: Exception) {
            callback.onLog(LogLevel.ERROR, "Error stopping advertising: ${e.message}")
        } finally {
            advertiser = null
        }

        try {
            gattServer?.close()
            callback.onLog(LogLevel.DEBUG, "GATT Server closed.")
        } catch (e: Exception) {
            callback.onLog(LogLevel.ERROR, "Error closing GATT Server: ${e.message}")
        } finally {
            gattServer = null // Nullify to prevent reuse
        }
    }

    fun sendMessageToAllCentrals(message: String) {
        val service = gattServer?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) ?: return

        characteristic.value = message.toByteArray(Charsets.UTF_8)
        for (device in connectedDevices) {
            gattServer?.notifyCharacteristicChanged(device, characteristic, false)
        }

        callback.onLog(LogLevel.INFO, "Server Sent message to client -> $message")
    }

    // Optional cleanup on disconnect
    private fun clearNotifyCharacteristic() {
        val service = gattServer?.getService(SERVICE_UUID) ?: return
        val characteristic = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID) ?: return
        characteristic.value = ByteArray(0) // Reset the value to prevent stale data
    }

    // Helper function for future service reconfiguration
    private fun resetGattServer() {
        gattServer?.close()
        gattServer = null
        startGattServer() // Rebuild the GATT server
    }

    companion object {
        private const val TAG = "BLEPeripheralManager"
    }
}

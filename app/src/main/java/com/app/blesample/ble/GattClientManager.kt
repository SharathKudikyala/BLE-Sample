import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.app.blesample.LogLevel
import com.app.blesample.ble.BLECentralManager
import com.app.blesample.ble.CHARACTERISTIC_UUID
import com.app.blesample.ble.DESCRIPTOR_UUID
import com.app.blesample.ble.NOTIFY_CHARACTERISTIC_UUID
import com.app.blesample.ble.SERVICE_UUID
import com.app.blesample.ble.ScanCallbackReceiver
import com.app.blesample.helpers.ReconnectLoopManager
import com.app.blesample.helpers.ScanHelper
import org.json.JSONObject

class GattClientManager(
    private val context: Context,
    private val callback: BLECentralManager.BLECallback,
    scanResultListener: ScanCallbackReceiver
) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null
    private val prefs = context.getSharedPreferences("recent_ble_devices", Context.MODE_PRIVATE)

    private val reconnectManager = ReconnectLoopManager(25000L) { tryReconnect() }

    private lateinit var scanHelper: ScanHelper

    private var connectionStartTime: Long = 0L

    private lateinit var currentUniqueDeviceID:String

    init {
        scanHelper = ScanHelper(
            context = context,
            serviceUuid = SERVICE_UUID,
            onDeviceFound = { device, deviceUniqueID ->
                getMacForUniqueId(deviceUniqueID)?.let {
                    callback.onLog(
                        LogLevel.INFO,
                        "Auto-connecting to recent device: ${device.address}"
                    )
                    scanHelper.stopScan()
                    connect(device, deviceUniqueID)
                } ?: run {
                    scanResultListener.onDeviceFound(device, deviceUniqueID)
                }
            },
            onLog = { level, message -> callback.onLog(level, message) }
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            callback.onLog(
                LogLevel.DEBUG,
                "onConnectionStateChange() - status=$status, newState=$newState"
            )

            if (status != BluetoothGatt.GATT_SUCCESS) {
                callback.onLog(LogLevel.ERROR, "Connection failed with status = $status")
                try {
                    gatt.close()
                } catch (e: Exception) {
                    callback.onLog(LogLevel.ERROR, "Error closing GATT: ${e.message}")
                }
                bluetoothGatt = null
                reconnectManager?.start()
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    bluetoothGatt = gatt // Ensure reference to current GATT
                    reconnectManager.stop()
                    val timeTaken = (System.nanoTime() - connectionStartTime) / 1_000_000
                    callback.onLog(LogLevel.INFO, "Connected in $timeTaken ms")
                    saveRecentDevice(currentUniqueDeviceID, gatt.device.address)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    callback.onLog(LogLevel.INFO, "Disconnected from GATT server.")
                    try {
                        gatt.close()
                    } catch (e: Exception) {
                        callback.onLog(LogLevel.ERROR, "Error closing GATT: ${e.message}")
                    }
                    bluetoothGatt = null
                    connectionStartTime = 0L
                    reconnectManager?.start()
                    callback.onLog(LogLevel.WARN, "Attempting reconnection...")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                callback.onLog(LogLevel.WARN, "Service discovery failed: $status")
                return
            }

            val service = gatt.getService(SERVICE_UUID) ?: run {
                callback.onLog(LogLevel.ERROR, "Service not found")
                return
            }

            val writeChar = service.getCharacteristic(CHARACTERISTIC_UUID)
            if (writeChar == null) {
                callback.onLog(LogLevel.ERROR, "Write Characteristic not found")
                return
            } else {
                callback.onLog(LogLevel.DEBUG, "Write Characteristic found")
            }

            val notifyChar = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)
            if (notifyChar == null) {
                callback.onLog(LogLevel.ERROR, "Notify Characteristic not found")
                return
            }

            val isNotifiable =
                notifyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            callback.onLog(LogLevel.DEBUG, "Notify Char - isNotifiable = $isNotifiable")

            if (isNotifiable) {
                val notifySuccess = gatt.setCharacteristicNotification(notifyChar, true)
                callback.onLog(
                    if (notifySuccess) LogLevel.DEBUG else LogLevel.WARN,
                    "setCharacteristicNotification() = $notifySuccess"
                )

                val descriptor = notifyChar.getDescriptor(DESCRIPTOR_UUID)
                descriptor?.let {
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    val descriptorWriteSuccess = gatt.writeDescriptor(it)
                    callback.onLog(
                        if (descriptorWriteSuccess) LogLevel.DEBUG else LogLevel.WARN,
                        "Descriptor write success: $descriptorWriteSuccess"
                    )
                } ?: callback.onLog(LogLevel.ERROR, "Descriptor not found on notify characteristic")
            } else {
                callback.onLog(LogLevel.ERROR, "Notify Characteristic is not notifiable")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            callback.onLog(LogLevel.DEBUG, "Descriptor written, status: $status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                callback.onConnected()
            } else {
                callback.onLog(LogLevel.ERROR, "Descriptor write failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            val messageBytes = characteristic.value
            if (messageBytes.isNotEmpty()) {
                val message = messageBytes.toString(Charsets.UTF_8)
                callback.onMessageReceived(message)
            } else {
                callback.onLog(LogLevel.WARN, "Received empty characteristic value")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            callback.onLog(LogLevel.DEBUG, "Message sent to Server, status = $status")
        }
    }

    fun startScanning() {
        reconnectManager.start()
    }

    fun connect(device: BluetoothDevice, deviceUniqueID: String) {
        if (!bluetoothAdapter.isEnabled) return

        if (isConnected()) return

        currentUniqueDeviceID = deviceUniqueID

        callback.onLog(LogLevel.INFO, "Trying to connect to ${device.address}")
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
        connectionStartTime = System.nanoTime()
    }

    private fun tryReconnect() {
        if (!bluetoothAdapter.isEnabled) return

        if (isConnected()) return

        /*val recent = getRecentDevices()
        if (recent.isEmpty()) {
            callback.onLog(LogLevel.DEBUG, "No recent devices to reconnect")
            return
        }*/
        callback.onLog(LogLevel.INFO, "Attempting reconnect via scan...")
        scanHelper.startScan(5000L)
    }

    private fun isConnected(): Boolean {
        return bluetoothGatt?.device?.let { device ->
            bluetoothManager.getConnectionState(
                device,
                BluetoothProfile.GATT
            ) == BluetoothProfile.STATE_CONNECTED
        } ?: false
    }

    /*private fun saveRecentDevice(address: String) {
        val devices = getRecentDevices().toMutableList()
        devices.remove(address)
        devices.add(0, address)
        prefs.edit().putStringSet(RECENT_DEVICE_MAP_KEY, devices.take(5).toSet()).apply()
    }*/

    /*private fun getRecentDevices(): List<String> {
        return prefs.getStringSet(RECENT_DEVICE_MAP_KEY, emptySet())?.toList() ?: emptyList()
    }*/

    private fun saveRecentDevice(uniqueId: String, address: String) {
        val map = getRecentDeviceMap()

        // insert or update if already exists
        map[uniqueId] = address

        // Keep only the 5 most recent entries
        val trimmed = map.entries.toList().takeLast(5).associate { it.toPair() }

        prefs.edit().putString(RECENT_DEVICE_MAP_KEY, JSONObject(trimmed).toString()).apply()
    }

    private fun getRecentDeviceMap(): MutableMap<String, String> {
        val json = prefs.getString(RECENT_DEVICE_MAP_KEY, "{}") ?: "{}"
        return JSONObject(json).let { obj ->
            obj.keys().asSequence().associateWith { obj.getString(it) }.toMutableMap()
        }
    }

    fun getRecentDevices(): List<String> {
        // Return uniqueIds in order (most recent last)
        return getRecentDeviceMap().keys.toList()
    }

    fun getMacForUniqueId(uniqueId: String): String? {
        return getRecentDeviceMap()[uniqueId]
    }

    fun clearRecentDevices() {
        prefs.edit().remove(RECENT_DEVICE_MAP_KEY).apply()
    }

    fun sendMessage(message: String) {
        val service = bluetoothGatt?.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)

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

    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        reconnectManager.stop()
        scanHelper.stopScan()
    }

    companion object {
        private const val TAG = "GattClientManager"
        private const val RECENT_DEVICE_MAP_KEY = "recent_devices_map"
    }
}

package com.app.blesample

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.blesample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), BLECentralManager.BLECallback {

    private lateinit var binding: ActivityMainBinding

    private lateinit var bleCentralManager: BLECentralManager
    private lateinit var blePeripheralManager: BLEPeripheralManager
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()
    private val logFilter = LogFilter.QA

    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private var deviceType: DeviceType = DeviceType.NONE

    enum class DeviceType {
        CENTRAL,
        PERIPHERAL,
        NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bleCentralManager = BLECentralManager(this, this)
        blePeripheralManager = BLEPeripheralManager(this, this)

        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        binding.listViewBluetoothDevices.adapter = deviceAdapter

        checkPermissionsAndStates()

        bleCentralManager.scanResultListener = { device ->
            runOnUiThread {
                if (!foundDevices.contains(device)) {
                    foundDevices.add(device)
                    val name = device.name ?: "Unnamed (${device.address})"
                    deviceNames.add(name)
                    deviceAdapter.notifyDataSetChanged()
                }
            }
        }

        binding.tvLog.movementMethod = ScrollingMovementMethod()
        binding.listViewBluetoothDevices.setOnItemClickListener { _, _, position, _ ->
            val device = foundDevices[position]
            bleCentralManager.connectToDevice(device)
        }

        binding.btnCentralMode.setOnClickListener {
            if (hasPermissions()) {
                updateLog(LogLevel.DEBUG, "--Your device is now Client--")
                deviceType = DeviceType.CENTRAL
                //updateUi(deviceType)
                blePeripheralManager.stopAdvertising()
                bleCentralManager.startScan()
            } else {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        }

        binding.btnPeripheralMode.setOnClickListener {
            if (hasPermissions()) {
                updateLog(LogLevel.DEBUG, "--Your device is now Server--")
                deviceType = DeviceType.PERIPHERAL
                //updateUi(deviceType)
                //clearDeviceList()
                bleCentralManager.stopScan()
                blePeripheralManager.startAdvertising()
            } else {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        }

        binding.ibSendMessage.setOnClickListener {
            val message = binding.etMessage.text.toString()
            binding.etMessage.text.clear()

            when (deviceType) {
                DeviceType.CENTRAL -> {
                    bleCentralManager.sendMessage(message)
                }

                DeviceType.PERIPHERAL -> {
                    blePeripheralManager.sendMessageToAllCentrals(message)
                }

                DeviceType.NONE -> {
                    updateLog(LogLevel.WARN, "Choose Client or Server")
                }
            }

            hideKeyboard(this, binding.etMessage)
        }
        updateLog(LogLevel.INFO, "App Version: $version")
    }

    private fun clearDeviceList() {
        deviceNames.clear()
        deviceAdapter.notifyDataSetChanged()
    }

    private fun updateUi(deviceType: DeviceType) {
        when (deviceType) {
            DeviceType.CENTRAL, DeviceType.NONE -> binding.listViewBluetoothDevices.visibility =
                View.VISIBLE

            DeviceType.PERIPHERAL -> binding.listViewBluetoothDevices.visibility = View.GONE
        }
    }

    private fun checkPermissionsAndStates() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        } else {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter?.isEnabled == false) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                checkLocationEnabled()
            }
        }
    }

    private fun checkLocationEnabled() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isLocationEnabled) {
            startActivityForResult(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_LOCATION
            )
        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onConnected() {
        runOnUiThread {
            updateLog(LogLevel.DEBUG, "Sending message 'Hello BLE Device!'")
            bleCentralManager.sendMessage("Hello BLE Device!")
        }
    }

    override fun onMessageReceived(message: String) {
        runOnUiThread {
            updateLog(LogLevel.INFO, "Received: $message")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                checkPermissionsAndStates()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    checkLocationEnabled()
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled.", Toast.LENGTH_SHORT).show()
                }
            }

            REQUEST_LOCATION -> {
                checkLocationEnabled()
            }
        }
    }

    override fun onLog(logLevel: LogLevel, message: String) {
        updateLog(logLevel, message)
    }

    private fun updateLog(level: LogLevel, message: String) {
        val isLogAllowed = when (logFilter) {
            LogFilter.DEV -> true
            LogFilter.QA -> level == LogLevel.INFO || level == LogLevel.WARN || level == LogLevel.ERROR
        }

        if (!isLogAllowed)
            return

        runOnUiThread {
            /*val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            binding.tvLog.append("\n[$timestamp] $message")*/
            binding.tvLog.append("\n[${level.tag}] $message")
            binding.scrollView.post {
                binding.scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun hideKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_ENABLE_BT = 2
        private const val REQUEST_LOCATION = 3
    }
}

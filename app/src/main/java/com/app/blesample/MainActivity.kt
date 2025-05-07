package com.app.blesample

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.app.blesample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), BLECentralManager.BLECallback {

    private lateinit var binding: ActivityMainBinding

    private lateinit var bleCentralManager: BLECentralManager
    private lateinit var blePeripheralManager: BLEPeripheralManager
    private lateinit var deviceAdapter: ArrayAdapter<String>
    private val foundDevices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()

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
            updateLog("Connecting to Device : ${device.name}")
            bleCentralManager.connectToDevice(device)  // you'll expose this function from BLEManager
        }

        binding.btnCentralMode.setOnClickListener {
            if (hasPermissions()) {
                deviceType = DeviceType.CENTRAL
                blePeripheralManager.stopAdvertising()
                bleCentralManager.startScan()
            } else {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            }
        }

        binding.btnPeripheralMode.setOnClickListener {
            if (hasPermissions()) {
                deviceType = DeviceType.PERIPHERAL
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
                    blePeripheralManager.sendMessageToCentral(message)
                }

                DeviceType.NONE -> {
                    Toast.makeText(this, "Choose Central or Peripheral", Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onConnected() {
        runOnUiThread {
            Toast.makeText(this, "Connected to BLE device", Toast.LENGTH_SHORT).show()
            updateLog("I: Sending message 'Hello BLE Device!'")
            bleCentralManager.sendMessage("Hello BLE Device!")
        }
    }

    override fun onMessageReceived(message: String) {
        runOnUiThread {
            updateLog("Received: $message")
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
                bleCentralManager.startScan()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onLog(message: String) {
        updateLog(message)
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            binding.tvLog.append("\n$message")
            binding.scrollView.post {
                binding.scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun generateRandomMessage(): String {
        val messages = listOf(
            "hi",
            "hello",
            "how are you?",
            "great",
            "Keep doing"
        )
        return messages.random()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }
}

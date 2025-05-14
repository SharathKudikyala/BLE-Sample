# Android ↔ BLE Peer Communication

**Version:** 1.2  
**Date:** 09 May 2025  
**Prepared By:** Kudikyala Sai Sharath

## Overview

This Android BLE application facilitates reliable peer-to-peer communication using Bluetooth Low Energy (BLE). The app supports both Central (Client) and Peripheral (Server) roles, allowing role selection at runtime.

## Prerequisites

Ensure the following permissions are declared in your AndroidManifest:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

> **Note:** `ACCESS_FINE_LOCATION` is required for BLE scanning on Android 11+.

## Role Selection and Features

### Central (Client)
- Scans for BLE servers using UUID filters
- Lists nearby servers and allows manual connection
- Sends messages via write characteristic
- Receives notifications from the server

### Peripheral (Server)
- Advertises BLE services with UUID and a unique ID
- Accepts multiple client connections
- Sends notifications to connected clients

## UUID & Characteristics

| Name                 | UUID                                     | Direction             |
|----------------------|-------------------------------------------|------------------------|
| Service UUID         | `0000ffe0-0000-1000-8000-00805f9b34fb`     | —                      |
| Write Characteristic | `0000ffe1-0000-1000-8000-00805f9b34fb`     | Central → Peripheral   |
| Notify Characteristic| `0000ffe2-0000-1000-8000-00805f9b34fb`     | Peripheral → Central   |
| CCCD Descriptor      | `00002902-0000-1000-8000-00805f9b34fb`     | Enables Notify         |
| ServiceData UUID     | `0000abcd-0000-1000-8000-00805f9b34fb`     | Used for Reconnect     |

## BLE APIs Used

Common Android BLE APIs utilized:
- `BluetoothLeScanner.startScan()`
- `connectGatt(...)`
- `BluetoothGatt.discoverServices()`
- `getService(UUID)`
- `getCharacteristic(UUID)`
- `setCharacteristicNotification(...)`
- `writeDescriptor(...)`
- `writeCharacteristic(...)`
- `onCharacteristicChanged(...)`

## Scan Filter (Client-Side)

To filter scan results:

```kotlin
ScanFilter.Builder()
    .setServiceUuid(ParcelUuid.fromString(BLEPeripheralManager.SERVICE_UUID.toString()))
    .build()
```

## Seamless Reconnection Strategy

### Problem
BLE MAC addresses are randomized in modern Android, breaking reconnect logic that depends on static addresses.

### Solution
Use a **custom `uniqueId`** in BLE `advertiseData` (`serviceData` field) instead of MAC address.

### Implementation

#### Server
- Generate `uniqueId` on first launch and store it in `SharedPreferences`.
- Advertise the `uniqueId` in the serviceData field.

```kotlin
AdvertiseData.Builder()
    .addServiceData(ParcelUuid(SERVICE_UUID), uniqueId.toByteArray(Charsets.UTF_8))
    .build()
```

#### Client
- Extract `uniqueId` from `ScanRecord` during scan.
- Map `uniqueId → MAC` for future reconnections.
- Auto-connect when a known `uniqueId` reappears.

```kotlin
val serviceData = scanRecord.getServiceData(ParcelUuid(SERVICE_DATA_UUID))
val deviceUniqueId = serviceData?.decodeToString()
```

### Reconnect Loop
- Scan for 5s → Wait 25s → Repeat
- Stop scan upon match and connect

## Real-World Scanning Strategies

| Mode         | Usage                       | Power Use |
|--------------|-----------------------------|-----------|
| Low Power    | Background scanning          | Low       |
| Balanced     | Foreground / retry logic     | Medium    |
| Low Latency  | Active/manual scan           | High      |

> **Best Practice:** Use LOW_LATENCY in foreground and stop scanning after match.

## Usage Instructions

1. Launch the app and grant permissions
2. Choose a role:
   - **Client**: Starts scanning and lists servers
   - **Server**: Starts advertising with `uniqueId`
3. Tap a device to connect
4. Begin message exchange

## Observations

- Use `setIncludeDeviceName(false)` to avoid BLE packet size errors.
- MAC address is volatile; always use `uniqueId` for device tracking.

## Results

- Successful Android ↔ Windows message exchange
- Seamless reconnections using `uniqueId`
- Reliable bidirectional communication
- Fully automated reconnect support

## Key Classes / Files

| File                    | Description                              |
|-------------------------|------------------------------------------|
| `ScanHelper.kt`         | BLE scan logic and `uniqueId` parsing    |
| `GattClientManager.kt`  | Connection state and mapping handler     |
| `ReconnectLoopManager.kt` | Handles background scanning loop        |
| `BLECentralManager.kt`  | UI-facing central role logic             |
| `BLEPeripheralManager.kt`| Manages advertising and GATT server     |
| `SharedPreferences`     | Stores `uniqueId → MAC` mappings         |

## References

- [Bluetooth Low Energy | Android Developers](https://developer.android.com/guide/topics/connectivity/bluetooth-le)
- [BLE Sample Code on GitHub](https://github.com/android/connectivity-samples/tree/main/BluetoothLeGatt)
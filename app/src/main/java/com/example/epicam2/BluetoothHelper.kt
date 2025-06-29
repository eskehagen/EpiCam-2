package com.example.epicam2

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import java.util.*

class BluetoothHelper(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null
    private var ledCharacteristic: BluetoothGattCharacteristic? = null

    private val serviceUUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    private val ledUUID = UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    /// BLE Scan Callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            result.device?.let { device ->
                if (device.name == "ESP32") {
                    Log.d("BluetoothHelper", "ESP32 found, connecting...")
                    stopBleScan()

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.w("BluetoothHelper", "Missing BLUETOOTH_CONNECT permission")
                        return
                    }

                    try {
                        device.connectGatt(context, false, gattCallback)
                    } catch (e: SecurityException) {
                        Log.e("BluetoothHelper", "Connect failed: ${e.message}")
                    }
                }
            }
        }
    }

    /// Gatt Callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BluetoothHelper", "Missing BLUETOOTH_CONNECT permission for connection state change event")
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BluetoothHelper", "Connected to ESP32")
                bluetoothGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BluetoothHelper", "Disconnected from ESP32")
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(serviceUUID)
                ledCharacteristic = service?.getCharacteristic(ledUUID)
                Log.d("BluetoothHelper", "Service discovered, LED characteristic ready: ${ledCharacteristic != null}")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.d("BluetoothHelper", "Characteristic write status: $status")
        }
    }

    /// Connect BLE
    fun connectBle() {
        if (!isScanning && bluetoothAdapter?.isEnabled == true) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BluetoothHelper", "Missing BLUETOOTH_SCAN permission")
                return
            }

            try {
                Log.d("BluetoothHelper", "Starting BLE scan...")
                bleScanner?.startScan(scanCallback)
                isScanning = true

                Log.e("BluetoothHelper", "BLE scan success!")
                // Stop scan after timeout
                handler.postDelayed({ stopBleScan() }, 10000)
            }
            catch (e: SecurityException) {
                Log.e("BluetoothHelper", "BLE scan failed: ${e.message}")
            }
        }
    }

    /// Stop BLE Scan
    private fun stopBleScan() {
        if (isScanning) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w("BluetoothHelper", "Missing BLUETOOTH_SCAN permission for stopScan")
                return
            }

            try {
                bleScanner?.stopScan(scanCallback)
                Log.d("BluetoothHelper", "BLE Scan stopped")
            } catch (e: SecurityException) {
                Log.e("BluetoothHelper", "Stop BLE scan failed: ${e.message}")
            }
            isScanning = false
        }
    }

    /// Disconnect BLE
    fun disconnectBle() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BluetoothHelper", "Missing BLUETOOTH_CONNECT permission for disconnect")
            return
        }

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        ledCharacteristic = null
        Log.d("BluetoothHelper", "Disconnected manually")
    }

    /// Send lamp command to toggle light
    fun sendLampCommand(isOn: Boolean) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w("BluetoothHelper", "Missing BLUETOOTH_CONNECT permission to write characteristic command")
            return
        }

        val value = if (isOn) byteArrayOf(0x01) else byteArrayOf(0x00)
        ledCharacteristic?.let { characteristic ->
            characteristic.value = value
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            Log.d("BluetoothHelper", "Sending lamp ${if (isOn) "ON" else "OFF"}: $success")
        } ?: Log.w("BluetoothHelper", "LED characteristic not available")
    }
}

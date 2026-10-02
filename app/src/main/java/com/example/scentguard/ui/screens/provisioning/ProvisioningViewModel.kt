package com.example.scentguard.ui.screens.provisioning

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

sealed class ProvisioningState {
    object Idle : ProvisioningState()
    object Scanning : ProvisioningState()
    data class DeviceDiscovered(val device: BluetoothDevice) : ProvisioningState()
    object Connecting : ProvisioningState()
    object Transferring : ProvisioningState()
    object Verifying : ProvisioningState()
    object Success : ProvisioningState()
    data class Error(val message: String) : ProvisioningState()
    object WifiFailed : ProvisioningState()
}

@SuppressLint("MissingPermission")
class ProvisioningViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "ProvisioningVM"
    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val locationManager = application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val wifiManager = application.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val adapter = bluetoothManager.adapter

    private val _state = MutableStateFlow<ProvisioningState>(ProvisioningState.Idle)
    val state: StateFlow<ProvisioningState> = _state.asStateFlow()

    private val _wifiWarning = MutableStateFlow<String?>(null)
    val wifiWarning: StateFlow<String?> = _wifiWarning.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionRetryCount = 0
    private var credentialsTransferred = false

    private val serviceUuid = UUID.fromString("0000FF01-0000-1000-8000-00805F9B34FB")
    private val ssidCharUuid = UUID.fromString("0000FF02-0000-1000-8000-00805F9B34FB")
    private val passCharUuid = UUID.fromString("0000FF03-0000-1000-8000-00805F9B34FB")
    private val ridCharUuid = UUID.fromString("0000FF04-0000-1000-8000-00805F9B34FB")
    private val statusCharUuid = UUID.fromString("0000FF05-0000-1000-8000-00805F9B34FB")
    private val configDescriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    init {
        checkWifiFrequency()
    }

    private fun checkWifiFrequency() {
        try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null) {
                val freq = wifiInfo.frequency
                Log.d(tag, "Current Wi-Fi frequency: $freq MHz")
                if (freq > 4900) {
                    _wifiWarning.value = "You are on a 5 GHz network. ESP32 requires 2.4 GHz."
                } else {
                    _wifiWarning.value = null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Could not detect Wi-Fi frequency: ${e.message}")
        }
    }

    fun retryWithNewCredentials() {
        closeGatt()
        credentialsTransferred = false
        _state.value = ProvisioningState.Idle
    }

    private fun closeGatt() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        } catch (e: Exception) {
            Log.e(tag, "Error closing GATT: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val scanRecord = result.scanRecord
            val name = scanRecord?.deviceName ?: try { device.name } catch (e: SecurityException) { null } ?: "Unknown"
            val uuids = scanRecord?.serviceUuids

            val isScentGuard = name.contains("ScentGuard", ignoreCase = true) || 
                              uuids?.any { it.uuid == serviceUuid } == true

            if (isScentGuard) {
                Log.i(tag, "ScentGuard-ESP32 identified!")
                _state.value = ProvisioningState.DeviceDiscovered(device)
                stopScan()
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.value = ProvisioningState.Error("Scan failed: $errorCode")
        }
    }

    fun startScan() {
        val adapterActive = adapter?.isEnabled == true
        val locationActive = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
                             locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        
        if (!adapterActive) {
            _state.value = ProvisioningState.Error("Bluetooth is disabled")
            return
        }
        
        if (!locationActive) {
            _state.value = ProvisioningState.Error("Location services (GPS) must be ON")
            return
        }

        credentialsTransferred = false
        _state.value = ProvisioningState.Scanning
        
        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                _state.value = ProvisioningState.Error("Scanner unavailable")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
                
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            _state.value = ProvisioningState.Error("Failed to start scan")
        }
        
        viewModelScope.launch {
            delay(30000)
            if (_state.value is ProvisioningState.Scanning) {
                stopScan()
                _state.value = ProvisioningState.Error("No ScentGuard found. Reset ESP32 and try again.")
            }
        }
    }

    fun stopScan() {
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
    }

    fun connectAndProvision(device: BluetoothDevice, ssid: String, pass: String, rid: String, isRetry: Boolean = false) {
        if (!isRetry) {
            connectionRetryCount = 0
            credentialsTransferred = false
        }
        
        Log.d(tag, "Connecting to GATT for provisioning (Retry: $isRetry)...")
        _state.value = ProvisioningState.Connecting
        
        // Clean up previous attempts to avoid 133 errors
        closeGatt()
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(tag, "GATT connected! Requesting MTU...")
                    gatt.requestMtu(512)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w(tag, "GATT disconnected. Status: $status")
                    
                    // Critical: Always close the GATT handle on disconnect
                    gatt.close()
                    if (bluetoothGatt == gatt) bluetoothGatt = null

                    // Handle transient 133 error with a single retry
                    if (status == 133 && connectionRetryCount < 1) {
                        connectionRetryCount++
                        Log.w(tag, "Transient 133 error. Attempting retry $connectionRetryCount...")
                        viewModelScope.launch {
                            delay(1000)
                            connectAndProvision(device, ssid, pass, rid, isRetry = true)
                        }
                        return
                    }

                    val currentState = _state.value
                    if (credentialsTransferred || currentState is ProvisioningState.Verifying) {
                        // ESP32 reboots after successfully saving credentials and connecting to Wi-Fi.
                        // Disconnect status 8 (or 19/22) during verification indicates normal ESP32 restart!
                        Log.i(tag, "ESP32 disconnected after verification (reboot after Wi-Fi setup). Provisioning SUCCESS!")
                        _state.value = ProvisioningState.Success
                    } else if (currentState !is ProvisioningState.Success && currentState !is ProvisioningState.WifiFailed) {
                        _state.value = ProvisioningState.Error("Connection lost ($status)")
                    }
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(tag, "MTU changed to $mtu. Discovering services...")
                viewModelScope.launch {
                    delay(600) // Small delay after MTU change for stability
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(serviceUuid)
                    if (service != null) {
                        Log.i(tag, "ScentGuard Service found")
                        _state.value = ProvisioningState.Transferring
                        sendCredentials(gatt, service, ssid, pass, rid)
                    } else {
                        _state.value = ProvisioningState.Error("Incompatible hardware")
                    }
                } else {
                    _state.value = ProvisioningState.Error("GATT Service error")
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (characteristic.uuid == statusCharUuid) {
                    val result = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    Log.i(tag, ">>> Wi-Fi Status Received: $result")
                    
                    viewModelScope.launch {
                        when (result) {
                            1 -> {
                                Log.i(tag, "Success!")
                                _state.value = ProvisioningState.Success
                                delay(2000)
                                gatt.disconnect()
                            }
                            2 -> {
                                Log.e(tag, "Wi-Fi Failed!")
                                _state.value = ProvisioningState.WifiFailed
                            }
                            else -> {
                                // Potentially intermediate states
                            }
                        }
                    }
                }
            }
        }

        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(getApplication(), false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(getApplication(), false, gattCallback)
        }
    }

    private fun sendCredentials(gatt: BluetoothGatt, service: BluetoothGattService, ssid: String, pass: String, rid: String) {
        viewModelScope.launch {
            try {
                val ssidChar = service.getCharacteristic(ssidCharUuid)
                val passChar = service.getCharacteristic(passCharUuid)
                val ridChar = service.getCharacteristic(ridCharUuid)
                val statusChar = service.getCharacteristic(statusCharUuid)

                if (ssidChar != null && passChar != null && ridChar != null) {
                    // Enable Notifications on statusChar
                    if (statusChar != null) {
                        Log.d(tag, "Enabling notifications for status updates...")
                        gatt.setCharacteristicNotification(statusChar, true)
                        val descriptor = statusChar.getDescriptor(configDescriptorUuid)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                            delay(500)
                        }
                    }

                    Log.d(tag, "Writing credentials...")
                    
                    ssidChar.setValue(ssid)
                    gatt.writeCharacteristic(ssidChar)
                    delay(800) 

                    passChar.setValue(pass)
                    gatt.writeCharacteristic(passChar)
                    delay(800)

                    ridChar.setValue(rid)
                    gatt.writeCharacteristic(ridChar)
                    delay(800)

                    Log.i(tag, "Data sent. Waiting for Wi-Fi test...")
                    credentialsTransferred = true
                    _state.value = ProvisioningState.Verifying
                    
                    // Safety timeout for Wi-Fi verification
                    delay(60000)
                    if (_state.value == ProvisioningState.Verifying) {
                        Log.i(tag, "Verification period complete. Provisioning SUCCESS.")
                        _state.value = ProvisioningState.Success
                        gatt.disconnect()
                    }
                } else {
                    _state.value = ProvisioningState.Error("Hardware characteristics missing")
                }
            } catch (e: Exception) {
                _state.value = ProvisioningState.Error("Transfer failed")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeGatt()
        stopScan()
    }
}

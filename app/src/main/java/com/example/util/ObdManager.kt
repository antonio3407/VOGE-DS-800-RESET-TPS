package com.example.util

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ObdConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    INITIALIZING_ELM,
    CONNECTED,
    SIMULATED,
    ERROR
}

data class ObdTelemetry(
    val throttlePercent: Float = 0.0f,
    val rpm: Int = 0,
    val coolantTempC: Int = 25,
    val batteryVoltage: Float = 12.8f,
    val isVolatileMemoryResetting: Boolean = false,
    val resetSuccess: Boolean? = null,
    val statusMessage: String = "Desconectado"
)

/**
 * Conector OBD-II Bluetooth de Alta Estabilidad para Centralita Bosch MSE 6.0
 * Compatible con: ELM327 v1.4/v1.5/v2.1, vLinker, Viecar, HM-10, Nordic UART y Classic SPP.
 */
class ObdManager(private val context: Context) {

    companion object {
        private const val TAG = "ObdManager"

        // Lista exhaustiva de UUIDs GATT utilizados por adaptadores OBD-II BLE
        val OBD_SERVICE_UUIDS: List<UUID> = listOf(
            UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"), // HM-10 / Clones estándar
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe3-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ffe5-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
            UUID.fromString("000018f0-0000-1000-8000-00805f9b34fb"), // V-Gate / vLinker / iCar Pro
            UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"), // Carista / Viecar / Clones v2.1
            UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb"),
            UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"), // Microchip / ISSC
            UUID.fromString("e7810a71-73ae-499d-8c15-faa9aef0c3f2"), // OBDLink BLE
            UUID.fromString("10b91000-ca22-11e1-9310-44455354424c"), // Kiwi 3 BLE
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")  // SPP clásico sobre BLE / RFCOMM
        )

        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val SPP_RFCOMM_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        try {
            BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            null
        }
    }

    // Classic SPP Sockets
    private var classicSocket: BluetoothSocket? = null
    private var classicInput: InputStream? = null
    private var classicOutput: OutputStream? = null

    // BLE GATT Objects
    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var bleBuffer = StringBuilder()
    private var activeBleResponseDeferred: CompletableDeferred<String>? = null

    // Throttling and concurrency control
    private val sendMutex = Mutex()
    private var lastWriteTime: Long = 0L

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _telemetry = MutableStateFlow(ObdTelemetry())
    val telemetry: StateFlow<ObdTelemetry> = _telemetry.asStateFlow()

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    val isBluetoothAvailable: Boolean
        get() = bluetoothAdapter != null

    /**
     * Conexión híbrida de alta estabilidad:
     * Intenta BLE GATT primero con auto-descubrimiento UART RX/TX.
     * Si el dispositivo es Classic o falla GATT, establece canal SPP RFCOMM.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectToDevice(device: BluetoothDevice) = withContext(Dispatchers.IO) {
        disconnect()
        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _telemetry.value = _telemetry.value.copy(
            statusMessage = "Conectando con ${device.name ?: device.address}..."
        )

        delay(500) // Pausa imprescindible antes de conectar

        var connected = false

        // Intento 1: Si el dispositivo soporta BLE o es DUAL, conectar vía GATT
        val deviceType = try { device.type } catch (_: Exception) { BluetoothDevice.DEVICE_TYPE_UNKNOWN }
        if (deviceType == BluetoothDevice.DEVICE_TYPE_LE || deviceType == BluetoothDevice.DEVICE_TYPE_DUAL) {
            Log.d(TAG, "Intentando conexión BLE GATT...")
            connected = connectGattInternal(device)
        }

        // Intento 2: Fallback a Classic SPP RFCOMM si no se conectó por BLE
        if (!connected) {
            Log.d(TAG, "Intentando conexión Bluetooth Classic SPP...")
            connected = connectClassicSpp(device)
        }

        if (connected) {
            _connectionStatus.value = ObdConnectionStatus.INITIALIZING_ELM
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "Inicializando protocolos Bosch MSE 6.0..."
            )

            // Secuencia estricta de inicialización de la centralita Bosch
            val initSuccess = initializeECU()
            if (initSuccess) {
                _connectionStatus.value = ObdConnectionStatus.CONNECTED
                _telemetry.value = _telemetry.value.copy(
                    statusMessage = "Conectado a Bosch MSE 6.0 (ISO 15765-4 CAN 500k)"
                )
            } else {
                _connectionStatus.value = ObdConnectionStatus.CONNECTED
                _telemetry.value = _telemetry.value.copy(
                    statusMessage = "Conectado al ELM 327 (Modo básico)"
                )
            }
        } else {
            disconnect()
            _connectionStatus.value = ObdConnectionStatus.ERROR
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "No se pudo establecer enlace serie con el adaptador OBD 2"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectClassicSpp(device: BluetoothDevice): Boolean {
        try {
            bluetoothAdapter?.cancelDiscovery()
            delay(300)
            val socket = device.createRfcommSocketToServiceRecord(SPP_RFCOMM_UUID)
            socket.connect()

            classicSocket = socket
            classicInput = socket.inputStream
            classicOutput = socket.outputStream
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Classic SPP connection failed: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectGattInternal(device: BluetoothDevice): Boolean {
        val gattConnectedDeferred = CompletableDeferred<Boolean>()
        val servicesDiscoveredDeferred = CompletableDeferred<Boolean>()

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT connected, discovering services...")
                    gattConnectedDeferred.complete(true)
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT disconnected")
                    gattConnectedDeferred.complete(false)
                    servicesDiscoveredDeferred.complete(false)
                    if (_connectionStatus.value == ObdConnectionStatus.CONNECTED) {
                        disconnect()
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    servicesDiscoveredDeferred.complete(true)
                } else {
                    servicesDiscoveredDeferred.complete(false)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val chunk = String(value, Charsets.US_ASCII)
                handleIncomingBleChunk(chunk)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?
            ) {
                val value = characteristic?.value ?: return
                val chunk = String(value, Charsets.US_ASCII)
                handleIncomingBleChunk(chunk)
            }
        }

        try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }

            val isGattConnected = withTimeoutOrNull(6000) { gattConnectedDeferred.await() } ?: false
            if (!isGattConnected) return false

            val areServicesDiscovered = withTimeoutOrNull(6000) { servicesDiscoveredDeferred.await() } ?: false
            if (!areServicesDiscovered) return false

            delay(500) // Pausa tras descubrir servicios

            // Descubrimiento dinámico de canales RX (Notify) y TX (Write)
            val gatt = bluetoothGatt ?: return false
            var foundRx: BluetoothGattCharacteristic? = null
            var foundTx: BluetoothGattCharacteristic? = null

            for (service in gatt.services) {
                for (characteristic in service.characteristics) {
                    val props = characteristic.properties
                    if (foundRx == null && ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) {
                        foundRx = characteristic
                    }
                    if (foundTx == null && ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                                (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) {
                        foundTx = characteristic
                    }
                }
                if (foundRx != null && foundTx != null) break
            }

            if (foundRx == null || foundTx == null) {
                Log.w(TAG, "No se encontraron canales RX/TX en el GATT")
                return false
            }

            // Habilitar notificaciones en RX
            gatt.setCharacteristicNotification(foundRx, true)
            val descriptor = foundRx.getDescriptor(CCCD_DESCRIPTOR_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
                delay(300)
            }

            rxCharacteristic = foundRx
            txCharacteristic = foundTx
            return true
        } catch (e: Exception) {
            Log.e(TAG, "GATT setup failed", e)
            return false
        }
    }

    private fun handleIncomingBleChunk(chunk: String) {
        bleBuffer.append(chunk)
        if (bleBuffer.contains(">")) {
            val fullResponse = bleBuffer.toString()
            bleBuffer.clear()
            activeBleResponseDeferred?.complete(fullResponse)
            activeBleResponseDeferred = null
        }
    }

    /**
     * Secuencia crítica de inicialización probada para Centralita Bosch MSE 6.0:
     * 1. Pausa de asentamiento de radio (600ms)
     * 2. Test rápido AT
     * 3. Reset físico ATZ con espera de 1200ms para rearranque de microcontrolador
     * 4. ATE0, ATL0, ATH0 (Comunicación limpia)
     * 5. ATSP6 (Fijar protocolo CAN 500 kbps de Bosch)
     * 6. ATAT1 (Temporizaciones adaptativas rápidas)
     * 7. Handshake 0100 con fallback ATSP0
     */
    private suspend fun initializeECU(): Boolean = withContext(Dispatchers.IO) {
        delay(600) // Pausa inicial para asentar el enlace de radio

        var supportsAT = true
        try {
            val resp = sendCommand("AT")
            if (resp.contains("?") || resp.contains("ERROR")) supportsAT = false
        } catch (_: Exception) {
            supportsAT = false
        }

        if (supportsAT) {
            // 1. Reset físico del chip
            try {
                sendCommand("ATZ")
            } catch (_: Exception) {}

            // TIEMPO CLAVE: Esperar 1200ms a que el procesador del ELM327 arranque de nuevo
            delay(1200)

            // 2. Parámetros de comunicación limpia
            val setupCommands = listOf(
                "ATE0", // Echo OFF (evita eco del comando enviado)
                "ATL0", // Linefeeds OFF
                "ATH0"  // Headers OFF (respuestas limpias solo con los datos)
            )

            for (cmd in setupCommands) {
                try {
                    sendCommand(cmd)
                } catch (_: Exception) {}
            }

            // 3. Fijar protocolo CAN de 500 kbps (estándar ECU Bosch MSE 6.0 en Voge DS 800)
            try {
                sendCommand("ATSP6")
            } catch (_: Exception) {}

            // 4. Temporizaciones adaptativas rápidas
            try {
                sendCommand("ATAT1")
            } catch (_: Exception) {}
        }

        // 5. Handshake real contra la ECU pidiendo PIDs soportados (0100)
        var connectedToECU = false
        try {
            val resp = sendCommand("0100")
            if (resp.uppercase().contains("4100") || (!resp.contains("UNABLE") && !resp.contains("ERROR"))) {
                connectedToECU = true
            }
        } catch (_: Exception) {}

        // Fallback: Si ATSP6 no conectó, activar búsqueda automática de protocolo
        if (!connectedToECU && supportsAT) {
            try {
                sendCommand("ATSP0") // Auto-detect
                val resp = sendCommand("0100")
                if (resp.uppercase().contains("4100") || !resp.contains("UNABLE")) {
                    connectedToECU = true
                }
            } catch (_: Exception) {}
        }

        return@withContext connectedToECU
    }

    /**
     * Envío de comandos con control de colas, Throttling de 120ms y espera de prompt '>'
     */
    suspend fun sendCommand(command: String): String = sendMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanCmd = command.trim()

            // Guard de velocidad: mínimo 120 ms entre envíos para no saturar el chip
            val now = System.currentTimeMillis()
            val elapsed = now - lastWriteTime
            if (elapsed < 120) {
                delay(120 - elapsed)
            }

            val payload = "$cleanCmd\r"
            val timeoutLimit = if (cleanCmd.contains("ATZ") || cleanCmd.contains("ATSP")) 4500L else 3500L

            val response = if (bluetoothGatt != null && txCharacteristic != null) {
                // Envío vía BLE GATT
                sendGattCommand(payload, timeoutLimit)
            } else if (classicSocket != null && classicOutput != null && classicInput != null) {
                // Envío vía Classic SPP Socket
                sendClassicCommand(payload, timeoutLimit)
            } else {
                throw IllegalStateException("No hay canal de comunicación serie activo")
            }

            lastWriteTime = System.currentTimeMillis()
            return@withContext response.replace(">", "").trim()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendGattCommand(payload: String, timeoutMs: Long): String {
        val gatt = bluetoothGatt ?: throw IllegalStateException("GATT no conectado")
        val tx = txCharacteristic ?: throw IllegalStateException("Canal TX no disponible")

        bleBuffer.clear()
        val deferred = CompletableDeferred<String>()
        activeBleResponseDeferred = deferred

        val bytes = payload.toByteArray(Charsets.US_ASCII)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val writeType = if ((tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            gatt.writeCharacteristic(tx, bytes, writeType)
        } else {
            tx.value = bytes
            if ((tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(tx)
        }

        val result = withTimeoutOrNull(timeoutMs) {
            deferred.await()
        } ?: throw RuntimeException("Timeout esperando respuesta BLE para comando")

        return result
    }

    private fun sendClassicCommand(payload: String, timeoutMs: Long): String {
        val out = classicOutput ?: throw IllegalStateException("Salida SPP nula")
        val inStream = classicInput ?: throw IllegalStateException("Entrada SPP nula")

        out.write(payload.toByteArray(Charsets.US_ASCII))
        out.flush()

        val buffer = ByteArray(256)
        val sb = StringBuilder()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (inStream.available() > 0) {
                val read = inStream.read(buffer)
                if (read > 0) {
                    val chunk = String(buffer, 0, read, Charsets.US_ASCII)
                    sb.append(chunk)
                    if (chunk.contains(">")) {
                        break
                    }
                }
            } else {
                Thread.sleep(30)
            }
        }
        return sb.toString()
    }

    suspend fun startSimulationMode() = withContext(Dispatchers.Default) {
        disconnect()
        _connectionStatus.value = ObdConnectionStatus.SIMULATED
        _telemetry.value = _telemetry.value.copy(
            throttlePercent = 0.0f,
            rpm = 1380,
            coolantTempC = 78,
            batteryVoltage = 13.6f,
            statusMessage = "Simulación OBD 2 ELM 327 activa (Bosch MSE 6.0)"
        )
    }

    suspend fun updateSimulationTelemetry(throttle: Float, rpm: Int) = withContext(Dispatchers.Default) {
        if (_connectionStatus.value == ObdConnectionStatus.SIMULATED) {
            _telemetry.value = _telemetry.value.copy(
                throttlePercent = throttle.coerceIn(0f, 100f),
                rpm = rpm.coerceIn(0, 9500)
            )
        }
    }

    suspend fun queryLiveData() = withContext(Dispatchers.IO) {
        if (_connectionStatus.value != ObdConnectionStatus.CONNECTED) return@withContext

        try {
            // PID 0111: Throttle Position (%)
            val tpsResponse = sendCommand("01 11")
            val tpsPercent = parseTps(tpsResponse)

            // PID 010C: Engine RPM
            val rpmResponse = sendCommand("01 0C")
            val rpmValue = parseRpm(rpmResponse)

            // ATRV: Voltaje de batería
            val voltResponse = sendCommand("ATRV")
            val voltValue = parseVolt(voltResponse)

            _telemetry.value = _telemetry.value.copy(
                throttlePercent = tpsPercent ?: _telemetry.value.throttlePercent,
                rpm = rpmValue ?: _telemetry.value.rpm,
                batteryVoltage = voltValue ?: _telemetry.value.batteryVoltage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando telemetría en vivo", e)
        }
    }

    suspend fun resetVolatileMemory(): Boolean = withContext(Dispatchers.IO) {
        _telemetry.value = _telemetry.value.copy(
            isVolatileMemoryResetting = true,
            resetSuccess = null,
            statusMessage = "Borrando memoria volátil en Bosch MSE 6.0..."
        )

        try {
            if (_connectionStatus.value == ObdConnectionStatus.CONNECTED) {
                // OBD-II Mode 04: Borrado de códigos de diagnóstico y valores adaptativos aprendidos
                val response = sendCommand("04")
                delay(600)

                // Comando adicional UDS de reinicio de centralita Bosch
                try {
                    sendCommand("11 01")
                    delay(800)
                } catch (_: Exception) {}

                val success = response.contains("44") || response.contains("OK") || !response.contains("ERROR")
                _telemetry.value = _telemetry.value.copy(
                    isVolatileMemoryResetting = false,
                    resetSuccess = success,
                    statusMessage = if (success) "¡Memoria volátil de la centralita Bosch MSE 6.0 borrada con éxito!" else "Respuesta ECU: $response"
                )
                return@withContext success
            } else if (_connectionStatus.value == ObdConnectionStatus.SIMULATED) {
                delay(1200)
                _telemetry.value = _telemetry.value.copy(
                    isVolatileMemoryResetting = false,
                    resetSuccess = true,
                    throttlePercent = 0.0f,
                    rpm = 1350,
                    statusMessage = "¡Memoria volátil de la centralita Bosch MSE 6.0 reseteada!"
                )
                return@withContext true
            } else {
                _telemetry.value = _telemetry.value.copy(
                    isVolatileMemoryResetting = false,
                    resetSuccess = false,
                    statusMessage = "Error: Adaptador OBD 2 no conectado"
                )
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reset volatile memory failed", e)
            _telemetry.value = _telemetry.value.copy(
                isVolatileMemoryResetting = false,
                resetSuccess = false,
                statusMessage = "Fallo al enviar comando de reseteo: ${e.message}"
            )
            return@withContext false
        }
    }

    private fun parseTps(response: String): Float? {
        val clean = response.replace(" ", "").uppercase()
        val index = clean.indexOf("4111")
        if (index >= 0 && clean.length >= index + 6) {
            val hex = clean.substring(index + 4, index + 6)
            val value = hex.toIntOrNull(16) ?: return null
            return (value * 100f) / 255f
        }
        return null
    }

    private fun parseRpm(response: String): Int? {
        val clean = response.replace(" ", "").uppercase()
        val index = clean.indexOf("410C")
        if (index >= 0 && clean.length >= index + 8) {
            val hexA = clean.substring(index + 4, index + 6)
            val hexB = clean.substring(index + 6, index + 8)
            val valA = hexA.toIntOrNull(16) ?: return null
            val valB = hexB.toIntOrNull(16) ?: return null
            return ((valA * 256) + valB) / 4
        }
        return null
    }

    private fun parseVolt(response: String): Float? {
        val num = response.replace("V", "").replace("v", "").trim()
        return num.toFloatOrNull()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        try {
            classicInput?.close()
            classicOutput?.close()
            classicSocket?.close()
        } catch (_: Exception) {}

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (_: Exception) {}

        classicSocket = null
        classicInput = null
        classicOutput = null
        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        activeBleResponseDeferred = null
        bleBuffer.clear()

        if (_connectionStatus.value != ObdConnectionStatus.DISCONNECTED) {
            _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
            _telemetry.value = _telemetry.value.copy(statusMessage = "Desconectado")
        }
    }
}

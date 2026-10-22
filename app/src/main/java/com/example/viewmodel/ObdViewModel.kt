package com.example.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.util.ObdConnectionStatus
import com.example.util.ObdManager
import com.example.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ObdUiState(
    val connectionStatus: ObdConnectionStatus = ObdConnectionStatus.DISCONNECTED,
    val throttlePercent: Float = 0.0f,
    val rpm: Int = 0,
    val batteryVoltage: Float = 12.8f,
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val selectedDevice: BluetoothDevice? = null,
    val isResetting: Boolean = false,
    val resetMessage: String? = null,
    val isSimulationMode: Boolean = false,
    val statusMessage: String = "Desconectado. Conecta un adaptador OBD 2 ELM 327.",
    val showResetConfirmationDialog: Boolean = false
)

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    private val obdManager = ObdManager(application)
    private val ttsManager = TtsManager(application)

    private val _uiState = MutableStateFlow(ObdUiState())
    val uiState: StateFlow<ObdUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var simulationJob: Job? = null

    init {
        loadPairedDevices()
        observeObdManager()
    }

    private fun observeObdManager() {
        viewModelScope.launch {
            obdManager.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
                if (status == ObdConnectionStatus.CONNECTED) {
                    startTelemetryPolling()
                    ttsManager.speak("Conectado con éxito al adaptador OBD 2 ELM 327 y a la centralita Bosch MSE 6.0.")
                } else if (status == ObdConnectionStatus.SIMULATED) {
                    startSimulationLoop()
                    ttsManager.speak("Modo simulación OBD 2 activo.")
                } else {
                    stopTelemetryPolling()
                }
            }
        }

        viewModelScope.launch {
            obdManager.telemetry.collect { tele ->
                _uiState.update {
                    it.copy(
                        throttlePercent = tele.throttlePercent,
                        rpm = tele.rpm,
                        batteryVoltage = tele.batteryVoltage,
                        isResetting = tele.isVolatileMemoryResetting,
                        statusMessage = tele.statusMessage
                    )
                }
            }
        }
    }

    fun loadPairedDevices() {
        val devices = obdManager.getPairedDevices()
        val defaultElm = devices.firstOrNull { dev ->
            val name = dev.name ?: ""
            name.contains("OBD", ignoreCase = true) || name.contains("ELM", ignoreCase = true)
        } ?: devices.firstOrNull()

        _uiState.update {
            it.copy(
                pairedDevices = devices,
                selectedDevice = defaultElm
            )
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        _uiState.update { it.copy(selectedDevice = device) }
    }

    fun connectToSelectedDevice() {
        val device = _uiState.value.selectedDevice
        if (device != null) {
            viewModelScope.launch {
                stopSimulation()
                obdManager.connectToDevice(device)
            }
        } else {
            // If no physical ELM327 paired, fallback or notify user
            _uiState.update {
                it.copy(statusMessage = "No se ha seleccionado ningún dispositivo OBD 2 ELM 327 emparejado.")
            }
        }
    }

    fun disconnect() {
        stopTelemetryPolling()
        stopSimulation()
        obdManager.disconnect()
        _uiState.update {
            it.copy(
                isSimulationMode = false,
                throttlePercent = 0.0f,
                rpm = 0,
                statusMessage = "Desconectado"
            )
        }
    }

    fun toggleSimulationMode() {
        val current = _uiState.value.isSimulationMode
        if (!current) {
            disconnect()
            _uiState.update { it.copy(isSimulationMode = true) }
            viewModelScope.launch {
                obdManager.startSimulationMode()
            }
        } else {
            disconnect()
        }
    }

    private fun startTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.connectionStatus == ObdConnectionStatus.CONNECTED) {
                obdManager.queryLiveData()
                delay(250) // 4Hz refresh rate
            }
        }
    }

    private fun stopTelemetryPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            var phase = 0f
            while (isActive && _uiState.value.isSimulationMode) {
                phase += 0.1f
                // Simulate realistic warm idle fluctuations ~1360-1420 RPM and slight throttle opening
                val baseRpm = 1380
                val jitter = (kotlin.math.sin(phase.toDouble()) * 25).toInt()
                val currentRpm = baseRpm + jitter

                // Throttle position at idle is typically ~0.0% to 2.4%
                val simulatedThrottle = if (_uiState.value.isResetting) 0.0f else (kotlin.math.abs(kotlin.math.sin(phase.toDouble() * 0.3)) * 1.5).toFloat()

                obdManager.updateSimulationTelemetry(
                    throttle = simulatedThrottle,
                    rpm = currentRpm
                )
                delay(300)
            }
        }
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _uiState.update { it.copy(isSimulationMode = false) }
    }

    fun showResetConfirmation(show: Boolean) {
        _uiState.update { it.copy(showResetConfirmationDialog = show) }
    }

    fun executeResetVolatileMemory() {
        showResetConfirmation(false)
        viewModelScope.launch {
            ttsManager.speak("Iniciando borrado de memoria volátil de la centralita Bosch MSE 6.0.")
            val success = obdManager.resetVolatileMemory()
            if (success) {
                ttsManager.playStepCompletedChime()
                ttsManager.speak("Memoria volátil de la centralita Bosch MSE 6.0 reseteada con éxito. Valores adaptativos reiniciados.")
                _uiState.update {
                    it.copy(
                        resetMessage = "¡Memoria volátil borrada correctamente! Los parámetros de ralentí y mezcla se han reiniciado."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        resetMessage = "Error al intentar borrar la memoria volátil. Verifica la conexión con el ELM 327."
                    )
                }
            }
        }
    }

    fun clearResetMessage() {
        _uiState.update { it.copy(resetMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopTelemetryPolling()
        stopSimulation()
        obdManager.disconnect()
        ttsManager.release()
    }
}

package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SequencesRepository
import com.example.model.MotorcycleSequence
import com.example.model.SequenceCategory
import com.example.model.SequenceStep
import com.example.util.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val sequences: List<MotorcycleSequence> = SequencesRepository.sequences,
    val selectedCategory: SequenceCategory = SequenceCategory.ALL,
    val searchQuery: String = "",
    val activeSequence: MotorcycleSequence? = null,
    val currentStepIndex: Int = 0,
    val remainingSeconds: Int = 0,
    val stepTotalSeconds: Int = 0,
    val isTimerRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val ttsEnabled: Boolean = true,
    val beepsEnabled: Boolean = true,
    val speechRate: Float = 1.0f
) {
    val currentStep: SequenceStep?
        get() = activeSequence?.steps?.getOrNull(currentStepIndex)

    val progressFraction: Float
        get() = if (stepTotalSeconds > 0) {
            (stepTotalSeconds - remainingSeconds).toFloat() / stepTotalSeconds.toFloat()
        } else 1f

    val overallStepFraction: Float
        get() = if (activeSequence != null && activeSequence.steps.isNotEmpty()) {
            (currentStepIndex + 1).toFloat() / activeSequence.steps.size.toFloat()
        } else 0f

    val filteredSequences: List<MotorcycleSequence>
        get() = sequences.filter { seq ->
            val matchesCategory = selectedCategory == SequenceCategory.ALL || seq.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                seq.title.contains(searchQuery, ignoreCase = true) ||
                seq.shortSummary.contains(searchQuery, ignoreCase = true) ||
                seq.applicability.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
}

class SequenceViewModel(application: Application) : AndroidViewModel(application) {

    val ttsManager = TtsManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val isSpeaking = ttsManager.isSpeaking

    private var timerJob: Job? = null

    fun selectCategory(category: SequenceCategory) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openSequence(sequence: MotorcycleSequence) {
        _uiState.update {
            it.copy(
                activeSequence = sequence,
                currentStepIndex = 0,
                isCompleted = false,
                isTimerRunning = false,
                remainingSeconds = 0,
                stepTotalSeconds = 0
            )
        }
        setupStep(sequence, 0)
    }

    private fun setupStep(sequence: MotorcycleSequence, stepIndex: Int) {
        timerJob?.cancel()
        val step = sequence.steps.getOrNull(stepIndex) ?: return

        val duration = step.durationSeconds
        _uiState.update {
            it.copy(
                currentStepIndex = stepIndex,
                remainingSeconds = duration,
                stepTotalSeconds = duration,
                isTimerRunning = duration > 0,
                isCompleted = false
            )
        }

        // Voice announcement
        if (_uiState.value.ttsEnabled) {
            ttsManager.speak(step.voiceInstruction)
        }

        // Start countdown if step has duration
        if (duration > 0) {
            startCountdown()
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerRunning = true) }
            while (_uiState.value.remainingSeconds > 0 && _uiState.value.isTimerRunning) {
                delay(1000L)
                val currentRem = _uiState.value.remainingSeconds - 1
                _uiState.update { it.copy(remainingSeconds = currentRem) }

                // Audio cue during final 3 seconds
                if (_uiState.value.beepsEnabled) {
                    if (currentRem in 1..3) {
                        ttsManager.playCountdownWarnBeep()
                    } else if (currentRem > 3 && currentRem % 5 == 0) {
                        ttsManager.playTickBeep()
                    }
                }

                if (currentRem <= 0) {
                    onTimerFinished()
                    break
                }
            }
        }
    }

    private fun onTimerFinished() {
        _uiState.update { it.copy(isTimerRunning = false) }
        ttsManager.playStepCompletedChime()

        val state = _uiState.value
        val seq = state.activeSequence ?: return

        if (state.currentStepIndex < seq.steps.size - 1) {
            if (state.ttsEnabled) {
                ttsManager.speak("Tiempo cumplido. Pulsa siguiente paso cuando estés listo.", flush = false)
            }
        } else {
            // Sequence completed
            _uiState.update { it.copy(isCompleted = true) }
            if (state.ttsEnabled) {
                ttsManager.speak("Secuencia finalizada con éxito. Comprueba el correcto funcionamiento de tu moto.", flush = false)
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isTimerRunning = false) }
    }

    fun resumeTimer() {
        if (_uiState.value.remainingSeconds > 0) {
            startCountdown()
        }
    }

    fun nextStep() {
        val state = _uiState.value
        val seq = state.activeSequence ?: return
        if (state.currentStepIndex < seq.steps.size - 1) {
            setupStep(seq, state.currentStepIndex + 1)
        } else {
            _uiState.update { it.copy(isCompleted = true, isTimerRunning = false) }
            if (state.ttsEnabled) {
                ttsManager.speak("Secuencia finalizada con éxito.")
            }
        }
    }

    fun previousStep() {
        val state = _uiState.value
        val seq = state.activeSequence ?: return
        if (state.currentStepIndex > 0) {
            setupStep(seq, state.currentStepIndex - 1)
        }
    }

    fun skipTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(remainingSeconds = 0, isTimerRunning = false) }
        onTimerFinished()
    }

    fun restartCurrentSequence() {
        val seq = _uiState.value.activeSequence ?: return
        setupStep(seq, 0)
    }

    fun repeatCurrentStepVoice() {
        val step = _uiState.value.currentStep ?: return
        ttsManager.speak(step.voiceInstruction)
    }

    fun toggleTts() {
        val newVal = !_uiState.value.ttsEnabled
        _uiState.update { it.copy(ttsEnabled = newVal) }
        if (!newVal) {
            ttsManager.stop()
        } else {
            val step = _uiState.value.currentStep
            if (step != null) {
                ttsManager.speak("Locuciones activadas. ${step.voiceInstruction}")
            }
        }
    }

    fun toggleBeeps() {
        _uiState.update { it.copy(beepsEnabled = !it.beepsEnabled) }
    }

    fun setSpeechRate(rate: Float) {
        _uiState.update { it.copy(speechRate = rate) }
        ttsManager.setSpeechRate(rate)
    }

    fun closeActiveSequence() {
        timerJob?.cancel()
        ttsManager.stop()
        _uiState.update {
            it.copy(
                activeSequence = null,
                isTimerRunning = false,
                isCompleted = false,
                currentStepIndex = 0,
                remainingSeconds = 0
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        ttsManager.release()
    }
}

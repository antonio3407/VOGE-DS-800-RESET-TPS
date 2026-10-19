package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ActionIcon
import com.example.model.MotorcycleSequence
import com.example.model.SequenceStep
import com.example.ui.theme.DashboardCyanSecondary
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.RallyLimeTertiary
import com.example.ui.theme.RallyOrangePrimary
import com.example.ui.theme.StatusWarning
import com.example.viewmodel.SequenceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequenceRunnerScreen(
    viewModel: SequenceViewModel,
    sequence: MotorcycleSequence,
    onBack: () -> Unit
) {
    BackHandler {
        onBack()
    }

    val uiState by viewModel.uiState.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()

    val currentStep = uiState.currentStep
    val stepIndex = uiState.currentStepIndex
    val totalSteps = sequence.steps.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = sequence.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (uiState.isCompleted) "Secuencia completada" else "Paso ${stepIndex + 1} de $totalSteps",
                            style = MaterialTheme.typography.bodySmall,
                            color = DashboardCyanSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("nav_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a la lista"
                        )
                    }
                },
                actions = {
                    // Repeat voice
                    IconButton(
                        onClick = { viewModel.repeatCurrentStepVoice() },
                        modifier = Modifier.testTag("repeat_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Repetir Locución",
                            tint = if (isSpeaking) DashboardCyanSecondary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Restart sequence
                    IconButton(
                        onClick = { viewModel.restartCurrentSequence() },
                        modifier = Modifier.testTag("restart_sequence_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reiniciar Secuencia"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Overall Sequence Progress Bar
            LinearProgressIndicator(
                progress = { uiState.overallStepFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = RallyOrangePrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            if (uiState.isCompleted) {
                SequenceCompletedView(
                    sequence = sequence,
                    onRestart = { viewModel.restartCurrentSequence() },
                    onExit = onBack
                )
            } else if (currentStep != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Audio & Feedback Toolbar
                    AudioToolbar(
                        ttsEnabled = uiState.ttsEnabled,
                        beepsEnabled = uiState.beepsEnabled,
                        speechRate = uiState.speechRate,
                        isSpeaking = isSpeaking,
                        onToggleTts = { viewModel.toggleTts() },
                        onToggleBeeps = { viewModel.toggleBeeps() },
                        onSetSpeechRate = { viewModel.setSpeechRate(it) },
                        onRepeatVoice = { viewModel.repeatCurrentStepVoice() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Target Badge
                    ActionTargetBadge(target = currentStep.actionTarget, icon = currentStep.actionIcon)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Main Countdown Timer Widget (If step has duration)
                    if (currentStep.durationSeconds > 0) {
                        CountdownTimerWidget(
                            remainingSeconds = uiState.remainingSeconds,
                            totalSeconds = uiState.stepTotalSeconds,
                            progress = uiState.progressFraction,
                            isRunning = uiState.isTimerRunning,
                            onTogglePlayPause = {
                                if (uiState.isTimerRunning) viewModel.pauseTimer() else viewModel.resumeTimer()
                            },
                            onSkip = { viewModel.skipTimer() }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Step Detail Card
                    StepInstructionCard(
                        step = currentStep,
                        stepIndex = stepIndex,
                        totalSteps = totalSteps,
                        isSpeaking = isSpeaking
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Navigation Buttons
                    RunnerBottomControls(
                        hasPrevious = stepIndex > 0,
                        isLastStep = stepIndex == totalSteps - 1,
                        hasTimerRunning = uiState.isTimerRunning,
                        onPrevious = { viewModel.previousStep() },
                        onNext = { viewModel.nextStep() },
                        onSkipTimer = { viewModel.skipTimer() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AudioToolbar(
    ttsEnabled: Boolean,
    beepsEnabled: Boolean,
    speechRate: Float,
    isSpeaking: Boolean,
    onToggleTts: () -> Unit,
    onToggleBeeps: () -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onRepeatVoice: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Speaking indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isSpeaking) DashboardCyanSecondary else if (ttsEnabled) RallyLimeTertiary else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSpeaking) "Hablando..." else if (ttsEnabled) "Voz activa" else "Silenciado",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = if (isSpeaking) DashboardCyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick toggles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Speech speed chips
                listOf(0.8f, 1.0f, 1.2f).forEach { rate ->
                    val isSelected = kotlin.math.abs(speechRate - rate) < 0.05f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) RallyOrangePrimary else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${rate}x",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Toggle Beeps
                IconButton(
                    onClick = onToggleBeeps,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_beeps_button")
                ) {
                    Icon(
                        imageVector = if (beepsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = "Tonos de cuenta atrás",
                        tint = if (beepsEnabled) DashboardCyanSecondary else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Toggle TTS voice
                IconButton(
                    onClick = onToggleTts,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("toggle_tts_button")
                ) {
                    Icon(
                        imageVector = if (ttsEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Voz",
                        tint = if (ttsEnabled) RallyOrangePrimary else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTargetBadge(target: String, icon: ActionIcon) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = RallyOrangePrimary.copy(alpha = 0.15f),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(RallyOrangePrimary)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = null,
                tint = RallyOrangePrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ACCIÓN: ${target.uppercase()}",
                color = RallyOrangePrimary,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun CountdownTimerWidget(
    remainingSeconds: Int,
    totalSeconds: Int,
    progress: Float,
    isRunning: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkip: () -> Unit
) {
    // Pulse animation during last 3 seconds
    val isCritical = remainingSeconds in 1..3
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isCritical) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val timerColor = when {
        remainingSeconds <= 3 && remainingSeconds > 0 -> Color(0xFFFF1744)
        remainingSeconds <= 10 -> StatusWarning
        else -> DashboardCyanSecondary
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                timerColor.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "TEMPORIZADOR DE CUENTA ATRÁS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Circular Countdown Dial
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(170.dp)
                    .scale(scalePulse)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = timerColor,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timeFormatted,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = timerColor
                    )
                    Text(
                        text = if (remainingSeconds > 0) "SEGUNDOS" else "¡COMPLETADO!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (remainingSeconds == 0) RallyLimeTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls for timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onTogglePlayPause,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("timer_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pausar" else "Reanudar"
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRunning) "Pausar" else "Reanudar")
                }

                Spacer(modifier = Modifier.width(12.dp))

                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("timer_skip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Saltar tiempo",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Saltar", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StepInstructionCard(
    step: SequenceStep,
    stepIndex: Int,
    totalSteps: Int,
    isSpeaking: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Step Number and Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(RallyOrangePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${step.stepNumber}",
                        fontWeight = FontWeight.Black,
                        color = Color.Black,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Detailed Written Instruction
            Text(
                text = step.instruction,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Voice script preview
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (isSpeaking) DashboardCyanSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Locución auditiva:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "\"${step.voiceInstruction}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSpeaking) DashboardCyanSecondary else MaterialTheme.colorScheme.onSurface,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }

            // Tip / Warning if present
            if (!step.tip.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = StatusWarning.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(StatusWarning.copy(alpha = 0.5f))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = step.tip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunnerBottomControls(
    hasPrevious: Boolean,
    isLastStep: Boolean,
    hasTimerRunning: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipTimer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasPrevious) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier
                    .weight(0.4f)
                    .height(52.dp)
                    .testTag("step_prev_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Anterior", fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(if (hasPrevious) 0.6f else 1f)
                .height(52.dp)
                .testTag("step_next_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLastStep) RallyLimeTertiary else RallyOrangePrimary,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = if (isLastStep) "FINALIZAR SECUENCIA" else "SIGUIENTE PASO",
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SequenceCompletedView(
    sequence: MotorcycleSequence,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(RallyLimeTertiary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = RallyLimeTertiary,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "¡SECUENCIA COMPLETADA!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = RallyLimeTertiary,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = sequence.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Centralita Bosch MSE 6.0 • Voge DS 800 Rally",
            style = MaterialTheme.typography.bodyMedium,
            color = DashboardCyanSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Verificación Final Recomendada:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "Verifica que el panel TFT no muestre testigos de error encendidos.",
                    "Arranca el motor en punto muerto y verifica un ralentí estable (1.300 - 1.400 rpm).",
                    "Comprueba la respuesta suave del acelerador electrónico.",
                    "Si realizaste prueba dinámica, comprueba el comportamiento del ABS/TCS."
                ).forEach { item ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = RallyLimeTertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onExit,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("exit_completed_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RallyOrangePrimary,
                contentColor = Color.Black
            )
        ) {
            Text(
                text = "VOLVER A LA LISTA DE SECUENCIAS",
                fontWeight = FontWeight.Black
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("repeat_completed_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Repetir Esta Secuencia")
        }
    }
}

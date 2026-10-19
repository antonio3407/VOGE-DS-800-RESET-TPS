package com.example.model

enum class SequenceCategory(val displayName: String, val iconLabel: String) {
    ALL("Todas", "⚡"),
    BOSCH_ECU("Bosch MSE 6.0", "🧠"),
    SERVICE("Mantenimiento", "🔧"),
    CHASSIS_CONTROLS("Mandos & ABS/TCS", "🏍️"),
    TPMS_SENSORS("TPMS & Ruedas", "🛞"),
    DIAGNOSTICS("Diagnóstico", "🩺")
}

enum class ActionIcon {
    THROTTLE,
    IGNITION_KEY,
    HANDLEBAR_BUTTON,
    TIMER,
    ENGINE,
    WRENCH,
    ABS_BUTTON,
    BATTERY,
    TIRE,
    CHECK_LIGHT
}

data class SequenceStep(
    val stepNumber: Int,
    val title: String,
    val instruction: String,
    val voiceInstruction: String,
    val durationSeconds: Int = 0,
    val actionTarget: String,
    val actionIcon: ActionIcon = ActionIcon.HANDLEBAR_BUTTON,
    val tip: String? = null
)

data class MotorcycleSequence(
    val id: String,
    val title: String,
    val category: SequenceCategory,
    val shortSummary: String,
    val difficulty: String,
    val estimatedDuration: String,
    val prerequisites: List<String>,
    val applicability: String = "Voge DS 800 Rally • Bosch MSE 6.0",
    val steps: List<SequenceStep>,
    val badge: String = "Secuencia Oficial"
)

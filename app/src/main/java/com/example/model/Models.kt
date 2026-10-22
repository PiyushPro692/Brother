package com.example.model

import java.util.UUID

enum class ActionType {
    TAP,
    SWIPE
}

enum class TimerUnit {
    SECONDS,
    MINUTES,
    HOURS;

    fun toSeconds(value: Long): Long = when (this) {
        SECONDS -> value
        MINUTES -> value * 60L
        HOURS -> value * 3600L
    }

    fun fromSeconds(seconds: Long): Long = when (this) {
        SECONDS -> seconds
        MINUTES -> seconds / 60L
        HOURS -> seconds / 3600L
    }
}

enum class AdResumeMode {
    CONTINUE_FROM_LEFT,
    RESTART_FROM_START
}

data class TargetPoint(
    val id: String = UUID.randomUUID().toString(),
    val number: Int = 1,
    val type: ActionType = ActionType.TAP,
    val xRatio: Float = 0.5f,
    val yRatio: Float = 0.5f,
    val endXRatio: Float = 0.5f,
    val endYRatio: Float = 0.3f,
    val delayMs: Long = 10L,
    val pressDurationMs: Long = 50L,
    val swipeDurationMs: Long = 300L,
    val isSelected: Boolean = false
)

data class MacroProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default Profile",
    val targets: List<TargetPoint> = emptyList(),
    val loopCount: Int = 0, // 0 = continuous / infinite loop
    val delayBetweenLoopsMs: Long = 0L,
    val sessionDurationSeconds: Long = 0L, // 0 = until stop button is pressed
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class AppSwitchStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

data class AppSwitchItem(
    val packageName: String,
    val appName: String,
    val durationSeconds: Int = 120, // default 2 minutes
    val order: Int = 0,
    val isSelected: Boolean = true,
    val status: AppSwitchStatus = AppSwitchStatus.PENDING,
    val cacheSizeMB: Float = 0f
)

data class AppSettings(
    val detectCrossClick: Boolean = true,
    val aiInterruptionDetection: Boolean = true,
    val autoResumeAfterInterruption: Boolean = true,
    val resumeModeAfterInterruption: AdResumeMode = AdResumeMode.CONTINUE_FROM_LEFT,
    val keepScreenAwake: Boolean = true, // Prevent screen shut down while running
    val soundEnabled: Boolean = false,
    val vibrationEnabled: Boolean = false,
    val doubleBackIntervalMs: Long = 1800L,
    val maxRetries: Int = 3,
    val overlayOpacity: Float = 0.95f,
    val sessionTimerDuration: Long = 60L,
    val sessionTimerUnit: TimerUnit = TimerUnit.SECONDS,
    // Click Assistant exact settings
    val gestureDelayMs: Long = 10L,
    val gestureRepetitions: Int = 1,
    val touchAndHoldDelayMs: Long = 50L,
    val swipeDurationMs: Long = 300L,
    val pinchDurationSeconds: Int = 3,
    val scriptLoopDelayMs: Long = 0L,
    val stopScriptAfterOption: String = "Until the stop button is pressed",
    val randomIntervalMs: Long = 0L,
    val randomDurationMs: Long = 0L,
    val randomPositionRadiusPx: Int = 0,
    // Open Apps / Clear cache exact settings
    val turboMode: Boolean = false,
    val universalAppTimerSeconds: Long = 120L // Single universal timer for all apps
)

enum class EventType {
    INFO,
    WARNING,
    ERROR,
    PAUSE,
    RECOVERY,
    AI
}

data class AutomationEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val message: String,
    val type: EventType = EventType.INFO
)

enum class PlaybackState {
    STOPPED,
    RUNNING,
    PAUSED,
    RECOVERING
}

data class Checkpoint(
    val macroId: String,
    val stepIndex: Int,
    val loopIndex: Int,
    val remainingDelayMs: Long,
    val remainingSessionSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)

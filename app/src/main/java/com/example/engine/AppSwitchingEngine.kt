package com.example.engine

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.data.AutomationRepository
import com.example.model.AppSwitchItem
import com.example.model.AppSwitchStatus
import com.example.model.EventType
import com.example.service.AutomationForegroundService
import com.example.service.ClickAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppSwitchSessionStatus(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val currentAppIndex: Int = -1,
    val currentAppName: String = "",
    val currentPackageName: String = "",
    val remainingAppSeconds: Long = 0L,
    val totalApps: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

class AppSwitchingEngine(
    private val context: Context,
    private val repository: AutomationRepository,
    private val scope: CoroutineScope
) {
    private val _session = MutableStateFlow(AppSwitchSessionStatus())
    val session: StateFlow<AppSwitchSessionStatus> = _session.asStateFlow()

    private var switchJob: Job? = null
    private var timerJob: Job? = null

    fun startSession() {
        val selectedApps = repository.appSwitchItems.value.filter { it.isSelected }
        if (selectedApps.isEmpty()) {
            repository.logEvent("Cannot start Open Apps session: No apps selected", EventType.WARNING)
            return
        }

        repository.resetAppSwitchStatuses()
        _session.value = AppSwitchSessionStatus(
            isRunning = true,
            isPaused = false,
            currentAppIndex = 0,
            totalApps = selectedApps.size
        )

        AutomationForegroundService.start(
            context,
            title = "App Queue Active",
            message = "Starting app queue (${selectedApps.size} apps)"
        )

        repository.logEvent("Started app switching queue with ${selectedApps.size} apps", EventType.INFO)
        runAppQueue(startIndex = 0)
    }

    fun pauseSession() {
        timerJob?.cancel()
        switchJob?.cancel()
        _session.value = _session.value.copy(isPaused = true)
        val currentApp = _session.value.currentAppName
        AutomationForegroundService.update(context, "Paused: $currentApp", "Timer halted")
        repository.logEvent("App queue session paused at '$currentApp'", EventType.PAUSE)
    }

    fun resumeSession() {
        _session.value = _session.value.copy(isPaused = false)
        val currentIndex = _session.value.currentAppIndex.coerceAtLeast(0)
        runAppQueue(startIndex = currentIndex, remainingSeconds = _session.value.remainingAppSeconds)
        repository.logEvent("App queue session resumed", EventType.INFO)
    }

    fun skipCurrentApp() {
        timerJob?.cancel()
        switchJob?.cancel()
        val apps = repository.appSwitchItems.value.filter { it.isSelected }
        val currentIndex = _session.value.currentAppIndex
        if (currentIndex in apps.indices) {
            val app = apps[currentIndex]
            repository.updateAppSwitchStatus(app.packageName, AppSwitchStatus.SKIPPED)
            repository.logEvent("Skipped app '${app.appName}'", EventType.INFO)
        }

        val nextIndex = currentIndex + 1
        if (nextIndex < apps.size) {
            runAppQueue(startIndex = nextIndex)
        } else {
            completeSession()
        }
    }

    fun stopSession() {
        timerJob?.cancel()
        timerJob = null
        switchJob?.cancel()
        switchJob = null

        _session.value = AppSwitchSessionStatus(isRunning = false, isPaused = false)
        AutomationForegroundService.stop(context)
        repository.logEvent("App queue session stopped by user", EventType.INFO)
    }

    private fun runAppQueue(startIndex: Int, remainingSeconds: Long? = null) {
        switchJob?.cancel()
        switchJob = scope.launch(Dispatchers.Main) {
            val apps = repository.appSwitchItems.value.filter { it.isSelected }
            val settings = repository.settings.value

            for (i in startIndex until apps.size) {
                val app = apps[i]
                _session.value = _session.value.copy(
                    currentAppIndex = i,
                    currentAppName = app.appName,
                    currentPackageName = app.packageName
                )

                // Launch current app directly
                val launched = launchApp(app.packageName, app.appName)
                if (launched) {
                    repository.updateAppSwitchStatus(app.packageName, AppSwitchStatus.RUNNING)
                    repository.logEvent("Opened app ${i + 1}/${apps.size}: ${app.appName}", EventType.INFO)
                    AutomationForegroundService.update(
                        context,
                        "Running: ${app.appName}",
                        "App ${i + 1} of ${apps.size}"
                    )
                } else {
                    repository.updateAppSwitchStatus(app.packageName, AppSwitchStatus.FAILED)
                    repository.logEvent("Failed to launch ${app.appName}", EventType.WARNING)
                    _session.value = _session.value.copy(failedCount = _session.value.failedCount + 1)
                    continue
                }

                // Default 2 minutes (120s) or user configured duration
                val duration = when {
                    i == startIndex && remainingSeconds != null && remainingSeconds > 0 -> remainingSeconds
                    app.durationSeconds > 0 -> app.durationSeconds.toLong()
                    settings.universalAppTimerSeconds > 0 -> settings.universalAppTimerSeconds
                    else -> 120L
                }.coerceAtLeast(5L)

                val completedNormally = runMonotonicCountdown(duration, app.appName, i + 1, apps.size)
                if (!completedNormally) {
                    return@launch
                }

                // App timer finished
                repository.updateAppSwitchStatus(app.packageName, AppSwitchStatus.COMPLETED)
                _session.value = _session.value.copy(completedCount = _session.value.completedCount + 1)
                repository.logEvent("Timer finished for ${app.appName}. Closing and switching to next app in queue...", EventType.INFO)

                // Back out of current app and remove from background
                val service = ClickAccessibilityService.instance
                if (service != null && ClickAccessibilityService.isServiceRunning()) {
                    service.closeCurrentForegroundApp()
                } else {
                    delay(500L)
                }
            }

            // All apps completed
            completeSession()
        }
    }

    private suspend fun runMonotonicCountdown(seconds: Long, appName: String, currentIndex: Int, totalApps: Int): Boolean {
        var remaining = seconds
        val startTime = SystemClock.elapsedRealtime()
        val totalMs = seconds * 1000L
        var lastNotificationRemaining = -1L

        while (remaining > 0) {
            _session.value = _session.value.copy(remainingAppSeconds = remaining)

            // Throttled notification update every 3 seconds or when <= 5s to eliminate IPC notification lag
            if (lastNotificationRemaining == -1L || (lastNotificationRemaining - remaining >= 3) || remaining <= 5) {
                lastNotificationRemaining = remaining
                val timeDisplay = when {
                    remaining >= 3600 -> "${remaining / 3600}h ${(remaining % 3600) / 60}m"
                    remaining >= 60 -> "${remaining / 60}m ${remaining % 60}s"
                    else -> "${remaining}s"
                }
                AutomationForegroundService.update(
                    context,
                    "$appName • $timeDisplay left",
                    "Queue: App $currentIndex of $totalApps"
                )
            }

            delay(500L)
            val elapsedMs = SystemClock.elapsedRealtime() - startTime
            remaining = ((totalMs - elapsedMs) / 1000L).coerceAtLeast(0L)
            if (_session.value.isPaused || !_session.value.isRunning) {
                return false
            }
        }
        _session.value = _session.value.copy(remainingAppSeconds = 0L)
        return true
    }

    private fun launchApp(packageName: String, appName: String): Boolean {
        return try {
            // Strategy 1: Launch via AccessibilityService (exempt from background activity launch restrictions)
            val service = ClickAccessibilityService.instance
            if (service != null && ClickAccessibilityService.isServiceRunning()) {
                val ok = service.launchAppPackage(packageName)
                if (ok) {
                    Log.d("AppSwitchingEngine", "Launched $packageName via AccessibilityService")
                    return true
                }
            }

            // Strategy 2: High priority Full-Screen Intent trigger (works on background across Android 10-15)
            AutomationForegroundService.triggerAppLaunchFullScreen(context, packageName, appName)

            // Strategy 3: Standard context.startActivity
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            )
            context.startActivity(intent)
            Log.d("AppSwitchingEngine", "Launched $packageName via context.startActivity")
            true
        } catch (e: Exception) {
            Log.e("AppSwitchingEngine", "Cannot launch app $packageName", e)
            false
        }
    }

    private fun completeSession() {
        val total = _session.value.totalApps
        val completed = _session.value.completedCount
        val failed = _session.value.failedCount

        _session.value = AppSwitchSessionStatus(isRunning = false, isPaused = false)
        AutomationForegroundService.stop(context)

        val summary = "Finished $completed of $total apps${if (failed > 0) " ($failed failed)" else ""}."
        AutomationForegroundService.showCompletionNotification(context, summary)
        repository.logEvent("Open Apps queue completed: $summary", EventType.INFO)

        // Silent completion - no sound or vibration
    }

    companion object {
        @Volatile
        private var INSTANCE: AppSwitchingEngine? = null

        fun getInstance(context: Context, repository: AutomationRepository, scope: CoroutineScope): AppSwitchingEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSwitchingEngine(context.applicationContext, repository, scope).also { INSTANCE = it }
            }
        }

        fun getExistingInstance(): AppSwitchingEngine? = INSTANCE
    }
}

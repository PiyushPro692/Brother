package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ActionType
import com.example.model.AppSettings
import com.example.model.AppSwitchItem
import com.example.model.AppSwitchStatus
import com.example.model.AutomationEvent
import com.example.model.Checkpoint
import com.example.model.EventType
import com.example.model.MacroProfile
import com.example.model.TargetPoint
import com.example.model.TimerUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AutomationRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("click_assistant_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _currentMacro = MutableStateFlow(loadCurrentMacro())
    val currentMacro: StateFlow<MacroProfile> = _currentMacro.asStateFlow()

    private val _savedProfiles = MutableStateFlow(loadSavedProfiles())
    val savedProfiles: StateFlow<List<MacroProfile>> = _savedProfiles.asStateFlow()

    private val _appSwitchItems = MutableStateFlow(loadAppSwitchItems())
    val appSwitchItems: StateFlow<List<AppSwitchItem>> = _appSwitchItems.asStateFlow()

    private val _eventLogs = MutableStateFlow(loadEventLogs())
    val eventLogs: StateFlow<List<AutomationEvent>> = _eventLogs.asStateFlow()

    private val _checkpoint = MutableStateFlow(loadCheckpoint())
    val checkpoint: StateFlow<Checkpoint?> = _checkpoint.asStateFlow()

    // ---------------- Settings ----------------
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        prefs.edit().apply {
            putBoolean("detectCrossClick", newSettings.detectCrossClick)
            putBoolean("aiInterruptionDetection", newSettings.aiInterruptionDetection)
            putBoolean("autoResumeAfterInterruption", newSettings.autoResumeAfterInterruption)
            putString("resumeModeAfterInterruption", newSettings.resumeModeAfterInterruption.name)
            putBoolean("keepScreenAwake", newSettings.keepScreenAwake)
            putBoolean("soundEnabled", newSettings.soundEnabled)
            putBoolean("vibrationEnabled", newSettings.vibrationEnabled)
            putLong("doubleBackIntervalMs", newSettings.doubleBackIntervalMs)
            putInt("maxRetries", newSettings.maxRetries)
            putFloat("overlayOpacity", newSettings.overlayOpacity)
            putLong("sessionTimerDuration", newSettings.sessionTimerDuration)
            putString("sessionTimerUnit", newSettings.sessionTimerUnit.name)
            putLong("gestureDelayMs", newSettings.gestureDelayMs)
            putInt("gestureRepetitions", newSettings.gestureRepetitions)
            putLong("touchAndHoldDelayMs", newSettings.touchAndHoldDelayMs)
            putLong("swipeDurationMs", newSettings.swipeDurationMs)
            putInt("pinchDurationSeconds", newSettings.pinchDurationSeconds)
            putLong("scriptLoopDelayMs", newSettings.scriptLoopDelayMs)
            putString("stopScriptAfterOption", newSettings.stopScriptAfterOption)
            putBoolean("turboMode", newSettings.turboMode)
            putLong("universalAppTimerSeconds", newSettings.universalAppTimerSeconds)
            apply()
        }
    }

    private fun loadSettings(): AppSettings {
        return AppSettings(
            detectCrossClick = prefs.getBoolean("detectCrossClick", true),
            aiInterruptionDetection = prefs.getBoolean("aiInterruptionDetection", true),
            autoResumeAfterInterruption = prefs.getBoolean("autoResumeAfterInterruption", true),
            resumeModeAfterInterruption = try {
                com.example.model.AdResumeMode.valueOf(prefs.getString("resumeModeAfterInterruption", com.example.model.AdResumeMode.CONTINUE_FROM_LEFT.name) ?: com.example.model.AdResumeMode.CONTINUE_FROM_LEFT.name)
            } catch (e: Exception) {
                com.example.model.AdResumeMode.CONTINUE_FROM_LEFT
            },
            keepScreenAwake = prefs.getBoolean("keepScreenAwake", true),
            soundEnabled = prefs.getBoolean("soundEnabled", true),
            vibrationEnabled = prefs.getBoolean("vibrationEnabled", true),
            doubleBackIntervalMs = prefs.getLong("doubleBackIntervalMs", 1800L),
            maxRetries = prefs.getInt("maxRetries", 3),
            overlayOpacity = prefs.getFloat("overlayOpacity", 0.95f),
            sessionTimerDuration = prefs.getLong("sessionTimerDuration", 60L),
            sessionTimerUnit = try {
                TimerUnit.valueOf(prefs.getString("sessionTimerUnit", TimerUnit.SECONDS.name) ?: TimerUnit.SECONDS.name)
            } catch (e: Exception) {
                TimerUnit.SECONDS
            },
            gestureDelayMs = prefs.getLong("gestureDelayMs", 10L),
            gestureRepetitions = prefs.getInt("gestureRepetitions", 1),
            touchAndHoldDelayMs = prefs.getLong("touchAndHoldDelayMs", 50L),
            swipeDurationMs = prefs.getLong("swipeDurationMs", 300L),
            pinchDurationSeconds = prefs.getInt("pinchDurationSeconds", 3),
            scriptLoopDelayMs = prefs.getLong("scriptLoopDelayMs", 0L),
            stopScriptAfterOption = prefs.getString("stopScriptAfterOption", "Until the stop button is pressed") ?: "Until the stop button is pressed",
            turboMode = prefs.getBoolean("turboMode", false),
            universalAppTimerSeconds = prefs.getLong("universalAppTimerSeconds", 120L)
        )
    }

    // ---------------- Active Macro ----------------
    fun updateCurrentMacro(macro: MacroProfile) {
        _currentMacro.value = macro
        saveMacroToFile("current_macro.json", macro)
    }

    fun addTarget(point: TargetPoint) {
        val updatedTargets = _currentMacro.value.targets.toMutableList().apply { add(point) }
        updateCurrentMacro(_currentMacro.value.copy(targets = updatedTargets, updatedAt = System.currentTimeMillis()))
    }

    fun removeTarget(targetId: String) {
        val filtered = _currentMacro.value.targets.filterNot { it.id == targetId }
        val renumbered = filtered.mapIndexed { index, targetPoint ->
            targetPoint.copy(number = index + 1)
        }
        updateCurrentMacro(_currentMacro.value.copy(targets = renumbered, updatedAt = System.currentTimeMillis()))
    }

    fun clearTargets() {
        updateCurrentMacro(_currentMacro.value.copy(targets = emptyList(), updatedAt = System.currentTimeMillis()))
    }

    fun updateTarget(updated: TargetPoint) {
        val targets = _currentMacro.value.targets.map {
            if (it.id == updated.id) updated else it
        }
        updateCurrentMacro(_currentMacro.value.copy(targets = targets, updatedAt = System.currentTimeMillis()))
    }

    fun updateMacroLoopSettings(loopCount: Int, delayBetweenLoopsMs: Long, sessionDurationSeconds: Long = 0L) {
        val updated = _currentMacro.value.copy(
            loopCount = loopCount,
            delayBetweenLoopsMs = delayBetweenLoopsMs,
            sessionDurationSeconds = sessionDurationSeconds,
            updatedAt = System.currentTimeMillis()
        )
        updateCurrentMacro(updated)
        // Also sync with AppSettings scriptLoopDelayMs
        updateSettings(_settings.value.copy(scriptLoopDelayMs = delayBetweenLoopsMs))
        logEvent("Updated loop settings: ${if (loopCount == 0) "Infinite" else "$loopCount times"}, delay: ${delayBetweenLoopsMs}ms", EventType.INFO)
    }

    // ---------------- Profile Management ----------------
    fun saveProfile(profileName: String): MacroProfile {
        val profile = _currentMacro.value.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = profileName.trim(),
            updatedAt = System.currentTimeMillis()
        )
        val currentList = _savedProfiles.value.filterNot { it.name.equals(profileName.trim(), ignoreCase = true) }
        val updated = currentList + profile
        _savedProfiles.value = updated
        saveProfilesToFile(updated)
        logEvent("Saved macro profile '$profileName' with ${profile.targets.size} actions", EventType.INFO)
        return profile
    }

    fun updateProfile(profileId: String): Boolean {
        val existing = _savedProfiles.value.find { it.id == profileId } ?: return false
        val updated = existing.copy(
            targets = _currentMacro.value.targets,
            loopCount = _currentMacro.value.loopCount,
            delayBetweenLoopsMs = _currentMacro.value.delayBetweenLoopsMs,
            sessionDurationSeconds = _currentMacro.value.sessionDurationSeconds,
            updatedAt = System.currentTimeMillis()
        )
        val newList = _savedProfiles.value.map { if (it.id == profileId) updated else it }
        _savedProfiles.value = newList
        saveProfilesToFile(newList)
        logEvent("Updated macro profile '${existing.name}'", EventType.INFO)
        return true
    }

    fun loadProfile(profileId: String): Boolean {
        val profile = _savedProfiles.value.find { it.id == profileId } ?: return false
        _currentMacro.value = profile
        saveMacroToFile("current_macro.json", profile)
        logEvent("Loaded profile '${profile.name}' with ${profile.targets.size} actions", EventType.INFO)
        return true
    }

    fun deleteProfile(profileId: String): Boolean {
        val profile = _savedProfiles.value.find { it.id == profileId } ?: return false
        val updated = _savedProfiles.value.filterNot { it.id == profileId }
        _savedProfiles.value = updated
        saveProfilesToFile(updated)
        logEvent("Deleted macro profile '${profile.name}'", EventType.INFO)
        return true
    }

    // ---------------- App Switch Switching ----------------
    fun setAppSwitchItems(items: List<AppSwitchItem>) {
        _appSwitchItems.value = items
        saveAppSwitchToFile(items)
    }

    fun updateAppSwitchStatus(packageName: String, status: AppSwitchStatus) {
        val updated = _appSwitchItems.value.map {
            if (it.packageName == packageName) it.copy(status = status) else it
        }
        _appSwitchItems.value = updated
        saveAppSwitchToFile(updated)
    }

    fun resetAppSwitchStatuses() {
        val reset = _appSwitchItems.value.map { it.copy(status = AppSwitchStatus.PENDING) }
        _appSwitchItems.value = reset
        saveAppSwitchToFile(reset)
    }

    // ---------------- Events and Logs ----------------
    fun logEvent(message: String, type: EventType = EventType.INFO) {
        val event = AutomationEvent(message = message, type = type)
        val updated = (listOf(event) + _eventLogs.value).take(100)
        _eventLogs.value = updated
        saveEventLogsToFile(updated)
    }

    fun clearLogs() {
        _eventLogs.value = emptyList()
        File(context.filesDir, "event_logs.json").delete()
    }

    // ---------------- Checkpoints ----------------
    fun saveCheckpoint(checkpoint: Checkpoint) {
        _checkpoint.value = checkpoint
        val json = JSONObject().apply {
            put("macroId", checkpoint.macroId)
            put("stepIndex", checkpoint.stepIndex)
            put("loopIndex", checkpoint.loopIndex)
            put("remainingDelayMs", checkpoint.remainingDelayMs)
            put("remainingSessionSeconds", checkpoint.remainingSessionSeconds)
            put("timestamp", checkpoint.timestamp)
        }
        prefs.edit().putString("last_checkpoint", json.toString()).apply()
    }

    fun clearCheckpoint() {
        _checkpoint.value = null
        prefs.edit().remove("last_checkpoint").apply()
    }

    private fun loadCheckpoint(): Checkpoint? {
        val str = prefs.getString("last_checkpoint", null) ?: return null
        return try {
            val json = JSONObject(str)
            Checkpoint(
                macroId = json.getString("macroId"),
                stepIndex = json.getInt("stepIndex"),
                loopIndex = json.getInt("loopIndex"),
                remainingDelayMs = json.getLong("remainingDelayMs"),
                remainingSessionSeconds = json.getLong("remainingSessionSeconds"),
                timestamp = json.getLong("timestamp")
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---------------- JSON Disk Serialization ----------------
    private fun saveMacroToFile(fileName: String, macro: MacroProfile) {
        try {
            val file = File(context.filesDir, fileName)
            file.writeText(serializeMacro(macro).toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadCurrentMacro(): MacroProfile {
        val file = File(context.filesDir, "current_macro.json")
        if (!file.exists()) {
            return MacroProfile(
                name = "Macro 1",
                targets = listOf(
                    TargetPoint(number = 1, xRatio = 0.5f, yRatio = 0.45f, delayMs = 500L, pressDurationMs = 50L),
                    TargetPoint(number = 2, xRatio = 0.5f, yRatio = 0.65f, delayMs = 1000L, pressDurationMs = 50L)
                )
            )
        }
        return try {
            deserializeMacro(JSONObject(file.readText()))
        } catch (e: Exception) {
            MacroProfile(name = "Macro 1")
        }
    }

    private fun saveProfilesToFile(profiles: List<MacroProfile>) {
        try {
            val arr = JSONArray()
            profiles.forEach { arr.put(serializeMacro(it)) }
            File(context.filesDir, "saved_profiles.json").writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadSavedProfiles(): List<MacroProfile> {
        val file = File(context.filesDir, "saved_profiles.json")
        if (!file.exists()) {
            return listOf(
                MacroProfile(
                    name = "Quick Tap 2x",
                    targets = listOf(
                        TargetPoint(number = 1, xRatio = 0.5f, yRatio = 0.5f, delayMs = 300L),
                        TargetPoint(number = 2, xRatio = 0.5f, yRatio = 0.6f, delayMs = 500L)
                    )
                ),
                MacroProfile(
                    name = "Swipe & Refresh",
                    targets = listOf(
                        TargetPoint(number = 1, type = ActionType.SWIPE, xRatio = 0.5f, yRatio = 0.7f, endXRatio = 0.5f, endYRatio = 0.3f, swipeDurationMs = 400L, delayMs = 1000L)
                    )
                )
            )
        }
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<MacroProfile>()
            for (i in 0 until arr.length()) {
                list.add(deserializeMacro(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAppSwitchToFile(items: List<AppSwitchItem>) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("packageName", item.packageName)
                    put("appName", item.appName)
                    put("durationSeconds", item.durationSeconds)
                    put("order", item.order)
                    put("isSelected", item.isSelected)
                    put("status", item.status.name)
                }
                arr.put(obj)
            }
            File(context.filesDir, "app_switch_items.json").writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAppSwitchItems(): List<AppSwitchItem> {
        val file = File(context.filesDir, "app_switch_items.json")
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<AppSwitchItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AppSwitchItem(
                        packageName = obj.getString("packageName"),
                        appName = obj.getString("appName"),
                        durationSeconds = obj.optInt("durationSeconds", 120),
                        order = obj.optInt("order", i),
                        isSelected = obj.optBoolean("isSelected", true),
                        status = try {
                            AppSwitchStatus.valueOf(obj.optString("status", AppSwitchStatus.PENDING.name))
                        } catch (e: Exception) {
                            AppSwitchStatus.PENDING
                        }
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveEventLogsToFile(logs: List<AutomationEvent>) {
        try {
            val arr = JSONArray()
            logs.take(50).forEach { log ->
                val obj = JSONObject().apply {
                    put("id", log.id)
                    put("timestamp", log.timestamp)
                    put("message", log.message)
                    put("type", log.type.name)
                }
                arr.put(obj)
            }
            File(context.filesDir, "event_logs.json").writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadEventLogs(): List<AutomationEvent> {
        val file = File(context.filesDir, "event_logs.json")
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            val list = mutableListOf<AutomationEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    AutomationEvent(
                        id = obj.getString("id"),
                        timestamp = obj.getLong("timestamp"),
                        message = obj.getString("message"),
                        type = try {
                            EventType.valueOf(obj.getString("type"))
                        } catch (e: Exception) {
                            EventType.INFO
                        }
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeMacro(macro: MacroProfile): JSONObject {
        val obj = JSONObject()
        obj.put("id", macro.id)
        obj.put("name", macro.name)
        obj.put("loopCount", macro.loopCount)
        obj.put("delayBetweenLoopsMs", macro.delayBetweenLoopsMs)
        obj.put("sessionDurationSeconds", macro.sessionDurationSeconds)
        obj.put("createdAt", macro.createdAt)
        obj.put("updatedAt", macro.updatedAt)

        val targetsArr = JSONArray()
        macro.targets.forEach { target ->
            val tObj = JSONObject().apply {
                put("id", target.id)
                put("number", target.number)
                put("type", target.type.name)
                put("xRatio", target.xRatio.toDouble())
                put("yRatio", target.yRatio.toDouble())
                put("endXRatio", target.endXRatio.toDouble())
                put("endYRatio", target.endYRatio.toDouble())
                put("delayMs", target.delayMs)
                put("pressDurationMs", target.pressDurationMs)
                put("swipeDurationMs", target.swipeDurationMs)
            }
            targetsArr.put(tObj)
        }
        obj.put("targets", targetsArr)
        return obj
    }

    private fun deserializeMacro(json: JSONObject): MacroProfile {
        val targetsList = mutableListOf<TargetPoint>()
        val targetsArr = json.optJSONArray("targets")
        if (targetsArr != null) {
            for (i in 0 until targetsArr.length()) {
                val t = targetsArr.getJSONObject(i)
                targetsList.add(
                    TargetPoint(
                        id = t.optString("id", java.util.UUID.randomUUID().toString()),
                        number = t.optInt("number", i + 1),
                        type = try { ActionType.valueOf(t.optString("type", ActionType.TAP.name)) } catch (e: Exception) { ActionType.TAP },
                        xRatio = t.optDouble("xRatio", 0.5).toFloat(),
                        yRatio = t.optDouble("yRatio", 0.5).toFloat(),
                        endXRatio = t.optDouble("endXRatio", 0.5).toFloat(),
                        endYRatio = t.optDouble("endYRatio", 0.3).toFloat(),
                        delayMs = t.optLong("delayMs", 500L),
                        pressDurationMs = t.optLong("pressDurationMs", 50L),
                        swipeDurationMs = t.optLong("swipeDurationMs", 300L)
                    )
                )
            }
        }

        return MacroProfile(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            name = json.optString("name", "Macro Profile"),
            targets = targetsList,
            loopCount = json.optInt("loopCount", 0),
            delayBetweenLoopsMs = json.optLong("delayBetweenLoopsMs", 1000L),
            sessionDurationSeconds = json.optLong("sessionDurationSeconds", 0L),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: AutomationRepository? = null

        fun getInstance(context: Context): AutomationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutomationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

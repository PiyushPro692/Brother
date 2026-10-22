package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.data.AutomationRepository
import com.example.engine.AppSwitchingEngine
import com.example.ui.screens.OpenAppsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

enum class Screen {
    OPEN_APPS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var repository: AutomationRepository
    private lateinit var appSwitchingEngine: AppSwitchingEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        repository = AutomationRepository.getInstance(applicationContext)

        setContent {
            val scope = rememberCoroutineScope()
            remember {
                appSwitchingEngine = AppSwitchingEngine.getInstance(applicationContext, repository, scope)
            }

            val settings by repository.settings.collectAsState()
            val appSwitchItems by repository.appSwitchItems.collectAsState()
            val eventLogs by repository.eventLogs.collectAsState()
            val switchSessionStatus by appSwitchingEngine.session.collectAsState()

            var currentScreen by remember { mutableStateOf(Screen.OPEN_APPS) }

            // Back button handling: smoothly minimizes to background so app queue continues running
            BackHandler {
                if (currentScreen != Screen.OPEN_APPS) {
                    currentScreen = Screen.OPEN_APPS
                } else {
                    moveTaskToBack(true)
                }
            }

            MyApplicationTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.OPEN_APPS -> {
                            OpenAppsScreen(
                                appSwitchItems = appSwitchItems,
                                settings = settings,
                                sessionStatus = switchSessionStatus,
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onUpdateItems = { repository.setAppSwitchItems(it) },
                                onUpdateSettings = { repository.updateSettings(it) },
                                onStartSession = {
                                    appSwitchingEngine.startSession()
                                },
                                onPauseSession = { appSwitchingEngine.pauseSession() },
                                onResumeSession = { appSwitchingEngine.resumeSession() },
                                onSkipCurrent = { appSwitchingEngine.skipCurrentApp() },
                                onStopSession = { appSwitchingEngine.stopSession() }
                            )
                        }

                        Screen.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                eventLogs = eventLogs,
                                onBack = { currentScreen = Screen.OPEN_APPS },
                                onUpdateSettings = { repository.updateSettings(it) },
                                onClearLogs = { repository.clearLogs() }
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.platform.LocalContext
import com.example.service.ClickAccessibilityService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppSettings
import com.example.model.AutomationEvent
import com.example.model.EventType
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    eventLogs: List<AutomationEvent>,
    onBack: () -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onClearLogs: () -> Unit
) {
    var showLogsDialog by remember { mutableStateOf(false) }
    var doubleBackSlider by remember(settings.doubleBackIntervalMs) {
        mutableFloatStateOf(settings.doubleBackIntervalMs.toFloat())
    }
    var maxRetriesValue by remember(settings.maxRetries) {
        mutableIntStateOf(settings.maxRetries)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sound and Vibration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Feedback & Audio", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = PrimaryCyan)
                            Text("Completion Sound", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = settings.soundEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(soundEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan, checkedTrackColor = Color(0xFF004D63))
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = PrimaryCyan)
                            Text("Haptic Vibration", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = settings.vibrationEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(vibrationEnabled = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryCyan, checkedTrackColor = Color(0xFF004D63))
                        )
                    }
                }
            }

            // Emergency Exit & Controlled Recovery
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Safety & Recovery Controls", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Double-Back Emergency Interval", color = Color.White, fontSize = 14.sp)
                            Text("${(doubleBackSlider / 1000).toString().take(3)}s", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                        }
                        Text("Double-tapping the Android Back key within this interval instantly halts gestures and cleans up the overlay.", fontSize = 12.sp, color = Color.Gray)
                        Slider(
                            value = doubleBackSlider,
                            onValueChange = {
                                doubleBackSlider = it
                                onUpdateSettings(settings.copy(doubleBackIntervalMs = it.toLong()))
                            },
                            valueRange = 800f..3000f,
                            colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                        )
                    }

                    HorizontalDivider(color = DarkSurfaceVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Max Recovery Retries", color = Color.White, fontSize = 14.sp)
                            Text("Times to retry an unfulfilled target before pausing playback.", fontSize = 12.sp, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (maxRetriesValue > 1) {
                                        maxRetriesValue--
                                        onUpdateSettings(settings.copy(maxRetries = maxRetriesValue))
                                    }
                                },
                                modifier = Modifier.height(34.dp)
                            ) { Text("-", color = Color.White) }
                            Text("$maxRetriesValue", fontWeight = FontWeight.Bold, color = PrimaryCyan, fontSize = 15.sp)
                            OutlinedButton(
                                onClick = {
                                    if (maxRetriesValue < 10) {
                                        maxRetriesValue++
                                        onUpdateSettings(settings.copy(maxRetries = maxRetriesValue))
                                    }
                                },
                                modifier = Modifier.height(34.dp)
                            ) { Text("+", color = Color.White) }
                        }
                    }
                }
            }

            // Event Log & Diagnostic Viewer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Diagnostics & Event Logs", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Text("${eventLogs.size} events", fontSize = 12.sp, color = PrimaryCyan)
                    }

                    Text("View real-time event logs of gestures, AI ad detection triggers, checkpoints, and recovery retries.", fontSize = 12.sp, color = Color.Gray)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showLogsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = PrimaryCyan)
                            Text("View Log", color = Color.White, modifier = Modifier.padding(start = 6.dp))
                        }

                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRose)
                            Text("Clear", color = AccentRose, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        AlertDialog(
            onDismissRequest = { showLogsDialog = false },
            title = { Text("Event Logs", fontWeight = FontWeight.Bold, color = PrimaryCyan) },
            text = {
                val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                if (eventLogs.isEmpty()) {
                    Text("No events recorded yet.", color = Color.LightGray)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(eventLogs) { event ->
                            val chipColor = when (event.type) {
                                EventType.INFO -> Color(0xFF38BDF8)
                                EventType.WARNING -> AccentAmber
                                EventType.ERROR -> AccentRose
                                EventType.PAUSE -> Color(0xFFA855F7)
                                EventType.RECOVERY -> AccentEmerald
                                EventType.AI -> PrimaryCyan
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(event.type.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chipColor)
                                        Text(dateFormat.format(Date(event.timestamp)), fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Text(event.message, fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogsDialog = false }) {
                    Text("Close", color = PrimaryCyan)
                }
            }
        )
    }
}

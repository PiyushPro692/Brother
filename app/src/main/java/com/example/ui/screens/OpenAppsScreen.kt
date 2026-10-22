package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import com.example.service.ClickAccessibilityService
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.AppSwitchSessionStatus
import com.example.model.AppSettings
import com.example.model.AppSwitchItem
import com.example.model.AppSwitchStatus
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentRose
import com.example.ui.theme.DarkCardBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatAppSeconds(seconds: Long): String {
    return when {
        seconds >= 3600 -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        }
        seconds >= 60 -> {
            val m = seconds / 60
            val s = seconds % 60
            if (s > 0) "${m}m ${s}s" else "${m}m"
        }
        else -> "${seconds}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenAppsScreen(
    appSwitchItems: List<AppSwitchItem>,
    settings: AppSettings,
    sessionStatus: AppSwitchSessionStatus,
    onNavigateToSettings: () -> Unit,
    onUpdateItems: (List<AppSwitchItem>) -> Unit,
    onUpdateSettings: (AppSettings) -> Unit,
    onStartSession: () -> Unit,
    onPauseSession: () -> Unit,
    onResumeSession: () -> Unit,
    onSkipCurrent: () -> Unit,
    onStopSession: () -> Unit
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    var showUniversalTimerDialog by remember { mutableStateOf(false) }
    var editingAppItem by remember { mutableStateOf<AppSwitchItem?>(null) }

    // Pre-populate with installed apps if list is empty (default 2 minutes = 120s per app)
    LaunchedEffect(Unit) {
        if (appSwitchItems.isEmpty()) {
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolves = pm.queryIntentActivities(intent, 0)
                val apps = resolves.take(6).mapIndexed { i, res ->
                    AppSwitchItem(
                        packageName = res.activityInfo.packageName,
                        appName = res.loadLabel(pm).toString(),
                        durationSeconds = 120, // 2 minutes default
                        order = i,
                        isSelected = i < 3
                    )
                }
                if (apps.isNotEmpty()) {
                    onUpdateItems(apps)
                }
            }
        }
    }

    val selectedCount by remember(appSwitchItems) {
        derivedStateOf { appSwitchItems.count { it.isSelected } }
    }

    val totalEstimatedSeconds by remember(appSwitchItems) {
        derivedStateOf {
            appSwitchItems.filter { it.isSelected }.sumOf { it.durationSeconds.toLong() }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isAccessibilityActive by remember {
        mutableStateOf(ClickAccessibilityService.isServiceRunning() || ClickAccessibilityService.isAccessibilitySettingsEnabled(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityActive = ClickAccessibilityService.isServiceRunning() ||
                        ClickAccessibilityService.isAccessibilitySettingsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val current = ClickAccessibilityService.isServiceRunning() ||
                    ClickAccessibilityService.isAccessibilitySettingsEnabled(context)
            if (isAccessibilityActive != current) {
                isAccessibilityActive = current
            }
            kotlinx.coroutines.delay(1200L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "App Switcher",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryCyan.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan)
                            ) {
                                Text(
                                    "2 MIN",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = PrimaryCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Text(
                            "$selectedCount apps in queue • Total ~${formatAppSeconds(totalEstimatedSeconds)}",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    // Accessibility Setup Button
                    IconButton(
                        onClick = {
                            ClickAccessibilityService.openAccessibilitySettings(context)
                            Toast.makeText(context, "Turn on Click Assistant AI in Accessibility Services", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.testTag("top_bar_accessibility_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Accessibility Setup",
                            tint = if (isAccessibilityActive) AccentEmerald else Color(0xFFF59E0B)
                        )
                    }

                    // Quick Add button
                    IconButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.testTag("top_bar_add_apps_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Apps", tint = PrimaryCyan)
                    }

                    // Settings
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("top_bar_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated Accessibility Status / Setup Banner
                item(key = "accessibility_banner") {
                    if (!isAccessibilityActive) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Enable Accessibility for Auto-Switch",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "To automatically close open apps and redirect to the next app when the 2-minute timer ends, turn on Click Assistant AI in Accessibility Settings.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = {
                                        ClickAccessibilityService.openAccessibilitySettings(context)
                                        Toast.makeText(context, "Select 'Click Assistant AI' in Downloaded Services and enable it", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("enable_accessibility_button")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Enable Accessibility in Settings", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentEmerald.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AccentEmerald.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { ClickAccessibilityService.openAccessibilitySettings(context) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Accessibility Active • Auto-Close & Next App Ready",
                                        color = AccentEmerald,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text("Active", color = AccentEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // Active Session Progress Banner (when running)
                if (sessionStatus.isRunning) {
                    item(key = "active_session_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .border(1.dp, PrimaryCyan, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (sessionStatus.isPaused) Color(0xFFF59E0B) else AccentEmerald)
                                        )
                                        Text(
                                            text = if (sessionStatus.isPaused) "Queue Paused" else "App Open On Screen",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Text(
                                        text = "App ${sessionStatus.currentAppIndex + 1} of ${sessionStatus.totalApps}",
                                        fontSize = 12.sp,
                                        color = PrimaryCyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = sessionStatus.currentAppName,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatAppSeconds(sessionStatus.remainingAppSeconds)} remaining",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Will close and open next app",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = {
                                        val remaining = sessionStatus.remainingAppSeconds.toFloat()
                                        (remaining / 120f).coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = PrimaryCyan,
                                    trackColor = DarkSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Controls Bar (Select All & Timer config)
                item(key = "header_controls") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
                        color = DarkSurface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clickable {
                                    val selectAll = selectedCount != appSwitchItems.size
                                    onUpdateItems(appSwitchItems.map { it.copy(isSelected = selectAll) })
                                }
                            ) {
                                Checkbox(
                                    checked = selectedCount == appSwitchItems.size && appSwitchItems.isNotEmpty(),
                                    onCheckedChange = { check ->
                                        onUpdateItems(appSwitchItems.map { it.copy(isSelected = check) })
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = PrimaryCyan,
                                        checkmarkColor = Color.Black,
                                        uncheckedColor = Color.Gray
                                    )
                                )
                                Text(
                                    text = if (selectedCount == appSwitchItems.size) "All Selected" else "Select All",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Universal Timer Chip
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                modifier = Modifier.clickable { showUniversalTimerDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "${formatAppSeconds(settings.universalAppTimerSeconds)}/app",
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Add Apps Button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = PrimaryCyan.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { showAppPicker = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                                    Text("Add App", color = PrimaryCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Empty queue state
                if (appSwitchItems.isEmpty()) {
                    item(key = "empty_queue") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(48.dp))
                                Text("No Apps in Queue", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Tap below to pick installed apps to switch automatically.", fontSize = 13.sp, color = Color(0xFF94A3B8))
                                Button(
                                    onClick = { showAppPicker = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Apps", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // App Queue Items
                itemsIndexed(
                    items = appSwitchItems,
                    key = { _, item -> item.packageName }
                ) { index, item ->
                    val isCurrent = sessionStatus.isRunning && sessionStatus.currentPackageName == item.packageName

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.dp,
                                if (isCurrent) PrimaryCyan else DarkCardBorder,
                                RoundedCornerShape(14.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { check ->
                                    val updated = appSwitchItems.mapIndexed { i, itm ->
                                        if (i == index) itm.copy(isSelected = check) else itm
                                    }
                                    onUpdateItems(updated)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PrimaryCyan,
                                    checkmarkColor = Color.Black,
                                    uncheckedColor = Color.Gray
                                )
                            )

                            // Step Number
                            Surface(
                                shape = CircleShape,
                                color = if (item.isSelected) PrimaryCyan.copy(alpha = 0.2f) else DarkSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (item.isSelected) PrimaryCyan else Color.Gray
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // App Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.appName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (item.isSelected) Color.White else Color.Gray
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Duration chip (clickable to adjust)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = DarkSurfaceVariant,
                                        modifier = Modifier.clickable { editingAppItem = item }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(Icons.Default.Timer, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(12.dp))
                                            Text(
                                                text = formatAppSeconds(item.durationSeconds.toLong()),
                                                fontSize = 11.sp,
                                                color = PrimaryCyan,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    if (isCurrent) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = AccentEmerald.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontSize = 10.sp,
                                                color = AccentEmerald,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else if (item.status == AppSwitchStatus.COMPLETED) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DarkSurfaceVariant
                                        ) {
                                            Text(
                                                text = "DONE",
                                                fontSize = 10.sp,
                                                color = AccentEmerald,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Up / Down reorder arrows
                            Column {
                                if (index > 0) {
                                    IconButton(
                                        onClick = {
                                            val list = appSwitchItems.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index - 1]
                                            list[index - 1] = temp
                                            onUpdateItems(list)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropUp, contentDescription = "Move Up", tint = Color.Gray)
                                    }
                                }
                                if (index < appSwitchItems.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val list = appSwitchItems.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index + 1]
                                            list[index + 1] = temp
                                            onUpdateItems(list)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Move Down", tint = Color.Gray)
                                    }
                                }
                            }

                            // Delete
                            IconButton(
                                onClick = {
                                    onUpdateItems(appSwitchItems.filterIndexed { i, _ -> i != index })
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Bottom Action Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .shadow(16.dp),
                color = DarkSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sessionStatus.isRunning) {
                        Button(
                            onClick = if (sessionStatus.isPaused) onResumeSession else onPauseSession,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (sessionStatus.isPaused) PrimaryCyan else Color(0xFFF59E0B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = if (sessionStatus.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (sessionStatus.isPaused) "Resume" else "Pause",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }

                        Button(
                            onClick = onSkipCurrent,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.FastForward, contentDescription = "Skip App", tint = Color.White)
                        }

                        Button(
                            onClick = onStopSession,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                if (selectedCount == 0) {
                                    Toast.makeText(context, "Select at least one app to start", Toast.LENGTH_SHORT).show()
                                } else {
                                    onStartSession()
                                    Toast.makeText(context, "Queue started! Opening first app...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("start_queue_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Start App Queue ($selectedCount Apps)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }
    }

    // App Picker Dialog
    if (showAppPicker) {
        InstalledAppsPickerModal(
            currentSelected = appSwitchItems,
            onDismiss = { showAppPicker = false },
            onConfirmSelection = { selected ->
                // Ensure 2 min default for newly selected items if not set
                val formatted = selected.map {
                    if (it.durationSeconds <= 0) it.copy(durationSeconds = 120) else it
                }
                onUpdateItems(formatted)
                showAppPicker = false
            }
        )
    }

    // Universal Timer Dialog
    if (showUniversalTimerDialog) {
        var timerVal by remember { mutableStateOf(settings.universalAppTimerSeconds.coerceAtLeast(10L)) }

        AlertDialog(
            onDismissRequest = { showUniversalTimerDialog = false },
            title = { Text("Duration for All Apps", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "How long each app stays open before the next app is brought forward:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "${formatAppSeconds(timerVal)} (${timerVal}s)",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan
                    )
                    Slider(
                        value = timerVal.toFloat(),
                        onValueChange = { timerVal = it.toLong() },
                        valueRange = 10f..300f,
                        steps = 29,
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30L, 60L, 120L, 180L, 300L).forEach { sec ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (timerVal == sec) PrimaryCyan else DarkSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { timerVal = sec }
                            ) {
                                Text(
                                    text = if (sec >= 60) "${sec / 60}m" else "${sec}s",
                                    color = if (timerVal == sec) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings(settings.copy(universalAppTimerSeconds = timerVal))
                        onUpdateItems(appSwitchItems.map { it.copy(durationSeconds = timerVal.toInt()) })
                        showUniversalTimerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Apply to All", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUniversalTimerDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Edit Single App Timer Dialog
    editingAppItem?.let { appItem ->
        var inputSec by remember { mutableStateOf(appItem.durationSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { editingAppItem = null },
            title = { Text("Timer for ${appItem.appName}", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Seconds before closing and switching to the next app:",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = inputSec,
                        onValueChange = { inputSec = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (seconds)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(30, 60, 120, 180, 240).forEach { sec ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkSurfaceVariant,
                                modifier = Modifier.clickable { inputSec = sec.toString() }
                            ) {
                                Text(
                                    text = if (sec >= 60) "${sec / 60}m" else "${sec}s",
                                    color = PrimaryCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = inputSec.toIntOrNull()?.coerceAtLeast(5) ?: 120
                        onUpdateItems(appSwitchItems.map {
                            if (it.packageName == appItem.packageName) it.copy(durationSeconds = parsed) else it
                        })
                        editingAppItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAppItem = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = DarkSurface
        )
    }
}

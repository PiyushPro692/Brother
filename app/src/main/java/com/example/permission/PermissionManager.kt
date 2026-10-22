package com.example.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.ClickAccessibilityService

data class PermissionItem(
    val id: String,
    val title: String,
    val description: String,
    val privacyNote: String,
    val isRequired: Boolean,
    val isGranted: Boolean,
    val statusLabel: String
)

data class PermissionDashboardState(
    val accessibility: PermissionItem,
    val overlay: PermissionItem,
    val notifications: PermissionItem,
    val foregroundService: PermissionItem,
    val batteryOptimization: PermissionItem,
    val totalRequired: Int,
    val grantedRequired: Int,
    val allRequiredGranted: Boolean
)

object PermissionManager {

    fun checkDashboard(context: Context): PermissionDashboardState {
        val isAccess = ClickAccessibilityService.isAccessibilitySettingsEnabled(context)
        val isOverlay = Settings.canDrawOverlays(context)
        val isNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val accessItem = PermissionItem(
            id = "accessibility",
            title = "Accessibility Service",
            description = "Dispatches automated taps and gestures onto the screen at your designated targets, and detects on-screen ad popups.",
            privacyNote = "No personal data, passwords, or typed inputs are recorded or transmitted off-device.",
            isRequired = true,
            isGranted = isAccess,
            statusLabel = if (isAccess) "Active" else "Action Required"
        )

        val overlayItem = PermissionItem(
            id = "overlay",
            title = "Display Over Other Apps",
            description = "Draws the floating macro toolbar, numbered target markers, and active session countdown over games and other apps.",
            privacyNote = "The overlay is only shown when you explicitly start it and closes instantly on double-back.",
            isRequired = true,
            isGranted = isOverlay,
            statusLabel = if (isOverlay) "Granted" else "Action Required"
        )

        val notifItem = PermissionItem(
            id = "notifications",
            title = "Notifications & Alerts",
            description = "Displays persistent session countdown, notification shade Emergency Stop button, and 'Work completed' chime alert.",
            privacyNote = "Only delivers local status notifications for running automation sessions.",
            isRequired = false,
            isGranted = isNotif,
            statusLabel = if (isNotif) "Allowed" else "Recommended"
        )

        val fgsItem = PermissionItem(
            id = "foreground_service",
            title = "Foreground Service",
            description = "Maintains execution priority so the Android operating system does not freeze automation during app switching.",
            privacyNote = "Operating with declared SPECIAL_USE foreground service type.",
            isRequired = true,
            isGranted = true, // Declared in Manifest
            statusLabel = "Configured"
        )

        val batteryItem = PermissionItem(
            id = "battery",
            title = "Unrestricted Battery Usage",
            description = "Exempts Click Assistant AI from OEM battery killing mechanisms during extended automation loops.",
            privacyNote = "Recommended for unattended automation lasting longer than 10 minutes.",
            isRequired = false,
            isGranted = isBattery,
            statusLabel = if (isBattery) "Unrestricted ON" else "Optimized"
        )

        val requiredList = listOf(accessItem, overlayItem, fgsItem)
        val grantedRequiredCount = requiredList.count { it.isGranted }

        return PermissionDashboardState(
            accessibility = accessItem,
            overlay = overlayItem,
            notifications = notifItem,
            foregroundService = fgsItem,
            batteryOptimization = batteryItem,
            totalRequired = requiredList.size,
            grantedRequired = grantedRequiredCount,
            allRequiredGranted = grantedRequiredCount == requiredList.size
        )
    }

    /**
     * Checks if all required automation permissions are currently valid.
     * Returns a list of human-readable missing permission names if any have been revoked.
     */
    fun getMissingRequiredPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        if (!ClickAccessibilityService.isAccessibilitySettingsEnabled(context)) {
            missing.add("Accessibility Service")
        }
        if (!Settings.canDrawOverlays(context)) {
            missing.add("Display Over Other Apps (Floating Overlay)")
        }
        return missing
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openNotificationSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}

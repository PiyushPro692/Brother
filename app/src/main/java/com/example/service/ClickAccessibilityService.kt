package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * AccessibilityService class dedicated to:
 * 1. Automated app switching and background activity launching
 * 2. Background closure of foreground apps (via sequential back/home actions)
 * 3. System privileges to bypass Android Background Activity Launch (BAL) restrictions
 */
class ClickAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service connected for automated app switching")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active window event receiver
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Gracefully backs out of the currently running foreground app
     * so it is closed and removed from the active screen.
     */
    suspend fun closeCurrentForegroundApp() {
        withContext(Dispatchers.Main) {
            try {
                // Step 1: First back press to dismiss any active modals or keyboards
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(250L)
                // Step 2: Second back press to exit the active activity
                performGlobalAction(GLOBAL_ACTION_BACK)
                delay(250L)
                // Step 3: Home action to ensure background state is cleanly reset to desktop
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(400L)
            } catch (e: Exception) {
                Log.e(TAG, "Error performing back navigation", e)
            }
        }
    }

    /**
     * Launches the target app package with AccessibilityService privileges,
     * which reliably bypasses Android Background Activity Launch (BAL) restrictions.
     */
    fun launchAppPackage(packageName: String): Boolean {
        return try {
            val pm = packageManager
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            startActivity(intent)
            Log.d(TAG, "Successfully launched $packageName from AccessibilityService")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            false
        }
    }

    companion object {
        private const val TAG = "ClickAccessibility"

        @Volatile
        var instance: ClickAccessibilityService? = null
            private set

        fun isServiceRunning(): Boolean = instance != null

        /**
         * Checks both the framework AccessibilityManager and the system secure settings
         * to reliably detect if Click Assistant AI accessibility is enabled.
         */
        fun isAccessibilitySettingsEnabled(context: Context): Boolean {
            if (instance != null) return true

            // Framework AccessibilityManager query
            try {
                val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                if (enabledServices != null) {
                    for (serviceInfo in enabledServices) {
                        val sInfo = serviceInfo.resolveInfo?.serviceInfo
                        if (sInfo != null && sInfo.packageName == context.packageName) {
                            if (sInfo.name.contains("ClickAccessibilityService")) {
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking AccessibilityManager", e)
            }

            // Secure settings string query
            try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(enabledServices)

                val targetPkg = context.packageName
                while (colonSplitter.hasNext()) {
                    val componentName = colonSplitter.next()
                    if (componentName.contains(targetPkg, ignoreCase = true) &&
                        componentName.contains("ClickAccessibilityService", ignoreCase = true)) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking secure settings", e)
            }

            return false
        }

        /**
         * Direct intent to the Android Accessibility Settings screen
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot open accessibility settings", e)
            }
        }
    }
}

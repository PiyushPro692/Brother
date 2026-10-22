package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class AutomationForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopAutomationBroadcast()
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Click Assistant AI Active"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Automation is running..."

        val notification = buildNotification(title, message)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(title: String, message: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Switcher Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active app switching timer progress"
                setSound(null, null)
                enableVibration(false)
            }
            val switchChannel = NotificationChannel(
                SWITCH_CHANNEL_ID,
                "App Transition Trigger",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Brings the next scheduled app forward on timer completion"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(switchChannel)
        }
    }

    private fun stopAutomationBroadcast() {
        sendBroadcast(Intent(ACTION_EMERGENCY_STOP_TRIGGERED))
    }

    companion object {
        const val CHANNEL_ID = "automation_service_channel"
        const val SWITCH_CHANNEL_ID = "app_switch_high_channel"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_SWITCH_ID = 1003

        const val ACTION_START = "com.example.action.START_AUTOMATION"
        const val ACTION_STOP = "com.example.action.STOP_AUTOMATION"
        const val ACTION_UPDATE = "com.example.action.UPDATE_AUTOMATION"
        const val ACTION_EMERGENCY_STOP_TRIGGERED = "com.example.action.EMERGENCY_STOP_TRIGGERED"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"

        fun start(context: Context, title: String = "App Switcher Active", message: String = "App queue running...") {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun update(context: Context, title: String, message: String) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AutomationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Uses a Full-Screen Intent notification to bring the target app forward
         * across all Android versions even when running in the background.
         */
        fun triggerAppLaunchFullScreen(context: Context, packageName: String, appName: String): Boolean {
            return try {
                val pm = context.packageManager
                val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )

                val pendingIntent = PendingIntent.getActivity(
                    context,
                    (System.currentTimeMillis() % 100000).toInt(),
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val switchNotif = NotificationCompat.Builder(context, SWITCH_CHANNEL_ID)
                    .setContentTitle("Opening $appName")
                    .setContentText("Next scheduled app starting now...")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setSilent(true)
                    .setSound(null)
                    .setVibrate(null)
                    .setFullScreenIntent(pendingIntent, true)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                manager.notify(NOTIFICATION_SWITCH_ID, switchNotif)

                // Also trigger send
                try {
                    pendingIntent.send()
                } catch (_: Exception) {}

                // Dismiss transition notification after 1.5s
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    manager.cancel(NOTIFICATION_SWITCH_ID)
                }, 1500L)

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }

        fun showCompletionNotification(context: Context, summary: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Queue completed")
                .setContentText(summary)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)
                .setSilent(true)
                .setSound(null)
                .setVibrate(null)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            manager.notify(1002, notification)
        }
    }
}

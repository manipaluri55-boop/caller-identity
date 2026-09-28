package com.calleridentity.utility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Native Android [NotificationListenerService] implementation.
 *
 * Runs continuously in the background independently of the Activity lifecycle,
 * receiving notification post/remove events when granted system Notification Access.
 */
class NotificationMonitorService : NotificationListenerService() {

    private lateinit var processor: NotificationProcessor
    private lateinit var settings: SettingsManager

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "NotificationMonitorService onCreate")
        settings = SettingsManager.getInstance(applicationContext)
        processor = NotificationProcessor(applicationContext)
        isRunning = true
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "NotificationMonitorService: onListenerConnected - Ready to receive notification events")
        isConnected = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "NotificationMonitorService: onListenerDisconnected - Listener disconnected by system")
        isConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        try {
            // Process the notification through our isolated processor
            processor.processNotification(sbn)
        } catch (t: Throwable) {
            // Guarantee that an unexpected error processing one notification NEVER terminates the service
            Log.e(TAG, "Fatal uncaught error while processing notification in service", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        // Kept for lifecycle completeness and continuous observation
        if (sbn != null && Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Notification removed: ${sbn.packageName} id=${sbn.id}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "NotificationMonitorService onDestroy")
        isRunning = false
        isConnected = false
    }

    companion object {
        private const val TAG = "NotificationMonitor"

        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Checks whether this app has been granted Notification Access by the user.
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabledListeners.contains(context.packageName)
        }

        /**
         * Builds an explicit Intent to open the Android system Notification Listener settings.
         */
        fun createNotificationSettingsIntent(): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * Request the system to rebind the service if disconnected while permission is granted.
         */
        fun requestRebind(context: Context) {
            try {
                val componentName = ComponentName(context, NotificationMonitorService::class.java)
                requestRebind(componentName)
            } catch (e: Exception) {
                Log.d(TAG, "requestRebind not supported or failed: ${e.message}")
            }
        }
    }
}

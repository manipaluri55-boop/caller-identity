package com.calleridentity.utility

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates the core rule-evaluation, duplicate suppression,
 * and native PendingIntent execution logic for incoming status bar notifications.
 */
class NotificationProcessor(private val context: Context) {

    private val settings = SettingsManager.getInstance(context)

    // In-memory cache for duplicate suppression: key -> lastProcessedTimestamp
    private val duplicateCache = ConcurrentHashMap<String, Long>()

    /**
     * Entry point invoked from [NotificationMonitorService.onNotificationPosted].
     * Executes fully independently without crashing the host service on unexpected errors.
     */
    fun processNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return

        try {
            // STEP 1: Check master monitoring switch
            if (!settings.isMonitoringEnabled()) {
                Log.d(TAG, "Monitoring is OFF. Skipping notification from ${sbn.packageName}")
                return
            }

            // STEP 2: Filter by target package name
            val targetPackage = settings.getTargetPackage()
            val packageName = sbn.packageName ?: ""
            if (!packageName.equals(targetPackage, ignoreCase = true)) {
                // Not the target package -> return immediately without recording
                return
            }

            // Target package detected while monitoring is active!
            settings.incrementDetected()

            val notification = sbn.notification ?: run {
                recordFailure(
                    sbn = sbn,
                    title = "Unknown",
                    text = "Notification payload is null",
                    reason = "Notification payload is null"
                )
                return
            }

            // STEP 3: Extract notification text fields and combine into searchable string
            val extras: Bundle? = notification.extras
            val title = extractTitle(extras, notification)
            val bodyText = extractText(extras, notification)
            val combinedText = buildCombinedText(title, bodyText, extras)

            // STEP 4: Check for excluded phrase (case-insensitive)
            val excludedPhrase = settings.getExcludedPhrase()
            if (excludedPhrase.isNotEmpty() && combinedText.contains(excludedPhrase, ignoreCase = true)) {
                Log.i(TAG, "Notification matches excluded phrase: '$excludedPhrase'. Ignoring.")
                recordIgnored(
                    sbn = sbn,
                    title = title,
                    text = bodyText,
                    reason = "Excluded notification"
                )
                return
            }

            // Duplicate Protection Check
            if (settings.isDuplicateProtectionEnabled()) {
                val dedupKey = buildDeduplicationKey(sbn, combinedText)
                val now = System.currentTimeMillis()
                val lastProcessed = duplicateCache[dedupKey] ?: 0L
                val cooldownMs = settings.getDuplicateCooldownMs()

                if (now - lastProcessed < cooldownMs) {
                    Log.i(TAG, "Duplicate notification detected within cooldown (${now - lastProcessed}ms < ${cooldownMs}ms). Ignoring.")
                    recordIgnored(
                        sbn = sbn,
                        title = title,
                        text = bodyText,
                        reason = "Duplicate notification within cooldown (${cooldownMs / 1000}s)"
                    )
                    return
                }

                // Update cache timestamp and clean up expired keys
                duplicateCache[dedupKey] = now
                cleanupDuplicateCache(now, cooldownMs * 2)
            }

            // ELIGIBLE NOTIFICATION: Execute notification's own native content action
            executeContentAction(sbn, notification, title, bodyText)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in processNotification", e)
            recordFailure(
                sbn = sbn,
                title = "Error",
                text = "Processing exception: ${e.message}",
                reason = "Unexpected error: ${e.javaClass.simpleName}"
            )
        }
    }

    /**
     * Executes the notification's own PendingIntent/contentIntent.
     * Uses the native Android action without any fake screen taps or accessibility workarounds.
     * If contentIntent is null, gracefully checks notification action buttons (e.g. Accept, View, Open).
     */
    private fun executeContentAction(
        sbn: StatusBarNotification,
        notification: Notification,
        title: String,
        bodyText: String
    ) {
        // Priority 1: Primary contentIntent (tapping the notification body)
        var pendingIntent: PendingIntent? = notification.contentIntent
        var intentSource = "contentIntent"

        // Priority 2: Action buttons (e.g., "Accept", "Open", "View", or first action)
        if (pendingIntent == null && notification.actions != null && notification.actions.isNotEmpty()) {
            val preferredAction = notification.actions.firstOrNull { action ->
                val label = action.title?.toString()?.lowercase() ?: ""
                label.contains("accept") || label.contains("open") || label.contains("view")
            } ?: notification.actions.firstOrNull()

            if (preferredAction?.actionIntent != null) {
                pendingIntent = preferredAction.actionIntent
                intentSource = "action: ${preferredAction.title ?: "Action"}"
            }
        }

        // Priority 3: Full-screen intent if defined
        if (pendingIntent == null && notification.fullScreenIntent != null) {
            pendingIntent = notification.fullScreenIntent
            intentSource = "fullScreenIntent"
        }

        if (pendingIntent == null) {
            Log.w(TAG, "Eligible notification has no valid content action (contentIntent & actions are null).")
            recordFailure(
                sbn = sbn,
                title = title,
                text = bodyText,
                reason = "No valid content action"
            )
            return
        }

        try {
            Log.i(TAG, "Executing notification $intentSource for package ${sbn.packageName}...")
            pendingIntent.send()
            Log.i(TAG, "$intentSource.send() executed successfully!")

            settings.incrementClicked()
            val event = NotificationEvent(
                packageName = sbn.packageName,
                notificationTitle = title.ifBlank { "Eligible Notification" },
                notificationText = bodyText,
                result = RESULT_CLICKED,
                reason = "Clicked via $intentSource"
            )
            settings.recordEvent(event)

        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "contentIntent is canceled", e)
            recordFailure(
                sbn = sbn,
                title = title,
                text = bodyText,
                reason = "Canceled PendingIntent"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException executing contentIntent", e)
            recordFailure(
                sbn = sbn,
                title = title,
                text = bodyText,
                reason = "SecurityException: ${e.message}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception executing contentIntent", e)
            recordFailure(
                sbn = sbn,
                title = title,
                text = bodyText,
                reason = "Execution error: ${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    private fun recordIgnored(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        reason: String
    ) {
        settings.incrementIgnored()
        val event = NotificationEvent(
            packageName = sbn.packageName,
            notificationTitle = title.ifBlank { "Notification" },
            notificationText = text,
            result = RESULT_IGNORED,
            reason = reason
        )
        settings.recordEvent(event)
    }

    private fun recordFailure(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        reason: String
    ) {
        settings.incrementFailed()
        val event = NotificationEvent(
            packageName = sbn.packageName ?: "unknown",
            notificationTitle = title.ifBlank { "Notification" },
            notificationText = text,
            result = RESULT_FAILED,
            reason = reason
        )
        settings.recordEvent(event)
    }

    private fun extractTitle(extras: Bundle?, notification: Notification): String {
        var title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (title.isNullOrBlank()) {
            title = extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
        }
        if (title.isNullOrBlank()) {
            title = notification.tickerText?.toString()
        }
        return title?.trim() ?: ""
    }

    private fun extractText(extras: Bundle?, notification: Notification): String {
        val parts = mutableListOf<String>()

        extras?.getCharSequence(Notification.EXTRA_TEXT)?.let { parts.add(it.toString()) }
        extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts.add(it.toString()) }
        extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { parts.add(it.toString()) }
        extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.let { parts.add(it.toString()) }

        extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { lines ->
            for (line in lines) {
                if (!line.isNullOrBlank()) parts.add(line.toString())
            }
        }

        if (parts.isEmpty() && notification.tickerText != null) {
            parts.add(notification.tickerText.toString())
        }

        return parts.distinct().joinToString(" \n ").trim()
    }

    private fun buildCombinedText(title: String, text: String, extras: Bundle?): String {
        val sb = StringBuilder()
        if (title.isNotEmpty()) sb.append(title).append(" ")
        if (text.isNotEmpty()) sb.append(text).append(" ")

        extras?.let {
            for (key in it.keySet()) {
                val value = it.get(key)
                if (value is CharSequence && value.isNotBlank()) {
                    sb.append(value).append(" ")
                }
            }
        }

        return sb.toString().trim()
    }

    private fun buildDeduplicationKey(sbn: StatusBarNotification, content: String): String {
        val rawKey = "${sbn.packageName}|${sbn.id}|${sbn.tag ?: ""}|${contentHash(content)}"
        return rawKey
    }

    private fun contentHash(content: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(content.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            content.hashCode().toString()
        }
    }

    private fun cleanupDuplicateCache(now: Long, maxAgeMs: Long) {
        if (duplicateCache.size > 200) {
            val it = duplicateCache.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (now - entry.value > maxAgeMs) {
                    it.remove()
                }
            }
        }
    }

    companion object {
        private const val TAG = "NotificationProcessor"
        const val RESULT_CLICKED = "CLICKED"
        const val RESULT_IGNORED = "IGNORED"
        const val RESULT_FAILED = "FAILED"
    }
}

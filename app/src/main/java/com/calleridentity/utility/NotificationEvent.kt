package com.calleridentity.utility

/**
 * Represents an audited notification event processed by Caller Identity.
 * Stored locally on-device only.
 */
data class NotificationEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val packageName: String,
    val notificationTitle: String,
    val notificationText: String,
    val result: String, // "CLICKED", "IGNORED", "FAILED"
    val reason: String
)

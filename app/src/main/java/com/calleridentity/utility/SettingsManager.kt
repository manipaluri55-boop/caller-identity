package com.calleridentity.utility

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistent configuration, statistics, and local activity history
 * using Android SharedPreferences. All operations are local to the device.
 */
class SettingsManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    // Observable flows for Compose UI updates
    private val _monitoringEnabledFlow = MutableStateFlow(isMonitoringEnabled())
    val monitoringEnabledFlow: StateFlow<Boolean> = _monitoringEnabledFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow(getHistory())
    val historyFlow: StateFlow<List<NotificationEvent>> = _historyFlow.asStateFlow()

    private val _statsFlow = MutableStateFlow(getStats())
    val statsFlow: StateFlow<StatsData> = _statsFlow.asStateFlow()

    data class StatsData(
        val detected: Int,
        val clicked: Int,
        val ignored: Int,
        val failed: Int
    )

    // --- Master Monitoring Control (Default: OFF) ---
    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean(KEY_MONITORING_ENABLED, false)
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply()
        _monitoringEnabledFlow.value = enabled
    }

    // --- Target Package ---
    fun getTargetPackage(): String {
        return prefs.getString(KEY_TARGET_PACKAGE, DEFAULT_TARGET_PACKAGE) ?: DEFAULT_TARGET_PACKAGE
    }

    fun setTargetPackage(pkg: String) {
        val clean = pkg.trim()
        prefs.edit().putString(KEY_TARGET_PACKAGE, clean).apply()
    }

    // --- Excluded Phrase ---
    fun getExcludedPhrase(): String {
        return prefs.getString(KEY_EXCLUDED_PHRASE, DEFAULT_EXCLUDED_PHRASE) ?: DEFAULT_EXCLUDED_PHRASE
    }

    fun setExcludedPhrase(phrase: String) {
        prefs.edit().putString(KEY_EXCLUDED_PHRASE, phrase.trim()).apply()
    }

    // --- Duplicate Protection ---
    fun isDuplicateProtectionEnabled(): Boolean {
        return prefs.getBoolean(KEY_DUPLICATE_PROTECTION, true)
    }

    fun setDuplicateProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DUPLICATE_PROTECTION, enabled).apply()
    }

    fun getDuplicateCooldownMs(): Long {
        return prefs.getLong(KEY_DUPLICATE_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)
    }

    fun setDuplicateCooldownMs(cooldownMs: Long) {
        prefs.edit().putLong(KEY_DUPLICATE_COOLDOWN_MS, cooldownMs).apply()
    }

    // --- Statistics ---
    fun getStats(): StatsData {
        return StatsData(
            detected = prefs.getInt(KEY_STAT_DETECTED, 0),
            clicked = prefs.getInt(KEY_STAT_CLICKED, 0),
            ignored = prefs.getInt(KEY_STAT_IGNORED, 0),
            failed = prefs.getInt(KEY_STAT_FAILED, 0)
        )
    }

    @Synchronized
    fun incrementDetected() {
        val current = prefs.getInt(KEY_STAT_DETECTED, 0) + 1
        prefs.edit().putInt(KEY_STAT_DETECTED, current).apply()
        refreshStats()
    }

    @Synchronized
    fun incrementClicked() {
        val current = prefs.getInt(KEY_STAT_CLICKED, 0) + 1
        prefs.edit().putInt(KEY_STAT_CLICKED, current).apply()
        refreshStats()
    }

    @Synchronized
    fun incrementIgnored() {
        val current = prefs.getInt(KEY_STAT_IGNORED, 0) + 1
        prefs.edit().putInt(KEY_STAT_IGNORED, current).apply()
        refreshStats()
    }

    @Synchronized
    fun incrementFailed() {
        val current = prefs.getInt(KEY_STAT_FAILED, 0) + 1
        prefs.edit().putInt(KEY_STAT_FAILED, current).apply()
        refreshStats()
    }

    @Synchronized
    fun resetStats() {
        prefs.edit()
            .putInt(KEY_STAT_DETECTED, 0)
            .putInt(KEY_STAT_CLICKED, 0)
            .putInt(KEY_STAT_IGNORED, 0)
            .putInt(KEY_STAT_FAILED, 0)
            .apply()
        refreshStats()
    }

    private fun refreshStats() {
        _statsFlow.value = getStats()
    }

    // --- Activity History ---
    @Synchronized
    fun getHistory(): List<NotificationEvent> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<NotificationEvent>>() {}.type
            gson.fromJson<List<NotificationEvent>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun recordEvent(event: NotificationEvent) {
        val current = getHistory().toMutableList()
        current.add(0, event) // Add latest at start
        if (current.size > MAX_HISTORY_SIZE) {
            current.removeAt(current.size - 1)
        }
        val json = gson.toJson(current)
        prefs.edit()
            .putString(KEY_HISTORY, json)
            .apply()
        _historyFlow.value = current
    }

    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        _historyFlow.value = emptyList()
    }

    fun getLastActivity(): NotificationEvent? {
        val list = getHistory()
        return list.firstOrNull()
    }

    fun syncFromDisk() {
        _monitoringEnabledFlow.value = isMonitoringEnabled()
        _statsFlow.value = getStats()
        _historyFlow.value = getHistory()
    }

    companion object {
        const val PREFS_NAME = "caller_identity_prefs"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_TARGET_PACKAGE = "target_package"
        const val KEY_EXCLUDED_PHRASE = "excluded_phrase"
        const val KEY_DUPLICATE_PROTECTION = "duplicate_protection"
        const val KEY_DUPLICATE_COOLDOWN_MS = "duplicate_cooldown_ms"
        const val KEY_STAT_DETECTED = "stat_detected"
        const val KEY_STAT_CLICKED = "stat_clicked"
        const val KEY_STAT_IGNORED = "stat_ignored"
        const val KEY_STAT_FAILED = "stat_failed"
        const val KEY_HISTORY = "history_events"

        const val DEFAULT_TARGET_PACKAGE = "com.driveu.partner"
        const val DEFAULT_EXCLUDED_PHRASE = "you are checked in to receive bookings"
        const val DEFAULT_COOLDOWN_MS = 3000L // 3 seconds cooldown prevents repeated flicker while capturing rapid notifications
        private const val MAX_HISTORY_SIZE = 250

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
            }
        }
    }
}

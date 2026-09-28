package com.calleridentity.utility

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private val isNotificationAccessGrantedState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager.getInstance(applicationContext)

        setContent {
            CallerIdentityApp(
                settingsManager = settingsManager,
                hasNotificationAccess = isNotificationAccessGrantedState.value,
                onOpenNotificationSettings = {
                    try {
                        val intent = NotificationMonitorService.createNotificationSettingsIntent()
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open Notification Settings: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh access permission state and cached data
        isNotificationAccessGrantedState.value = NotificationMonitorService.isNotificationAccessGranted(this)
        settingsManager.syncFromDisk()
    }
}

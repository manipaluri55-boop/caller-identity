package com.calleridentity.utility

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    private val isNotificationAccessGrantedState =
        androidx.compose.runtime.mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager.getInstance(applicationContext)

        setContent {
            CallerIdentityApp(
                settingsManager = settingsManager,
                hasNotificationAccess = isNotificationAccessGrantedState.value,
                onOpenNotificationSettings = {
                    try {
                        val intent =
                            NotificationMonitorService.createNotificationSettingsIntent()
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Could not open Notification Settings: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()

        isNotificationAccessGrantedState.value =
            NotificationMonitorService.isNotificationAccessGranted(this)

        settingsManager.syncFromDisk()
    }
}


@Composable
fun CallerIdentityApp(
    settingsManager: SettingsManager,
    hasNotificationAccess: Boolean,
    onOpenNotificationSettings: () -> Unit
) {
    val monitoringEnabled by
        settingsManager.monitoringEnabledFlow.collectAsState()

    val stats by
        settingsManager.statsFlow.collectAsState()

    val history by
        settingsManager.historyFlow.collectAsState()

    MaterialTheme {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                item {

                    Text(
                        text = "Caller Identity",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Notification monitoring dashboard",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }


                item {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {

                                    Text(
                                        text = "Notification Monitoring",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = if (monitoringEnabled)
                                            "Monitoring is ON"
                                        else
                                            "Monitoring is OFF",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Switch(
                                    checked = monitoringEnabled,
                                    onCheckedChange = {
                                        settingsManager.setMonitoringEnabled(it)
                                    }
                                )
                            }
                        }
                    }
                }


                item {

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor =
                                if (hasNotificationAccess)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.errorContainer
                        )
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    imageVector =
                                        if (hasNotificationAccess)
                                            Icons.Default.CheckCircle
                                        else
                                            Icons.Default.Warning,
                                    contentDescription = null
                                )

                                Spacer(
                                    modifier = Modifier.padding(6.dp)
                                )

                                Text(
                                    text =
                                        if (hasNotificationAccess)
                                            "Notification Access Granted"
                                        else
                                            "Notification Access Required",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(10.dp)
                            )

                            Text(
                                text =
                                    if (hasNotificationAccess)
                                        "The app can receive notification events."
                                    else
                                        "Android notification access must be enabled for this app."
                            )

                            Spacer(
                                modifier = Modifier.height(10.dp)
                            )

                            if (!hasNotificationAccess) {

                                Button(
                                    onClick = onOpenNotificationSettings,
                                    modifier = Modifier.fillMaxWidth()
                                ) {

                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null
                                    )

                                    Spacer(
                                        modifier = Modifier.padding(4.dp)
                                    )

                                    Text("Open Notification Settings")
                                }
                            }
                        }
                    }
                }


                item {

                    Text(
                        text = "Statistics",
                        style = MaterialTheme.typography.titleLarge
                    )
                }


                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        StatCard(
                            title = "Detected",
                            value = stats.detected,
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "Clicked",
                            value = stats.clicked,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }


                item {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        StatCard(
                            title = "Ignored",
                            value = stats.ignored,
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "Failed",
                            value = stats.failed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }


                item {

                    OutlinedButton(
                        onClick = {
                            settingsManager.syncFromDisk()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null
                        )

                        Spacer(
                            modifier = Modifier.padding(4.dp)
                        )

                        Text("Refresh Data")
                    }
                }


                item {

                    Text(
                        text = "Configuration",
                        style = MaterialTheme.typography.titleLarge
                    )
                }


                item {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null
                                )

                                Spacer(
                                    modifier = Modifier.padding(6.dp)
                                )

                                Text(
                                    text = "Target Application",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Spacer(
                                modifier = Modifier.height(8.dp)
                            )

                            Text(
                                text = settingsManager.getTargetPackage(),
                                fontSize = 14.sp
                            )

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Text(
                                text = "Excluded phrase",
                                style = MaterialTheme.typography.titleSmall
                            )

                            Spacer(
                                modifier = Modifier.height(4.dp)
                            )

                            Text(
                                text = settingsManager.getExcludedPhrase(),
                                fontSize = 14.sp
                            )

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Text(
                                text = "Duplicate protection: " +
                                        if (
                                            settingsManager.isDuplicateProtectionEnabled()
                                        )
                                            "ON"
                                        else
                                            "OFF"
                            )
                        }
                    }
                }


                item {

                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleLarge
                    )
                }


                if (history.isEmpty()) {

                    item {

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text = "No notification activity recorded yet.",
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                } else {

                    items(history.take(20)) { event ->

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {

                                Text(
                                    text = event.toString(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }


                item {

                    Spacer(
                        modifier = Modifier.height(20.dp)
                    )

                    Text(
                        text = "Caller Identity",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "Local notification monitoring utility",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}


@Composable
private fun StatCard(
    title: String,
    value: Int,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall
            )
        }
    }
}

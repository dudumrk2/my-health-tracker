package com.myhealthtracker.app.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.myhealthtracker.app.data.health.DailyHealthData
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId

private const val DAILY_STEP_GOAL = 10_000L

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isAvailable by viewModel.healthConnectAvailable.collectAsState()
    val hasPermissions by viewModel.hasPermissions.collectAsState()

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.checkHealthConnectStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row {
                IconButton(onClick = onNavigateToProfile) {
                    Text("⚙️")
                }
                IconButton(onClick = onLogout) {
                    Text("🚪")
                }
            }
        }

        if (!isAvailable) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Health Connect is not installed or not supported on this device.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else if (!hasPermissions) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "App needs permissions to read steps, sleep, and workouts from Health Connect.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionsLauncher.launch(viewModel.permissions) }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }

        when (val state = uiState) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.padding(32.dp))
            }
            is DashboardUiState.Success -> {
                val data = state.healthData
                val hasProfile = state.hasProfile

                if (!hasProfile) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Please complete your profile to enable all features.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onNavigateToProfile) {
                                Text("Complete Profile")
                            }
                        }
                    }
                }

                DashboardContent(
                    data = data,
                    isSyncing = isSyncing,
                    onSyncClick = { viewModel.triggerManualSync() }
                )
            }
            is DashboardUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
    }
}

@Composable
fun DashboardContent(
    data: DailyHealthData?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val steps = data?.steps ?: 0L
            val progress = (steps.toFloat() / DAILY_STEP_GOAL).coerceIn(0f, 1f)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Today's Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$steps / $DAILY_STEP_GOAL steps",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                }
            }
        }

        item {
            val sleepMinutes = data?.sleepMinutes ?: 0
            val hours = sleepMinutes / 60
            val minutes = sleepMinutes % 60

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sleep Duration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${hours}h ${minutes}m",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Sleep Sessions: ${data?.sleepSessions?.size ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text("Today's Workouts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        val workouts = data?.workouts ?: emptyList()
        if (workouts.isEmpty()) {
            item {
                Text(
                    text = "No workouts recorded today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(workouts) { workout ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(workout.type, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val localTime = Instant.ofEpochSecond(workout.startTime.seconds)
                                .atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("hh:mm a"))
                            Text(
                                text = "Started at $localTime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${workout.durationMin} min",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (data?.syncedAt != null) {
                    val syncTime = Instant.ofEpochSecond(data.syncedAt.seconds)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"))
                    Text(
                        text = "Last synced: $syncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (isSyncing) {
                    CircularProgressIndicator()
                } else {
                    Button(onClick = onSyncClick) {
                        Text("Sync Health Connect")
                    }
                }
            }
        }
    }
}

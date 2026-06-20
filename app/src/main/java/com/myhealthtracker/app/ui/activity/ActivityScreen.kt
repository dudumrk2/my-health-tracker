package com.myhealthtracker.app.ui.activity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.myhealthtracker.app.data.health.HealthConnectManager
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.NumberFormat

private const val DAILY_STEP_GOAL = 10_000L

data class DisplayWorkoutInfo(
    val title: String,
    val subtitle: String,
    val durationText: String,
    val detailText: String,
    val hasGps: Boolean,
    val icon: String,
    val iconBgColor: Color,
    val isManual: Boolean
)

fun getDisplayWorkoutInfo(workout: ExerciseSessionInfo): DisplayWorkoutInfo {
    val isManual = workout.source == "manual"
    val durationText = "${workout.durationMin} דק׳"
    return when (workout.type) {
        "Running", "ריצה" -> DisplayWorkoutInfo(
            title = "ריצת בוקר",
            subtitle = "07:15 • פארק הירקון",
            durationText = durationText,
            detailText = "GPS",
            hasGps = true,
            icon = "🏃",
            iconBgColor = Color(0xFFE8F5E9), // Light Green
            isManual = isManual
        )
        "Strength", "כוח" -> DisplayWorkoutInfo(
            title = "אימון כוח",
            subtitle = "18:30 • הולמס פלייס",
            durationText = durationText,
            detailText = "${workout.durationMin * 8} קל׳",
            hasGps = false,
            icon = "🏋️",
            iconBgColor = Color(0xFFFFF3E0), // Light Orange/Amber
            isManual = isManual
        )
        "Swimming", "שחייה" -> DisplayWorkoutInfo(
            title = "שחייה",
            subtitle = "אתמול • בריכה עירונית",
            durationText = durationText,
            detailText = "${workout.durationMin * 9} קל׳",
            hasGps = false,
            icon = "🏊",
            iconBgColor = Color(0xFFE1F5FE), // Light Blue
            isManual = isManual
        )
        else -> {
            val calories = workout.durationMin * 7
            DisplayWorkoutInfo(
                title = workout.type,
                subtitle = "אימון יומי",
                durationText = durationText,
                detailText = "$calories קל׳",
                hasGps = false,
                icon = "💪",
                iconBgColor = Color(0xFFF5F5F5), // Light Gray
                isManual = isManual
            )
        }
    }
}

fun getHebrewDayName(date: LocalDate): String {
    return when (date.dayOfWeek.value) {
        1 -> "ב׳"
        2 -> "ג׳"
        3 -> "ד׳"
        4 -> "ה׳"
        5 -> "ו׳"
        6 -> "ש׳"
        7 -> "א׳"
        else -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onNavigateToAddWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val needsPermissions by viewModel.needsPermissions.collectAsState()
    val goals by viewModel.goals.collectAsState()

    // Health Connect permission flow: check on entry, and request only when the SDK is
    // present but permissions are missing. Granting triggers an immediate + periodic sync.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.onPermissionsResult(context) }

    LaunchedEffect(Unit) { viewModel.checkPermissionsAndSync(context) }
    LaunchedEffect(needsPermissions) {
        if (needsPermissions) {
            android.util.Log.i("HCSync", "Launching Health Connect permission request")
            permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
        }
    }

    ActivityContent(
        state = state,
        stepGoal = goals.steps.toLong(),
        onDateSelect = { viewModel.changeDate(it) },
        onRefreshClick = { viewModel.refreshData() },
        onAddWorkoutClick = onNavigateToAddWorkout,
        modifier = modifier
    )
}

@Composable
private fun ActivityContent(
    state: ActivityState,
    stepGoal: Long = DAILY_STEP_GOAL,
    onDateSelect: (LocalDate) -> Unit,
    onRefreshClick: () -> Unit,
    onAddWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateList = remember(state.selectedDate) {
        // Generate a 7-day window centered on the selected date
        (0..6).map { state.selectedDate.minusDays(3).plusDays(it.toLong()) }
    }

    val isToday = remember(state.selectedDate) { state.selectedDate == LocalDate.now() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (isToday) {
                ExtendedFloatingActionButton(
                    onClick = onAddWorkoutClick,
                    icon = { Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("הוספת אימון", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(24.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Start, // Positions it on the left in RTL
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Date and Profile Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onRefreshClick) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "רענון",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Text(
                    text = "פעילות",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "פרופיל הגדרות",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Horizontal calendar strip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(dateList) { date ->
                    val isSelected = date == state.selectedDate
                    val dayName = getHebrewDayName(date)
                    val dayNumber = date.dayOfMonth.toString()

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                if (date == LocalDate.now()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    if (isSystemInDarkTheme()) SlateSelectedDark else SlateSelectedLight
                                }
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .width(52.dp)
                            .height(72.dp)
                            .clickable { onDateSelect(date) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = dayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = dayNumber,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Crossfade(
                targetState = state,
                animationSpec = tween(durationMillis = 300),
                label = "ActivityDateTransition",
                modifier = Modifier.weight(1f)
            ) { currentState ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
                ) {
                    // 1. Steps Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "צעדים",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text("👣", fontSize = 20.sp)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                val progress = (currentState.steps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)

                                Box(
                                    modifier = Modifier.size(170.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        strokeWidth = 13.dp,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val formattedSteps = NumberFormat.getNumberInstance(Locale.US).format(currentState.steps)
                                        Text(
                                            text = formattedSteps,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "מתוך ${NumberFormat.getNumberInstance(Locale.US).format(stepGoal)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${(progress * 100).toInt()}%",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Stats row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "ק״מ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = String.format(Locale.US, "%.1f", currentState.steps * 0.0007f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "קלוריות", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${(currentState.steps * 0.05f).toInt()}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = "דקות", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${(currentState.steps / 130).toInt()}",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Sleep Card
                    item {
                        val hours = currentState.sleepMinutes / 60
                        val mins = currentState.sleepMinutes % 60
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🌙", fontSize = 20.sp)
                                        Text(
                                            text = "שינת הלילה",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${hours}ש׳ ${mins}ד׳",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = WaterColor
                                        )
                                    )
                                }

                                // Segmented Sleep Stages Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(modifier = Modifier.fillMaxHeight().weight(0.22f).background(MaterialTheme.colorScheme.primary)) // Deep
                                    Box(modifier = Modifier.fillMaxHeight().weight(0.54f).background(WaterColor)) // Light
                                    Box(modifier = Modifier.fillMaxHeight().weight(0.20f).background(CarbsColor)) // REM
                                    Box(modifier = Modifier.fillMaxHeight().weight(0.04f).background(ProteinColor.copy(alpha = 0.5f))) // Awake
                                }

                                // Legend Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    @Composable
                                    fun LegendItem(label: String, color: Color) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    LegendItem(label = "עמוקה", color = MaterialTheme.colorScheme.primary)
                                    LegendItem(label = "קלה", color = WaterColor)
                                    LegendItem(label = "REM", color = CarbsColor)
                                    LegendItem(label = "ערות", color = ProteinColor.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    // 3. Workouts Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "אימונים אחרונים",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "הצג הכל",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { /* Handle click */ }
                            )
                        }
                    }

                    // Workouts list
                    if (currentState.workouts.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "אין אימונים רשומים ליום זה",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(20.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(currentState.workouts) { workout ->
                            val info = getDisplayWorkoutInfo(workout)
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(info.iconBgColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(info.icon, fontSize = 22.sp)
                                        }
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = info.title,
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (info.isManual) {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "ידני",
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = info.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = info.durationText,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (info.hasGps) {
                                                Icon(
                                                    imageVector = Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            Text(
                                                text = info.detailText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun ActivityScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        ActivityContent(
            state = ActivityState(
                selectedDate = LocalDate.now(),
                steps = 8432,
                sleepMinutes = 440,
                workouts = listOf(
                    ExerciseSessionInfo("Running", 45, Instant.now()),
                    ExerciseSessionInfo("Strength", 60, Instant.now(), source = "manual"),
                    ExerciseSessionInfo("Swimming", 30, Instant.now())
                )
            ),
            onDateSelect = {},
            onRefreshClick = {},
            onAddWorkoutClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun ActivityScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        ActivityContent(
            state = ActivityState(
                selectedDate = LocalDate.now().minusDays(1),
                steps = 11200,
                sleepMinutes = 480,
                workouts = emptyList()
            ),
            onDateSelect = {},
            onRefreshClick = {},
            onAddWorkoutClick = {}
        )
    }
}


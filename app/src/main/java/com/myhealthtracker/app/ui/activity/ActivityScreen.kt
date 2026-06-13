package com.myhealthtracker.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val DAILY_STEP_GOAL = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onNavigateToAddWorkout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    ActivityContent(
        state = state,
        onPrevDayClick = { viewModel.selectPreviousDay() },
        onNextDayClick = { viewModel.selectNextDay() },
        onRefreshClick = { viewModel.refreshData() },
        onAddWorkoutClick = onNavigateToAddWorkout,
        modifier = modifier
    )
}

@Composable
private fun ActivityContent(
    state: ActivityState,
    onPrevDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onAddWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("he"))
    val formattedDate = state.selectedDate.format(dateFormatter)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWorkoutClick,
                icon = { Text("💪", fontSize = 18.sp) },
                text = { Text("הוספת אימון", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top Date Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RTL navigation: arrow right goes to previous day
                IconButton(onClick = onPrevDayClick) {
                    Text("◀️", fontSize = 18.sp) // Previous Day (RTL)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (state.selectedDate == LocalDate.now()) "היום" else formattedDate,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(
                    onClick = onNextDayClick,
                    enabled = state.selectedDate.isBefore(LocalDate.now())
                ) {
                    Text("▶️", fontSize = 18.sp) // Next Day (RTL)
                }

                IconButton(onClick = onRefreshClick) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("🔄", fontSize = 18.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. Steps Progress Ring Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val progress = (state.steps.toFloat() / DAILY_STEP_GOAL.toFloat()).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier.size(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { progress },
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                    strokeWidth = 12.dp,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        style = MaterialTheme.typography.headlineLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = "מהיעד",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "${state.steps} / $DAILY_STEP_GOAL צעדים",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // 2. Sleep Card
                item {
                    val hours = state.sleepMinutes / 60
                    val mins = state.sleepMinutes % 60
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(WaterColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🌙", fontSize = 24.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "שינת הלילה",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "עמוקה, קלה ושלבי REM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${hours}ש׳ ${mins}ד׳",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = WaterColor
                                )
                            )
                        }
                    }
                }

                // 3. Workouts Header
                item {
                    Text(
                        text = "אימונים ופעילויות",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Workouts list
                if (state.workouts.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "אין אימונים רשומים ליום זה",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(state.workouts) { workout ->
                        val isManual = workout.source == "manual"

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
                                    // Icon representation
                                    val icon = when (workout.type) {
                                        "Running", "ריצה" -> "🏃"
                                        "Walking", "הליכה" -> "🚶"
                                        "Cycling", "רכיבה" -> "🚴"
                                        "Swimming", "שחייה" -> "🏊"
                                        "Strength", "כוח" -> "🏋️"
                                        "Yoga", "יוגה" -> "🧘"
                                        else -> "💪"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(icon, fontSize = 20.sp)
                                    }
                                    Column {
                                        Text(
                                            text = workout.type,
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        // Badge
                                        SuggestionChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    text = if (isManual) "ידני" else "סונכרן (HC)",
                                                    fontSize = 10.sp
                                                )
                                            },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${workout.durationMin} דק׳",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
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
                    ExerciseSessionInfo("ריצה", 30, Instant.now()),
                    ExerciseSessionInfo("יוגה (ידני)", 45, Instant.now())
                )
            ),
            onPrevDayClick = {},
            onNextDayClick = {},
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
            onPrevDayClick = {},
            onNextDayClick = {},
            onRefreshClick = {},
            onAddWorkoutClick = {}
        )
    }
}

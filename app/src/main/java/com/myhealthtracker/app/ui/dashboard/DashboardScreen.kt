package com.myhealthtracker.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.health.DailyHealthData
import java.time.Instant
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.theme.*

private const val DAILY_STEP_GOAL = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToAddMeasurement: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddMeasurement,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "הוספת מדידה",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Start, // Positions it on the left in RTL
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        DashboardContent(
            state = state,
            onRefreshClick = { viewModel.refreshInsights() },
            onProfileClick = onNavigateToProfile,
            onAddMeasurementClick = onNavigateToAddMeasurement,
            onLogoutClick = onLogout,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    state: DashboardState,
    onRefreshClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddMeasurementClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom Top App Bar (Stitch Design Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "שלום, ${state.profile?.gender?.let { if (it == "נקבה") "אלופה" else "משתמש" } ?: "משתמש"}",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "פרופיל הגדרות",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. AI Unified Insight Card (Stitch Design Style)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary // Brand green background
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "תובנת AI חכמה",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    if (state.isRefreshing) {
                        Text(
                            text = "מחשב תובנות בריאות...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = state.unifiedInsight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // 2. Daily Activity Summary Card (Stitch Design Style)
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
                        Text(
                            text = "פעילות יומית",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${state.todayHealth.steps} צעדים",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Steps Bar Chart representing 7 days (mocked with today highlighted)
                    StepsBarChart(
                        todaySteps = state.todayHealth.steps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(vertical = 8.dp)
                    )

                    // Weekly Exercise Targets
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Aerobic Goal Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "אירובי שבועי",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.weeklyAerobicMinutes} / 150 דק׳",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LinearProgressIndicator(
                                progress = { (state.weeklyAerobicMinutes.toFloat() / 150f).coerceIn(0f, 1f) },
                                color = TealLight,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp)
                            )
                        }
                        // Strength Goal Column
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "אימוני כוח שבועיים",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${state.weeklyStrengthWorkouts} / 2 אימונים",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LinearProgressIndicator(
                                progress = { (state.weeklyStrengthWorkouts.toFloat() / 2f).coerceIn(0f, 1f) },
                                color = ProteinColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 4.dp)
                            )
                        }
                    }

                    // Info banner
                    val missingSteps = (DAILY_STEP_GOAL - state.todayHealth.steps).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (missingSteps > 0) {
                                    "חסרים לך רק $missingSteps צעדים ליעד היומי. הליכה קצרה עכשיו תשפר את עיכול ארוחת הצהריים."
                                } else {
                                    "כל הכבוד! עברת את יעד הצעדים היומי שלך היום."
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 3. Sleep Card (Stitch Design Style)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val sleepHours = state.todayHealth.sleepMinutes / 60
                val sleepMins = state.todayHealth.sleepMinutes % 60
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "שינה",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${sleepHours}ש׳ ${sleepMins}ד׳",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "ממוצע שבועי",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Three Separate Progress Rows
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Deep Sleep
                        SleepProgressRow(
                            label = "שינה עמוקה",
                            percentage = 22,
                            color = MaterialTheme.colorScheme.primary
                        )
                        // Light Sleep
                        SleepProgressRow(
                            label = "שינה קלה",
                            percentage = 54,
                            color = ProteinColor
                        )
                        // REM Sleep
                        SleepProgressRow(
                            label = "REM",
                            percentage = 24,
                            color = CarbsColor
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🌙", fontSize = 16.sp)
                        Text(
                            text = "המלצת AI: כדאי להימנע ממסכים 30 דקות לפני השינה לשיפור ה-REM.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 4. Weekly Food Summary Card (Stitch Design Style)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val totalCalories = state.meals.sumOf { it.totals.calories }
                val mealCount = state.meals.size.coerceAtLeast(1)
                val avgCal = if (totalCalories > 0) totalCalories / mealCount else 1850

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "סיכום תזונה שבועי",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$avgCal",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "קלוריות (ממוצע)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "45%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "פחמימות",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "30%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "חלבון",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Styled Recommendation Box with left border
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .drawBorderLeft(color = MaterialTheme.colorScheme.primary, width = 4.dp)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "השבוע צרכת 15% יותר חלבון מהממוצע שלך, מה שתומך בהתאוששות השרירים שזוהתה.",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 5. Body Metrics Card (Stitch Design Style)
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
                        Text(
                            text = "מדדי גוף",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onAddMeasurementClick() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "הוספת מדידה",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Weights in chronological order; fall back to the profile's setup
                    // weight so the badge reflects real data even before a manual measurement.
                    val weightHistory = state.bodyMeasurements.mapNotNull { it.weightKg }
                    val lastWeight = weightHistory.lastOrNull() ?: state.profile?.weightKg
                    // Waist/hips aren't captured at setup — show the latest real value or "—".
                    val lastWaist = state.bodyMeasurements.lastOrNull { it.waistCm != null }?.waistCm
                    val lastHips = state.bodyMeasurements.lastOrNull { it.hipsCm != null }?.hipsCm
                    val weightDelta = if (weightHistory.size >= 2) {
                        weightHistory.last() - weightHistory[weightHistory.size - 2]
                    } else null

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                    ) {
                        // Drawing Weight Trend Graph
                        WeightTrendGraph(
                            measurements = state.bodyMeasurements,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating weight badge
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (lastWeight != null) "${formatMeasurement(lastWeight)} ק״ג" else "— ק״ג",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (weightDelta != null && kotlin.math.abs(weightDelta) >= 0.05) {
                                    Text(
                                        text = "|",
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        fontSize = 12.sp
                                    )
                                    val direction = if (weightDelta < 0) "ירידה" else "עלייה"
                                    Text(
                                        text = "$direction של ${formatMeasurement(kotlin.math.abs(weightDelta))} ק״ג",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "היקף מותניים",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (lastWaist != null) "${formatMeasurement(lastWaist)} ס״מ" else "—",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column {
                            Text(
                                text = "היקף ירכיים",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (lastHips != null) "${formatMeasurement(lastHips)} ס״מ" else "—",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StepsBarChart(
    todaySteps: Long,
    modifier: Modifier = Modifier
) {
    val stepsList = listOf(3500L, todaySteps.coerceAtLeast(100L), 6200L, 2200L, 5900L, 5100L, 4100L)
    val maxSteps = stepsList.maxOrNull()?.coerceAtLeast(1L)?.toFloat() ?: 10000f

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        stepsList.forEachIndexed { index, steps ->
            val heightPercent = (steps.toFloat() / maxSteps).coerceIn(0.1f, 1f)
            val isToday = index == 1 // Highlighting index 1 to match highlighted column in Stitch mockup

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightPercent)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
fun SleepProgressRow(
    label: String,
    percentage: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { percentage.toFloat() / 100f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

/** Trims a trailing ".0" so 75.0 shows as "75" while 75.5 stays "75.5". */
private fun formatMeasurement(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

@Composable
fun WeightTrendGraph(
    measurements: List<BodyMeasurement>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    // A single weight (e.g. from setup) is drawn as a flat baseline; no synthetic trend.
    val weights = measurements.mapNotNull { it.weightKg }
    val points = if (weights.size == 1) weights + weights else weights

    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val paddingX = 15f
        val paddingY = 20f

        val minWeight = points.minOrNull() ?: 70.0
        val maxWeight = points.maxOrNull() ?: 80.0
        val weightRange = (maxWeight - minWeight).coerceAtLeast(0.5)

        val stepX = (width - paddingX * 2) / (points.size - 1)
        val path = Path()
        val fillPath = Path()

        val coordinates = points.mapIndexed { index, weight ->
            val x = paddingX + index * stepX
            val ratioY = (weight - minWeight) / weightRange
            val y = height - paddingY - (ratioY * (height - paddingY * 2)).toFloat()
            Offset(x, y)
        }

        if (coordinates.isNotEmpty()) {
            path.moveTo(coordinates[0].x, coordinates[0].y)
            fillPath.moveTo(coordinates[0].x, height)
            fillPath.lineTo(coordinates[0].x, coordinates[0].y)

            for (i in 0 until coordinates.size - 1) {
                val from = coordinates[i]
                val to = coordinates[i + 1]
                val controlPoint1 = Offset(from.x + stepX / 2f, from.y)
                val controlPoint2 = Offset(to.x - stepX / 2f, to.y)

                path.cubicTo(
                    controlPoint1.x, controlPoint1.y,
                    controlPoint2.x, controlPoint2.y,
                    to.x, to.y
                )
                fillPath.cubicTo(
                    controlPoint1.x, controlPoint1.y,
                    controlPoint2.x, controlPoint2.y,
                    to.x, to.y
                )
            }

            fillPath.lineTo(coordinates.last().x, height)
            fillPath.close()

            // Draw gradient shadow
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.15f),
                    primaryColor.copy(alpha = 0.0f)
                ),
                startY = coordinates.minOf { it.y },
                endY = height
            )
            drawPath(path = fillPath, brush = fillBrush)

            // Draw main sparkline
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw last point dot
            val lastPoint = coordinates.last()
            drawCircle(
                color = primaryColor,
                radius = 5.dp.toPx(),
                center = lastPoint
            )
            drawCircle(
                color = surfaceColor,
                radius = 2.5.dp.toPx(),
                center = lastPoint
            )
        }
    }
}

// Extension to draw left border for recommendation box
fun Modifier.drawBorderLeft(color: Color, width: androidx.compose.ui.unit.Dp) = this.drawWithContent {
    drawContent()
    val widthPx = width.toPx()
    drawLine(
        color = color,
        start = Offset(0f, 0f),
        end = Offset(0f, size.height),
        strokeWidth = widthPx
    )
}


@Preview(showBackground = true, name = "Light Theme")
@Composable
fun DashboardScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        DashboardContent(
            state = DashboardState(
                profile = UserProfile(birthYear = 1990, weightKg = 75.0, heightCm = 178.0, gender = "זכר"),
                todayHealth = DailyHealthData(steps = 8432, sleepMinutes = 435),
                meals = listOf(
                    MealEntry(mealId = "1", date = "2026-06-12", loggedAt = Instant.now(), inputType = "text", description = "", items = emptyList(), totals = MealTotals(430, 21, 38, 21)),
                    MealEntry(mealId = "2", date = "2026-06-12", loggedAt = Instant.now(), inputType = "text", description = "", items = emptyList(), totals = MealTotals(500, 53, 55, 5))
                ),
                bodyMeasurements = listOf(
                    BodyMeasurement("2026-06-10", 75.5, 88.0, 102.0),
                    BodyMeasurement("2026-06-11", 75.2, 88.0, 102.0),
                    BodyMeasurement("2026-06-12", 74.2, 88.0, 102.0)
                ),
                unifiedInsight = "נראה שהשינה העמוקה שלך השתפרה ב-15% מאז שהתחלת להפחית פחמימות בארוחות הערב. השילוב עם פעילות גופנית מתונה בבוקר יוצר אפקט חיובי על קצב חילוף החומרים שלך."
            ),
            onRefreshClick = {},
            onProfileClick = {},
            onAddMeasurementClick = {},
            onLogoutClick = {}
        )
    }
}

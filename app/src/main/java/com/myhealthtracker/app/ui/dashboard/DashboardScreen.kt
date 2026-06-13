package com.myhealthtracker.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

    DashboardContent(
        state = state,
        onRefreshClick = { viewModel.refreshInsights() },
        onProfileClick = onNavigateToProfile,
        onAddMeasurementClick = onNavigateToAddMeasurement,
        onLogoutClick = onLogout,
        modifier = modifier
    )
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
        // Custom Top App Bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "היי, ${state.profile?.gender?.let { if (it == "נקבה") "אלופה" else "אלוף" } ?: "משתמש"}! 👋",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "MyHealthTracker",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            actions = {
                IconButton(onClick = onRefreshClick) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("🔄", fontSize = 20.sp)
                    }
                }
                IconButton(onClick = onProfileClick) {
                    Text("⚙️", fontSize = 20.sp)
                }
                IconButton(onClick = onLogoutClick) {
                    Text("🚪", fontSize = 20.sp)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. AI Unified Insight Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("💡", fontSize = 20.sp)
                        Text(
                            text = "תובנות AI יומיות",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.isRefreshing) {
                        Text(
                            text = "מחשב תובנות בריאות...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = state.unifiedInsight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // 2. Daily Activity Summary Card
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
                    Text(
                        text = "פעילות יומית",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${state.todayHealth.steps} / $DAILY_STEP_GOAL",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "צעדים היום",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // Circular steps progress indicator
                        val progress = (state.todayHealth.steps.toFloat() / DAILY_STEP_GOAL.toFloat()).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier.size(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                strokeWidth = 6.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        text = "המלצת פעילות: כדאי להשלים הליכה של 15 דקות בערב להגעה ליעד.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Sleep Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val hours = state.todayHealth.sleepMinutes / 60
                val mins = state.todayHealth.sleepMinutes % 60
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
                            text = "שינה ושיקום",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "🌙 ${hours}ש׳ ${mins}ד׳",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = WaterColor
                            )
                        )
                    }

                    // Sleep Stages Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "שלבי שינה (דקות)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            // Deep: 2h 15m (135m), REM: 1h 45m (105m), Light: remaining (200m)
                            Box(
                                modifier = Modifier
                                    .weight(135f)
                                    .fillMaxHeight()
                                    .background(WaterColor)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(105f)
                                    .fillMaxHeight()
                                    .background(ProteinColor)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(200f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(WaterColor))
                                Text("עמוקה (135ד׳)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ProteinColor))
                                Text("REM (105ד׳)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
                                Text("קלה (200ד׳)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        text = "המלצת שינה: איכות השינה מעולה. הקפד על שעת שינה קבועה הלילה.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Weekly Food Summary Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Compute average calories/macros of meals
                val totalCalories = state.meals.sumOf { it.totals.calories }
                val totalProtein = state.meals.sumOf { it.totals.proteinG }
                val totalCarbs = state.meals.sumOf { it.totals.carbsG }
                val totalFat = state.meals.sumOf { it.totals.fatG }
                val mealCount = state.meals.size.coerceAtLeast(1)
                
                val avgCal = totalCalories / mealCount
                val avgProtein = totalProtein / mealCount
                val avgCarbs = totalCarbs / mealCount
                val avgFat = totalFat / mealCount

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "תקציר תזונה שבועי",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$avgCal קק״ל",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = FatColor
                            )
                            Text(
                                text = "ממוצע יומי",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("חלבון", style = MaterialTheme.typography.labelSmall, color = ProteinColor, fontWeight = FontWeight.Bold)
                                Text("${avgProtein}ג׳", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("פחמימות", style = MaterialTheme.typography.labelSmall, color = CarbsColor, fontWeight = FontWeight.Bold)
                                Text("${avgCarbs}ג׳", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("שומן", style = MaterialTheme.typography.labelSmall, color = FatColor, fontWeight = FontWeight.Bold)
                                Text("${avgFat}ג׳", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        text = "תובנת תזונה: צריכת החלבון היומית ממוצעת. מומלץ להעלות במעט את החלבון בימי אימון.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Body Metrics Card
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
                            text = "מדדי גוף ומגמות",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Button(
                            onClick = onAddMeasurementClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("+ מדידה", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val lastMeasurement = state.bodyMeasurements.lastOrNull()
                    if (lastMeasurement != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text("משקל", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${lastMeasurement.weightKg ?: "--"} ק״ג", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("מותניים", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${lastMeasurement.waistCm ?: "--"} ס״מ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("ירכיים", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${lastMeasurement.hipsCm ?: "--"} ס״מ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Drawing weight trend line graph
                        WeightTrendGraph(
                            measurements = state.bodyMeasurements,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text = "עדיין אין מדידות גוף. הוסף מדידה ראשונה כדי להתחיל לעקוב.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun WeightTrendGraph(
    measurements: List<BodyMeasurement>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val points = measurements.mapNotNull { it.weightKg }
    
    if (points.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("ממתין למדידות נוספות להצגת מגמה", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 10f

        val minWeight = points.minOrNull() ?: 0.0
        val maxWeight = points.maxOrNull() ?: 100.0
        val weightRange = (maxWeight - minWeight).coerceAtLeast(1.0)

        val stepX = (width - padding * 2) / (points.size - 1)
        val path = Path()

        points.forEachIndexed { index, weight ->
            val x = padding + index * stepX
            val ratioY = (weight - minWeight) / weightRange
            // Mirror Y coordinate since canvas 0,0 is top-left
            val y = height - padding - (ratioY * (height - padding * 2)).toFloat()

            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }

            // Draw data point circles
            drawCircle(
                color = primaryColor,
                radius = 4.dp.toPx(),
                center = Offset(x, y)
            )
        }

        // Draw the trend line
        drawPath(
            path = path,
            color = primaryColor,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun DashboardScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        DashboardContent(
            state = DashboardState(
                profile = UserProfile(birthYear = 1990, weightKg = 75.0, heightCm = 178.0, gender = "זכר"),
                todayHealth = DailyHealthData(steps = 8432, sleepMinutes = 440),
                meals = listOf(
                    MealEntry(mealId = "1", date = "2026-06-12", loggedAt = Instant.now(), inputType = "text", description = "", items = emptyList(), totals = MealTotals(430, 21, 38, 21)),
                    MealEntry(mealId = "2", date = "2026-06-12", loggedAt = Instant.now(), inputType = "text", description = "", items = emptyList(), totals = MealTotals(500, 53, 55, 5))
                ),
                bodyMeasurements = listOf(
                    BodyMeasurement("2026-06-10", 75.5, 85.0, 97.0),
                    BodyMeasurement("2026-06-11", 75.2, 84.5, 96.5),
                    BodyMeasurement("2026-06-12", 75.0, 84.0, 96.0)
                ),
                unifiedInsight = "הפעילות שלך היום מצוינת! צעדת כבר 8,432 צעדים מתוך יעד של 10,000. כדאי ללכת עוד קצת בערב כדי להגיע ליעד."
            ),
            onRefreshClick = {},
            onProfileClick = {},
            onAddMeasurementClick = {},
            onLogoutClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun DashboardScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        DashboardContent(
            state = DashboardState(
                profile = UserProfile(birthYear = 1990, weightKg = 75.0, heightCm = 178.0, gender = "נקבה"),
                todayHealth = DailyHealthData(steps = 6200, sleepMinutes = 390),
                meals = emptyList(),
                bodyMeasurements = emptyList(),
                unifiedInsight = "אין תובנות זמינות כרגע. לחץ על רענון לקבלת תובנת AI."
            ),
            onRefreshClick = {},
            onProfileClick = {},
            onAddMeasurementClick = {},
            onLogoutClick = {}
        )
    }
}

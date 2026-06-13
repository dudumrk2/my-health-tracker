package com.myhealthtracker.app.ui.food

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.data.MealEntry
import com.myhealthtracker.app.data.MealItem
import com.myhealthtracker.app.data.MealTotals
import com.myhealthtracker.app.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAILY_CALORIE_TARGET = 2000
private const val DAILY_WATER_TARGET_ML = 2000 // 2L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    viewModel: FoodViewModel,
    onNavigateToAddMeal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    FoodContent(
        state = state,
        onPrevDayClick = { viewModel.selectPreviousDay() },
        onNextDayClick = { viewModel.selectNextDay() },
        onRefreshClick = { viewModel.refreshAdvice() },
        onQuickAddWaterClick = { viewModel.quickAddWater(it) },
        onAddMealClick = onNavigateToAddMeal,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodContent(
    state: FoodState,
    onPrevDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onQuickAddWaterClick: (Int) -> Unit,
    onAddMealClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("he"))
    val formattedDate = state.selectedDate.format(dateFormatter)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMealClick,
                icon = { Text("🥗", fontSize = 18.sp) },
                text = { Text("הוספת ארוחה", fontWeight = FontWeight.Bold) },
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
            // Date Selection Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                IconButton(onClick = onNextDayClick) {
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
                // 1. AI Suggestion Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("✨", fontSize = 20.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "המלצה תזונתית יומית",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                if (state.isRefreshing) {
                                    Text(
                                        text = "מעדכן המלצות...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                    )
                                } else {
                                    Text(
                                        text = state.aiAdvice,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Nutrition Summary Card (Calories + Macros)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "סיכום תזונתי יומי",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            
                            // Calories progress
                            val calorieProgress = (state.totals.calories.toFloat() / DAILY_CALORIE_TARGET).coerceIn(0f, 1f)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${state.totals.calories} / $DAILY_CALORIE_TARGET קק״ל",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "אנרגיה שנצרכה",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${(calorieProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            
                            LinearProgressIndicator(
                                progress = { calorieProgress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )

                            // Macros progress bars
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Protein: target 120g (mock)
                                MacroProgressBar(
                                    name = "חלבון",
                                    value = state.totals.proteinG,
                                    target = 120,
                                    color = ProteinColor
                                )
                                // Carbs: target 220g (mock)
                                MacroProgressBar(
                                    name = "פחמימות",
                                    value = state.totals.carbsG,
                                    target = 220,
                                    color = CarbsColor
                                )
                                // Fat: target 65g (mock)
                                MacroProgressBar(
                                    name = "שומן",
                                    value = state.totals.fatG,
                                    target = 65,
                                    color = FatColor
                                )
                            }
                        }
                    }
                }

                // 3. Water Log Card
                item {
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
                                    text = "יומן מים",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "💧 ${state.waterIntakeMl} / $DAILY_WATER_TARGET_ML מ״ל",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = WaterColor
                                    )
                                )
                            }

                            // Quick add buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { onQuickAddWaterClick(250) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WaterColor.copy(alpha = 0.15f),
                                        contentColor = WaterColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ כוס (250 מ״ל)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { onQuickAddWaterClick(500) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = WaterColor.copy(alpha = 0.15f),
                                        contentColor = WaterColor
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ בקבוק (500 מ״ל)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 4. Meal Journal Header
                item {
                    Text(
                        text = "יומן ארוחות",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Meal items
                if (state.meals.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "לא נרשמו ארוחות ביום זה",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(state.meals) { meal ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (meal.inputType == "image") "📷" else "📝",
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = meal.description.take(30) + if (meal.description.length > 30) "..." else "",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Text(
                                        text = "${meal.totals.calories} קק״ל",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                                
                                // Items Breakdown list
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    meal.items.forEach { item ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${item.name} (${item.quantity})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "${item.calories} קק״ל",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                                
                                // Macros row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "חלבון: ${meal.totals.proteinG}ג׳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ProteinColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "פחמימות: ${meal.totals.carbsG}ג׳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CarbsColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "שומן: ${meal.totals.fatG}ג׳",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FatColor,
                                        fontWeight = FontWeight.Bold
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

@Composable
fun MacroProgressBar(
    name: String,
    value: Int,
    target: Int,
    color: Color
) {
    val progress = (value.toFloat() / target).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = "$value / $target ג׳",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = color,
            trackColor = color.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun FoodScreenPreviewLight() {
    MyHealthTrackerTheme(darkTheme = false) {
        FoodContent(
            state = FoodState(
                selectedDate = LocalDate.now(),
                meals = listOf(
                    MealEntry(
                        date = "2026-06-12",
                        inputType = "text",
                        description = "ארוחת בוקר קלה",
                        items = listOf(
                            MealItem("חביתה", "1 מנה", 180, 14, 2, 12),
                            MealItem("לחם מלא", "2 פרוסות", 160, 6, 30, 2)
                        ),
                        totals = MealTotals(340, 20, 32, 14)
                    )
                ),
                waterIntakeMl = 1250,
                totals = MealTotals(340, 20, 32, 14),
                aiAdvice = "צריכת החלבון שלך יפה מאוד ועומדת על 20 גרם."
            ),
            onPrevDayClick = {},
            onNextDayClick = {},
            onRefreshClick = {},
            onQuickAddWaterClick = {},
            onAddMealClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun FoodScreenPreviewDark() {
    MyHealthTrackerTheme(darkTheme = true) {
        FoodContent(
            state = FoodState(
                selectedDate = LocalDate.now().minusDays(1),
                meals = emptyList(),
                waterIntakeMl = 500,
                totals = MealTotals(0, 0, 0, 0),
                aiAdvice = "עדיין לא רשמת ארוחות היום."
            ),
            onPrevDayClick = {},
            onNextDayClick = {},
            onRefreshClick = {},
            onQuickAddWaterClick = {},
            onAddMealClick = {}
        )
    }
}

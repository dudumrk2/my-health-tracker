package com.myhealthtracker.app.ui.food

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.goals.HealthGoals
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealItem
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.model.MealQuality
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.ui.meal.MealQualityCard
import com.myhealthtracker.app.ui.meal.MealRecommendationCard
import java.time.Instant
import com.myhealthtracker.app.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import com.myhealthtracker.app.notification.QuickActionsNotificationManager.WATER_STEP_ML

private const val DAILY_CALORIE_TARGET = 2500
private const val DAILY_WATER_TARGET_ML = 3000 // 3.0L

private fun getHebrewDayName(date: LocalDate): String {
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

private fun formatInstantToTime(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

private fun formatLiters(ml: Int): String {
    val liters = ml / 1000f
    return when {
        ml % 1000 == 0 -> String.format(Locale.US, "%.0f", liters)
        ml % 100 == 0 -> String.format(Locale.US, "%.1f", liters)
        else -> String.format(Locale.US, "%.2f", liters)
    }
}

private fun getMealTitle(description: String, index: Int): String {
    val descLower = description.lowercase()
    return when {
        descLower.contains("בוקר") || descLower.contains("יוגורט") -> "ארוחת בוקר"
        descLower.contains("צהריים") || descLower.contains("עוף") || descLower.contains("חזה") -> "ארוחת צהריים"
        descLower.contains("ערב") || descLower.contains("סלמון") -> "ארוחת ערב"
        index == 0 -> "ארוחת בוקר"
        index == 1 -> "ארוחת צהריים"
        index == 2 -> "נשנוש"
        else -> "ארוחה"
    }
}

private fun getMealEmoji(description: String): String {
    val descLower = description.lowercase()
    return when {
        descLower.contains("יוגורט") || descLower.contains("גרנולה") -> "🥣"
        descLower.contains("עוף") || descLower.contains("סלט") || descLower.contains("חזה") -> "🍛"
        descLower.contains("תפוח") || descLower.contains("פרי") || descLower.contains("שקדים") -> "🍎"
        descLower.contains("סלמון") || descLower.contains("דג") -> "🍣"
        else -> "🥗"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodScreen(
    viewModel: FoodViewModel,
    onNavigateToAddMeal: () -> Unit,
    onNavigateToProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val goals by viewModel.goals.collectAsState()

    FoodContent(
        state = state,
        goals = goals,
        onDateSelect = { viewModel.changeDate(it) },
        onRefreshClick = { viewModel.refreshAdvice() },
        onQuickAddWaterClick = { viewModel.quickAddWater(it) },
        onAddMealClick = onNavigateToAddMeal,
        onProfileClick = onNavigateToProfile,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodContent(
    state: FoodState,
    goals: HealthGoals = GoalCalculator.compute(UserProfile()),
    onDateSelect: (LocalDate) -> Unit,
    onRefreshClick: () -> Unit,
    onQuickAddWaterClick: (Int) -> Unit,
    onAddMealClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    var selectedMealTitle by remember { mutableStateOf("") }

    val dateList = remember(state.selectedDate) {
        // Generate a 7-day window centered on the selected date
        (0..6).map { state.selectedDate.minusDays(3).plusDays(it.toLong()) }
    }

    val isToday = remember(state.selectedDate) { state.selectedDate == LocalDate.now() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            floatingActionButton = {
                if (isToday) {
                    ExtendedFloatingActionButton(
                        onClick = onAddMealClick,
                        icon = { Text("🥗", fontSize = 18.sp) },
                        text = { Text("הוספת ארוחה", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp)
                    )
                }
            },
            floatingActionButtonPosition = FabPosition.Start
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Right in RTL = Refresh (circular arrow)
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "רענן",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Center = Title
                    Text(
                        text = "אוכל",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Left in RTL = Profile
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "פרופיל",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Horizontal calendar strip (forcing LTR so it goes from left to right chronologically)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(dateList) { date ->
                            val isSelected = date == state.selectedDate
                            val isCurrentDay = date == LocalDate.now()
                            val dayName = getHebrewDayName(date)
                            val dayNumber = date.dayOfMonth.toString()

                            // White reads better than the theme's onPrimary on the slate
                            // background (onPrimary defaults to a dark hue in dark mode).
                            val selectedContentColor =
                                if (isCurrentDay) MaterialTheme.colorScheme.onPrimary else Color.White

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = when {
                                        isSelected && isCurrentDay -> MaterialTheme.colorScheme.primary
                                        isSelected -> if (isSystemInDarkTheme()) SlateSelectedDark else SlateSelectedLight
                                        else -> MaterialTheme.colorScheme.surface
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
                                        color = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = dayNumber,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedContent(
                    targetState = state.selectedDate,
                    transitionSpec = {
                        // Later day → slide in from the right (toward the tapped card);
                        // earlier day → slide in from the left. Strip is forced LTR.
                        val direction = if (targetState.isAfter(initialState)) {
                            AnimatedContentTransitionScope.SlideDirection.Left
                        } else {
                            AnimatedContentTransitionScope.SlideDirection.Right
                        }
                        (slideIntoContainer(direction, tween(300)) + fadeIn(tween(300))) togetherWith
                            (slideOutOfContainer(direction, tween(300)) + fadeOut(tween(300)))
                    },
                    label = "FoodDateTransition",
                    modifier = Modifier.weight(1f)
                ) { _ ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        // 1. AI Suggestion Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "המלצה חכמה להיום",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = if (state.isRefreshing) "מחשב המלצות..." else state.aiAdvice,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Nutrition Summary Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val consumedCal = state.totals.calories
                                    val calorieTarget = goals.caloriesKcal
                                    val remainingCal = (calorieTarget - consumedCal).coerceAtLeast(0)
                                    
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "נותרו עוד",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = String.format("%,d", remainingCal),
                                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "קלוריות",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "מתוך $calorieTarget",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    // Macros progress bars side-by-side
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            MacroProgressBarHorizontal(
                                                name = "חלבון",
                                                value = state.totals.proteinG,
                                                target = goals.proteinG,
                                                color = ProteinColor
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            MacroProgressBarHorizontal(
                                                name = "פחמימות",
                                                value = state.totals.carbsG,
                                                target = goals.carbsG,
                                                color = CarbsColor
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            MacroProgressBarHorizontal(
                                                name = "שומן",
                                                value = state.totals.fatG,
                                                target = goals.fatG,
                                                color = FatColor
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Water Log Card
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                            text = "שתיית מים",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${formatLiters(state.waterIntakeMl)} / ${formatLiters(goals.waterMl)} ליטר",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = WaterColor
                                            )
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 10 Water Drops, proportional to the daily goal so the
                                        // last drop fills exactly at the target regardless of step size.
                                        val dropStep = WATER_STEP_ML
                                        val totalDrops = (goals.waterMl.toFloat() / dropStep.toFloat()).roundToInt().coerceAtLeast(1)
                                        val filledDropsCount = (state.waterIntakeMl.toFloat() / dropStep.toFloat()).roundToInt().coerceIn(0, totalDrops)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            for (i in 0 until totalDrops) {
                                                val isFilled = i < filledDropsCount
                                                val alpha by animateFloatAsState(
                                                    targetValue = if (isFilled) 1f else 0.2f,
                                                    animationSpec = tween(durationMillis = 500),
                                                    label = "WaterDropAlpha"
                                                )
                                                Text(
                                                    text = "💧",
                                                    fontSize = 20.sp,
                                                    modifier = Modifier.alpha(alpha)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Quick Add Button
                                        if (isToday) {
                                            Button(
                                                onClick = { onQuickAddWaterClick(WATER_STEP_ML) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                ),
                                                shape = RoundedCornerShape(20.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("הוספת מים", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
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
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Meal items
                        if (state.meals.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "לא נרשמו ארוחות ביום זה",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            itemsIndexed(state.meals) { index, meal ->
                                val mealTitle = getMealTitle(meal.description, index)
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedMealTitle = mealTitle
                                            selectedMeal = meal
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Right Side: Circular Avatar + Details
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Circular Image/Avatar
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = getMealEmoji(meal.description),
                                                    fontSize = 28.sp
                                                )
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = getMealTitle(meal.description, index),
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = meal.description.ifEmpty { "ארוחה ללא תיאור" },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        // Left Side: Time and Calories
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatInstantToTime(meal.loggedAt),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${meal.totals.calories}",
                                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "קלוריות",
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

            selectedMeal?.let { meal ->
                MealDetailSheet(
                    meal = meal,
                    title = selectedMealTitle,
                    onDismiss = { selectedMeal = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealDetailSheet(
    meal: MealEntry,
    title: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatInstantToTime(meal.loggedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "סגור",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = meal.description.ifEmpty { "ארוחה ללא תיאור" },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "סה״כ קלוריות",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${meal.totals.calories} קק״ל",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MacroProgressBarHorizontal(
                                name = "חלבון",
                                value = meal.totals.proteinG,
                                target = 150,
                                color = ProteinColor
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroProgressBarHorizontal(
                                name = "פחמימות",
                                value = meal.totals.carbsG,
                                target = 250,
                                color = CarbsColor
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroProgressBarHorizontal(
                                name = "שומן",
                                value = meal.totals.fatG,
                                target = 70,
                                color = FatColor
                            )
                        }
                    }
                }
            }

            // Ingredients list
            if (meal.items.isNotEmpty()) {
                Text(
                    text = "מרכיבי הארוחה",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                meal.items.forEach { item ->
                    MealDetailItemCard(item = item)
                }
            }

            val quality = meal.quality
            if (quality != null) {
                MealQualityCard(quality = quality)
            }

            val recommendation = meal.recommendation
            if (!recommendation.isNullOrEmpty()) {
                MealRecommendationCard(recommendation = recommendation)
            }
        }
    }
}

@Composable
private fun MealDetailItemCard(item: MealItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name.ifEmpty { "פריט ללא שם" },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.quantity,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MealDetailItemMacro(label = "קלו׳", value = "${item.calories}")
                MealDetailItemMacro(label = "חלבון", value = "${item.proteinG}g")
                MealDetailItemMacro(label = "פחמימות", value = "${item.carbsG}g")
                MealDetailItemMacro(label = "שומן", value = "${item.fatG}g")
            }
        }
    }
}

@Composable
private fun MealDetailItemMacro(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun MacroProgressBarHorizontal(
    name: String,
    value: Int,
    target: Int,
    color: Color
) {
    val progressTarget = (value.toFloat() / target).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(durationMillis = 800),
        label = "MacroProgress"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$value/$target" + "g",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )
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
                        mealId = "1",
                        date = "2026-06-12",
                        loggedAt = Instant.now(),
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
            onDateSelect = {},
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
            onDateSelect = {},
            onRefreshClick = {},
            onQuickAddWaterClick = {},
            onAddMealClick = {}
        )
    }
}

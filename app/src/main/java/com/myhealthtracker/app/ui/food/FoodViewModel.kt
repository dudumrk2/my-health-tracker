package com.myhealthtracker.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.insights.InsightCategory
import com.myhealthtracker.app.data.insights.InsightsRefreshException
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.insights.pickInsight
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.WaterRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class FoodState(
    val selectedDate: LocalDate = LocalDate.now(),
    val meals: List<MealEntry> = emptyList(),
    val waterIntakeMl: Int = 0,
    val totals: MealTotals = MealTotals(0, 0, 0, 0),
    val aiAdvice: String = "",
    val aiAdviceLabel: String? = null,
    val isRefreshing: Boolean = false
)

class FoodViewModel(
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val waterRepository: WaterRepository = AppContainer.waterRepository,
    private val insightsRepository: InsightsRepository = AppContainer.insightsRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val state: StateFlow<FoodState> = combine(
        _selectedDate,
        mealRepository.meals,
        waterRepository.waterLog,
        _isRefreshing,
        insightsRepository.insights
    ) { date, allMeals, waterMap, isRefreshing, insights ->
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

        val dailyMeals = allMeals.filter { it.date == dateStr }
        val waterIntake = waterMap[dateStr] ?: 0

        val totalCal = dailyMeals.sumOf { it.totals.calories }
        val totalProtein = dailyMeals.sumOf { it.totals.proteinG }
        val totalCarbs = dailyMeals.sumOf { it.totals.carbsG }
        val totalFat = dailyMeals.sumOf { it.totals.fatG }

        val nutrition = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.NUTRITION)

        FoodState(
            selectedDate = date,
            meals = dailyMeals,
            waterIntakeMl = waterIntake,
            totals = MealTotals(totalCal, totalProtein, totalCarbs, totalFat),
            aiAdvice = if (isRefreshing) "" else nutrition.text,
            aiAdviceLabel = if (isRefreshing) null else nutrition.label,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoodState()
    )

    fun changeDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun selectPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun selectNextDay() {
        val today = LocalDate.now()
        if (_selectedDate.value.isBefore(today)) {
            _selectedDate.value = _selectedDate.value.plusDays(1)
        }
    }

    fun quickAddWater(amountMl: Int) {
        val dateStr = _selectedDate.value.format(DateTimeFormatter.ISO_LOCAL_DATE)
        waterRepository.addWater(dateStr, amountMl)
    }

    /** Triggers a backend refresh of today's insights; the snapshot listener pushes the new value. */
    fun refreshAdvice() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                insightsRefresher.refresh()
            } catch (_: InsightsRefreshException) {
                // Friendly failure: keep the last known insight; user can retry.
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

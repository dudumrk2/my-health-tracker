package com.myhealthtracker.app.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.water.WaterRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.MealTotals
import com.myhealthtracker.app.data.FakeRepository
import kotlinx.coroutines.delay
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
    val isRefreshing: Boolean = false
)

class FoodViewModel(
    private val mealRepository: MealRepository = FakeRepository,
    private val waterRepository: WaterRepository = FakeRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _forcedAdvice = MutableStateFlow<String?>(null)

    val state: StateFlow<FoodState> = combine(
        _selectedDate,
        mealRepository.meals,
        waterRepository.waterLog,
        _isRefreshing,
        _forcedAdvice
    ) { date, allMeals, waterMap, isRefreshing, forcedAdvice ->
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        
        // Filter meals for selected date
        val dailyMeals = allMeals.filter { it.date == dateStr }
        val waterIntake = waterMap[dateStr] ?: 0

        // Sum calories and macros
        val totalCal = dailyMeals.sumOf { it.totals.calories }
        val totalProtein = dailyMeals.sumOf { it.totals.proteinG }
        val totalCarbs = dailyMeals.sumOf { it.totals.carbsG }
        val totalFat = dailyMeals.sumOf { it.totals.fatG }

        // Compute AI advice based on current meals or forced advice
        val advice = when {
            forcedAdvice != null -> forcedAdvice
            isRefreshing -> ""
            dailyMeals.isEmpty() -> "עדיין לא רשמת ארוחות היום. רשום ארוחה לקבלת המלצה תזונתית מבוססת AI!"
            totalProtein < 50 -> "צריכת החלבון שלך נמוכה כרגע (רק ${totalProtein}ג׳). כדאי לשלב חלבון רזה כמו ביצים, חזה עוף או טופו בארוחה הבאה."
            totalCarbs > 200 -> "צריכת הפחמימות שלך גבוהה יחסית (${totalCarbs}ג׳). כדאי לשקול להעדיף פחמימות מורכבות בעלות ערך גליקמי נמוך."
            else -> "מאזן המאקרו שלך נראה מצוין היום! המשך להקפיד על שתיית מים מרובה."
        }

        FoodState(
            selectedDate = date,
            meals = dailyMeals,
            waterIntakeMl = waterIntake,
            totals = MealTotals(totalCal, totalProtein, totalCarbs, totalFat),
            aiAdvice = advice,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FoodState()
    )

    fun changeDate(date: LocalDate) {
        _selectedDate.value = date
        _forcedAdvice.value = null
    }

    fun selectPreviousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
        _forcedAdvice.value = null
    }

    fun selectNextDay() {
        // Upper bound constraint check (disable navigating to future dates)
        val today = LocalDate.now()
        if (_selectedDate.value.isBefore(today)) {
            _selectedDate.value = _selectedDate.value.plusDays(1)
            _forcedAdvice.value = null
        }
    }

    fun quickAddWater(amountMl: Int) {
        val dateStr = _selectedDate.value.format(DateTimeFormatter.ISO_LOCAL_DATE)
        waterRepository.addWater(dateStr, amountMl)
    }

    fun refreshAdvice() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1200) // Simulate AI calculation delay
            _forcedAdvice.value = "המלצת AI מעודכנת: לפי המאזן הנוכחי, אנו ממליצים להוסיף חטיף חלבון או יוגורט דל שומן כארוחת ביניים לקראת האימון."
            _isRefreshing.value = false
        }
    }
}

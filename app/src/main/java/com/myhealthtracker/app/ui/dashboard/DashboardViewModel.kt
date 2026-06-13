package com.myhealthtracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.MealEntry
import com.myhealthtracker.app.data.BodyMeasurement
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.profile.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DashboardState(
    val profile: UserProfile? = null,
    val todayHealth: DailyHealthData = DailyHealthData(),
    val meals: List<MealEntry> = emptyList(),
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val unifiedInsight: String = "",
    val isRefreshing: Boolean = false
)

class DashboardViewModel : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _forcedInsight = MutableStateFlow<String?>(null)

    val state: StateFlow<DashboardState> = combine(
        FakeRepository.profile,
        FakeRepository.healthDaily,
        FakeRepository.meals,
        FakeRepository.bodyMeasurements,
        FakeRepository.aiInsights,
        _isRefreshing,
        _forcedInsight
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val profile = array[0] as UserProfile?
        @Suppress("UNCHECKED_CAST")
        val healthDaily = array[1] as Map<String, DailyHealthData>
        @Suppress("UNCHECKED_CAST")
        val meals = array[2] as List<MealEntry>
        @Suppress("UNCHECKED_CAST")
        val bodyMeasurements = array[3] as List<BodyMeasurement>
        @Suppress("UNCHECKED_CAST")
        val insights = array[4] as Map<String, Pair<String, String>>
        val isRefreshing = array[5] as Boolean
        val forcedInsight = array[6] as String?

        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val todayHealth = healthDaily[todayStr] ?: DailyHealthData(date = todayStr)
        
        // Filter meals for this week (last 7 days)
        val today = LocalDate.now()
        val weeklyMeals = meals.filter { meal ->
            try {
                val mealDate = LocalDate.parse(meal.date)
                !mealDate.isBefore(today.minusDays(7))
            } catch (e: Exception) {
                false
            }
        }

        // Generate unified insight based on time of day (or forced on refresh)
        val insightPair = insights[todayStr]
        val resolvedInsight = when {
            forcedInsight != null -> forcedInsight
            isRefreshing -> ""
            insightPair != null -> {
                val currentHour = LocalTime.now().hour
                if (currentHour >= 15) {
                    insightPair.first // Today's insight
                } else {
                    insightPair.second // Tomorrow's (highlights from yesterday/morning prep)
                }
            }
            else -> "אין תובנות זמינות כרגע. לחץ על רענון לקבלת תובנת AI."
        }

        DashboardState(
            profile = profile,
            todayHealth = todayHealth,
            meals = weeklyMeals,
            bodyMeasurements = bodyMeasurements.sortedBy { it.date },
            unifiedInsight = resolvedInsight,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardState()
    )

    fun refreshInsights() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1500) // Simulate AI calculation delay
            
            val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val insights = FakeRepository.aiInsights.value[todayStr]
            
            // On manual refresh, we always force-load "today's" updated insight
            _forcedInsight.value = insights?.first ?: "הנתונים שלך מעודכנים! צריכת החלבון והצעדים שלך מראים מגמה מעולה היום. כדאי להקפיד לשתות עוד מים."
            _isRefreshing.value = false
        }
    }
}

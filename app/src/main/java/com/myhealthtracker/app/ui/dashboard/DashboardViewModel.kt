package com.myhealthtracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.data.profile.genderToHebrew
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.BodyMeasurement
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

class DashboardViewModel(
    private val profileRepository: ProfileRepository = FakeRepository,
    private val healthRepository: HealthRepository = FakeRepository,
    private val mealRepository: MealRepository = FakeRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository = FakeRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _forcedInsight = MutableStateFlow<String?>(null)

    private val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val state: StateFlow<DashboardState> = combine(
        profileRepository.getUserProfile("mock_uid"),
        healthRepository.getDailyHealthData("mock_uid", todayStr),
        mealRepository.meals,
        bodyMeasurementRepository.bodyMeasurements,
        FakeRepository.aiInsights,
        _isRefreshing,
        _forcedInsight
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val profileResult = array[0] as Result<UserProfile?>
        val rawProfile = profileResult.getOrNull()
        
        val localizedProfile = rawProfile?.let {
            it.copy(gender = genderToHebrew(it.gender))
        }

        @Suppress("UNCHECKED_CAST")
        val healthResult = array[1] as Result<DailyHealthData?>
        val todayHealth = healthResult.getOrNull() ?: DailyHealthData(date = todayStr)

        @Suppress("UNCHECKED_CAST")
        val meals = array[2] as List<MealEntry>
        @Suppress("UNCHECKED_CAST")
        val bodyMeasurements = array[3] as List<BodyMeasurement>
        @Suppress("UNCHECKED_CAST")
        val insights = array[4] as Map<String, Pair<String, String>>
        val isRefreshing = array[5] as Boolean
        val forcedInsight = array[6] as String?

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
            profile = localizedProfile,
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

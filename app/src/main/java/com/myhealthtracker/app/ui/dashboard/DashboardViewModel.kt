package com.myhealthtracker.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.profile.ProfileRepository
import com.myhealthtracker.app.data.profile.UserProfile
import com.myhealthtracker.app.data.profile.genderToHebrew
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.insights.InsightCategory
import com.myhealthtracker.app.data.insights.InsightsRefreshException
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.insights.model.DailyInsights
import com.myhealthtracker.app.data.insights.pickInsight
import com.myhealthtracker.app.data.meal.MealRepository
import com.myhealthtracker.app.data.body.BodyMeasurementRepository
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.data.FakeRepository
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

data class DashboardState(
    val profile: UserProfile? = null,
    val todayHealth: DailyHealthData = DailyHealthData(),
    val meals: List<MealEntry> = emptyList(),
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val unifiedInsight: String = "",
    val unifiedInsightLabel: String? = null,
    val isRefreshing: Boolean = false
)

class DashboardViewModel(
    private val profileRepository: ProfileRepository = FakeRepository,
    private val healthRepository: HealthRepository = FakeRepository,
    private val mealRepository: MealRepository = FakeRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository = FakeRepository,
    private val insightsRepository: InsightsRepository = AppContainer.insightsRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val state: StateFlow<DashboardState> = combine(
        profileRepository.getUserProfile("mock_uid"),
        healthRepository.getDailyHealthData("mock_uid", todayStr),
        mealRepository.meals,
        bodyMeasurementRepository.bodyMeasurements,
        insightsRepository.insights,
        _isRefreshing
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
        val insights = array[4] as DailyInsights?
        val isRefreshing = array[5] as Boolean

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

        val general = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.GENERAL)

        DashboardState(
            profile = localizedProfile,
            todayHealth = todayHealth,
            meals = weeklyMeals,
            bodyMeasurements = bodyMeasurements.sortedBy { it.date },
            unifiedInsight = if (isRefreshing) "" else general.text,
            unifiedInsightLabel = if (isRefreshing) null else general.label,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardState()
    )

    /** Triggers a backend refresh of today's insights; the snapshot listener pushes the new value. */
    fun refreshInsights() {
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

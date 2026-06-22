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
import com.myhealthtracker.app.data.insights.InsightCategory
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.insights.pickInsight
import com.myhealthtracker.app.data.model.MealEntry
import com.myhealthtracker.app.data.celebration.CelebrationController
import com.myhealthtracker.app.data.celebration.CelebrationRules
import com.myhealthtracker.app.data.goals.GoalCalculator
import com.myhealthtracker.app.data.model.BodyMeasurement
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    val weeklyAerobicMinutes: Int = 0,
    val weeklyStrengthWorkouts: Int = 0,
    val isRefreshing: Boolean = false
)

class DashboardViewModel(
    private val profileRepository: ProfileRepository = AppContainer.profileRepository,
    private val healthRepository: HealthRepository = AppContainer.healthRepository,
    private val mealRepository: MealRepository = AppContainer.mealRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository = AppContainer.bodyMeasurementRepository,
    private val insightsRepository: InsightsRepository = AppContainer.insightsRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher,
    private val celebrationController: CelebrationController = AppContainer.celebrationController,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val uid = uidProvider()

    private val profileFlow =
        if (uid != null) profileRepository.getUserProfile(uid) else flowOf(Result.success<UserProfile?>(null))
    private val healthFlow =
        if (uid != null) {
            val startDate = LocalDate.now().minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val endDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            healthRepository.getWeeklyHealthData(uid, startDate, endDate)
        } else {
            flowOf(Result.success(emptyList()))
        }

    val state: StateFlow<DashboardState> = combine(
        profileFlow,
        healthFlow,
        mealRepository.meals,
        bodyMeasurementRepository.bodyMeasurements,
        insightsRepository.insights,
        _isRefreshing
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val rawProfile = (array[0] as Result<UserProfile?>).getOrNull()
        val localizedProfile = rawProfile?.let { it.copy(gender = genderToHebrew(it.gender)) }

        // Evaluated per emission (not cached on the instance) so a process kept alive across
        // midnight uses the current date — otherwise a stale dedup key would suppress the new
        // day's step-goal celebration until the app is restarted.
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        @Suppress("UNCHECKED_CAST")
        val healthList = (array[1] as Result<List<DailyHealthData>>).getOrNull() ?: emptyList()
        val todayHealth = healthList.find { it.date == todayStr } ?: DailyHealthData(date = todayStr)

        @Suppress("UNCHECKED_CAST")
        val meals = array[2] as List<MealEntry>
        @Suppress("UNCHECKED_CAST")
        val bodyMeasurements = array[3] as List<BodyMeasurement>
        val insights = array[4] as? com.myhealthtracker.app.data.insights.model.DailyInsights
        val isRefreshing = array[5] as Boolean

        // Filter meals for this week (last 7 days)
        val weeklyMeals = meals.filter { meal ->
            try {
                !LocalDate.parse(meal.date).isBefore(today.minusDays(7))
            } catch (e: Exception) {
                false
            }
        }

        var weeklyAerobicMinutes = 0
        var weeklyStrengthWorkouts = 0
        for (day in healthList) {
            for (w in day.workouts) {
                val type = w.type.lowercase()
                if (listOf("ריצה", "הליכה", "אופניים", "ספינינג", "זומבה", "running", "walking", "cycling", "spinning", "zumba").contains(type)) {
                    weeklyAerobicMinutes += w.durationMin
                } else if (listOf("כוח", "פונקציונלי", "strength", "functional").contains(type)) {
                    weeklyStrengthWorkouts += 1
                }
            }
        }

        // Unified general insight: today's sentence if present, else last night's
        // tomorrow emphasis, else a "not ready" message. Selection is presence-based.
        val unifiedInsight = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.GENERAL).text

        // ── Celebrations (state-derived; each fires once via the dedup store) ──
        // Goals use the raw (English-gender) profile; localizedProfile would break
        // GoalCalculator's "male"/"female" checks.
        val goals = GoalCalculator.compute(rawProfile ?: UserProfile())

        celebrationController.tryCelebrate(
            CelebrationRules.stepGoal(todayHealth.steps, goals.steps, todayStr)
        )

        val weekStart = CelebrationRules.startOfWeekSunday(today)
        val weeklyWorkoutCount = healthList
            .filter { day ->
                runCatching {
                    val d = LocalDate.parse(day.date)
                    !d.isBefore(weekStart) && !d.isAfter(today)
                }.getOrDefault(false)
            }
            .sumOf { it.workouts.size }
        celebrationController.tryCelebrate(
            CelebrationRules.workoutMilestones(weeklyWorkoutCount, CelebrationRules.weekId(today))
        )

        val yesterdayStr = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val yesterdayMeals = meals.filter { it.date == yesterdayStr }
        celebrationController.tryCelebrate(
            CelebrationRules.calorieGoalYesterday(
                yesterdayMealCount = yesterdayMeals.size,
                yesterdayCalories = yesterdayMeals.sumOf { it.totals.calories },
                goalCalories = goals.caloriesKcal,
                yesterday = yesterdayStr
            )
        )

        DashboardState(
            profile = localizedProfile,
            todayHealth = todayHealth,
            meals = weeklyMeals,
            bodyMeasurements = bodyMeasurements.sortedBy { it.date },
            unifiedInsight = unifiedInsight,
            weeklyAerobicMinutes = weeklyAerobicMinutes,
            weeklyStrengthWorkouts = weeklyStrengthWorkouts,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardState()
    )

    /** On-demand AI refresh of today's insights; the snapshot listener updates the card. */
    fun refreshInsights() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                insightsRefresher.refresh()
            } catch (_: Exception) {
                // Friendly failure: keep the last known insight; the user can retry.
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

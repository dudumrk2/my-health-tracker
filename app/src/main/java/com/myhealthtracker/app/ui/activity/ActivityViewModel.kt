package com.myhealthtracker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.insights.InsightCategory
import com.myhealthtracker.app.data.insights.InsightsRefreshException
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.data.insights.InsightsRepository
import com.myhealthtracker.app.data.insights.pickInsight
import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ActivityState(
    val selectedDate: LocalDate = LocalDate.now(),
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val workouts: List<ExerciseSessionInfo> = emptyList(),
    val activityInsight: String = "",
    val activityInsightLabel: String? = null,
    val sleepInsight: String = "",
    val sleepInsightLabel: String? = null,
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val healthRepository: HealthRepository = FakeRepository,
    private val insightsRepository: InsightsRepository = AppContainer.insightsRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _isRefreshing = MutableStateFlow(false)

    private val _healthData = _selectedDate.flatMapLatest { date ->
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        healthRepository.getDailyHealthData("mock_uid", dateStr)
    }

    val state: StateFlow<ActivityState> = combine(
        _selectedDate,
        _healthData,
        _isRefreshing,
        insightsRepository.insights
    ) { date, healthResult, isRefreshing, insights ->
        val healthData = healthResult.getOrNull() ?: DailyHealthData(date = date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        val activity = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.ACTIVITY)
        val sleep = pickInsight(insights?.today, insights?.tomorrow, InsightCategory.SLEEP)
        ActivityState(
            selectedDate = date,
            steps = healthData.steps,
            sleepMinutes = healthData.sleepMinutes,
            workouts = healthData.workouts,
            activityInsight = if (isRefreshing) "" else activity.text,
            activityInsightLabel = if (isRefreshing) null else activity.label,
            sleepInsight = if (isRefreshing) "" else sleep.text,
            sleepInsightLabel = if (isRefreshing) null else sleep.label,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActivityState()
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

    /** Triggers a backend refresh of today's insights; the snapshot listener pushes the new value. */
    fun refreshData() {
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

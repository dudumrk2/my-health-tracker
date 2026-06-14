package com.myhealthtracker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.insights.InsightsRefresher
import com.myhealthtracker.app.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ActivityState(
    val selectedDate: LocalDate = LocalDate.now(),
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val workouts: List<ExerciseSessionInfo> = emptyList(),
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val healthRepository: HealthRepository = AppContainer.healthRepository,
    private val insightsRefresher: InsightsRefresher = AppContainer.insightsRefresher,
    private val uidProvider: () -> String? = { AppContainer.currentUid() }
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _isRefreshing = MutableStateFlow(false)

    private val _healthData = _selectedDate.flatMapLatest { date ->
        val uid = uidProvider()
        if (uid == null) {
            flowOf(Result.success<DailyHealthData?>(null))
        } else {
            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            healthRepository.getDailyHealthData(uid, dateStr)
        }
    }

    val state: StateFlow<ActivityState> = combine(
        _selectedDate,
        _healthData,
        _isRefreshing
    ) { date, healthResult, isRefreshing ->
        val healthData = healthResult.getOrNull() ?: DailyHealthData(date = date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        ActivityState(
            selectedDate = date,
            steps = healthData.steps,
            sleepMinutes = healthData.sleepMinutes,
            workouts = healthData.workouts,
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

    /** Triggers the backend insights refresh; the snapshot listeners update the cards. */
    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                insightsRefresher.refresh()
            } catch (_: Exception) {
                // Friendly failure: keep the last known data; the user can retry.
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

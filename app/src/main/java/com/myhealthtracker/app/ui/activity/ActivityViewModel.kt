package com.myhealthtracker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.health.HealthRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
import com.myhealthtracker.app.data.FakeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val isRefreshing: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val healthRepository: HealthRepository = FakeRepository
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

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1000) // Simulate refresh delay
            _isRefreshing.value = false
        }
    }
}

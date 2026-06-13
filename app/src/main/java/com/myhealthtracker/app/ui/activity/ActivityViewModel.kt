package com.myhealthtracker.app.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myhealthtracker.app.data.FakeRepository
import com.myhealthtracker.app.data.health.ExerciseSessionInfo
import com.myhealthtracker.app.data.health.DailyHealthData
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

data class ActivityState(
    val selectedDate: LocalDate = LocalDate.now(),
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val workouts: List<ExerciseSessionInfo> = emptyList(),
    val isRefreshing: Boolean = false
)

class ActivityViewModel : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val state: StateFlow<ActivityState> = combine(
        _selectedDate,
        FakeRepository.healthDaily,
        _isRefreshing
    ) { date, healthMap, isRefreshing ->
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val healthData = healthMap[dateStr] ?: DailyHealthData(date = dateStr)
        
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
        _selectedDate.value = _selectedDate.value.plusDays(1)
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(1000) // Simulate refresh delay
            _isRefreshing.value = false
        }
    }
}
